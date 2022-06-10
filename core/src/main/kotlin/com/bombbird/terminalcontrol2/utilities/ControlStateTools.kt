package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.Route.*
import com.bombbird.terminalcontrol2.networking.AircraftControlStateUpdateData
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.ashley.remove
import ktx.collections.GdxArray
import ktx.scene2d.Scene2DSkin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Helper file containing functions for dealing with aircraft control states */

/** Gets the appropriate aircraft blip icon for its [flightType] and [sectorID] */
fun getAircraftIcon(flightType: Byte, sectorID: Byte): TextureRegionDrawable {
    return TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion(
        when (sectorID) {
            SectorInfo.CENTRE -> "aircraftEnroute"
            SectorInfo.TOWER -> "aircraftTower"
            else -> when (flightType) {
                FlightType.DEPARTURE -> "aircraftDeparture"
                FlightType.ARRIVAL -> "aircraftArrival"
                FlightType.EN_ROUTE -> "aircraftEnroute"
                else -> {
                    Gdx.app.log("ControlState", "Unknown flight type $flightType")
                    "aircraftEnroute"
                }
            }
        }))
}

/**
 * Adds a new clearance sent by the client to the pending clearances for an aircraft
 * @param entity the aircraft entity to add the clearance to
 * @param clearance the [AircraftControlStateUpdateData] object that the clearance will be constructed from
 * @param returnTripTime the time taken for a ping to be sent and acknowledged, in ms, calculated by the server; half of
 * this value in seconds will be subtracted from the delay time (2s) to account for any ping time lag
 * */
fun addNewClearanceToPendingClearances(entity: Entity, clearance: AircraftControlStateUpdateData, returnTripTime: Int) {
    val pendingClearances = entity[PendingClearances.mapper]
    val newClearance = ClearanceState(clearance.primaryName, Route.fromSerialisedObject(clearance.route), Route.fromSerialisedObject(clearance.hiddenLegs),
        clearance.vectorHdg, clearance.vectorTurnDir, clearance.clearedAlt,
        clearance.clearedIas, clearance.minIas, clearance.maxIas, clearance.optimalIas,
        clearance.clearedApp, clearance.clearedTrans)
    if (pendingClearances == null) entity += PendingClearances(Queue<ClearanceState.PendingClearanceState>().apply {
        addLast(ClearanceState.PendingClearanceState(2f - returnTripTime / 2000f, newClearance))
    })
    else pendingClearances.clearanceQueue.apply {
        val lastTime = last().timeLeft
        addLast(ClearanceState.PendingClearanceState(max(2f - returnTripTime / 2000f - lastTime, 0.01f), newClearance))
    }
}

/**
 * Checks whether the provided legs are equal - leg class and all of their properties must be the same except for
 * skipped legs or cancelled restrictions (for waypoint legs)
 * @param leg1 the first leg to compare
 * @param leg2 the second leg to compare
 * @return a boolean denoting whether the two input legs are the same
 * */
fun compareLegEquality(leg1: Leg, leg2: Leg): Boolean {
    if (leg1.phase != leg2.phase) return false
    return when {
        leg1 is WaypointLeg && leg2 is WaypointLeg ->
            leg1.wptId == leg2.wptId && leg1.minAltFt == leg2.minAltFt && leg1.maxAltFt == leg2.maxAltFt &&
                    leg1.maxSpdKt == leg2.maxSpdKt && leg1.flyOver == leg2.flyOver && leg1.turnDir == leg2.turnDir
        leg1 is HoldLeg && leg2 is HoldLeg ->
            leg1.wptId == leg2.wptId && leg1.maxAltFt == leg2.maxAltFt && leg1.minAltFt == leg2.minAltFt &&
                    leg1.maxSpdKtLower == leg2.maxSpdKtLower && leg1.maxSpdKtHigher == leg2.maxSpdKtHigher &&
                    leg1.inboundHdg == leg2.inboundHdg && leg1.legDist == leg2.legDist && leg1.turnDir == leg2.turnDir
        leg1 is VectorLeg && leg2 is VectorLeg -> leg1.heading == leg2.heading && leg1.turnDir == leg2.turnDir
        leg1 is InitClimbLeg && leg2 is InitClimbLeg -> leg1.heading == leg2.heading && leg1.minAltFt == leg2.minAltFt
        leg1 is DiscontinuityLeg && leg2 is DiscontinuityLeg -> true
        else -> false
    }
}

