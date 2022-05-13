package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.utilities.compareLegEquality
import ktx.collections.GdxArray

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
                     var vectorHdg: Short? = null, var clearedAlt: Int = 0, var clearedIas: Short = 0,
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
         * @param newClearance the new player sent clearance
         * */
        fun updateClearanceAct(newClearance: ClearanceState) {
            actingClearance.route.legs.let { currRoute -> newClearance.route.legs.let { newRoute ->
                if (currRoute.size >= 1 && newRoute.size >= 1) {
                    val currFirstLeg = currRoute.first()
                    val newFirstLeg = newRoute.first()
                    if (currFirstLeg is Route.WaypointLeg && newFirstLeg is Route.WaypointLeg && !compareLegEquality(currFirstLeg, newFirstLeg)) {
                        // 2 possible waypoint leg conflict scenarios
                        var currDirIndex = -1
                        // Check for whether the new route contains the current direct
                        for (i in 0 until newRoute.size) {
                            if (compareLegEquality(currFirstLeg, newRoute[i])) {
                                currDirIndex = i
                                break
                            }
                        }
                        if (currDirIndex > 0) {
                            // 1. Aircraft has flown by waypoint, new clearance is the same route except it still contains
                            // that waypoint; remove all legs from the new clearance till the current direct
                            newRoute.removeRange(0, currDirIndex)
                        } else {
                            // 2. Aircraft has flown by waypoint, but the new clearance does not contain the current direct
                            // (possibly due to player assigning approach transition); clear the current route, and add the
                            // new route from the 2nd leg onwards
                            currRoute.clear()
                            currRoute.addAll(GdxArray(newRoute).apply { removeIndex(0) })
                        }
                    }
                }
            }}

            // TODO other potential conflicts
        }

        // TODO Updates the aircraft command state to follow (this) acting clearance state, including sanity checks
        fun updateClearanceActToCommandState(commandTarget: CommandTarget) {

        }
    }

    /**
     * Clearance class that keeps track of which clearances have been altered by the player
     *
     * [routePrimaryNameChanged] - whether [routePrimaryName] has changed
     *
     * [routeChanged] - whether [route] has changed
     *
     * [hiddenLegsChanged] - whether [hiddenLegs] has changed
     *
     * [vectorHdgChanged] - whether [vectorHdg] has changed
     *
     * [clearedAltChanged] - whether [clearedAlt] has changed
     *
     * [clearedIasChanged] - whether [clearedIas] has changed
     *  */
    class ClearanceStateChanged(var routePrimaryNameChanged: Boolean = false, var routeChanged: Boolean = false, var hiddenLegsChanged: Boolean = false,
                                var vectorHdgChanged: Boolean = false, var clearedAltChanged: Boolean = false, var clearedIasChanged: Boolean = false)

    /**
     * Clearance class that also contains a [timeLeft] value that keeps track of the time remaining before the [clearanceState]
     * should be executed
     * */
    class PendingClearanceState(var timeLeft: Float, val clearanceState: ClearanceState)

    /**
     * Updates this clearance state (of the UI) to match the input [latestClearance]
     * @param latestClearance the clearance state to update the pane's state to; should be the aircraft's latest clearance state
     * @param clearanceStateChanged if not null, takes into account existing changes made by the player
     * */
    fun updateUIClearanceState(latestClearance: ClearanceState, clearanceStateChanged: ClearanceStateChanged? = null) {
        clearanceStateChanged?.apply {
            if (!routePrimaryNameChanged) routePrimaryName = latestClearance.routePrimaryName
            if (!routeChanged) route.setToRoute(latestClearance.route)
            else {
                // Sanity check - all legs in the current pane clearance state must be present in the new clearance state;
                // otherwise, those legs must be removed
                var i = 0
                while (0 <= i && i < route.legs.size) route.legs[i].let { leg ->
                    if (!latestClearance.route.legs.contains(leg, false)) {
                        route.legs.removeIndex(i)
                        i--
                    }
                    i++
                }
            }
            if (!hiddenLegsChanged) hiddenLegs.setToRoute(latestClearance.hiddenLegs)
            if (!vectorHdgChanged) vectorHdg = latestClearance.vectorHdg
            if (!clearedAltChanged) clearedAlt = latestClearance.clearedAlt
            // Set to new IAS if the current IAS has not changed, or if it has changed but is equal to the current optimal IAS,
            // and is different from the new optimal IAS, and the new clearance is equal to the new optimal IAS
            if (!clearedIasChanged ||
                (clearedIas == optimalIas && optimalIas != latestClearance.optimalIas && latestClearance.clearedIas == latestClearance.optimalIas)) clearedIas = latestClearance.clearedIas
        } ?: run {
            routePrimaryName = latestClearance.routePrimaryName
            route.legs.clear()
            route.extendRoute(latestClearance.route)
            hiddenLegs.legs.clear()
            hiddenLegs.extendRoute(latestClearance.hiddenLegs)
            vectorHdg = latestClearance.vectorHdg
            clearedAlt = latestClearance.clearedAlt
            clearedIas = latestClearance.clearedIas
        }
        minIas = latestClearance.minIas
        maxIas = latestClearance.maxIas
        optimalIas = latestClearance.optimalIas
    }
}
