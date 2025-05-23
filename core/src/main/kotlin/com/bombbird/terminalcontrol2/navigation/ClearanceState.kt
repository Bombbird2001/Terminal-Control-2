package com.bombbird.terminalcontrol2.navigation

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MIN_ALT
import com.bombbird.terminalcontrol2.utilities.*
import com.squareup.moshi.JsonClass
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.ashley.remove
import kotlin.math.min

/**
 * Clearance class that contains data for player transmitted aircraft clearances
 *
 * [routePrimaryName] is the name of the SID/STAR that the route is derived from
 *
 * [route] is the active route the aircraft is flying - the first leg in the array is the active leg which the aircraft
 * will target; components will also be tagged to the aircraft to implement the actual behaviour
 *
 * [hiddenLegs] is an array of legs that are removed from the original route due to the use of transitions, but may be
 * added back to the route should the transition change
 *
 * [vectorHdg] is the player cleared vector heading, and should be null when the aircraft is not under direct vectors
 *
 * [clearedAlt] is the player cleared altitude (before taking into account SID/STAR altitude restrictions)
 *
 * [clearedIas] is from the CommandTarget component; the targetIas value can be used directly as it is not possible to
 * "Accelerate/Decelerate via SID/STAR"
 *
 * [minIas] is the minimum IAS the aircraft can fly
 *
 * [maxIas] is the maximum IAS the aircraft can fly
 *
 * [optimalIas] is the optimal IAS the aircraft will try to fly
 *
 * [clearedApp] is the approach cleared for the aircraft
 *
 * [clearedTrans] is the transition cleared for the aircraft
 *
 * [cancelLastMaxSpd] is a boolean that indicates whether the last maximum speed clearance should be cancelled
 *
 * Also contains utility functions for updating the actual aircraft command state
 */
