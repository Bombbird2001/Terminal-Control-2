package com.bombbird.terminalcontrol2.traffic

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.TakeoffProtectionZone
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.dataclasses.TrafficSettingsData
import com.bombbird.terminalcontrol2.systems.TrafficSystemInterval
import com.bombbird.terminalcontrol2.systems.TrajectorySystemInterval
import com.bombbird.terminalcontrol2.utilities.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.*
import ktx.collections.GdxArray
import ktx.collections.GdxSet
import ktx.math.plus
import ktx.math.times
import kotlin.math.*

val disallowedCallsigns = GdxSet<String>()

/**
 * Object for storing traffic modes for the game
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
fun createRandomArrival(airports: List<Airport>, gs: GameServer) {
    val airportDist = CumulativeDistribution<Entity>()
    for (element in airports) { element.entity.apply {
        if (hasNot(ArrivalClosed.mapper)) airportDist.add(this, get(AirportInfo.mapper)?.tfcRatio?.toFloat() ?: return@apply)
    }}
    airportDist.generateNormalized()
    val arpt = if (airportDist.size() == 0) return else airportDist.value()
    createRandomArrivalForAirport(arpt, gs)
}

/**
 * Creates an arrival to the [airport] with a randomly selected STAR
 */
fun createRandomArrivalForAirport(airport: Entity, gs: GameServer) {
    val spawnData = generateRandomTrafficForAirport(airport) ?: return
    val callsign = generateRandomCallsign(spawnData.first, spawnData.second, gs) ?: return
    // Choose random aircraft type from the array of possible aircraft
    val icaoType = spawnData.third.random() ?: run {
        FileLog.info("TrafficTools", "No aircraft available for ${spawnData.first} in ${airport[AirportInfo.mapper]?.icaoCode}")
        "B77W"
    }
    createArrival(callsign, icaoType, airport, gs)
}

/**
 * Creates a new arrival aircraft with the input data
 * @param callsign the callsign of the aircraft
 * @param icaoType the ICAO aircraft type
 * @param airport the airport the arrival is flying into
 * @param gs the gameServer to instantiate the aircraft in
 */
fun createArrival(callsign: String, icaoType: String, airport: Entity, gs: GameServer) {
    if (gs.aircraft.containsKey(callsign)) {
        FileLog.info("TrafficTools", "Aircraft with callsign $callsign already exists")
        return
    }
    val randomStar = randomStar(airport)
    val starRoute = randomStar?.getRandomSTARRouteForRunway() ?: Route()
    val origStarRoute = Route().apply { setToRouteCopy(starRoute) }
    val spawnPos = calculateArrivalSpawnPoint(starRoute, gs.primarySector)

    gs.aircraft.put(callsign, Aircraft(callsign, spawnPos.first, spawnPos.second, 0f, icaoType, FlightType.ARRIVAL, false).apply {
        entity += ArrivalAirport(airport[AirportInfo.mapper]?.arptId ?: 0)
        entity += ArrivalRouteZone().apply { starZone.addAll(getZonesForArrivalRoute(origStarRoute)) }
        var alt = calculateArrivalSpawnAltitude(entity, airport, origStarRoute, spawnPos.first, spawnPos.second, starRoute)
        alt = amendAltForNearbyTraffic(alt, spawnPos.first, spawnPos.second, entity)
        entity[Altitude.mapper]?.altitudeFt = alt
        val dir = (entity[Direction.mapper] ?: Direction()).apply { trackUnitVector.rotateDeg(-spawnPos.third - 180) }
        val aircraftPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: AircraftTypeData.AircraftPerfData()
        val ias = calculateArrivalSpawnIAS(origStarRoute, starRoute, alt, aircraftPerf)
        val tas = calculateTASFromIAS(alt, ias.toFloat())
        val nextWpt = (if (starRoute.size > 0) starRoute[0] else null) as? Route.WaypointLeg
        val nextMinStarAlt = (ceil((getHighestMinAlt(starRoute) ?: Int.MIN_VALUE) / 1000f) * 1000).roundToInt()
        val nextMaxStarAlt = (floor((nextWpt?.maxAltFt ?: Int.MAX_VALUE) / 1000f) * 1000).roundToInt()
        val clearedAlt = min(min(getACCStartAltitude(), (alt / 1000).toInt() * 1000), nextMaxStarAlt)
        val cmdTargetAlt = max(clearedAlt, nextMinStarAlt)
        val speed = (entity[Speed.mapper] ?: Speed()).apply {
            speedKts = tas
            // Set to vertical speed required to reach cleared altitude in 10 seconds, capped by the minimum vertical speed
            val minVertSpd = calculateMinVerticalSpd(aircraftPerf, alt, tas, 0f, approachExpedite = false, takingOff = false, takeoffGoAround = false)
            vertSpdFpm = max(minVertSpd, (cmdTargetAlt - alt) * 6)
        }
        val affectedByWind = (entity[AffectedByWind.mapper] ?: AffectedByWind()).apply { getInterpolatedWindVector(spawnPos.first, spawnPos.second) }
        entity[GroundTrack.mapper]?.apply {
            val tasVector = dir.trackUnitVector * ktToPxps(speed.speedKts.toInt())
            trackVectorPxps = tasVector + affectedByWind.windVectorPxps
        }
        val clearanceAct = ClearanceAct(ClearanceState(randomStar?.name ?: "", starRoute, Route(),
                if (starRoute.size == 0) (spawnPos.third + MAG_HDG_DEV).toInt().toShort() else null, null,
                clearedAlt, false, ias).ActingClearance())
        entity += clearanceAct
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = cmdTargetAlt
            targetIasKt = ias
            targetHdgDeg = modulateHeading(spawnPos.third + 180)
        }
        val spds = getMinMaxOptimalIAS(entity)
        clearanceAct.actingClearance.clearanceState.apply {
            minIas = spds.first
            maxIas = spds.second
            optimalIas = spds.third
        }
        entity += InitialArrivalSpawn()
        if (alt > 10000) entity += DecelerateTo240kts()
        entity += ContactFromCentre(MAX_ALT + MathUtils.random(400, 1500))
        initialiseArrivalRequests(entity)
        gs.sendAircraftSpawn(this)
    })
}

