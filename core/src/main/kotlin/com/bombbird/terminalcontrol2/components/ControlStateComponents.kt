package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import ktx.ashley.Mapper

/** Component for tagging sector control information */
data class Controllable(var sectorId: Byte = 0): Component {
    companion object: Mapper<Controllable>()
}

/**
 * Component for tagging aircraft flight type
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

/**
 * Component for tagging the pending [ClearanceState]s an aircraft has been cleared, as well as the corresponding reaction
 * time for each clearance
 * */
class PendingClearances(val clearanceArray: Queue<Pair<Float, ClearanceState>> = Queue(5)): Component {
    companion object: Mapper<PendingClearances>()
}

/**
 * Component for tagging the latest [ClearanceState] an aircraft has been cleared; for use on client aircraft only
 * since clients do not need to remember the clearances sent apart from the latest one
 * */
class ClearanceAct(val clearance: ClearanceState = ClearanceState()): Component {
    companion object: Mapper<ClearanceAct>()
}

/**
 * Component for tagging when an aircraft's latest [PendingClearances] or [CommandTarget] changes
 *
 * The server will send a TCP update to all clients informing them of the updated clearance state
 * */
class ClearanceChanged: Component {
    companion object: Mapper<ClearanceChanged>()
}
