package com.bombbird.terminalcontrol2.traffic

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*
import ktx.collections.GdxArray
import ktx.collections.GdxSet
import ktx.math.plus
import ktx.math.times
import kotlin.math.max
import kotlin.math.min

val disallowedCallsigns = GdxSet<String>()

/**
 * Class for storing traffic modes for the game
 *
 * [ARRIVALS_TO_CONTROL] will instruct the game server to spawn arrivals to maintain an arrival count
 *
 * [FLOW_RATE] will instruct the game server to spawn arrivals at a set rate
 */
object TrafficMode {
    const val ARRIVALS_TO_CONTROL: Byte = 0
    const val FLOW_RATE: Byte = 1
}

/**
 * Creates an arrival with a randomly selected airport and STAR
 * @param airports the full list of airports in the game world
 * @param gs the gameServer to instantiate the aircraft in
 */
fun createRandomArrival(airports: GdxArray<Airport>, gs: GameServer) {
    val airportDist = CumulativeDistribution<Entity>()
    for (i in 0 until airports.size) { airports[i]?.entity?.apply {
        if (hasNot(ArrivalClosed.mapper)) airportDist.add(this, get(AirportInfo.mapper)?.tfcRatio?.toFloat() ?: return@apply)
    }}
    airportDist.generateNormalized()
    val arpt = if (airportDist.size() == 0) return else airportDist.value()
    val spawnData = generateRandomTrafficForAirport(arpt) ?: return
    val callsign = generateRandomCallsign(spawnData.first, spawnData.second, gs) ?: return
    // Choose random aircraft type from the array of possible aircraft
    val icaoType = spawnData.third.random() ?: run {
        Gdx.app.log("TrafficTools", "No aircraft available for ${spawnData.first} in ${arpt[AirportInfo.mapper]?.icaoCode}")
        "B77W"
    }
    createArrival(callsign, icaoType, arpt, gs)
}

/**
 * Creates a new arrival aircraft with the input data
 * @param callsign the callsign of the aircraft
 * @param icaoType the ICAO aircraft type
 * @param airport the airport the arrival is flying into
 * @param gs the gameServer to instantiate the aircraft in
 * */
fun createArrival(callsign: String, icaoType: String, airport: Entity, gs: GameServer) {
    if (gs.aircraft.containsKey(callsign)) {
        Gdx.app.log("TrafficTools", "Aircraft with callsign $callsign already exists")
        return
    }
    val randomStar = randomStar(airport)
    val starRoute = randomStar?.getRandomSTARRouteForRunway() ?: Route()
    val origStarRoute = Route().apply { setToRouteCopy(starRoute) }
    val spawnPos = calculateArrivalSpawnPoint(starRoute, gs.primarySector)

    gs.aircraft.put(callsign, Aircraft(callsign, spawnPos.first, spawnPos.second, 0f, icaoType, FlightType.ARRIVAL, false).apply {
        entity += ArrivalAirport(airport[AirportInfo.mapper]?.arptId ?: 0)
        val alt = calculateArrivalSpawnAltitude(entity, airport, origStarRoute, spawnPos.first, spawnPos.second, starRoute)
        entity[Altitude.mapper]?.altitudeFt = alt
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(-spawnPos.third - 180)
        val aircraftPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: AircraftTypeData.AircraftPerfData()
        val ias = calculateArrivalSpawnIAS(origStarRoute, starRoute, alt, aircraftPerf)
        val tas = calculateTASFromIAS(alt, ias.toFloat())
        val clearedAlt = min(15000, (alt / 1000).toInt() * 1000)
        entity[Speed.mapper]?.apply {
            speedKts = tas
            // Set to vertical speed required to reach cleared altitude in 10 seconds, capped by the minimum vertical speed
            val minVertSpd = calculateMinVerticalSpd(aircraftPerf, alt, tas, 0f, approachExpedite = false, takingOff = false, takeoffClimb = false)
            vertSpdFpm = max(minVertSpd, (clearedAlt - alt) * 6)
        }
        entity += ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState(randomStar?.name ?: "", starRoute, Route(),
                if (starRoute.size == 0) (spawnPos.third + MAG_HDG_DEV).toInt().toShort() else null, null,
                clearedAlt, ias)
        ))
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = modulateHeading(spawnPos.third + 180)
        }
        entity += InitialArrivalSpawn()
        if (alt > 10000) entity += DecelerateTo240kts()
        gs.sendAircraftSpawn(this)
    })
}

/**
 * Chooses a random STAR from all STARs available for the input airport
 *
 * This also takes into account any noise restrictions
 * @param airport the airport to use
 * @return the [SidStar.STAR] chosen
 * */