/**
 * Removes the aircraft from the game world, and deletes related information from data structures
 * @param aircraft the aircraft to remove
 */
fun despawnAircraft(aircraft: Entity) {
    val engine = getEngine(false)
    engine.removeEntityOnMainThread(aircraft, false)
    GAME.gameServer?.let {
        val callsign = aircraft[AircraftInfo.mapper]?.icaoCallsign ?: return
        it.aircraft.removeKey(callsign) // Remove from aircraft map
        engine.getSystem<TrafficSystemInterval>().removeAircraftOnDespawn(aircraft) // Remove from conflict levels
        engine.getSystem<TrafficSystemInterval>().removeAircraftWakeZones(aircraft) // Remove wake zones
        it.sendAircraftDespawn(callsign) // Send removal data to all clients
    }
}

/**
 * Chooses a random STAR from all STARs available for the input airport
 *
 * This also takes into account any noise restrictions
 * @param airport the airport to use
 * @return the [SidStar.STAR] chosen
 */
private fun randomStar(airport: Entity): SidStar.STAR? {
    val availableStars = GdxArray<SidStar.STAR>()
    val availableStarsIgnoreTime = GdxArray<SidStar.STAR>()
    val runwaysAvailable = HashSet<String>()
    airport[RunwayChildren.mapper]?.rwyMap?.let { rwyMap ->
        Entries(rwyMap).forEach { rwyEntry ->
            val rwy = rwyEntry.value
            // Add names of runways that are active for landing to set
            if (rwy.entity.has(ActiveLanding.mapper)) runwaysAvailable.add(rwy.entity[RunwayInfo.mapper]?.rwyName ?: return@forEach)
        }
    }
    val activeRwyConfig = airport[ActiveRunwayConfig.mapper]
    if (activeRwyConfig == null) {
        FileLog.info("TrafficTools", "No active runway configuration for random STAR")
        return null
    }
    airport[STARChildren.mapper]?.starMap?.let { starMap ->
        Entries(starMap).forEach { starEntry ->
            val star = starEntry.value
            // Add to list of eligible STARs if both runway and time restriction checks passes
            if ((star.rwyLegs.keys() intersect runwaysAvailable).isEmpty()) return@forEach
            if (!star.rwyConfigsAllowed.contains(activeRwyConfig.configId)) return@forEach
            availableStarsIgnoreTime.add(star)
            if (!star.isUsableForDayNight()) return@forEach
            availableStars.add(star)
        }
    }

    if (availableStars.isEmpty) {
        if (availableStarsIgnoreTime.isEmpty) {
            FileLog.info("TrafficTools", "No STAR available for ${airport[AirportInfo.mapper]?.name}")
            return null
        }
        return availableStarsIgnoreTime.random()
    }
    return availableStars.random()
}

/**
 * If necessary, calculates and returns the altitude of the spawning aircraft to ensure it is above any nearby traffic
 * within 7nm and +-2000ft
 * @param currSpawnAlt the current altitude that the aircraft plans to spawn at
 * @param posX the X coordinate of the aircraft
 * @param posY the Y coordinate of the aircraft
 * @param arrival the arrival entity we're checking for
 */
