package com.bombbird.terminalcontrol2.navigation

import com.badlogic.ashley.core.Entity

/** Clearance class that contains data for player transmitted aircraft clearances
 *
 * [routePrimaryName], [route] and [hiddenLegs] are from the CommandRoute component
 *
 * [vectorHdg] is from the CommandVector component, and should be null unless the aircraft is under direct vectors
 *
 * [clearedAlt] is from the CommandAltitude component
 *
 * [clearedIas] is from the CommandTarget component; the targetIas value can be used directly as it is not possible to
 * "Accelerate/Decelerate via SID/STAR"
 *
 * Also contains utility functions for updating the actual aircraft command state
 * */
class ClearanceState(val routePrimaryName: String = "", val route: Route = Route(), val hiddenLegs: Route = Route(),
                     val vectorHdg: Short? = null, val clearedAlt: Int = 0, val clearedIas: Short = 0) {

    // TODO Updates the aircraft command state to this clearance state, including sanity checks
    fun updateCommandState(entity: Entity) {

    }
}