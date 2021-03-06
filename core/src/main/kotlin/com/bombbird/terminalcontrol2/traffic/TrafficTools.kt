package com.bombbird.terminalcontrol2.traffic

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.systems.TrafficSystemInterval
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*
import ktx.collections.GdxArray
import ktx.collections.GdxSet
import ktx.math.plus
import ktx.math.times
import kotlin.math.*

val disallowedCallsigns = GdxSet<String>()

/**
 * Class for storing traffic modes for the game
 *
 * [NORMAL] is [ARRIVALS_TO_CONTROL] except landing planes successfully will gradually raise the count, while separation
 * conflicts will decrease the count
 *
 * [ARRIVALS_TO_CONTROL] will instruct the game server to spawn arrivals to maintain an arrival count
 *
 * [FLOW_RATE] will instruct the game server to spawn arrivals at a set rate
 */
object TrafficMode {
    const val NORMAL: Byte = 0
    const val ARRIVALS_TO_CONTROL: Byte = 1
    const val FLOW_RATE: Byte = 2
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
        entity += ArrivalRouteZone().apply { starZone.addAll(getZonesForRoute(origStarRoute)) }
        val alt = calculateArrivalSpawnAltitude(entity, airport, origStarRoute, spawnPos.first, spawnPos.second, starRoute)
        entity[Altitude.mapper]?.altitudeFt = alt
        val dir = (entity[Direction.mapper] ?: Direction()).apply { trackUnitVector.rotateDeg(-spawnPos.third - 180) }
        val aircraftPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: AircraftTypeData.AircraftPerfData()
        val ias = calculateArrivalSpawnIAS(origStarRoute, starRoute, alt, aircraftPerf)
        val tas = calculateTASFromIAS(alt, ias.toFloat())
        val clearedAlt = min((ceil(MAX_ALT / 1000f).roundToInt() - 1) * 1000, (alt / 1000).toInt() * 1000)
        val speed = (entity[Speed.mapper] ?: Speed()).apply {
            speedKts = tas
            // Set to vertical speed required to reach cleared altitude in 10 seconds, capped by the minimum vertical speed
            val minVertSpd = calculateMinVerticalSpd(aircraftPerf, alt, tas, 0f, approachExpedite = false, takingOff = false, takeoffClimb = false)
            vertSpdFpm = max(minVertSpd, (clearedAlt - alt) * 6)
        }
        val affectedByWind = (entity[AffectedByWind.mapper] ?: AffectedByWind()).apply { getClosestAirportWindVector(spawnPos.first, spawnPos.second) }
        entity[GroundTrack.mapper]?.apply {
            val tasVector = dir.trackUnitVector * ktToPxps(speed.speedKts.toInt())
            trackVectorPxps = tasVector + affectedByWind.windVectorPxps
        }
        val clearanceAct = ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState(randomStar?.name ?: "", starRoute, Route(),
                if (starRoute.size == 0) (spawnPos.third + MAG_HDG_DEV).toInt().toShort() else null, null,
                clearedAlt, ias)
        ))
        entity += clearanceAct
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = modulateHeading(spawnPos.third + 180)
        }
        val spds = getMinMaxOptimalIAS(entity)
        clearanceAct.actingClearance.actingClearance.apply {
            minIas = spds.first
            maxIas = spds.second
            optimalIas = spds.third
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
    val availableStars = GdxArray<SidStar.STAR>()
    val runwaysAvailable = HashSet<String>()
    airport[RunwayChildren.mapper]?.rwyMap?.values()?.forEach { rwy ->
        // Add names of runways that are active for landing to set
        if (rwy.entity.has(ActiveLanding.mapper)) runwaysAvailable.add(rwy.entity[RunwayInfo.mapper]?.rwyName ?: return@forEach)
    }
    airport[STARChildren.mapper]?.starMap?.values()?.forEach { star ->
        // Add to list of eligible STARs if both runway and time restriction checks passes
        if ((star.rwyLegs.keys() intersect  runwaysAvailable).isEmpty()) return@forEach
        if (!star.isUsableForDayNight()) return@forEach
        availableStars.add(star)
    }

    if (availableStars.isEmpty) {
        Gdx.app.log("TrafficTools", "No STAR available for ${airport[AirportInfo.mapper]?.name}")
        return null
    }
    return availableStars.random()
}