private fun amendAltForNearbyTraffic(currSpawnAlt: Float, posX: Float, posY: Float, arrival: Entity): Float {
    val upperRange = 2000f
    val lowerRange = 2000f
    val minDistNm = 7f
    val gs = GAME.gameServer ?: return currSpawnAlt
    val entitiesToCheck = gs.engine.getSystem<TrafficSystemInterval>().getEntitiesWithinArrivalSpawnAltitude(currSpawnAlt, lowerRange, upperRange)
    val svcCeil = arrival[AircraftInfo.mapper]?.aircraftPerf?.maxAlt ?: return currSpawnAlt
    for (i in 0 until entitiesToCheck.size) {
        val entity = entitiesToCheck[i]
        if (entity == arrival) continue
        val pos = entity[Position.mapper] ?: continue
        val alt = entity[Altitude.mapper]?.altitudeFt ?: continue
        if (calculateDistanceBetweenPoints(posX, posY, pos.x, pos.y) < nmToPx(minDistNm) &&
            (currSpawnAlt - lowerRange < alt || currSpawnAlt + upperRange > alt)) {
            val newAlt = alt + upperRange * 2
            if (newAlt > svcCeil) return svcCeil.toFloat()
            return amendAltForNearbyTraffic(newAlt, posX, posY, arrival)
        }
    }

    return currSpawnAlt
}

fun appTestArrival(gs: GameServer) {
    gs.aircraft.put("SHIBA3", Aircraft("SHIBA3", 250f, 750f, 23000f, "B78X", FlightType.ARRIVAL, false).apply {
        entity += ArrivalAirport(0)
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(-150f)
        val ias = 240.toShort()
        val tas = calculateTASFromIAS(23000f, ias.toFloat())
        entity[Speed.mapper]?.apply {
            speedKts = tas
            vertSpdFpm = 0f
        }
        val clearedAlt = 10000
        entity += ClearanceAct(ClearanceState("NTN1A", Route(), Route(),
                150, null, clearedAlt, false, ias).ActingClearance())
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = 150f
        }
        entity += InitialArrivalSpawn()
    })

    gs.aircraft.put("SHIBA4", Aircraft("SHIBA4", 250f, 750f, 20000f, "A359", FlightType.ARRIVAL, false).apply {
        entity += ArrivalAirport(0)
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(-150f)
        val ias = 240.toShort()
        val tas = calculateTASFromIAS(20000f, ias.toFloat())
        entity[Speed.mapper]?.apply {
            speedKts = tas
            vertSpdFpm = 0f
        }
        val clearedAlt = 20000
        entity += ClearanceAct(ClearanceState("NTN1A", Route(), Route(),
                150, null, clearedAlt, false, ias).ActingClearance())
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = 150f
        }
        entity += InitialArrivalSpawn()
        entity += ContactFromCentre(20500)
    })

//    gs.aircraft.put("SHIBA5", Aircraft("SHIBA5", 650f, 15f, 1800f, "B77W", FlightType.ARRIVAL, false).apply {
//        entity += ArrivalAirport(1)
//        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(110f)
//        val ias = 190.toShort()
//        val tas = calculateTASFromIAS(1800f, ias.toFloat())
//        entity[Speed.mapper]?.apply {
//            speedKts = tas
//            vertSpdFpm = 0f
//        }
//        val clearedAlt = 1800
//        entity += ClearanceAct(ClearanceState("", Route(), Route(),
//                250, null, clearedAlt, false, ias).ActingClearance())
//        entity[CommandTarget.mapper]?.apply {
//            targetAltFt = clearedAlt
//            targetIasKt = ias
//            targetHdgDeg = 250f
//        }
//        entity += InitialArrivalSpawn()
//    })
}

/**
 * Creates a new departure aircraft with the input data, but not cleared for takeoff
 * @param airport the airport the departure is flying from
 * @param gs the gameServer to instantiate the aircraft in
 */
fun createRandomDeparture(airport: Entity, gs: GameServer) {
    val spawnData = generateRandomTrafficForAirport(airport) ?: return
    val callsign = generateRandomCallsign(spawnData.first, spawnData.second, gs) ?: return
    // Choose random aircraft type from the array of possible aircraft
    val icaoType = spawnData.third.random() ?: run {
        FileLog.info("TrafficTools", "No aircraft available for ${spawnData.first} in ${airport[AirportInfo.mapper]?.icaoCode}")
        "B77W"
    }
    gs.aircraft.put(callsign, Aircraft(callsign, 0f, 0f, 0f, icaoType, FlightType.DEPARTURE, false).apply {
        entity += WaitingTakeoff()
        entity += ClearanceAct()
        addEmergencyIfNeeded(entity, airport)
        airport += AirportNextDeparture(entity)
        gs.sendAircraftSpawn(this)
    })
}

/**
 * Checks if the takeoff protection zone for departing from [rwy] climbing to [takeoffAlt] is clear of other aircraft
 * (excluding same airport arrivals & departures)
 */