/**
 * Checks whether the provided routes have the same route segments (i.e. from one leg to the next)
 *
 * Use this function when comparing route clearances whose difference are to be drawn on the radarScreen
 * @param route1 the first route to compare
 * @param route2 the second route to compare
 * @return an array containing the index of the leg(s) in [route2] whose leg segment differs from in [route1]
 * */
fun checkRouteSegmentEquality(route1: Route, route2: Route): GdxArray<Int> {
    route1.legs.let { legs1 -> route2.legs.let { legs2 ->
        val changedIndices = GdxArray<Int>()
        var leg2Index = -1
        var leg2SecondIndex = 0
        while (leg2SecondIndex < legs2.size) {
            val firstLeg = if (leg2Index == -1) null else legs2[leg2Index]
            val secondLeg = legs2[leg2SecondIndex]
            // If second leg is skipped, increment second counter and continue to next iteration
            if ((secondLeg as? WaypointLeg)?.legActive == false) {
                leg2SecondIndex++
                continue
            } else if (secondLeg is HoldLeg) {
                // If second leg is hold leg, check for presence of the same leg in legs1 and add to changed if not present
                // Increment second counter and continue to next iteration
                if (checkLegChanged(route1, secondLeg)) changedIndices.add(leg2SecondIndex)
                leg2SecondIndex++
                continue
            }
            var leg1Index = -1
            var leg1SecondIndex = 0
            var found = false
            while (leg1SecondIndex < legs1.size) {
                val oldFirstLeg = if (leg1Index == -1) null else legs1[leg1Index]
                val oldSecondLeg = legs1[leg1SecondIndex]
                // If second leg is skipped or is hold, increment second counter and continue to next iteration
                if ((oldSecondLeg as? WaypointLeg)?.legActive == false || oldSecondLeg is HoldLeg) {
                    leg1SecondIndex++
                    continue
                }
                // Compare the 2 first legs and the 2 second legs; the first legs must either both be null (i.e. aircraft
                // position) or equal, and the second legs must be equal; if met, found is true and break from this loop
                if (((firstLeg == null && oldFirstLeg == null) ||
                        (firstLeg != null && oldFirstLeg != null && compareLegEquality(firstLeg, oldFirstLeg))) &&
                    compareLegEquality(secondLeg, oldSecondLeg)) {
                    found = true
                    break
                }
                // Set leg1 index to the second leg1 index, and increment second leg1 index
                leg1Index = leg1SecondIndex
                leg1SecondIndex++
            }
            // If not found by the end of the leg1 loop, add the leg2 index to list of changed indices
            if (!found) changedIndices.add(leg2Index)
            // Set leg2 index to the second leg2 index, and increment second leg2 index
            leg2Index = leg2SecondIndex
            leg2SecondIndex++
        }
        return changedIndices
    }}
}

/**
 * Checks whether the provided routes are equal - route length, and all legs must be strictly equal (including skipped legs,
 * cancelled restrictions for waypoint legs)
 *
 * Use this function when comparing route clearances in the UI
 * @param route1 the first route to compare
 * @param route2 the second route to compare
 * @return a boolean denoting whether the two routes are strictly equal
 * */
fun checkRouteEqualityStrict(route1: Route, route2: Route): Boolean {
    route1.legs.let { legs1 -> route2.legs.let { legs2 ->
        if (legs1.size != legs2.size) return false
        for (i in 0 until legs1.size) {
            val leg1 = legs1[i]
            val leg2 = legs2[i]
            if (leg1 != leg2) return false // Use data class generated equality check to ensure all properties are equal
        }
        return true
    }}
}

/**
 * Compares input leg with a route and checks if it has differed
 *
 * Conditions to be considered changed:
 *  - If leg is a vector/init climb/discontinuity/hold leg, and route does not contain a leg with the exact same value
 *  - If leg is a hold leg, route does not contain a leg with the same wptId
 *  - If leg is a waypoint leg, route does not contain a leg with the same wptId and restrictions
 * @param route the original to route to check for the leg
 * @param leg the leg to check for in the route
 * @return a boolean denoting whether the leg has differed from in the supplied route
 * */
