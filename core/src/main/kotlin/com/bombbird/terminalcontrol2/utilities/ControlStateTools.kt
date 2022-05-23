package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.networking.AircraftControlStateUpdateData
import ktx.ashley.get
import ktx.ashley.plusAssign
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
        clearance.vectorHdg, clearance.vectorTurnDir, clearance.clearedAlt, clearance.clearedIas, clearance.minIas, clearance.maxIas, clearance.optimalIas)
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
fun compareLegEquality(leg1: Route.Leg, leg2: Route.Leg): Boolean {
    if (leg1.phase != leg2.phase) return false
    return when {
        leg1 is Route.WaypointLeg && leg2 is Route.WaypointLeg ->
            leg1.wptId == leg2.wptId && leg1.minAltFt == leg2.minAltFt && leg1.maxAltFt == leg2.maxAltFt &&
                    leg1.maxSpdKt == leg2.maxSpdKt && leg1.flyOver == leg2.flyOver && leg1.turnDir == leg2.turnDir
        leg1 is Route.HoldLeg && leg2 is Route.HoldLeg ->
            leg1.wptId == leg2.wptId && leg1.maxAltFt == leg2.maxAltFt && leg1.minAltFt == leg2.minAltFt &&
                    leg1.maxSpdKtLower == leg2.maxSpdKtLower && leg1.maxSpdKtHigher == leg2.maxSpdKtHigher &&
                    leg1.inboundHdg == leg2.inboundHdg && leg1.legDist == leg2.legDist && leg1.turnDir == leg2.turnDir
        leg1 is Route.VectorLeg && leg2 is Route.VectorLeg -> leg1.heading == leg2.heading && leg1.turnDir == leg2.turnDir
        leg1 is Route.InitClimbLeg && leg2 is Route.InitClimbLeg -> leg1.heading == leg2.heading && leg1.minAltFt == leg2.minAltFt
        leg1 is Route.DiscontinuityLeg && leg2 is Route.DiscontinuityLeg -> true
        else -> false
    }
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
fun checkLegChanged(route: Route, leg: Route.Leg): Boolean {
    when (leg) {
        is Route.VectorLeg, is Route.InitClimbLeg, is Route.DiscontinuityLeg -> return !route.legs.contains(leg, false)
        is Route.HoldLeg -> {
            for (i in 0 until route.legs.size) route.legs[i]?.apply { if (this is Route.HoldLeg && (wptId == leg.wptId || (wptId <= -1 && leg.wptId <= -1))) return false }
            // No legs found with same wpt ID (or no present position hold leg with ID <= -1)
            return true
        }
        is Route.WaypointLeg -> {
            for (i in 0 until route.legs.size) route.legs[i]?.apply { if (this is Route.WaypointLeg && compareLegEquality(this, leg)) return false }
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
 * @return a [Triple] with 3 booleans denoting whether [Route.WaypointLeg.altRestrActive], [Route.WaypointLeg.spdRestrActive]
 * and [Route.WaypointLeg.legActive] has changed respectively
 * */
fun checkRestrChanged(route: Route, leg: Route.WaypointLeg): Triple<Boolean, Boolean, Boolean> {
    for (i in 0 until route.legs.size) route.legs[i]?.apply { if (this is Route.WaypointLeg && compareLegEquality(this, leg)) {
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
            clearanceState1.clearedIas == clearanceState2.clearedIas
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
    val lastRestriction = entity[LastRestrictions.mapper] ?: return Triple(150, 250, 240)
    val flightType = entity[FlightType.mapper] ?: return Triple(150, 250, 240)
    val takingOff = entity[TakeoffClimb.mapper] != null || entity[TakeoffRoll.mapper] != null
    val crossOverAlt = calculateCrossoverAltitude(perf.tripIas, perf.maxMach)
    val below10000ft = altitude.altitudeFt < 10000
    val between10000ftAndCrossover = altitude.altitudeFt >= 10000 && altitude.altitudeFt < crossOverAlt
    val aboveCrossover = altitude.altitudeFt >= crossOverAlt
    val minSpd: Short = when {
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
    val nextRouteMaxSpd = actingClearance.route.getNextMaxSpd()
    // SID/STAR enforced max speeds
    val maxSpd = if (actingClearance.vectorHdg != null) null // If being cleared on vectors, no speed restrictions
    else {
        var holdMaxSpd: Short? = null
        var holding = false
        // Check if aircraft is holding; if so use the max speed for the holding leg
        actingClearance.route.legs.let {
            if (it.size > 0) holdMaxSpd = (it[0] as? Route.HoldLeg)?.let { holdLeg ->
                holding = true
                if (altitude.altitudeFt > 14050) holdLeg.maxSpdKtHigher else holdLeg.maxSpdKtLower
            }
        }
        // If aircraft is not holding, get restrictions for the SID/STAR
        if (!holding) holdMaxSpd = lastRestriction.maxSpdKt.let { lastMaxSpd ->
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
 * Creates a new custom waypoint for the purpose of present position holds, and adds it to the waypoint map
 *
 * Also sends a network update to all clients informing them of the new waypoint addition
 * @param posX the x coordinate of the new waypoint
 * @param posY the y coordinate of the new wayoint
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