fun isTakeoffProtectionZoneClear(rwy: Entity, takeoffAlt: Int): Boolean {
    val rwyPos = rwy[Position.mapper] ?: return false
    val rwyInfo = rwy[RunwayInfo.mapper] ?: return false
    val depArpt = rwyInfo.airport.entity[AirportInfo.mapper]?.arptId ?: return false
    val rwyLengthHalf = rwyInfo.lengthM / 2f
    val rwyDir = rwy[Direction.mapper]?.trackUnitVector ?: return false
    val startPos = Vector2(rwyPos.x, rwyPos.y) + rwyDir * (mToPx(rwyLengthHalf))
    val rwyEndPos = Vector2(rwyPos.x, rwyPos.y) + rwyDir * (mToPx(rwyLengthHalf) * 2)
    val takeoffProtZone = TakeoffProtectionZone(startPos.x, startPos.y, rwyEndPos.x, rwyEndPos.y, takeoffAlt)

    val trajectorySystem = getEngine(false).getSystem<TrajectorySystemInterval>()
    val allTrajectoryPoints = trajectorySystem.trajectoryTimeStates
    var pointsInitialized = false
    // Check points within 60 to 90s and at or below the takeoff altitude in the protection zone
    for (timeIndex in (60 / TRAJECTORY_UPDATE_INTERVAL_S - 1).roundToInt() until allTrajectoryPoints.size) {
        for (altitudeLevel in allTrajectoryPoints[timeIndex]) {
            for (i in 0 until min(
                getSectorIndexForAlt(takeoffAlt.toFloat(), trajectorySystem.startingAltitude) + 1,
                altitudeLevel.size
            )) {
                pointsInitialized = true
                val point = altitudeLevel[i]
                // Ignore aircraft from the same airport - they're "supposed" to be managed by that same airport
                // Also simplifies the calculation, not having to deal with NOZs and NTZs
                val pointAircraft = point.entity[TrajectoryPointInfo.mapper]?.aircraft ?: continue
                val arpt2 = pointAircraft[DepartureAirport.mapper]?.arptId ?: pointAircraft[ArrivalAirport.mapper]?.arptId
                if (depArpt == arpt2) continue

                val pos = point.entity[Position.mapper] ?: continue
                if (takeoffProtZone.contains(pos.x, pos.y)) return false
            }
        }
    }

    // Only return true when there is at least one point in the trajectory prediction system - we may have just started
    // the game and no points have been generated yet
    return pointsInitialized
}

/**
 * Clears an aircraft for takeoff from a runway, setting its takeoff parameters
 * @param aircraft the aircraft to clear for takeoff
 * @param rwy the runway the aircraft is cleared to takeoff from
 * @param sid the SID to use for the aircraft
 */
fun clearForTakeoff(aircraft: Entity, rwy: Entity, sid: SidStar.SID) {
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
        // Set to runway elevation
        get(Altitude.mapper)?.altitudeFt = rwyAlt
        // Set to runway track
        get(Direction.mapper)?.trackUnitVector?.rotateDeg(rwyDir.trackUnitVector.angleDeg() - 90) // Runway heading
        // Calculate headwind component for takeoff
        val tailwind = rwyDir.let { dir ->
            val wind = getInterpolatedWindVector(pos.x, pos.y)
            calculateIASFromTAS(rwyAlt, pxpsToKt(wind.dot(dir.trackUnitVector)))
        }
        get(Speed.mapper)?.speedKts = 5f
        // Calculate required acceleration
        val acPerf = get(AircraftInfo.mapper)?.aircraftPerf ?: return@apply
        this += TakeoffRoll(max(1.5f,
            calculateRequiredAcceleration(0, calculateTASFromIAS(rwyAlt, acPerf.vR + tailwind).toInt().toShort(),
                ((rwy[RunwayInfo.mapper]?.lengthM ?: 3800) - 1000) * MathUtils.random(0.75f, 1f))), rwy)
        // Add route zones for SID
        val rwyName = rwy[RunwayInfo.mapper]?.rwyName ?: ""
        val initClimb = sid.rwyInitialClimbs.get(rwyName) ?: 3000
        val sidRoute = sid.getRandomSIDRouteForRunway(rwyName)
        get(DepartureRouteZone.mapper)?.sidZone?.also {
            it.clear()
            it.addAll(getZonesForInitialRunwayClimb(sidRoute, rwy))
            it.addAll(getZonesForDepartureRoute(sidRoute))
        }
        // Set initial clearance state
        this += ClearanceAct(ClearanceState(sid.name, sidRoute, Route(), null, null, initClimb,
            false, acPerf.climbOutSpeed).ActingClearance())
        get(CommandTarget.mapper)?.let {
            it.targetAltFt = initClimb
            it.targetIasKt = acPerf.climbOutSpeed
            it.targetHdgDeg = convertWorldAndRenderDeg(rwyDir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
        }
        aircraft += DepartureAirport(arptId, rwyId)
        // Set altitude to contact from tower, to centre
        aircraft += ContactFromTower(rwyAlt.roundToInt() + MathUtils.random(600, 1100))
        aircraft += ContactToCentre(MAX_ALT - MathUtils.random(500, 900))

        // Set runway as occupied
        rwy += RunwayOccupied()
        rwy[OppositeRunway.mapper]?.oppRwy?.plusAssign(RunwayOccupied())
        remove<WaitingTakeoff>()
        // Set runway previous departure
        (rwy[RunwayPreviousDeparture.mapper] ?: RunwayPreviousDeparture().apply { rwy += this }).apply {
            wakeCat = acPerf.wakeCategory
            recat = acPerf.recat
            timeSinceDepartureS = 0f
            isTurboprop = acPerf.propPowerWSLISA != null
        }
        // Set airport previous departure
        rwyInfo.airport.entity[DepartureInfo.mapper]?.apply { backlog-- }
        rwyInfo.airport.entity.remove<AirportNextDeparture>()
        rwyInfo.airport.entity[TimeSinceLastDeparture.mapper]?.time = 0f

        getOrLogMissing(AircraftInfo.mapper)?.icaoCallsign?.let {
            GAME.gameServer?.sendAircraftClearedForTakeoff(it, arptId, spawnPos.x, spawnPos.y, rwyAlt)
        }

        // Initialise aircraft requests if necessary
        initialiseDepartureRequests(aircraft)
    }
}