fun checkLegChanged(route: Route, leg: Leg): Boolean {
    when (leg) {
        is VectorLeg, is InitClimbLeg, is DiscontinuityLeg -> return !route.legs.contains(leg, false)
        is HoldLeg -> {
            for (i in 0 until route.legs.size) route.legs[i]?.apply { if (this is HoldLeg && (wptId == leg.wptId || (wptId <= -1 && leg.wptId <= -1))) return false }
            // No legs found with same wpt ID (or no present position hold leg with ID <= -1)
            return true
        }
        is WaypointLeg -> {
            for (i in 0 until route.legs.size) route.legs[i]?.apply { if (this is WaypointLeg && compareLegEquality(this, leg)) return false }
            // No legs found with same wpt ID, restrictions
            return true
        }
        else -> {
            Gdx.app.log("ControlStateTools", "Unknown leg type ${leg::class}")
            return true
        }
    }
}

/**
 * Compares input waypoint leg and checks if any of the restriction flags or the skipped flags has changed
 * @param route the original to route to check for the waypoint leg
 * @param leg the waypoint leg to check for in the route
 * @return a [Triple] with 3 booleans denoting whether [WaypointLeg.altRestrActive], [WaypointLeg.spdRestrActive]
 * and [WaypointLeg.legActive] has changed respectively
 * */
fun checkRestrChanged(route: Route, leg: WaypointLeg): Triple<Boolean, Boolean, Boolean> {
    for (i in 0 until route.legs.size) route.legs[i]?.apply { if (this is WaypointLeg && compareLegEquality(this, leg)) {
        return Triple(altRestrActive != leg.altRestrActive, spdRestrActive != leg.spdRestrActive, legActive != leg.legActive)
    }}
    // No legs found with exact same data, return all true
    return Triple(true, true, true)
}

/**
 * Compares input clearance states and checks if they are the same; routes must be strictly equal and route name,
 * cleared altitude and cleared IAS must be equal as well
 * @param clearanceState1 the first clearance state to compare
 * @param clearanceState2 the second clearance state to compare
 * @return a boolean denoting whether the two clearance states are equal
 * */
fun checkClearanceEquality(clearanceState1: ClearanceState, clearanceState2: ClearanceState): Boolean {
    if (!checkRouteEqualityStrict(clearanceState1.route, clearanceState2.route)) return false
    if (!checkRouteEqualityStrict(clearanceState1.hiddenLegs, clearanceState2.hiddenLegs)) return false
    return clearanceState1.routePrimaryName == clearanceState2.routePrimaryName &&
            clearanceState1.vectorHdg == clearanceState2.vectorHdg &&
            clearanceState1.clearedAlt == clearanceState2.clearedAlt &&
            clearanceState1.clearedIas == clearanceState2.clearedIas &&
            clearanceState1.clearedApp == clearanceState2.clearedApp &&
            clearanceState1.clearedTrans == clearanceState2.clearedTrans
}

/**
 * Gets the minimum, maximum an optimal IAS that can be cleared depending on aircraft performance and navigation status
 *
 * The optimal IAS is the default IAS the aircraft will fly at without player intervention, hence it needs to be returned
 * to be added to the UI pane speed selectBox
 *
 * This also takes into account any current or upcoming speed restrictions from SIDs/STARs
 * @param entity the aircraft entity to calculate the speed values for
 * @return a [Triple] that contains 3 shorts, the first being the minimum IAS, the second being the maximum IAS, and
 * the third being the optimal IAS for the phase of flight
 * */
