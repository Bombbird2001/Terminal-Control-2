package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import ktx.ashley.Mapper

/** Component for tagging sector control information */
data class Controllable(var sectorId: Byte = 0): Component {
    companion object: Mapper<Controllable>()
}

/** Component for tagging aircraft flight type
 *
 * [type] = 0 -> Arrival
 *
 * [type] = 1 -> Departure
 *
 * [type] = 2 -> En-route
 * */
data class FlightType(var type: Byte = 0): Component {
    companion object: Mapper<FlightType>() {
        const val ARRIVAL: Byte = 0
        const val DEPARTURE: Byte = 1
        const val EN_ROUTE: Byte = 2
    }
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from tower to approach/departure */
data class ContactFromTower(var altitudeFt: Int = 0): Component {
    companion object: Mapper<ContactFromTower>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from approach/departure to tower */
data class ContactToTower(var altitudeFt: Int = 0): Component {
    companion object: Mapper<ContactToTower>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from centre to approach/departure */
data class ContactFromCentre(var altitudeFt: Int = 0): Component {
    companion object: Mapper<ContactFromCentre>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from approach/departure to centre */
data class ContactToCentre(var altitudeFt: Int = 0): Component {
    companion object: Mapper<ContactToCentre>()
}