/**
 * Chooses a random SID from all SIDs available for the input runway
 *
 * This also takes into account any noise restrictions
 * @param rwy the runway to use
 * @return the [SidStar.SID] chosen
 */
fun randomSid(rwy: Entity): SidStar.SID? {
    val availableSids = GdxArray<SidStar.SID>()
    val availableSidsIgnoreTime = GdxArray<SidStar.SID>()
    val rwyName = rwy[RunwayInfo.mapper]?.rwyName
    if (rwyName == null) {
        FileLog.info("TrafficTools", "No runway info found")
        return null
    }
    val activeRwyConfig = rwy[RunwayInfo.mapper]?.airport?.entity?.get(ActiveRunwayConfig.mapper)
    if (activeRwyConfig == null) {
        FileLog.info("TrafficTools", "No active runway configuration for random SID")
        return null
    }
    rwy[RunwayInfo.mapper]?.airport?.entity?.get(SIDChildren.mapper)?.sidMap?.let { sidMap ->
        Entries(sidMap).forEach { sidEntry ->
            val sid = sidEntry.value
            // Add to list of eligible SIDs if runway, runway configuration and time restriction checks all pass
            if (!sid.rwyInitialClimbs.containsKey(rwyName)) return@forEach
            if (!sid.rwyConfigsAllowed.contains(activeRwyConfig.configId)) return@forEach
            availableSidsIgnoreTime.add(sid)
            if (!sid.isUsableForDayNight()) return@forEach
            availableSids.add(sid)
        }
    }

    if (availableSids.isEmpty) {
        if (availableSidsIgnoreTime.isEmpty) {
            FileLog.info("TrafficTools", "No SID available for runway $rwyName")
            return null
        }
        return availableSidsIgnoreTime.random()
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
            FileLog.info("TrafficTools", "No airlines available for ${airport[AirportInfo.mapper]?.arptId} ${airport[AirportInfo.mapper]?.icaoCode}")
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
 */
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
                FileLog.info("TrafficTools", "Failed to generate random callsign in time for $airline, aircraft will not be created")
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
 * @param currSelectedApp the currently selected approach of the aircraft, if any
 * @param includeClosedRunway whether to include approaches for closed runways; default is false
 * @return a [GdxArray] of strings containing the eligible approach names
 */
fun getAvailableApproaches(airport: Entity, currSelectedApp: String?, includeClosedRunway: Boolean = false): GdxArray<String> {
    val array = GdxArray<String>().apply { add("Approach") }
    val rwys = airport[RunwayChildren.mapper]?.rwyMap ?: return array
    val activeRwyConfig = airport[ActiveRunwayConfig.mapper]
    if (activeRwyConfig == null) {
        FileLog.info("TrafficTools", "No active runway configuration for approach selection")
        return array
    }
    airport[ApproachChildren.mapper]?.approachMap?.let { appMap ->
        Entries(appMap).forEach { appEntry ->
            val app = appEntry.value
            val allowedConfigs = app.entity[RunwayConfigurationList.mapper]?.rwyConfigs ?: return@forEach
            if (!allowedConfigs.contains(activeRwyConfig.configId, false)) return@forEach
            app.entity[ApproachInfo.mapper]?.also {
                if (app.entity.has(DeprecatedEntity.mapper)) return@forEach
                // Add to list of eligible approaches if its runway is active for landings, time restriction checks passes
                // and the runway configuration in use is one of the allowed configs
                if (rwys[it.rwyId]?.entity?.get(ActiveLanding.mapper) == null) return@forEach
                if (!includeClosedRunway && rwys[it.rwyId]?.entity?.has(RunwayClosed.mapper) != false) return@forEach
                if (!app.isUsableForDayNight()) return@forEach
                array.add(it.approachName)
            }
        }
    }
    if (currSelectedApp != null && !array.contains(currSelectedApp, false))
        array.insert(1, currSelectedApp)
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
        return if (getServerOrClientUseRecat()) recat - 'A'
        else when (wake) {
            'J' -> 0
            'H' -> 1
            'M' -> 2
            'L' -> 3
            else -> {
                FileLog.info("TrafficTools", "Unknown wake category $wake")
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
        val leaderIndex = getWakeRecatIndex(leaderWake, leaderRecat)
        val followerIndex = getWakeRecatIndex(followerWake, followerRecat)
        return if (getServerOrClientUseRecat()) RECAT_DIST[leaderIndex][followerIndex] else WAKE_DIST[leaderIndex][followerIndex]
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
        val leaderIndex = getWakeRecatIndex(leaderWake, leaderRecat)
        val followerIndex = getWakeRecatIndex(followerWake, followerRecat)
        // Minimum 90s separation between successive takeoffs regardless of wake/RECAT
        return max(90, if (getServerOrClientUseRecat()) RECAT_DEP_TIME[leaderIndex][followerIndex] else WAKE_DEP_TIME[leaderIndex][followerIndex])
    }
}

private fun checkPrevTurboprop(prevDep: RunwayPreviousDeparture, nextDep: AircraftTypeData.AircraftPerfData): Boolean {
    // If previous departure is a turboprop, and the next departure is a jet, then we need increased separation time
    // of 180s
    return prevDep.isTurboprop != true || nextDep.propPowerWSLISA != null || prevDep.timeSinceDepartureS >= 180
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
    if (gsPxps == 0f) return Float.MAX_VALUE
    return distPx / gsPxps
}

/**
 * Checks the traffic for the input runway
 * @param rwy the runway to check traffic for
 * @param airport the airport the runway is in
 * @return whether this runway is clear for departure
 */
fun checkSameRunwayTraffic(rwy: Entity, airport: Airport): Boolean {
    val nextDeparture = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.aircraftPerf ?: return false
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    val prevArrival = rwy[RunwayPreviousArrival.mapper]
    val nextArrival = rwy[RunwayNextArrival.mapper]
    // Check if runway is occupied
    if (rwy.has(RunwayOccupied.mapper)) return false
    // Check previous departure time required
    if (prevDeparture != null && WakeMatrix.getTimeRequired(prevDeparture.wakeCat, prevDeparture.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) > prevDeparture.timeSinceDepartureS) return false
    // Check previous arrival time required
    if (prevArrival != null && WakeMatrix.getTimeRequired(prevArrival.wakeCat, prevArrival.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) > prevArrival.timeSinceTouchdownS) return false
    // Check turboprop separation
    if (prevDeparture != null && !checkPrevTurboprop(prevDeparture, nextDeparture)) return false
    // Check time from touchdown - minimum 110s
    return nextArrival == null || calculateTimeToThreshold(nextArrival.aircraft, rwy) >= 110
}

/**
 * Checks the traffic for the opposite runway
 * @param rwy the opposite runway
 * @param airport the airport the runway is in
 * @return whether the opposite runway is clear for departure from original runway
 */
fun checkOppRunwayTraffic(rwy: Entity, airport: Airport): Boolean {
    val nextDeparture = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.aircraftPerf ?: return false
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    val prevArrival = rwy[RunwayPreviousArrival.mapper]
    val nextArrival = rwy[RunwayNextArrival.mapper]
    // Check if runway is occupied
    if (rwy.has(RunwayOccupied.mapper)) return false
    // Check previous departure time required
    if (prevDeparture != null && WakeMatrix.getTimeRequired(prevDeparture.wakeCat, prevDeparture.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) > prevDeparture.timeSinceDepartureS) return false
    // Check previous arrival time required
    if (prevArrival != null && WakeMatrix.getTimeRequired(prevArrival.wakeCat, prevArrival.recat,
            nextDeparture.wakeCategory, nextDeparture.recat) > prevArrival.timeSinceTouchdownS) return false
    // Check turboprop separation
    if (prevDeparture != null && !checkPrevTurboprop(prevDeparture, nextDeparture)) return false
    // Check distance from touchdown - minimum 18nm away
    return nextArrival == null || nextArrival.distFromThrPx >= nmToPx(18)
}

/**
 * Checks the departure traffic for a dependent parallel runway
 * @param rwy the dependent parallel runway
 * @param airport the airport the runway is in
 * @return whether the dependent parallel runway is clear for departure from original runway
 */
fun checkDependentParallelRunwayDepartureTraffic(rwy: Entity, airport: Airport): Boolean {
    val nextDeparture = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.aircraftPerf ?: return false
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    // Check time since departure - minimum 60s
    if (prevDeparture != null && prevDeparture.timeSinceDepartureS < 60) return false
    // Check turboprop separation
    if (prevDeparture != null && !checkPrevTurboprop(prevDeparture, nextDeparture)) return false
    return true
}

/**
 * Checks the traffic for a dependent opposite runway
 * @param rwy the dependent opposite runway
 * @param airport the airport the runway is in
 * @return whether the dependent opposite runway is clear for departure from original runway
 */
fun checkDependentOppositeRunwayTraffic(rwy: Entity, airport: Airport): Boolean {
    val nextDeparture = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.aircraftPerf ?: return false
    val nextArrival = rwy[RunwayNextArrival.mapper]
    val prevDeparture = rwy[RunwayPreviousDeparture.mapper]
    // Check distance from touchdown - minimum 18nm away
    if (nextArrival != null && nextArrival.distFromThrPx < nmToPx(18)) return false
    // Check time since departure - minimum 60s
    if (prevDeparture != null && prevDeparture.timeSinceDepartureS < 60) return false
    // Check turboprop separation
    if (prevDeparture != null && !checkPrevTurboprop(prevDeparture, nextDeparture)) return false
    return true
}

/**
 * Checks the traffic for an intersecting runway
 * @param rwy the intersecting runway
 * @return whether the intersecting runway is clear for departure from original runway
 */
fun checkCrossingRunwayTraffic(rwy: Entity): Boolean {
    val nextArrival = rwy[RunwayNextArrival.mapper]
    // Check if runway is occupied
    if (rwy.has(RunwayOccupied.mapper)) return false
    // Check time from touchdown - minimum 60s
    if (nextArrival != null && calculateTimeToThreshold(nextArrival.aircraft, rwy) < 60) return false
    return true
}

/**
 * Checks the traffic for a departure dependency rule
 * @param depRule the departure dependency rule to check
 * @return whether the departure dependency rule is clear for departure from original runway
 */
fun checkDepartureDependencyTraffic(depRule: DepartureDependency.DependencyRule): Boolean {
    depRule.arrivalSep?.let { arrSep ->
        // Check that arrival has either landed, or required time is available before it lands
        val nextArrivalAircraft = depRule.dependeeRwy[RunwayNextArrival.mapper]?.aircraft
        if (nextArrivalAircraft != null && !nextArrivalAircraft.has(LandingRoll.mapper) &&
                calculateTimeToThreshold(nextArrivalAircraft, depRule.dependeeRwy) < arrSep) return false
    }

    depRule.departureSep?.let { depSep ->
        // Check that required time has passed since last departure
        val prevDeparture = depRule.dependeeRwy[RunwayPreviousDeparture.mapper]
        if (prevDeparture != null && prevDeparture.timeSinceDepartureS < depSep) return false
    }

    return true
}

/**
 * Calculates the minimum additional time from the previous departure to the next departure for an airport depending on
 * its departure backlog
 * @param backlog the number of departure aircraft waiting
 * @param maxAdvDep the maximum number of departures to allow in advance of any arrivals (i.e. the maximum -departure backlog)
 * @return the minimum additional time, in seconds, between the previous departure and the next departure
 */
fun calculateAdditionalTimeToNextDeparture(backlog: Int, maxAdvDep: Int): Int {
    val threshold1 = (maxAdvDep * 0.5f).roundToInt()
    return when {
        backlog >= 10 -> 0
        backlog >= -threshold1 -> 0 + 240 * (10 - backlog) / (10 + threshold1)
        backlog >= -maxAdvDep -> 240 + (640 - 240) * (-threshold1 - backlog) / (maxAdvDep - threshold1)
        else -> 640
    }
}

/**
 * Finds the airport with the lowest elevation in the game world and gets its elevation
 * @return elevation of the lowest airport
 */
fun getLowestAirportElevation(): Float {
    var minElevation: Float? = null
    GAME.gameServer?.apply {
        Entries(airports).forEach {
            val alt = it.value.entity[Altitude.mapper]?.altitudeFt ?: return@forEach
            val finalMinElevation = minElevation
            if (finalMinElevation == null || alt < finalMinElevation) minElevation = alt
        }
    }
    return minElevation ?: 0f
}

/**
 * Gets the starting altitude for the conflict level distribution
 */
fun getConflictStartAltitude(): Int {
    return floor(getLowestAirportElevation() / VERT_SEP).roundToInt() * VERT_SEP
}

/**
 * Gets the minimum altitude that can be cleared by the ACC
 */
fun getACCStartAltitude(): Int {
    return (ceil(MAX_ALT / 1000f).roundToInt() - 1) * 1000
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
 */
fun getArrivalClosedAirports(): ByteArray {
    val airports = GdxArray<Byte>(AIRPORT_SIZE)
    GAME.gameServer?.airports?.let { arpts ->
        Entries(arpts).forEach {
            val arpt = it.value
            val id = arpt.entity[AirportInfo.mapper]?.arptId ?: return@forEach
            if (arpt.entity.has(ArrivalClosed.mapper)) airports.add(id)
        }
    }
    return airports.toArray().map { it }.toByteArray()
}

/**
 * Gets airports that are closed for departures
 * @return an array of bytes containing the ID of airports closed for departures
 */
fun getDepartureClosedAirports(): ByteArray {
    val airports = GdxArray<Byte>(AIRPORT_SIZE)
    GAME.gameServer?.airports?.let { arpts ->
        Entries(arpts).forEach {
            val arpt = it.value
            val id = arpt.entity[AirportInfo.mapper]?.arptId ?: return@forEach
            if (arpt.entity[DepartureInfo.mapper]?.closed == true) airports.add(id)
        }
    }
    return airports.toArray().map { it }.toByteArray()
}

/**
 * Gets the arrival traffic values for all airports
 *
 * @return an array of [TrafficSettingsData.AirportTrafficValue] containing the ID and arrival traffic values for all
 * airports
 */
fun getAirportArrivalValues(): Array<TrafficSettingsData.AirportTrafficValue> {
    val airports = GdxArray<TrafficSettingsData.AirportTrafficValue>(AIRPORT_SIZE)
    GAME.gameServer?.airports?.let { arpts ->
        Entries(arpts).forEach {
            val arpt = it.value
            val id = arpt.entity[AirportInfo.mapper]?.arptId ?: return@forEach
            val value = arpt.entity[AirportArrivalStats.mapper]?.targetTrafficValue ?: return@forEach
            airports.add(TrafficSettingsData.AirportTrafficValue(id, value))
        }
    }
    return airports.toArray().map { it }.toTypedArray()
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
    while (wake.wakeZones.size >= MAX_WAKE_DOTS) {
        val removed = wake.wakeZones.removeLast().second
        if (removed != null) system.removeWakeZone(removed)
    }
    // Increment distance from AC for each zone
    for (element in Queue.QueueIterator(wake.wakeZones))
        element.second?.entity?.get(WakeInfo.mapper)?.let { it.distFromAircraft += WAKE_DOT_SPACING_NM }
    val newWakeZone = if (wake.wakeZones.isEmpty) null
    else {
        val prevPos = wake.wakeZones.first().first
        val approachName = (aircraft[LocalizerCaptured.mapper]?.locApp ?: aircraft[GlideSlopeCaptured.mapper]?.gsApp
        ?: aircraft[VisualCaptured.mapper]?.parentApp ?: aircraft[CirclingApproach.mapper]?.let { app ->
            if (app.phase >= 1) app.circlingApp else null
        })?.get(ApproachInfo.mapper)?.approachName
        WakeZone(prevPos.x, prevPos.y, pos.x, pos.y, alt.altitudeFt, acInfo.icaoCallsign, acInfo.aircraftPerf.wakeCategory,
            acInfo.aircraftPerf.recat, aircraft[ArrivalAirport.mapper]?.arptId, approachName)
    }
    wake.wakeZones.addFirst(Pair(pos.copy(), newWakeZone))
    if (newWakeZone != null) system.addWakeZone(newWakeZone)
}

/**
 * Adds emergency components to aircraft depending on RNG results
 * @param aircraft the aircraft to maybe add emergency components to
 * @param airport the airport the aircraft is flying from, for elevation information
 */
private fun addEmergencyIfNeeded(aircraft: Entity, airport: Entity) {
    val emerChance = when (GAME.gameServer?.emergencyRate) {
        GameServer.EMERGENCY_LOW -> 0.005f
        GameServer.EMERGENCY_MEDIUM -> 0.01f
        GameServer.EMERGENCY_HIGH -> 0.02f
        else -> 0f
    }
    if (MathUtils.randomBoolean(emerChance)) {
        val elevation = airport[Altitude.mapper]?.altitudeFt ?: return
        val emerType = MathUtils.random(EmergencyPending.BIRD_STRIKE.toInt(), EmergencyPending.PRESSURE_LOSS.toInt()).toByte()
        val activationAlt = when (emerType) {
            EmergencyPending.BIRD_STRIKE -> {
                if (elevation > 7000) MathUtils.random(2300, 1500) + elevation
                else MathUtils.random(2300, 7000) + elevation
            }
            EmergencyPending.ENGINE_FAIL -> MathUtils.random(2300, 5000) + elevation
            EmergencyPending.HYDRAULIC_FAIL -> MathUtils.random(2300, 5000) + elevation
            EmergencyPending.MEDICAL -> MathUtils.random(4000, 14000) + elevation
            EmergencyPending.PRESSURE_LOSS -> MathUtils.random(12000f, 14500f)
            EmergencyPending.FUEL_LEAK -> MathUtils.random(11000f, 14500f)
            else -> {
                FileLog.info("TrafficTools", "Unknown emergency type $emerType")
                return
            }
        }
        aircraft += EmergencyPending(false, emerType, activationAlt.roundToInt())
    }
}