fun getMinMaxOptimalIAS(entity: Entity): Triple<Short, Short, Short> {
    val perf = entity[AircraftInfo.mapper]?.aircraftPerf ?: return Triple(150, 250, 240)
    val altitude = entity[Altitude.mapper] ?: return Triple(150, 250, 240)
    val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.actingClearance ?: return Triple(150, 250, 240)
    val flightType = entity[FlightType.mapper] ?: return Triple(150, 250, 240)
    val onApproach = entity.has(LocalizerCaptured.mapper) || entity.has(VisualCaptured.mapper) || entity.has(GlideSlopeCaptured.mapper)
    val takingOff = entity[TakeoffClimb.mapper] != null || entity[TakeoffRoll.mapper] != null
    val lastRestriction = entity[LastRestrictions.mapper]
    val crossOverAlt = calculateCrossoverAltitude(perf.tripIas, perf.tripMach)
    val below10000ft = altitude.altitudeFt < 10000
    val between10000ftAndCrossover = altitude.altitudeFt >= 10000 && altitude.altitudeFt < crossOverAlt
    val aboveCrossover = altitude.altitudeFt >= crossOverAlt
    val minSpd: Short = when {
        onApproach -> perf.appSpd
        takingOff || below10000ft -> perf.climbOutSpeed
        between10000ftAndCrossover || aboveCrossover -> (((altitude.altitudeFt - 10000) / (perf.maxAlt - 10000)) * (perf.climbOutSpeed * 2f / 9) + perf.climbOutSpeed * 10f / 9).roundToInt().toShort()
        else -> 160
    }
    // Aircraft enforced speeds - max 250 knots below 10000 ft, max IAS between 10000 ft and crossover, max mach above crossover
    val maxAircraftSpd: Short = when {
        below10000ft -> min(250, perf.maxIas.toInt()).toShort()
        between10000ftAndCrossover -> perf.maxIas
        aboveCrossover -> calculateIASFromMach(altitude.altitudeFt, perf.maxMach).roundToInt().toShort()
        else -> 250
    }
    val nextRouteMaxSpd = getNextMaxSpd(actingClearance.route)
    // SID/STAR enforced max speeds
    val maxSpd = if (actingClearance.vectorHdg != null) null // If being cleared on vectors, no speed restrictions
    else {
        var holdMaxSpd: Short? = null
        var holding = false
        // Check if aircraft is holding; if so use the max speed for the holding leg
        actingClearance.route.legs.let {
            if (it.size > 0) holdMaxSpd = (it[0] as? HoldLeg)?.let { holdLeg ->
                holding = true
                if (altitude.altitudeFt > 14050) holdLeg.maxSpdKtHigher else holdLeg.maxSpdKtLower
            }
        }
        // If aircraft is not holding, get restrictions for the SID/STAR
        if (!holding) holdMaxSpd = lastRestriction?.maxSpdKt.let { lastMaxSpd ->
            when {
                lastMaxSpd != null && nextRouteMaxSpd != null -> max(lastMaxSpd.toInt(), nextRouteMaxSpd.toInt()).toShort()
                lastMaxSpd != null -> when (flightType.type) {
                    FlightType.DEPARTURE -> null// No further max speeds, but aircraft is a departure so allow acceleration beyond previous max speed
                    FlightType.ARRIVAL -> lastMaxSpd// No further max speeds, use the last max speed
                    else -> null
                }
                nextRouteMaxSpd != null -> when (flightType.type) {
                    FlightType.DEPARTURE -> nextRouteMaxSpd// No max speeds before, but aircraft is a departure so must follow all subsequent max speeds
                    FlightType.ARRIVAL -> null// No max speeds before
                    else -> null
                }
                else -> null
            }
        }
        holdMaxSpd
    }
    val optimalSpd: Short = MathUtils.clamp(when {
        takingOff -> perf.climbOutSpeed
        below10000ft -> min(250, perf.maxIas.toInt()).toShort()
        between10000ftAndCrossover -> perf.tripIas
        aboveCrossover -> calculateIASFromMach(altitude.altitudeFt, perf.tripMach).roundToInt().toShort()
        else -> 240
    }, minSpd, maxAircraftSpd)
    return Triple(minSpd, min(maxAircraftSpd.toInt(), (maxSpd ?: maxAircraftSpd).toInt()).toShort(), min(optimalSpd.toInt(), (maxSpd ?: optimalSpd).toInt()).toShort())
}

/**
 * Calculates the arrival aircraft's IAS that it should be set to fly at on aircraft creation
 * @param origStarRoute the original route of the STAR
 * @param aircraftRoute the current aircraft route
 * @param spawnAlt the altitude at which the aircraft will spawn at
 * @return the IAS the aircraft should spawn at
 * */