private fun randomStar(airport: Entity): SidStar.STAR? {
    val currentTime = UsabilityFilter.DAY_ONLY // TODO change depending on whether night operations are active
    val availableStars = GdxArray<SidStar.STAR>()
    val runwaysAvailable = HashSet<String>()
    airport[RunwayChildren.mapper]?.rwyMap?.values()?.forEach { rwy ->
        // Add names of runways that are active for landing to set
        if (rwy.entity.has(ActiveLanding.mapper)) runwaysAvailable.add(rwy.entity[RunwayInfo.mapper]?.rwyName ?: return@forEach)
    }
    airport[STARChildren.mapper]?.starMap?.values()?.forEach { star ->
        // Add to list of eligible STARs if both runway and time restriction checks passes
        if ((star.rwyLegs.keys() intersect  runwaysAvailable).isEmpty()) return@forEach
        if (star.timeRestriction != UsabilityFilter.DAY_AND_NIGHT && star.timeRestriction != currentTime) return@forEach
        availableStars.add(star)
    }

    if (availableStars.isEmpty) {
        Gdx.app.log("TrafficTools", "No STAR available for ${airport[AirportInfo.mapper]?.name}")
        return null
    }
    return availableStars.random()
}

fun appTestArrival(gs: GameServer) {
    gs.aircraft.put("SHIBA4", Aircraft("SHIBA4", -200f, -125f, 2000f, "A359", FlightType.ARRIVAL, false).apply {
        entity += ArrivalAirport(0)
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(-70f)
        val ias = 240.toShort()
        val tas = calculateTASFromIAS(2000f, ias.toFloat())
        entity[Speed.mapper]?.apply {
            speedKts = tas
            vertSpdFpm = 0f
        }
        val clearedAlt = 2000
        entity += ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState("NTN1A", Route(), Route(),
                70, null, clearedAlt, ias)
        ))
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = 70f
        }
        entity += InitialArrivalSpawn()
    })

    gs.aircraft.put("SHIBA5", Aircraft("SHIBA5", 650f, 15f, 1800f, "B77W", FlightType.ARRIVAL, false).apply {
        entity += ArrivalAirport(1)
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(110f)
        val ias = 190.toShort()
        val tas = calculateTASFromIAS(1800f, ias.toFloat())
        entity[Speed.mapper]?.apply {
            speedKts = tas
            vertSpdFpm = 0f
        }
        val clearedAlt = 1800
        entity += ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState("", Route(), Route(),
                250, null, clearedAlt, ias)
        ))
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = 250f
        }
        entity += InitialArrivalSpawn()
    })
}

/**
 * Creates a new departure aircraft with the input data
 * @param airport the airport the departure is flying from
 * @param rwy the runway the departure is departing from
 * @param gs the gameServer to instantiate the aircraft in
 * */
fun createRandomDeparture(airport: Entity, rwy: Entity, gs: GameServer) {
    val spawnData = generateRandomTrafficForAirport(airport) ?: return
    val callsign = generateRandomCallsign(spawnData.first, spawnData.second, gs) ?: return
    // Choose random aircraft type from the array of possible aircraft
    val icaoType = spawnData.third.random() ?: run {
        Gdx.app.log("TrafficTools", "No aircraft available for ${spawnData.first} in ${airport[AirportInfo.mapper]?.icaoCode}")
        "B77W"
    }
    createDeparture(callsign, icaoType, rwy, gs)
}

/**
 * Creates a new departure aircraft on the input runway
 * @param callsign the callsign of the aircraft
 * @param icaoType the ICAO aircraft type
 * @param rwy the runway the departure aircraft will be using
 * @param gs the gameServer to instantiate the aircraft in
 * */
private fun createDeparture(callsign: String, icaoType: String, rwy: Entity, gs: GameServer) {
    val rwyPos = rwy[Position.mapper] ?: return
    val rwyDir = rwy[Direction.mapper] ?: return

    val rwyAlt = rwy[Altitude.mapper]?.altitudeFt ?: return
    val rwyInfo = rwy[RunwayInfo.mapper] ?: return
    val rwyTakeoffPosLength = rwyInfo.intersectionTakeoffM
    val spawnPos = Vector2(rwyPos.x, rwyPos.y) + rwyDir.trackUnitVector * mToPx(rwyTakeoffPosLength.toFloat())
    gs.aircraft.put(callsign, Aircraft(callsign, spawnPos.x, spawnPos.y, rwyAlt, icaoType, FlightType.DEPARTURE, false).apply {
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(rwyDir.trackUnitVector.angleDeg() - 90) // Runway heading
        // Calculate headwind component for takeoff
        val tailwind = rwyDir.let { dir -> entity[Position.mapper]?.let { pos ->
            val wind = getClosestAirportWindVector(pos.x, pos.y)
            calculateIASFromTAS(rwyAlt, pxpsToKt(wind.dot(dir.trackUnitVector)))
        }} ?: 0f
        entity[Speed.mapper]?.speedKts = -tailwind
        val acPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: return@apply
        entity += WaitingTakeoff()
        entity += TakeoffRoll(max(1.5f, calculateRequiredAcceleration(0, calculateTASFromIAS(rwyAlt, acPerf.vR + tailwind).toInt().toShort(), ((rwy[RunwayInfo.mapper]?.lengthM ?: 3800) - 1000) * MathUtils.random(0.75f, 1f))))
        val sid = randomSid(rwy)
        val rwyName = rwy[RunwayInfo.mapper]?.rwyName ?: ""
        val initClimb = sid?.rwyInitialClimbs?.get(rwyName) ?: 3000
        entity += ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState(sid?.name ?: "", sid?.getRandomSIDRouteForRunway(rwyName) ?: Route(), Route(),
                null, null, initClimb, acPerf.climbOutSpeed)
        ))
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = initClimb
            targetIasKt = acPerf.climbOutSpeed
            targetHdgDeg = convertWorldAndRenderDeg(rwyDir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
        }
        Timer.schedule(object : Timer.Task() {
            override fun run() {
                gs.postRunnableAfterEngineUpdate {
                    entity.remove<WaitingTakeoff>()
                }
            }
        }, 5f)
    })
}

