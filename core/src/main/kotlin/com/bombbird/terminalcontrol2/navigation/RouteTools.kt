package com.bombbird.terminalcontrol2.navigation

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.RouteZone
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.ROUTE_RNP_NM
import com.bombbird.terminalcontrol2.navigation.Route.*
import com.bombbird.terminalcontrol2.utilities.calculateDistanceBetweenPoints
import com.bombbird.terminalcontrol2.utilities.ftToPx
import com.bombbird.terminalcontrol2.utilities.getRequiredTrack
import com.bombbird.terminalcontrol2.utilities.mToPx
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.math.plus
import ktx.math.times
import kotlin.math.min

/** Helper file containing functions for dealing with aircraft route shenanigans */

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
 * Checks whether the provided routes are equal - route length, and all legs must be strictly equal (including skipped legs,
 * cancelled restrictions for waypoint legs)
 *
 * Use this function when comparing route clearances in the UI
 * @param route1 the first route to compare
 * @param route2 the second route to compare
 * @return a boolean denoting whether the two routes are strictly equal
 * */
fun checkRouteEqualityStrict(route1: Route, route2: Route): Boolean {
    route1.let { legs1 -> route2.let { legs2 ->
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
        is VectorLeg, is InitClimbLeg, is DiscontinuityLeg -> return !route.contains(leg)
        is HoldLeg -> {
            for (i in 0 until route.size) route[i].apply { if (this is HoldLeg && (wptId == leg.wptId || (wptId <= -1 && leg.wptId <= -1))) return false }
            // No legs found with same wpt ID (or no present position hold leg with ID <= -1)
            return true
        }
        is WaypointLeg -> {
            for (i in 0 until route.size) route[i].apply { if (this is WaypointLeg && compareLegEquality(this, leg)) return false }
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
    for (i in 0 until route.size) route[i].apply { if (this is WaypointLeg && compareLegEquality(this, leg)) {
        return Triple(altRestrActive != leg.altRestrActive, spdRestrActive != leg.spdRestrActive, legActive != leg.legActive)
    }}
    // No legs found with exact same data, return all true
    return Triple(true, true, true)
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
fun getFirstWaypointLegInSector(sector: Polygon, route: Route): Int? {
    for (i in 0 until route.size) {
        (route[i] as? WaypointLeg)?.apply {
            val pos = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(Position.mapper) ?: return@apply
            if (sector.contains(pos.x, pos.y)) return i
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
    (route[0] as? WaypointLeg)?.let { wpt1 ->
        for (i in 1 until route.size) {
            (route[i] as? WaypointLeg)?.also { wpt2 ->
                if (!wpt2.legActive) return@also
                val w1 = GAME.gameServer?.waypoints?.get(wpt1.wptId)?.entity?.get(Position.mapper) ?: return null
                val w2 = GAME.gameServer?.waypoints?.get(wpt2.wptId)?.entity?.get(Position.mapper) ?: return null
                return Pair(getRequiredTrack(w1.x, w1.y, w2.x, w2.y), wpt2.turnDir)
            }
        }
    }
    return null
}

/**
 * Gets the after waypoint vector leg; if no vector legs exist after the waypoint leg, null is returned
 * @param wpt the waypoint leg to check for a vector leg after
 * @param route the route to refer to
 * @return a [VectorLeg], or null if no vector leg found
 * */
fun getAfterWptHdgLeg(wpt: WaypointLeg, route: Route): VectorLeg? {
    for (i in 0 until route.size) route[i].apply {
        if (compareLegEquality(wpt, this)) {
            if (route.size > i + 1) (route[i + 1] as? VectorLeg)?.let { return it } ?: return null // If subsequent leg exists and is vector, return it
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
    for (i in 0 until route.size) (route[i] as? WaypointLeg)?.apply {
        if (CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName == wptName) {
            if (route.size > i + 1) (route[i + 1] as? VectorLeg)?.let { return Pair(it, this) } ?: return null // If subsequent leg exists and is vector, return it
        }
    }
    return null
}

/**
 * Gets the next waypoint leg that has a vector leg after it; if none are found, null is returned
 * @param route the route to refer to
 * @return a [WaypointLeg], or null if no vector leg found
 * */
fun getNextAfterWptHdgLeg(route: Route): WaypointLeg? {
    if (route.size < 2) return null
    for (i in 0 until route.size - 1) {
        val currLeg = route[i]
        if (currLeg is WaypointLeg && route[i + 1] is VectorLeg) return currLeg
    }
    return null
}

/**
 * Gets the first upcoming holding leg; if a non-waypoint leg is reached before finding any
 * hold legs, null is returned
 * @param route the route to refer to
 * @return a [HoldLeg], or null if no hold legs are found
 * */
fun getNextHoldLeg(route: Route): HoldLeg? {
    for (i in 0 until route.size) route[i].apply {
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
        if (route.size == 0) return null
        return (route[0] as? HoldLeg)?.let {
            // If the first leg is hold and has a wptId of less than or equal to -1 (present position waypoints have custom IDs less than -1, or -1 if uninitialised)
            if (it.wptId <= -1) it else null
        }
    }

    for (i in 0 until route.size) route[i].apply {
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
    for (i in 0 until route.size) (route[i] as? WaypointLeg)?.let { if (it.maxSpdKt != null && it.legActive && it.spdRestrActive) return it } ?: return null
    return null
}

/**
 * Gets the speed restriction active at the active leg in the current departure route
 * @param route the route to refer to
 * @return the max speed, or null if a speed restriction does not exist
 * */
fun getNextMaxSpd(route: Route): Short? {
    for (i in 0 until route.size) return (route[i] as? WaypointLeg)?.let {
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
    for (i in 0 until route.size) return (route[i] as? WaypointLeg)?.let {
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
    for (i in 0 until route.size) return (route[i] as? WaypointLeg)?.let {
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
    for (i in 0 until route.size) (route[i] as? WaypointLeg)?.also {
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
    for (i in 0 until route.size) {
        if (startLeg != null && !startFound) startFound = compareLegEquality(startLeg, route[i])
        if (!startFound) continue
        if (route[i].phase == Leg.MISSED_APP) return true
        if (route[i] !is WaypointLeg) return false
    }
    return true
}

/**
 * Removes all the legs until the first missed approach leg (excluding the discontinuity leg) is reached
 * @param route the route to remove the legs from
 */
fun removeAllLegsTillMissed(route: Route) {
    for (i in 0 until  route.size) route[i].let { leg -> if (leg.phase == Leg.MISSED_APP && leg !is DiscontinuityLeg) {
        if (i > 0) route.removeRange(0, i - 1)
        return
    }}
}

/**
 * Sets all the missed approach legs to normal legs; this is called after the aircraft initiates a go around so subsequent
 * approach clearances will not have conflicting missed approach leg clearances
 * @param route the route to update the missed approach legs in
 */
fun setAllMissedLegsToNormal(route: Route) {
    for (i in 0 until route.size) route[i].let { leg -> if (leg.phase == Leg.MISSED_APP)
        leg.phase = Leg.NORMAL
    }
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
    for (i in route.size - 1 downTo 0) route[i].apply {
        if (phase != Leg.MISSED_APP) return null
        (this as? WaypointLeg)?.minAltFt?.let { return it } ?:
        (this as? InitClimbLeg)?.minAltFt?.let { return it }
    }
    return null
}

/**
 * Calculates the distance, in pixels, on the route given aircraft position, and the starting and ending legs
 * @param aircraftPos the position of the aircraft; if null, will ignore position of the aircraft and calculate only between
 * the two provided legs
 * @param startLeg the leg to start calculating from
 * @param endLeg the leg to stop the calculation
 * @param route the route to use for the calculation
 * @return the distance to go on the route, in pixels
 */
fun calculateDistToGo(aircraftPos: Position?, startLeg: Leg, endLeg: Leg, route: Route): Float {
    var cumulativeDist = 0f
    var prevX = aircraftPos?.x
    var prevY = aircraftPos?.y
    var foundStart = false
    for (i in 0 until route.size) { route[i].let { leg ->
        if (!foundStart && compareLegEquality(startLeg, leg)) {
            // Start leg has just been found
            foundStart = true
        }
        if (foundStart) (leg as? WaypointLeg)?.apply {
            if (!legActive) return@apply
            val wptPos = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(Position.mapper) ?: return@apply
            val finalX = prevX
            val finalY = prevY
            if (finalX != null && finalY != null) {
                cumulativeDist += calculateDistanceBetweenPoints(finalX, finalY, wptPos.x, wptPos.y)
            }
            prevX = wptPos.x
            prevY = wptPos.y
        }
        if (foundStart && compareLegEquality(endLeg, leg)) {
            // End leg reached, return distance
            return cumulativeDist
        }
    }}
    return cumulativeDist
}

/**
 * Checks whether the segments in the new segment array are present in the original segment array
 *
 * If a segment in the new segment array is not present in the original array, its [LegSegment.changed] will be set to
 * true for rendering purposes
 * @param originalSegments the original route segment
 * @param newSegments the new route segment to compare against the original one
 */
fun checkRouteSegmentChanged(originalSegments: GdxArray<LegSegment>, newSegments: GdxArray<LegSegment>) {
    for (i in 0 until newSegments.size) { newSegments[i]?.let {  newSeg ->
        newSeg.changed = !originalSegments.contains(newSeg, false)
    }}
}

/**
 * Calculates the segments of the input route, and sets the provided route segment array to them
 * @param route the route to calculate segments for
 * @param routeSegmentArray the array to set the newly calculate segments to
 * @param directLeg the currently selected direct leg
 */
fun calculateRouteSegments(route: Route, routeSegmentArray: GdxArray<LegSegment>, directLeg: Leg?) {
    routeSegmentArray.clear()
    var prevIndex = -1
    for (i in 0 until route.size) {
        val leg1 = if (prevIndex == -1) null else route[prevIndex]
        val leg2 = route[i]
        var directToWptExists = false
        // If the first route leg is a waypoint, add a segment from aircraft to waypoint
        if (leg2 is WaypointLeg && leg2.legActive) {
            if ((leg1 !is WaypointLeg && directLeg != null && compareLegEquality(directLeg, leg2)) || leg1 == null) {
                routeSegmentArray.add(LegSegment(leg2))
                directToWptExists = true
            }
        }
        if (leg1 is WaypointLeg && leg2 is WaypointLeg && leg2.legActive) routeSegmentArray.add(LegSegment(leg1, leg2))
        if (leg2 is HoldLeg) routeSegmentArray.add(LegSegment(leg2))
        if (leg1 is WaypointLeg && leg2 is VectorLeg) routeSegmentArray.add(LegSegment(leg1, leg2))
        if (leg1 is HoldLeg && leg2 is WaypointLeg && leg2.legActive) {
            var prevWptFound = false
            if (prevIndex > 0) { (route[prevIndex - 1] as? WaypointLeg)?.let {
                prevWptFound = true
                routeSegmentArray.add(LegSegment(it, leg2))
            }}
            if (!prevWptFound && !directToWptExists) routeSegmentArray.add(LegSegment(leg1, leg2))
        }
        if (leg2 !is WaypointLeg || leg2.legActive) prevIndex = i
    }
}

/**
 * Updates the route to include the input approach and transition given the arrival airport
 * @param route the route to edit
 * @param hiddenLegs the aircraft clearance's hidden legs
 * @param arptId the ID of the arrival airport
 * @param appName the name of the approach, or null if none cleared
 * @param transName the name of the approach transition, or null if no approach cleared
 * */
fun updateApproachRoute(route: Route, hiddenLegs: Route, arptId: Byte?, appName: String?, transName: String?) {
    removeApproachLegs(route, hiddenLegs)
    if (arptId == null || appName == null || transName == null) return
    val app = CLIENT_SCREEN?.airports?.get(arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(appName) ?: return
    val trans = app.transitions[transName] ?: null // Force to nullable Route? type, instead of a Route! type
    // Search for the first leg in the current route that matches the first leg in the transition
    val matchingIndex = if (transName != "vectors" && (trans?.size ?: 0) > 0) (trans?.get(0) as? WaypointLeg)?.let { firstTransWpt ->
        route.also { currRoute -> for (i in 0 until currRoute.size) {
            if (firstTransWpt.wptId == (currRoute[i] as? WaypointLeg)?.wptId) return@let i
        }}
        null
    } else null
    route.apply {
        if (matchingIndex == null) {
            add(DiscontinuityLeg(Leg.APP_TRANS))
            if (trans != null) extendRouteCopy(trans)
        } else {
            // Add the legs after the transition waypoint to hidden legs
            for (i in matchingIndex + 1 until size) hiddenLegs.add(get(i))
            if (matchingIndex <= size - 2) removeRange(matchingIndex + 1, size - 1) // Remove them from the current route if waypoints exist after
            if (trans != null) {
                extendRouteCopy(trans)
                // Remove the duplicate waypoint
                removeIndex(matchingIndex + 1)
            }
        }
        extendRouteCopy(app.routeLegs)
        extendRouteCopy(app.missedLegs)
    }
}

/**
 * Remove the current approach, approach transition, missed approach legs from route, and adds all the hidden route
 * legs back
 *
 * Also clears the hidden leg route
 * @param route the route to remove approach legs from
 * @param hiddenLegs the route containing currently hidden legs to add back to the route
 * */
fun removeApproachLegs(route: Route, hiddenLegs: Route) {
    for (i in route.size - 1 downTo 0) {
        if (route[i].phase == Leg.NORMAL) break // Once a normal leg is encountered, break from loop
        route.removeIndex(i)
    }
    route.extendRoute(hiddenLegs)
    hiddenLegs.clear()
}

/**
 * Gets the route MVA exclusion zones for the input route
 * @param route the route to find zones for
 * @return an array of route zones for each waypoint -> waypoint segment on the route
 */
fun getZonesForRoute(route: Route): GdxArray<RouteZone> {
    if (route.size == 0) return GdxArray()
    val segmentArray = GdxArray<LegSegment>()
    calculateRouteSegments(route, segmentArray, route[0])
    val routeZones = GdxArray<RouteZone>()
    var currMinAlt: Int? = null
    for (i in 0 until segmentArray.size) {
        segmentArray[i]?.apply {
            val finalLeg1 = leg1
            val finalLeg2 = leg2
            if (finalLeg1 is WaypointLeg && finalLeg2 is WaypointLeg) {
                val wpt1Pos = GAME.gameServer?.waypoints?.get(finalLeg1.wptId)?.entity?.get(Position.mapper) ?: return@apply
                val wpt2Pos = GAME.gameServer?.waypoints?.get(finalLeg2.wptId)?.entity?.get(Position.mapper) ?: return@apply
                // Get the lower altitude restriction among the 2 waypoints; or if one is null, use altitude restriction
                // of the other; if both null, use null
                val minAlt = if (finalLeg1.minAltFt == null && finalLeg2.minAltFt == null) null
                else if (finalLeg1.minAltFt == null) finalLeg2.minAltFt
                else if (finalLeg2.minAltFt == null) finalLeg1.minAltFt
                else min(finalLeg1.minAltFt, finalLeg2.minAltFt)
                // Get the lowest of the current minimum altitude and the minimum altitude of the 2 new waypoints
                val finalCurrMinAlt = currMinAlt
                val newCurrMinAlt = if (minAlt == null) finalCurrMinAlt
                else if (finalCurrMinAlt == null) minAlt
                else min(finalCurrMinAlt, minAlt)
                currMinAlt = newCurrMinAlt
                routeZones.add(RouteZone(wpt1Pos.x, wpt1Pos.y, wpt2Pos.x, wpt2Pos.y, ROUTE_RNP_NM, newCurrMinAlt))
            }
        }
    }
    return routeZones
}

/**
 * Gets the initial MVA exclusion zones for the initial climb portion of the SID - this can consist of a maximum of an
 * initial climb leg followed by a waypoint leg; it is possible for either or both legs to not exist as well
 * @param route the SID route to get initial climb zones for
 * @return an array of zones, with a maximum of 2 zones for the initial climb
 */
fun getZonesForInitialRunwayClimb(route: Route, rwy: Entity): GdxArray<RouteZone> {
    val zones = GdxArray<RouteZone>()
    if (route.size == 0) return zones
    val rwyPos = rwy[Position.mapper] ?: return zones
    val rwyLengthHalf = (rwy[RunwayInfo.mapper]?.lengthM ?: return zones) / 2f
    val rwyDir = rwy[Direction.mapper]?.trackUnitVector ?: return zones
    val startPos = Vector2(rwyPos.x, rwyPos.y) + rwyDir * (mToPx(rwyLengthHalf))
    (route[0] as? WaypointLeg)?.apply {
        val wptPos = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(Position.mapper) ?: return@apply
        zones.add(RouteZone(startPos.x, startPos.y, wptPos.x, wptPos.y, ROUTE_RNP_NM, null))
        return zones
    } ?: (route[0] as? InitClimbLeg)?.apply {
        val rwyAlt = rwy[Altitude.mapper]?.altitudeFt ?: return@apply
        // Assume 7% gradient climb
        val distPxNeeded = ftToPx(minAltFt - rwyAlt) * 100 / 7
        val trackVector = Vector2(Vector2.Y).rotateDeg(MAG_HDG_DEV - heading) * distPxNeeded
        val climbEndPos = startPos + trackVector
        zones.add(RouteZone(startPos.x, startPos.y, climbEndPos.x, climbEndPos.y, ROUTE_RNP_NM, null))
        if (route.size < 2) return@apply
        (route[1] as? WaypointLeg)?.also { wpt ->
            val wptPos = GAME.gameServer?.waypoints?.get(wpt.wptId)?.entity?.get(Position.mapper) ?: return@apply
            zones.add(RouteZone(climbEndPos.x, climbEndPos.y, wptPos.x, wptPos.y, ROUTE_RNP_NM, null))
        }
    }
    return zones
}