fun calculateArrivalSpawnIAS(origStarRoute: Route, aircraftRoute: Route, spawnAlt: Float, aircraftPerf: AircraftTypeData.AircraftPerfData): Short {
    var maxSpd: Short? = null
    // Try to find an existing speed restriction
    if (aircraftRoute.legs.size > 0) { (aircraftRoute.legs[0] as? WaypointLeg)?.apply {
        for (i in 0 until origStarRoute.legs.size) { origStarRoute.legs[i]?.let { wpt ->
            if (compareLegEquality(this, wpt)) return@apply // Stop searching for max speed once current direct is reached
            val currMaxSpd = maxSpd
            maxSpdKt?.let { wptMaxSpd -> if (currMaxSpd == null || wptMaxSpd < currMaxSpd) maxSpd = maxSpdKt }
        }}
    }}

    val crossOverAlt = calculateCrossoverAltitude(aircraftPerf.tripIas, aircraftPerf.tripMach)
    return min((maxSpd ?: aircraftPerf.maxIas).toInt(), (if (spawnAlt > crossOverAlt) calculateIASFromMach(spawnAlt, aircraftPerf.tripMach).roundToInt().toShort()
    else aircraftPerf.tripIas).toInt()).toShort()
}

/**
 * Creates a new custom waypoint for the purpose of present position holds, and adds it to the waypoint map
 *
 * Also sends a network update to all clients informing them of the new waypoint addition
 * @param posX the x coordinate of the new waypoint
 * @param posY the y coordinate of the new waypoint
 * @return the wptID of the new created waypoint
 * */
fun createCustomHoldWaypoint(posX: Float, posY: Float): Short {
    // Search for an available ID below -1
    var wptId: Short? = null
    for (i in -2 downTo Short.MIN_VALUE) if (GAME.gameServer?.waypoints?.containsKey(i.toShort()) == false) {
        wptId = i.toShort()
        break
    }
    if (wptId == null) {
        Gdx.app.log("ControlStateTools", "Could not find a custom waypoint ID to use")
        return 0
    }
    // Create the waypoint, add to gameServer, and send data to clients
    val newWpt = Waypoint(wptId, "", posX.toInt().toShort(), posY.toInt().toShort(), false)
    GAME.gameServer?.apply {
        waypoints[wptId] = newWpt
        sendCustomWaypointAdditionToAll(newWpt)
    }
    return wptId
}

/**
 * Removes a custom waypoint with its ID
 * @param wptId the ID of the waypoint to remove; this must be less than -1
 * */
fun removeCustomHoldWaypoint(wptId: Short) {
    if (wptId >= -1) {
        Gdx.app.log("ControlStateTools", "Custom waypoint must have ID < -1; $wptId was provided")
        return
    }
    // Remove the custom waypoint with specified ID
    GAME.gameServer?.apply {
        waypoints[wptId]?.let { engine.removeEntity(it.entity) }
        waypoints.remove(wptId)
        sendCustomWaypointRemovalToAll(wptId)
    }
}

/**
 * Gets the first waypoint leg in the route that is within the input sector
 * @param sector the sector to test the waypoint in
 * @param route the route to refer to
 * @return a Pair, first being the [WaypointLeg], second being the index of the [WaypointLeg], or null if none found
 * */
fun getFirstWaypointLegInSector(sector: Polygon, route: Route): Pair<WaypointLeg, Int>? {
    for (i in 0 until route.legs.size) {
        (route.legs[i] as? WaypointLeg)?.apply {
            val pos = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(Position.mapper) ?: return@apply
            if (sector.contains(pos.x, pos.y)) return Pair(this, i)
        }
    }
    return null
}

/**
 * Gets the track and turn direction from the first to second waypoint, or null if there is no second waypoint leg
 * @param route the route to refer to
 * @return a Pair, the first being a float denoting the track, the second being a byte representing the turn direction
 * */
fun findNextWptLegTrackAndDirection(route: Route): Pair<Float, Byte>? {
    if (route.legs.size < 2) return null
    (route.legs[0] as? WaypointLeg)?.let { wpt1 ->
        (route.legs[1] as? WaypointLeg)?.let { wpt2 ->
            val w1 = GAME.gameServer?.waypoints?.get(wpt1.wptId)?.entity?.get(Position.mapper) ?: return null
            val w2 = GAME.gameServer?.waypoints?.get(wpt2.wptId)?.entity?.get(Position.mapper) ?: return null
            return Pair(getRequiredTrack(w1.x, w1.y, w2.x, w2.y), wpt2.turnDir)
        }
    } ?: return null
}