@JsonClass(generateAdapter = true)
data class ClearanceState(var routePrimaryName: String = "", val route: Route = Route(), val hiddenLegs: Route = Route(),
                          var vectorHdg: Short? = null, var vectorTurnDir: Byte? = null,
                          var clearedAlt: Int = 0, var expedite: Boolean = false, var clearedIas: Short = 0,
                          var minIas: Short = 0, var maxIas: Short = 0, var optimalIas: Short = 0,
                          var clearedApp: String? = null, var clearedTrans: String? = null,
                          var cancelLastMaxSpd: Boolean = false, var initiateGoAround: Boolean = false) {

    // Default constructor for JSON parsing
    @Suppress("unused")
    private constructor(): this(cancelLastMaxSpd = false, initiateGoAround = false)

    /**
     * Inner class used solely to denote and store the currently active clearance the aircraft is following
     *
     * Provides functions that interact with new player clearances as well as the aircraft command target state
     */
    inner class ActingClearance {
        val clearanceState: ClearanceState
            get() = this@ClearanceState

        /**
         * Updates this acting clearance to the new clearance, performing corrections in case of any conflicts caused due to
         * pilot response time
         *
         * Also adds the custom waypoint in the event of a hold at present position
         * @param newClearance the new player sent clearance (that is being removed from PendingClearances)
         * @param entity the aircraft entity that the clearance will be applied to
         */
        fun updateClearanceAct(newClearance: ClearanceState, entity: Entity) {
            val starChanged = routePrimaryName != newClearance.routePrimaryName
            routePrimaryName = newClearance.routePrimaryName
            val appChanged = newClearance.clearedApp != clearedApp
            val transChanged = newClearance.clearedTrans != clearedTrans

            val currFirstLeg = if (route.size > 0) route[0] else null
            route.let { currRoute -> newClearance.route.let { newRoute ->
                val newFirstLeg = if (newRoute.size > 0) newRoute[0] else null
                // Remove current present position hold leg if next leg is a different hold leg and is not an uninitialised leg, or not a hold leg
                if (currFirstLeg is Route.HoldLeg && currFirstLeg.wptId < -1 && (newFirstLeg !is Route.HoldLeg || (newFirstLeg.wptId.toInt() != -1 && newFirstLeg.wptId != currFirstLeg.wptId)))
                    removeCustomHoldWaypoint(currFirstLeg.wptId)
                // Create new present hold waypoint if previous leg is not a present hold leg (wptId >= 0), and new leg is uninitialised
                if ((currFirstLeg !is Route.HoldLeg || currFirstLeg.wptId >= 0) && (newFirstLeg as? Route.HoldLeg)?.wptId?.toInt() == -1)
                    entity[Position.mapper]?.apply {
                        newFirstLeg.wptId = createCustomHoldWaypoint(x, y)
                    }
                // If previous leg is a present hold leg, and new leg is an uninitialised present hold leg, set new leg ID to that of current leg
                if (currFirstLeg is Route.HoldLeg && currFirstLeg.wptId < -1 && newFirstLeg is Route.HoldLeg && newFirstLeg.wptId.toInt() == -1)
                    newFirstLeg.wptId = currFirstLeg.wptId

                // Aircraft has flown by its last waypoint, but the new clearance wants it to hold at that waypoint;
                // clear the current route, and add the new route from the 2nd leg (the hold leg) onwards
                if (currFirstLeg == null && newFirstLeg != null && newRoute.size >= 2 && newRoute[1] is Route.HoldLeg)
                    newRoute.removeIndex(0)

                if (currFirstLeg != null && newFirstLeg != null) {
                    if (!compareLegEquality(currFirstLeg, newFirstLeg)) {
                        // 4 possible leg conflict scenarios
                        if (newRoute.size >= 3 && newRoute[1] is Route.HoldLeg && compareLegEquality(newRoute[2], currFirstLeg)) {
                            // 1. Aircraft has flown by waypoint, but the new clearance wants it to hold at that waypoint;
                            // clear the current route, and add the new route from the 2nd leg (the hold leg) onwards
                            // Only works if there is a waypoint after the new hold leg
                            newRoute.removeIndex(0)
                        } else if (currFirstLeg is Route.WaypointLeg && newFirstLeg is Route.WaypointLeg) {
                            var currDirIndex = -1
                            // Check for whether the new route contains the current direct
                            for (i in 0 until newRoute.size) {
                                if (compareLegEquality(currFirstLeg, newRoute[i])) {
                                    currDirIndex = i
                                    break
                                }
                            }
                            if (currDirIndex > 0) {
                                // 2. Aircraft has been cleared a new approach or transition, new clearance contains leg(s)
                                // from the existing approach/transition but legs prior to that should not be removed
                                val adjustedCurrDir = if (transChanged || appChanged) {
                                    var newDirIndex = currDirIndex
                                    for (i in 0 until currDirIndex) {
                                        if (newRoute[i].phase == Route.Leg.APP_TRANS) {
                                            newDirIndex = i
                                            break
                                        }
                                    }
                                    newDirIndex
                                } /* else if (appChanged) {
                                    var newDirIndex = currDirIndex
                                    for (i in 0 until currDirIndex) {
                                        if (newRoute[i].phase == Route.Leg.APP) {
                                            newDirIndex = i
                                            break
                                        }
                                    }
                                    newDirIndex
                                } */ else {
                                    // 3. Aircraft has flown by waypoint, new clearance is the same route except it still
                                    // contains that waypoint; remove all legs from the new clearance till the current direct
                                    currDirIndex
                                }

                                if (adjustedCurrDir > 0) newRoute.removeRange(0, adjustedCurrDir - 1)
                            } else {
                                // 4. Aircraft has flown by waypoint, but the new clearance does not contain the current
                                // direct (possibly due to player assigning approach transition); remove the first leg
                                // from new clearance if the current direct is a normal phase leg
                                // 4a. The current direct leg may be an approach/transition phase if the aircraft has
                                // been cleared from vectors, in this case do not remove the first leg since the aircraft
                                // has not passed by it
                                // 4b. STAR may have been changed, in this case just use the new route as it is
                                if (currFirstLeg.phase == Route.Leg.NORMAL && !starChanged) newRoute.removeIndex(0)
                            }
                        }
                    }
                }
                currRoute.setToRoute(newRoute) // Set the acting route clearance to the new clearance (after corrections, if any)
            }}

            hiddenLegs.setToRoute(newClearance.hiddenLegs)
            if (vectorHdg != newClearance.vectorHdg || vectorTurnDir != CommandTarget.TURN_DEFAULT) {
                // Do not change the turn direction only if the heading has not changed, and current turn direction is default
                // i.e. Change turn direction if heading has changed or current turn direction is not default
                vectorTurnDir = newClearance.vectorTurnDir
            }
            vectorHdg = newClearance.vectorHdg
            clearedAlt = newClearance.clearedAlt
            expedite = newClearance.expedite

            val spds = getMinMaxOptimalIAS(entity)
            val flightType = entity[FlightType.mapper]
            // Only change the cleared speed if it is equal to the current optimal speed, and, if new optimal speed is
            // higher than previous optimal speed then aircraft must be departure; if new optimal speed is lower than
            // previous optimal speed then aircraft must be arrival
            if (newClearance.clearedIas == newClearance.optimalIas &&
                ((newClearance.clearedIas < spds.third && flightType?.type == FlightType.DEPARTURE) ||
                        (newClearance.clearedIas > spds.third && flightType?.type == FlightType.ARRIVAL)))
                newClearance.clearedIas = spds.third
            newClearance.minIas = spds.first
            newClearance.maxIas = spds.second
            newClearance.optimalIas = spds.third
            newClearance.clearedIas = MathUtils.clamp(newClearance.clearedIas, newClearance.minIas, newClearance.maxIas)
            if (clearedIas != newClearance.clearedIas && clearedApp != null) {
                // Add the DecelerateToAppSpd component to handle case when player clears a new speed after it
                // is already decelerating to final approach speed
                entity += DecelerateToAppSpd()
            }
            clearedIas = newClearance.clearedIas
            minIas = newClearance.minIas
            maxIas = newClearance.maxIas
            optimalIas = newClearance.optimalIas
            clearedApp = newClearance.clearedApp
            newClearance.clearedApp.let {
                // If the approach has been changed, remove all current approach components to allow aircraft to
                // re-establish on new approach (if new approach is null, then the approach is cancelled)
                if (appChanged) removeAllApproachComponents(entity)
                val app = GAME.gameServer?.airports?.get(entity[ArrivalAirport.mapper]?.arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(it)?.entity
                if (app == null) {
                    if (appChanged && (currFirstLeg == null || currFirstLeg.phase == Route.Leg.APP ||
                                currFirstLeg.phase == Route.Leg.APP_TRANS)) return@let clearCurrentRouteRestrictions(entity)
                    return@let
                }
                if (appChanged) {
                    if (app.has(Localizer.mapper)) entity += LocalizerArmed(app)
                    else app[ApproachInfo.mapper]?.rwyObj?.entity?.get(VisualApproach.mapper)?.let { visApp ->
                        entity += VisualArmed(visApp.visual, app)
                    }
                    if (app.has(GlideSlope.mapper)) entity += GlideSlopeArmed(app)
                    else if (app.has(StepDown.mapper)) entity += StepDownApproach(app)
                    app[Circling.mapper]?.let { circling ->
                        entity += CirclingApproach(app, MathUtils.random(circling.minBreakoutAlt, circling.maxBreakoutAlt))
                    }
                    // Visual approach can only be cleared by other approaches
                    entity += AppDecelerateTo190kts()
                    entity += DecelerateToAppSpd()
                    val alt = GAME.gameServer?.airports?.get(entity[ArrivalAirport.mapper]?.arptId)?.entity?.get(Altitude.mapper)?.altitudeFt ?: 0f
                    entity += ContactToTower(min((alt + MathUtils.random(1100, 1500)).toInt(), MIN_ALT - 50))
                }
            }
            clearedTrans = newClearance.clearedTrans
            if (cancelLastMaxSpd != newClearance.cancelLastMaxSpd) {
                cancelLastMaxSpd = newClearance.cancelLastMaxSpd
                entity += LatestClearanceChanged()
            }

            // Remove on go around route state if STAR changed
            if (starChanged) entity.remove<OnGoAroundRoute>()

            // Check player initiated go-around
            if (newClearance.initiateGoAround && isApproachCaptured(entity)) {
                entity += ShouldInitiateGoAround()
            }

            // Update route polygons
            entity[ArrivalRouteZone.mapper]?.also {
                val airport = GAME.gameServer?.airports?.get(entity[ArrivalAirport.mapper]?.arptId)?.entity ?: return@also
                if (starChanged) {
                    it.starZone.clear()
                    airport[STARChildren.mapper]?.starMap?.get(routePrimaryName)?.let { star ->
                        it.starZone.addAll(star.routeZones)
                    }
                }
                if (starChanged || transChanged || appChanged) {
                    it.appZone.clear()
                    airport[ApproachChildren.mapper]?.approachMap?.get(clearedApp)?.let { app ->
                        app.transitionRouteZones[clearedTrans]?.let { trans ->
                            it.appZone.addAll(trans)
                        }
                        app.transitions[clearedTrans]?.let { trans ->
                            it.appZone.addAll(getZonesForArrivalRoute(Route().apply {
                                if (trans.size > 0) add(trans[trans.size - 1])
                                if (app.routeLegs.size > 0) add(app.routeLegs[0])
                            }))
                        }
                        it.appZone.addAll(app.routeZones)
                        it.appZone.addAll(app.missedRouteZones)
                    }
                }
            }
        }
    }

    /** Inactivates any altitude and speed restrictions on the current route */
    fun deactivateAllRouteRestrictions() {
        for (i in 0 until route.size) {
            (route[i] as? Route.WaypointLeg)?.let {
                it.altRestrActive = false
                it.spdRestrActive = false
            }
        }
    }

    /**
     * Copies the current clearance state except assign the new input [altitude]
     */
    fun copyWithNewAltitude(altitude: Int): ClearanceState {
        return copy(clearedAlt = altitude, route = Route().apply { setToRouteCopy(route) },
            hiddenLegs = Route().apply { setToRouteCopy(hiddenLegs) })
    }

    /**
     * Clearance class that also contains a [timeLeft] value that keeps track of the time remaining before the [clearanceState]
     * should be executed
     */
    @JsonClass(generateAdapter = true)
    data class PendingClearanceState(var timeLeft: Float, val clearanceState: ClearanceState)

    /**
     * Updates this clearance state to match the input clearance for UI purposes
     * @param latestClearance the clearance state to update this state to; should be the aircraft's latest clearance state
     * @param uiClearance the current clearance state stored in the UI, without any player changes; set to null if
     * the updating of this clearance state should not take into account of any existing changes made by the player
     */
    fun updateUIClearanceState(latestClearance: ClearanceState, uiClearance: ClearanceState? = null) {
        uiClearance?.also {
            // Default clearance provided - compare this clearance against it and update the properties conditionally
            // For most of the variables, update if no change has been made compared to the original aircraft clearance state
            if (routePrimaryName == uiClearance.routePrimaryName) {
                routePrimaryName = latestClearance.routePrimaryName
                // If primary route has changed, don't bother doing sanity check on route
                if (checkRouteEqualityStrict(route, uiClearance.route)) route.setToRouteCopy(latestClearance.route)
                else {
                    var i = 0
                    while (i < route.size) { route[i].also { leg ->
                        (leg as? Route.WaypointLeg)?.let { wptLeg ->
                            // Sanity check - any waypoint legs in the current pane clearance state not in the new clearance
                            // must be removed, starting from the front, until a matching leg has been found
                            if (checkLegChanged(latestClearance.route, wptLeg)) {
                                route.removeIndex(i)
                                i--
                            } else i = route.size // Exit the loop once a matching waypoint has been found from the front
                        } ?: ((leg as? Route.HoldLeg) ?: (leg as? Route.VectorLeg))?.let {
                            // Additionally, for hold and after waypoint heading legs, the leg preceding them in the UI state
                            // must be a waypoint and present unless this leg is the first; otherwise, those legs must also be removed
                            val prevLeg = if (i >= 1) route[i - 1] else return@let
                            if (prevLeg !is Route.WaypointLeg || checkLegChanged(latestClearance.route, prevLeg)) {
                                route.removeIndex(i)
                                i--
                            }
                        }
                        i++
                        if (leg is Route.DiscontinuityLeg) i = route.size // If a discontinuity leg is found, we can keep all legs after
                    }}
                }
                if (checkRouteEqualityStrict(hiddenLegs, uiClearance.hiddenLegs)) hiddenLegs.setToRouteCopy(latestClearance.hiddenLegs)
            }
            if (vectorHdg == uiClearance.vectorHdg) {
                vectorHdg = latestClearance.vectorHdg
                // If user has not changed both the heading and the turn direction, set to new turn direction
                if (vectorTurnDir == uiClearance.vectorTurnDir) vectorTurnDir = latestClearance.vectorTurnDir
            }
            if (clearedAlt == uiClearance.clearedAlt) clearedAlt = latestClearance.clearedAlt
            if (expedite == uiClearance.expedite) expedite = latestClearance.expedite
            // Set to new IAS if the current IAS has not changed, or if it has changed but is equal to the current optimal IAS,
            // and is different from the new optimal IAS, and the new clearance is equal to the new optimal IAS
            if (clearedIas == uiClearance.clearedIas ||
                (clearedIas == optimalIas && optimalIas != latestClearance.optimalIas && latestClearance.clearedIas == latestClearance.optimalIas)) clearedIas = latestClearance.clearedIas
            if (clearedApp == uiClearance.clearedApp) clearedApp = latestClearance.clearedApp
            if (clearedTrans == uiClearance.clearedTrans) clearedTrans = latestClearance.clearedTrans
            if (cancelLastMaxSpd == uiClearance.cancelLastMaxSpd)
                cancelLastMaxSpd = latestClearance.cancelLastMaxSpd
        } ?: run {
            // No default clearance to compare against, copy the properties directly
            routePrimaryName = latestClearance.routePrimaryName
            route.setToRouteCopy(latestClearance.route)
            hiddenLegs.setToRouteCopy(latestClearance.hiddenLegs)
            vectorHdg = latestClearance.vectorHdg
            vectorTurnDir = latestClearance.vectorTurnDir
            clearedAlt = latestClearance.clearedAlt
            expedite = latestClearance.expedite
            clearedIas = latestClearance.clearedIas
            clearedApp = latestClearance.clearedApp
            clearedTrans = latestClearance.clearedTrans
            cancelLastMaxSpd = latestClearance.cancelLastMaxSpd
            initiateGoAround = false
        }
        minIas = latestClearance.minIas
        maxIas = latestClearance.maxIas
        optimalIas = latestClearance.optimalIas
    }
}
