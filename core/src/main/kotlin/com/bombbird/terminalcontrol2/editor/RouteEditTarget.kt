package com.bombbird.terminalcontrol2.editor

import com.bombbird.terminalcontrol2.navigation.Route

sealed class RouteEditTarget {
    data object ApproachMainRoute : RouteEditTarget()
    data object ApproachMissed : RouteEditTarget()
    data class ApproachTransition(val name: String) : RouteEditTarget()

    fun parsePhase(): Byte = when (this) {
        is ApproachMainRoute -> Route.Leg.APP
        is ApproachMissed -> Route.Leg.MISSED_APP
        is ApproachTransition -> Route.Leg.APP_TRANS
    }
}