/**
 * Gets the after waypoint vector leg; if no vector legs exist after the waypoint leg, null is returned
 * @param wpt the waypoint leg to check for a vector leg after
 * @param route the route to refer to
 * @return a [VectorLeg], or null if no vector leg found
 * */
fun getAfterWptHdgLeg(wpt: WaypointLeg, route: Route): VectorLeg? {
    for (i in 0 until route.legs.size) route.legs[i]?.apply {
        if (compareLegEquality(wpt, this)) {
            if (route.legs.size > i + 1) (route.legs[i + 1] as? VectorLeg)?.let { return it } ?: return null // If subsequent leg exists and is vector, return it
        }
    }
    return null
}

/**
 * Gets the after waypoint vector AND waypoint legs; if no vector legs exist after the waypoint leg, null is returned
 * @param wptName the waypoint name to check for a vector leg after
 * @param route the route to refer to
 * @return a pair, the first being the [VectorLeg] and the second being the [WaypointLeg] the earlier vector leg
 * belongs to, or null if no vector leg found
 * */
fun getAfterWptHdgLeg(wptName: String, route: Route): Pair<VectorLeg, WaypointLeg>? {
    for (i in 0 until route.legs.size) (route.legs[i] as? WaypointLeg)?.apply {
        if (GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName == wptName) {
            if (route.legs.size > i + 1) (route.legs[i + 1] as? VectorLeg)?.let { return Pair(it, this) } ?: return null // If subsequent leg exists and is vector, return it
        }
    }
    return null
}

/**
 * Gets the current direct if there is a vector leg immediately after; if no vector legs exist after the waypoint leg,
 * or current direct is not waypoint leg, null is returned
 * @param route the route to refer to
 * @return a [WaypointLeg], or null if no vector leg found
 * */
fun getNextAfterWptHdgLeg(route: Route): WaypointLeg? {
    if (route.legs.size < 2) return null
    val firstLeg = route.legs[0]
    if (firstLeg !is WaypointLeg || route.legs[1] !is VectorLeg) return null
    return firstLeg
}

/**
 * Gets the first upcoming holding leg; if a non-waypoint leg is reached before finding any
 * hold legs, null is returned
 * @param route the route to refer to
 * @return a [HoldLeg], or null if no hold legs are found
 * */
fun getNextHoldLeg(route: Route): HoldLeg? {
    for (i in 0 until route.legs.size) route.legs[i]?.apply {
        if (this is HoldLeg) return this
        else if (this !is WaypointLeg) return null
    }
    return null
}

/**
 * Gets the first upcoming hold leg with the input ID; if a non-waypoint/hold leg is reached before finding any hold
 * legs, null is returned
 *
 * If waypoint ID is -1, a search for present position hold leg in the first position is done instead, and returns it
 * if found
 * @param wptId the waypoint ID of the hold leg to search for in the route
 * @param route the route to refer to
 * @return a [HoldLeg], or null if no hold legs are found
 * */
fun findFirstHoldLegWithID(wptId: Short, route: Route): HoldLeg? {
    if (wptId <= -1) {
        // Searching for present position hold leg - only the first leg should be
        if (route.legs.size == 0) return null
        return (route.legs[0] as? HoldLeg)?.let {
            // If the first leg is hold and has a wptId of less than or equal to -1 (present position waypoints have custom IDs less than -1, or -1 if uninitialised)
            if (it.wptId <= -1) it else null
        }
    }

    for (i in 0 until route.legs.size) route.legs[i]?.apply {
        if (this is HoldLeg && this.wptId == wptId) return this
        else if (this !is WaypointLeg && this !is HoldLeg) return null
    }
    return null
}

/**
 * Gets the first upcoming waypoint leg with a speed restriction; if a non-waypoint leg is reached before finding any
 * waypoint legs with a restriction, null is returned
 * @param route the route to refer to
 * @return a [WaypointLeg], or null if no legs with a speed restriction are found
 * */
fun getNextWaypointWithSpdRestr(route: Route): WaypointLeg? {
    for (i in 0 until route.legs.size) (route.legs[i] as? WaypointLeg)?.let { if (it.maxSpdKt != null && it.spdRestrActive) return it } ?: return null
    return null
}