/**
 * Chooses a random SID from all SIDs available for the input runway
 *
 * This also takes into account any noise restrictions
 * @param rwy the runway to use
 * @return the [SidStar.SID] chosen
 * */
private fun randomSid(rwy: Entity): SidStar.SID? {
    val currentTime = UsabilityFilter.DAY_ONLY // TODO change depending on whether night operations are active
    val availableSids = GdxArray<SidStar.SID>()
    val rwyName = rwy[RunwayInfo.mapper]?.rwyName
    rwy[RunwayInfo.mapper]?.airport?.entity?.get(SIDChildren.mapper)?.sidMap?.values()?.forEach { sid ->
        // Add to list of eligible SIDs if both runway and time restriction checks passes
        if (!sid.rwyInitialClimbs.containsKey(rwyName)) return@forEach
        if (sid.timeRestriction != UsabilityFilter.DAY_AND_NIGHT && sid.timeRestriction != currentTime) return@forEach
        availableSids.add(sid)
    }

    if (availableSids.isEmpty) {
        Gdx.app.log("TrafficTools", "No SID available for runway $rwyName")
        return null
    }
    return availableSids.random()
}

/**
 * Chooses a random set of traffic data to be used for aircraft generation
 * @param airport the airport to generate traffic for
 * @return a triple, the first being the ICAO code of the airline/registration of private aircraft, second being whether
 * the aircraft is a private aircraft, third being the possible aircraft types that can be chosen
 */
private fun generateRandomTrafficForAirport(airport: Entity): Triple<String, Boolean, GdxArray<String>>? {
    return airport[RandomAirlineData.mapper]?.airlineDistribution?.let { dist ->
        if (dist.size() == 0) {
            Gdx.app.log("TrafficTools", "No airlines available for ${airport[AirportInfo.mapper]?.arptId} ${airport[AirportInfo.mapper]?.icaoCode}")
            null
        } else dist.value()
    }
}

/**
 * Generates a callsign for the input airline
 * @param airline the 3-letter ICAO code of the airline, or the full registration of the aircraft if it is a private
 * aircraft
 * @param private whether the aircraft is a private aircraft
 * @param gs the gameServer in which to check for duplicate callsigns
 * */
private fun generateRandomCallsign(airline: String, private: Boolean, gs: GameServer): String? {
    return if (private) {
        // Check if private aircraft already exists
        if (gs.aircraft.containsKey(airline)) null else airline
    } else {
        // Generate random number between 1 - 9999
        var flightNo: Int
        var loopCount = 0
        do {
            // If no suitable callsign found after 30 tries, return from this function (something is wrong)
            if (loopCount > 30) {
                Gdx.app.log("TrafficTools", "Failed to generate random callsign in time for $airline, aircraft will not be created")
                return null
            }
            flightNo = if (MathUtils.randomBoolean(0.1f)) MathUtils.random(1000, 9999) else MathUtils.random(1, 999)
            loopCount++
            // Check if it already exists, or is a disallowed callsign
        } while (gs.aircraft.containsKey(airline + flightNo) || disallowedCallsigns.contains(airline + flightNo))
        airline + flightNo
    }
}

/**
 * Gets all available approaches for the input airport
 * @param airport the airport to use
 * @return a [GdxArray] of strings containing the eligible approach names
 * */
fun getAvailableApproaches(airport: Entity): GdxArray<String> {
    val currentTime = UsabilityFilter.DAY_ONLY // TODO change depending on whether night operations are active
    val array = GdxArray<String>().apply { add("Approach") }
    val rwys = airport[RunwayChildren.mapper]?.rwyMap ?: return array
    airport[ApproachChildren.mapper]?.approachMap?.values()?.forEach { app ->
        app.entity[ApproachInfo.mapper]?.also {
            // Add to list of eligible approaches if its runway is active for landings and time restriction checks passes
            if (rwys[it.rwyId]?.entity?.get(ActiveLanding.mapper) == null) return@forEach
            if (app.timeRestriction != UsabilityFilter.DAY_AND_NIGHT && app.timeRestriction != currentTime) return@forEach
            array.add(it.approachName)
        }
    }
    return array
}