fun appTestArrival(gs: GameServer) {
    gs.aircraft.put("SHIBA3", Aircraft("SHIBA3", -175f, -150f, 2000f, "B78X", FlightType.ARRIVAL, false).apply {
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
 * Creates a new departure aircraft with the input data, but not cleared for takeoff
 * @param airport the airport the departure is flying from
 * @param gs the gameServer to instantiate the aircraft in
 * */
fun createRandomDeparture(airport: Entity, gs: GameServer) {
    val spawnData = generateRandomTrafficForAirport(airport) ?: return
    val callsign = generateRandomCallsign(spawnData.first, spawnData.second, gs) ?: return
    // Choose random aircraft type from the array of possible aircraft
    val icaoType = spawnData.third.random() ?: run {
        Gdx.app.log("TrafficTools", "No aircraft available for ${spawnData.first} in ${airport[AirportInfo.mapper]?.icaoCode}")
        "B77W"
    }
    gs.aircraft.put(callsign, Aircraft(callsign, 0f, 0f, 0f, icaoType, FlightType.DEPARTURE, false).apply {
        entity += WaitingTakeoff()
        entity += ClearanceAct()
        airport += AirportNextDeparture(entity)
        gs.sendAircraftSpawn(this)
    })
}

/**
 * Clears an aircraft for takeoff from a runway, setting its takeoff parameters
 * @param aircraft the aircraft to clear for takeoff
 * @param rwy the runway the aircraft is cleared to takeoff from
 */
fun clearForTakeoff(aircraft: Entity, rwy: Entity) {
    val rwyPos = rwy[Position.mapper] ?: return
    val rwyDir = rwy[Direction.mapper] ?: return

    val rwyAlt = rwy[Altitude.mapper]?.altitudeFt ?: return
    val rwyInfo = rwy[RunwayInfo.mapper] ?: return

    val rwyId = rwy[RunwayInfo.mapper]?.rwyId ?: return
    val arptId = rwyInfo.airport.entity[AirportInfo.mapper]?.arptId ?: return

    val rwyTakeoffPosLength = rwyInfo.intersectionTakeoffM
    val spawnPos = Vector2(rwyPos.x, rwyPos.y) + rwyDir.trackUnitVector * mToPx(rwyTakeoffPosLength.toFloat())
    aircraft.apply {
        // Set to runway position
        val pos = get(Position.mapper)?.also {
            it.x = spawnPos.x
            it.y = spawnPos.y
        } ?: return
        // Set to runway track
        get(Direction.mapper)?.trackUnitVector?.rotateDeg(rwyDir.trackUnitVector.angleDeg() - 90) // Runway heading
        // Calculate headwind component for takeoff
        val tailwind = rwyDir.let { dir ->
            val wind = getClosestAirportWindVector(pos.x, pos.y)
            calculateIASFromTAS(rwyAlt, pxpsToKt(wind.dot(dir.trackUnitVector)))
        }
        get(Speed.mapper)?.speedKts = -tailwind
        // Calculate required acceleration
        val acPerf = get(AircraftInfo.mapper)?.aircraftPerf ?: return@apply
        this += TakeoffRoll(max(1.5f,
            calculateRequiredAcceleration(0, calculateTASFromIAS(rwyAlt, acPerf.vR + tailwind).toInt().toShort(),
                ((rwy[RunwayInfo.mapper]?.lengthM ?: 3800) - 1000) * MathUtils.random(0.75f, 1f))), rwy)
        // Get random SID, add route zones
        val sid = randomSid(rwy)
        val rwyName = rwy[RunwayInfo.mapper]?.rwyName ?: ""
        val initClimb = sid?.rwyInitialClimbs?.get(rwyName) ?: 3000
        val sidRoute = sid?.getRandomSIDRouteForRunway(rwyName) ?: Route()
        get(DepartureRouteZone.mapper)?.sidZone?.also {
            it.clear()
            it.addAll(getZonesForInitialRunwayClimb(sidRoute, rwy))
            it.addAll(getZonesForRoute(sidRoute))
        }
        // Set initial clearance state
        this += ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState(sid?.name ?: "", sidRoute, Route(),
                null, null, initClimb, acPerf.climbOutSpeed)
        ))
        get(CommandTarget.mapper)?.let {
            it.targetAltFt = initClimb
            it.targetIasKt = acPerf.climbOutSpeed
            it.targetHdgDeg = convertWorldAndRenderDeg(rwyDir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
        }
        aircraft += DepartureAirport(arptId, rwyId)

        // Set runway as occupied
        rwy += RunwayOccupied()
        rwy[OppositeRunway.mapper]?.oppRwy?.plusAssign(RunwayOccupied())
        remove<WaitingTakeoff>()
        // Set runway previous departure
        (rwy[RunwayPreviousDeparture.mapper] ?: RunwayPreviousDeparture().apply { rwy += this }).apply {
            wakeCat = acPerf.wakeCategory
            recat = acPerf.recat
            timeSinceDepartureS = 0f
        }
        // Set airport previous departure
        rwyInfo.airport.entity[DepartureInfo.mapper]?.apply { backlog-- }
        rwyInfo.airport.entity.remove<AirportNextDeparture>()
    }
}

/**
 * Chooses a random SID from all SIDs available for the input runway
 *
 * This also takes into account any noise restrictions
 * @param rwy the runway to use
 * @return the [SidStar.SID] chosen
 * */
private fun randomSid(rwy: Entity): SidStar.SID? {
    val availableSids = GdxArray<SidStar.SID>()
    val rwyName = rwy[RunwayInfo.mapper]?.rwyName
    rwy[RunwayInfo.mapper]?.airport?.entity?.get(SIDChildren.mapper)?.sidMap?.values()?.forEach { sid ->
        // Add to list of eligible SIDs if both runway and time restriction checks passes
        if (!sid.rwyInitialClimbs.containsKey(rwyName)) return@forEach
        if (!sid.isUsableForDayNight()) return@forEach
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
    val array = GdxArray<String>().apply { add("Approach") }
    val rwys = airport[RunwayChildren.mapper]?.rwyMap ?: return array
    airport[ApproachChildren.mapper]?.approachMap?.values()?.forEach { app ->
        app.entity[ApproachInfo.mapper]?.also {
            // Add to list of eligible approaches if its runway is active for landings and time restriction checks passes
            if (rwys[it.rwyId]?.entity?.get(ActiveLanding.mapper) == null) return@forEach
            if (!app.isUsableForDayNight()) return@forEach
            array.add(it.approachName)
        }
    }
    return array
}

/**
 * Object for storing wake category and RECAT separation matrices
 *
 * 2 2D arrays are stored for each separation standard - one for takeoff timing separation, and the other for distance
 * separation
 */
object WakeMatrix {
    private val RECAT_DEP_TIME: Array<IntArray> = arrayOf(
        intArrayOf(0, 100, 120, 140, 160, 180), // Cat A leader
        intArrayOf(0, 0, 0, 100, 120, 140),     // Cat B leader
        intArrayOf(0, 0, 0, 80, 100, 120),      // Cat C leader
        intArrayOf(0, 0, 0, 0, 0, 120),         // Cat D leader
        intArrayOf(0, 0, 0, 0, 0, 100),         // Cat E leader
        intArrayOf(0, 0, 0, 0, 0, 80)           // Cat F leader
    )

    private val RECAT_DIST: Array<ByteArray> = arrayOf(
        byteArrayOf(3, 4, 5, 5, 6, 8), // Cat A leader
        byteArrayOf(0, 3, 4, 4, 5, 7), // Cat B leader
        byteArrayOf(0, 0, 3, 3, 4, 6), // Cat C leader
        byteArrayOf(0, 0, 0, 0, 0, 5), // Cat D leader
        byteArrayOf(0, 0, 0, 0, 0, 4), // Cat E leader
        byteArrayOf(0, 0, 0, 0, 0, 3), // Cat F leader
    )

    private val WAKE_DEP_TIME: Array<IntArray> = arrayOf(
        intArrayOf(0, 120, 180, 240), // Super leader
        intArrayOf(0, 0, 120, 180),   // Heavy leader
        intArrayOf(0, 0, 0, 180),     // Medium leader
        intArrayOf(0, 0, 0, 0)        // Light leader
    )

    private val WAKE_DIST: Array<ByteArray> = arrayOf(
        byteArrayOf(0, 5, 7, 8), // Super leader
        byteArrayOf(0, 4, 5, 6), // Heavy leader
        byteArrayOf(0, 0, 0, 5), // Medium leader
        byteArrayOf(0, 0, 0, 0)  // Light leader
    )

    /**
     * Gets the index of the input RECAT or wake category for use in the wake separation matrix
     * @param recat the RECAT category
     * @param wake the wake category
     * @return index of the RECAT/wake category to access the matrix
     */
    private fun getWakeRecatIndex(wake: Char, recat: Char): Int {
        val gs = GAME.gameServer ?: return 0
        return if (gs.useRecat) recat - 'A'
        else when (wake) {
            'J' -> 0
            'H' -> 1
            'M' -> 2
            'L' -> 3
            else -> {
                Gdx.app.log("TrafficTools", "Unknown wake category $wake")
                1
            }
        }
    }

    /**
     * Gets the distance required between the leader and follower aircraft, based on the relevant separation standard
     * @param leaderWake the wake category of the aircraft in front
     * @param leaderRecat the RECAT category of the aircraft in front
     * @param followerWake the wake category of the aircraft behind
     * @param followerRecat the RECAT category of the aircraft behind
     */
    fun getDistanceRequired(leaderWake: Char, leaderRecat: Char, followerWake: Char, followerRecat: Char): Byte {
        val gs = GAME.gameServer ?: return 0
        val leaderIndex = getWakeRecatIndex(leaderWake, leaderRecat)
        val followerIndex = getWakeRecatIndex(followerWake, followerRecat)
        return if (gs.useRecat) RECAT_DIST[leaderIndex][followerIndex] else WAKE_DIST[leaderIndex][followerIndex]
    }

    /**
     * Gets the time required between the leader and follower aircraft for departures, based on the relevant separation
     * standard
     * @param leaderWake the wake category of the aircraft that landed/departed previously
     * @param leaderRecat the RECAT category of the aircraft that landed/departed previously
     * @param followerWake the wake category of the aircraft departing behind
     * @param followerRecat the RECAT category of the aircraft departing behind
     */
    fun getTimeRequired(leaderWake: Char, leaderRecat: Char, followerWake: Char, followerRecat: Char): Int {
        val gs = GAME.gameServer ?: return 0
        val leaderIndex = getWakeRecatIndex(leaderWake, leaderRecat)
        val followerIndex = getWakeRecatIndex(followerWake, followerRecat)
        // Minimum 90s separation between successive takeoffs regardless of wake/RECAT
        return max(90, if (gs.useRecat) RECAT_DEP_TIME[leaderIndex][followerIndex] else WAKE_DEP_TIME[leaderIndex][followerIndex])
    }
}

/**
 * Calculates the distance, in pixels, the aircraft is from the runway threshold
 * @param aircraft the aircraft
 * @param rwy the runway
 * @return distance from the threshold
 */
fun calculateDistFromThreshold(aircraft: Entity, rwy: Entity): Float {
    val rwyPos  = rwy[Position.mapper] ?: return Float.MAX_VALUE
    val pos = aircraft[Position.mapper] ?: return Float.MAX_VALUE
    return calculateDistanceBetweenPoints(rwyPos.x, rwyPos.y, pos.x, pos.y)
}

/**
 * Calculates the time required, in seconds, to reach the runway threshold at the aircraft's current ground speed,
 * capped at 200 knots
 * @param aircraft the aircraft
 * @param rwy the runway
 * @return estimated time to reach runway threshold, or [Float.MAX_VALUE] if the time cannot be estimated
 */
fun calculateTimeToThreshold(aircraft: Entity, rwy: Entity): Float {
    val distPx = calculateDistFromThreshold(aircraft, rwy)
    val gsPxps = aircraft[GroundTrack.mapper]?.trackVectorPxps?.len() ?: return Float.MAX_VALUE
    return distPx / gsPxps
}

/**
 * Checks the traffic for the input runway
 * @param rwy the runway to check traffic for
 * @return whether this runway is clear for departure
 */
fun checkSameRunwayTraffic(rwy: Entity, additionalTime: Int): Boolean {
    val airport = rwy[RunwayInfo.mapper]?.airport ?: return false
    val nextDeparture = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.aircraftPerf ?: return false
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    val prevArrival = rwy[RunwayPreviousArrival.mapper]
    val nextArrival = rwy[RunwayNextArrival.mapper]
    // Check if runway is occupied
    if (rwy.has(RunwayOccupied.mapper)) return false
    // Check previous departure time required
    if (prevDeparture != null && WakeMatrix.getTimeRequired(prevDeparture.wakeCat, prevDeparture.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) + additionalTime > prevDeparture.timeSinceDepartureS) return false
    // Check previous arrival time required
    if (prevArrival != null && WakeMatrix.getTimeRequired(prevArrival.wakeCat, prevArrival.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) > prevArrival.timeSinceTouchdownS) return false
    // Check time from touchdown - minimum 60s
    if (nextArrival != null && calculateTimeToThreshold(nextArrival.aircraft, rwy) < 60) return false
    return true
}

/**
 * Checks the traffic for the opposite runway
 * @param rwy the opposite runway
 * @return whether the opposite runway is clear for departure from original runway
 */
fun checkOppRunwayTraffic(rwy: Entity, additionalTime: Int): Boolean {
    val airport = rwy[RunwayInfo.mapper]?.airport ?: return false
    val nextDeparture = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.aircraftPerf ?: return false
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    val prevArrival = rwy[RunwayPreviousArrival.mapper]
    val nextArrival = rwy[RunwayNextArrival.mapper]
    // Check if runway is occupied
    if (rwy.has(RunwayOccupied.mapper)) return false
    // Check previous departure time required
    if (prevDeparture != null && WakeMatrix.getTimeRequired(prevDeparture.wakeCat, prevDeparture.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) + additionalTime > prevDeparture.timeSinceDepartureS) return false
    // Check previous arrival time required
    if (prevArrival != null && WakeMatrix.getTimeRequired(prevArrival.wakeCat, prevArrival.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) > prevArrival.timeSinceTouchdownS) return false
    // Check distance from touchdown - minimum 15nm away
    if (nextArrival != null && calculateDistFromThreshold(nextArrival.aircraft, rwy) < nmToPx(15)) return false
    return true
}

/**
 * Checks the traffic for a dependent parallel runway
 * @param rwy the dependent parallel runway
 * @return whether the dependent parallel runway is clear for departure from original runway
 */
fun checkDependentParallelRunwayTraffic(rwy: Entity, additionalTime: Int): Boolean {
    val nextArrival = rwy[RunwayNextArrival.mapper]
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    // Check time from touchdown - minimum 60s
    if (nextArrival != null && calculateTimeToThreshold(nextArrival.aircraft, rwy) < 60) return false
    // Check time since departure - minimum 60s
    if (prevDeparture != null && prevDeparture.timeSinceDepartureS < 60 + additionalTime) return false
    return true
}

/**
 * Checks the traffic for a dependent opposite runway
 * @param rwy the dependent opposite runway
 * @return whether the dependent opposite runway is clear for departure from original runway
 */
fun checkDependentOppositeRunwayTraffic(rwy: Entity, additionalTime: Int): Boolean {
    val nextArrival = rwy[RunwayNextArrival.mapper]
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    // Check distance from touchdown - minimum 15nm away
    if (nextArrival != null && calculateDistFromThreshold(nextArrival.aircraft, rwy) < nmToPx(15)) return false
    // Check time since departure - minimum 60s
    if (prevDeparture != null && prevDeparture.timeSinceDepartureS < 60 + additionalTime) return false
    return true
}

/**
 * Checks the traffic for an intersecting runway
 * @param rwy the intersecting runway
 * @return whether the intersecting runway is clear for departure from original runway
 */
fun checkCrossingRunwayTraffic(rwy: Entity, additionalTime: Int): Boolean {
    val nextArrival = rwy[RunwayNextArrival.mapper]
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    // Check if runway is occupied
    if (rwy.has(RunwayOccupied.mapper)) return false
    // Check time from touchdown - minimum 30s
    if (nextArrival != null && calculateTimeToThreshold(nextArrival.aircraft, rwy) < 30) return false
    // No minimum time since departure needed, but check for additional time
    if (prevDeparture != null && prevDeparture.timeSinceDepartureS < additionalTime) return false
    return true
}

/**
 * Calculates the minimum additional time from the previous departure to the next departure for an airport depending on
 * its departure backlog
 * @param backlog the number of departure aircraft waiting
 * @return the minimum additional time, in seconds, between the previous departure and the next departure
 */
fun calculateAdditionalTimeToNextDeparture(backlog: Int): Int {
    return when {
        backlog >= 10 -> 0
        backlog >= -20 -> 0 + 120 * (10 - backlog) / 30
        backlog >= -40 -> 120 + (320 - 120) * (-20 - backlog) / 20
        else -> 320
    }
}

/**
 * Finds the airport with the lowest elevation in the game world and gets its elevation
 * @return elevation of the lowest airport
 */
fun getLowestAirportElevation(): Float {
    var minElevation: Float? = null
    GAME.gameServer?.apply {
        airports.values().forEach {
            val alt = it.entity[Altitude.mapper]?.altitudeFt ?: return@forEach
            val finalMinElevation = minElevation
            if (finalMinElevation == null || alt < finalMinElevation) minElevation = alt
        }
    }
    return minElevation ?: 0f
}

/**
 * Gets the sector index the entity belongs to based on its altitude, as well as the altitude of the lowest sector
 *
 * This can be used for both aircraft conflict and wake separation conflict level distribution
 * @param alt the altitude, in feet, of the entity
 * @param startingAltitude the altitude of the lowest sector, in feet
 * @return the index of the sector the entity should belong to
 */
fun getSectorIndexForAlt(alt: Float, startingAltitude: Int): Int {
    return floor((alt - startingAltitude) / VERT_SEP).roundToInt()
}

/**
 * Gets airports that are closed for arrivals
 * @return an array of bytes containing the ID of airports closed for arrivals
 * */
fun getArrivalClosedAirports(): ByteArray {
    val airports = GdxArray<Byte>(AIRPORT_SIZE)
    GAME.gameServer?.airports?.values()?.forEach {
        val id = it.entity[AirportInfo.mapper]?.arptId ?: return@forEach
        if (it.entity.has(ArrivalClosed.mapper)) airports.add(id)
    }
    return airports.toArray().map { it }.toByteArray()
}

/**
 * Gets airports that are closed for departures
 * @return an array of bytes containing the ID of airports closed for departures
 * */
fun getDepartureClosedAirports(): ByteArray {
    val airports = GdxArray<Byte>(AIRPORT_SIZE)
    GAME.gameServer?.airports?.values()?.forEach {
        val id = it.entity[AirportInfo.mapper]?.arptId ?: return@forEach
        if (it.entity[DepartureInfo.mapper]?.closed == true) airports.add(id)
    }
    return airports.toArray().map { it }.toByteArray()
}

/**
 * Updates the wake trails status for the aircraft - the last wake trail will be removed if queue is above a certain size,
 * and a new wake zone added based on the current aircraft position and altitude
 * @param aircraft the aircraft entity to update wake trail for
 * @param system the [TrafficSystemInterval] to update the wake level sectors for
 */
fun updateWakeTrailState(aircraft: Entity, system: TrafficSystemInterval) {
    val pos = aircraft[Position.mapper] ?: return
    val alt = aircraft[Altitude.mapper] ?: return
    val acInfo = aircraft[AircraftInfo.mapper] ?: return
    val wake = aircraft[WakeTrail.mapper] ?: return
    // Remove last wake dot if more than or equal to max count (since a new wake dot will be added)
    if (wake.wakeZones.size >= MAX_WAKE_DOTS) {
        val removed = wake.wakeZones.removeLast().second
        if (removed != null) system.removeWakeZone(removed)
    }
    // Increment distance from AC for each zone
    for (element in Queue.QueueIterator(wake.wakeZones))
        element.second?.entity?.get(WakeInfo.mapper)?.let { it.distFromAircraft += WAKE_DOT_SPACING_NM }
    val newWakeZone = if (wake.wakeZones.isEmpty) null
    else {
        val prevPos = wake.wakeZones.first().first
        WakeZone(prevPos.x, prevPos.y, pos.x, pos.y, alt.altitudeFt, acInfo.icaoCallsign, acInfo.aircraftPerf.wakeCategory, acInfo.aircraftPerf.recat)
    }
    wake.wakeZones.addFirst(Pair(pos.copy(), newWakeZone))
    if (newWakeZone != null) system.addWakeZone(newWakeZone)
}