/**
 * Gets the speed restriction active at the active leg in the current departure route
 * @param route the route to refer to
 * @return the max speed, or null if a speed restriction does not exist
 * */
fun getNextMaxSpd(route: Route): Short? {
    for (i in 0 until route.legs.size) return (route.legs[i] as? WaypointLeg)?.let {
        if (it.legActive && it.spdRestrActive) it.maxSpdKt else null
    } ?: continue
    return null
}

/**
 * Gets the next minimum altitude restriction for the route
 * @param route the route to refer to
 * @return the minimum altitude, or null if a minimum altitude restriction does not exist
 * */
fun getNextMinAlt(route: Route): Int? {
    for (i in 0 until route.legs.size) return (route.legs[i] as? WaypointLeg)?.let {
        if (it.legActive && it.altRestrActive) it.minAltFt else null
    } ?: continue
    return null
}

/**
 * Gets the next maximum altitude restriction for the route
 * @param route the route to refer to
 * @return the maximum altitude, or null if a maximum altitude restriction does not exist
 * */
fun getNextMaxAlt(route: Route): Int? {
    for (i in 0 until route.legs.size) return (route.legs[i] as? WaypointLeg)?.let {
        if (it.legActive && it.altRestrActive) it.maxAltFt else null
    } ?: continue
    return null
}

/**
 * Gets the FAF altitude for the current route, assuming an approach has been cleared
 * @param route the route to refer to
 * @return the FAF altitude of the cleared approach
 */
fun getFafAltitude(route: Route): Int? {
    var latestMinAlt: Int? = null
    for (i in 0 until route.legs.size) (route.legs[i] as? WaypointLeg)?.also {
        if (it.phase == Leg.APP) it.minAltFt?.let { faf -> latestMinAlt = faf }
        else if (it.phase == Leg.MISSED_APP) return latestMinAlt
    }
    return latestMinAlt
}

/**
 * Checks whether this route contains only waypoint legs (i.e. no discontinuity, vector or hold legs) until the start
 * of missed approach procedure
 * @param startLeg the leg to start checking from, or null to start checking from the first leg of the route
 * @param route the route to refer to
 * @return whether the route only contains waypoint legs
 */
fun hasOnlyWaypointLegsTillMissed(startLeg: Leg?, route: Route): Boolean {
    var startFound = startLeg == null
    for (i in 0 until route.legs.size) {
        if (startLeg != null) startFound = compareLegEquality(startLeg, route.legs[i])
        if (!startFound) continue
        if (route.legs[i].phase == Leg.MISSED_APP) return true
        if (route.legs[i] !is WaypointLeg) return false
    }
    return true
}

/**
 * Removes all the legs until the first missed approach leg (excluding the discontinuity leg) is reached
 * @param route the route to remove the legs from
 */
fun removeAllLegsTillMissed(route: Route) {
    for (i in 0 until  route.legs.size) route.legs[i]?.let { leg -> if (leg.phase == Leg.MISSED_APP && leg !is DiscontinuityLeg) {
        if (i > 0) route.legs.removeRange(0, i - 1)
        return
    }}
}

/**
 * Finds the missed approach altitude for the current route
 *
 * The altitude will be the last missed approach waypoint minimum altitude restriction or init climb altitude
 *
 * If no such legs are found, null is returned
 * @param route the route to find the missed approach altitude in
 */
fun findMissedApproachAlt(route: Route): Int? {
    for (i in route.legs.size - 1 downTo 0) route.legs[i]?.apply {
        if (phase != Leg.MISSED_APP) return null
        (this as? WaypointLeg)?.minAltFt?.let { return it } ?:
        (this as? InitClimbLeg)?.minAltFt?.let { return it }
    }
    return null
}

/**
 * Removes all approach components from the aircraft
 * @param aircraft the aircraft entity
 */
fun removeAllApproachComponents(aircraft: Entity) {
    aircraft.apply {
        remove<GlideSlopeArmed>()
        remove<GlideSlopeCaptured>()
        remove<LocalizerArmed>()
        remove<LocalizerCaptured>()
        remove<StepDownApproach>()
        remove<VisualCaptured>()
        remove<CirclingApproach>()
    }
}
