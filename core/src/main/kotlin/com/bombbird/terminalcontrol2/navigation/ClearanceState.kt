package com.bombbird.terminalcontrol2.navigation

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.utilities.checkLegChanged
import com.bombbird.terminalcontrol2.utilities.compareLegEquality
import com.bombbird.terminalcontrol2.utilities.checkRouteEqualityStrict
import com.bombbird.terminalcontrol2.utilities.getMinMaxOptimalIAS

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
 * [clearedAlt] is the player cleared altitude (before taking into account SID/STAR altitude restrictions
 *
 * [clearedIas] is from the CommandTarget component; the targetIas value can be used directly as it is not possible to
 * "Accelerate/Decelerate via SID/STAR"
 *
 * Also contains utility functions for updating the actual aircraft command state
 * */
class ClearanceState(var routePrimaryName: String = "", val route: Route = Route(), val hiddenLegs: Route = Route(),
                     var vectorHdg: Short? = null, var vectorTurnDir: Byte? = null,
                     var clearedAlt: Int = 0, var clearedIas: Short = 0,
                     var minIas: Short = 0, var maxIas: Short = 0, var optimalIas: Short = 0) {

    /**
     * Wrapper class used solely to denote and store the currently active clearance the aircraft is following
     *
     * Provides functions that interact with new player clearances as well as the aircraft command target state
     * */
    class ActingClearance(val actingClearance: ClearanceState = ClearanceState()) {
        /**
         * Updates this acting clearance to the new clearance, performing corrections in case of any conflicts caused due to
         * pilot response time
         * @param newClearance the new player sent clearance (that is being removed from PendingClearances)
         * @param entity the aircraft entity that the clearance will be applied to
         * */
        fun updateClearanceAct(newClearance: ClearanceState, entity: Entity) {
            actingClearance.routePrimaryName = newClearance.routePrimaryName

            actingClearance.route.let { currRoute -> newClearance.route.let { newRoute ->
                val currRouteLegs = currRoute.legs
                val newRouteLegs = newRoute.legs
                if (currRouteLegs.size >= 1 && newRouteLegs.size >= 1) {
                    val currFirstLeg = currRouteLegs.first()
                    val newFirstLeg = newRouteLegs.first()
                    if (!compareLegEquality(currFirstLeg, newFirstLeg)) {
                        // 3 possible leg conflict scenarios
                        if (currRouteLegs.size >= 2 && newRouteLegs[1].let { it is Route.HoldLeg && currFirstLeg is Route.WaypointLeg && it.wptId == currFirstLeg.wptId }) {
                            // 1. Aircraft has flown by waypoint, but the new clearance wants it to hold at that waypoint;
                            // clear the current route, and add the new route from the 2nd leg (the hold leg) onwards
                            newRouteLegs.removeIndex(0)
                        } else if (currFirstLeg is Route.WaypointLeg && newFirstLeg is Route.WaypointLeg) {
                            var currDirIndex = -1
                            // Check for whether the new route contains the current direct
                            for (i in 0 until newRouteLegs.size) {
                                if (compareLegEquality(currFirstLeg, newRouteLegs[i])) {
                                    currDirIndex = i
                                    break
                                }
                            }
                            if (currDirIndex > 0) {
                                // 2. Aircraft has flown by waypoint, new clearance is the same route except it still contains
                                // that waypoint; remove all legs from the new clearance till the current direct
                                newRouteLegs.removeRange(0, currDirIndex)
                            } else {
                                // 3. Aircraft has flown by waypoint, but the new clearance does not contain the current direct
                                // (possibly due to player assigning approach transition); remove the first leg from new clearance
                                newRouteLegs.removeIndex(0)
                            }
                        }
                    }
                }
                currRoute.setToRoute(newRoute) // Set the acting route clearance to the new clearance (after corrections, if any)
            }}

            actingClearance.hiddenLegs.setToRoute(newClearance.hiddenLegs)
            if (actingClearance.vectorHdg != newClearance.vectorHdg || actingClearance.vectorTurnDir != CommandTarget.TURN_DEFAULT) {
                // Do not change the turn direction only if the heading has not changed, and current turn direction is default
                // i.e. Change turn direction if heading has changed or current turn direction is not default
                actingClearance.vectorTurnDir = newClearance.vectorTurnDir
            }
            actingClearance.vectorHdg = newClearance.vectorHdg
            actingClearance.clearedAlt = newClearance.clearedAlt

            val spds = getMinMaxOptimalIAS(entity)
            if (newClearance.clearedIas == newClearance.optimalIas && newClearance.clearedIas != spds.third) newClearance.clearedIas = spds.third
            newClearance.minIas = spds.first
            newClearance.maxIas = spds.second
            newClearance.optimalIas = spds.third
            newClearance.clearedIas = MathUtils.clamp(newClearance.clearedIas, newClearance.minIas, newClearance.maxIas)
            actingClearance.clearedIas = newClearance.clearedIas
            actingClearance.minIas = newClearance.minIas
            actingClearance.maxIas = newClearance.maxIas
            actingClearance.optimalIas = newClearance.optimalIas
        }
    }

    /**
     * Clearance class that also contains a [timeLeft] value that keeps track of the time remaining before the [clearanceState]
     * should be executed
     * */
    class PendingClearanceState(var timeLeft: Float, val clearanceState: ClearanceState)

    /**
     * Updates this clearance state to match the input clearance for UI purposes
     * @param latestClearance the clearance state to update this state to; should be the aircraft's latest clearance state
     * @param defaultClearance the current clearance state stored in the UI, without any player changes; set to null if
     * the updating of this clearance state should not take into account of any existing changes made by the player
     * */
    fun updateUIClearanceState(latestClearance: ClearanceState, defaultClearance: ClearanceState? = null) {
        defaultClearance?.also {
            // Default clearance provided - compare this clearance against it and update the properties conditionally
            // For most of the variables, update if no change has been made compared to the original aircraft clearance state
            if (routePrimaryName == defaultClearance.routePrimaryName) routePrimaryName = latestClearance.routePrimaryName
            if (checkRouteEqualityStrict(route, defaultClearance.route)) route.setToRouteCopy(latestClearance.route)
            else {
                // Sanity check - all legs in the current pane clearance state must be present in the new clearance state;
                // otherwise, those legs must be removed
                var i = 0
                while (0 <= i && i < route.legs.size) route.legs[i].let { leg ->
                    if (checkLegChanged(latestClearance.route, leg)) {
                        route.legs.removeIndex(i)
                        i--
                    }
                    i++
                }
            }
            if (checkRouteEqualityStrict(hiddenLegs, defaultClearance.hiddenLegs)) hiddenLegs.setToRouteCopy(latestClearance.hiddenLegs)
            if (vectorHdg == defaultClearance.vectorHdg) {
                vectorHdg = latestClearance.vectorHdg
                // If user has not changed both the heading and the turn direction, set to new turn direction
                if (vectorTurnDir == defaultClearance.vectorTurnDir) vectorTurnDir = latestClearance.vectorTurnDir
            }
            if (clearedAlt == defaultClearance.clearedAlt) clearedAlt = latestClearance.clearedAlt
            // Set to new IAS if the current IAS has not changed, or if it has changed but is equal to the current optimal IAS,
            // and is different from the new optimal IAS, and the new clearance is equal to the new optimal IAS
            if (clearedIas == defaultClearance.clearedIas ||
                (clearedIas == optimalIas && optimalIas != latestClearance.optimalIas && latestClearance.clearedIas == latestClearance.optimalIas)) clearedIas = latestClearance.clearedIas
        } ?: run {
            // No default clearance to compare against, copy the properties directly
            routePrimaryName = latestClearance.routePrimaryName
            route.setToRouteCopy(latestClearance.route)
            hiddenLegs.setToRouteCopy(latestClearance.hiddenLegs)
            vectorHdg = latestClearance.vectorHdg
            vectorTurnDir = latestClearance.vectorTurnDir
            clearedAlt = latestClearance.clearedAlt
            clearedIas = latestClearance.clearedIas
        }
        minIas = latestClearance.minIas
        maxIas = latestClearance.maxIas
        optimalIas = latestClearance.optimalIas
    }
}
