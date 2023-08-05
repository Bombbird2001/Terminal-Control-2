package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper
import java.util.UUID

/** Component for tagging sector control information */
data class Controllable(var sectorId: Byte = 0, var controllerUUID: UUID? = null): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CONTROLLABLE

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
 */
@JsonClass(generateAdapter = true)
data class FlightType(var type: Byte = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.FLIGHT_TYPE

    companion object: Mapper<FlightType>() {
        const val ARRIVAL: Byte = 0
        const val DEPARTURE: Byte = 1
        const val EN_ROUTE: Byte = 2
    }
}

/** Component for tagging an aircraft on the ground waiting for takeoff (it won't be rendered or updated) */
@JsonClass(generateAdapter = true)
class WaitingTakeoff: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.WAITING_TAKEOFF

    companion object: Mapper<WaitingTakeoff>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from tower to approach/departure */
@JsonClass(generateAdapter = true)
data class ContactFromTower(var altitudeFt: Int = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CONTACT_FROM_TOWER

    companion object: Mapper<ContactFromTower>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from approach/departure to tower */
@JsonClass(generateAdapter = true)
data class ContactToTower(var altitudeFt: Int = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CONTACT_TO_TOWER

    companion object: Mapper<ContactToTower>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from centre to approach/departure */
@JsonClass(generateAdapter = true)
data class ContactFromCentre(var altitudeFt: Int = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CONTACT_FROM_CENTRE

    companion object: Mapper<ContactFromCentre>()
}

/** Component for tagging the [altitudeFt] when an aircraft should switch from approach/departure to centre */
@JsonClass(generateAdapter = true)
data class ContactToCentre(var altitudeFt: Int = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CONTACT_TO_CENTRE

    companion object: Mapper<ContactToCentre>()
}

/** Component for tagging aircraft that should accelerate to their trip speed (> 250 knots) once above 10000 feet */
@JsonClass(generateAdapter = true)
class AccelerateToAbove250kts: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ACCELERATE_TO_ABOVE_250KTS

    companion object: Mapper<AccelerateToAbove250kts>()
}

/** Component for tagging aircraft that should decelerate to 240 knots when nearing 10000 feet */
@JsonClass(generateAdapter = true)
class DecelerateTo240kts: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DECELERATE_TO_240KTS

    companion object: Mapper<AccelerateToAbove250kts>()
}

/** Component for tagging aircraft that should decelerate to 190 knots when less than 16 nm from the runway threshold */
@JsonClass(generateAdapter = true)
class AppDecelerateTo190kts: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.APP_DECELERATE_TO_190KTS

    companion object: Mapper<AppDecelerateTo190kts>()
}

/**
 * Component for tagging aircraft that should decelerate to their minimum approach speed when less than 5 nm from the
 * runway threshold
 */
@JsonClass(generateAdapter = true)
class DecelerateToAppSpd: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DECELERATE_TO_APP_SPD

    companion object: Mapper<DecelerateToAppSpd>()
}

/**
 * Component for tagging the pending [ClearanceState]s an aircraft has been cleared, as well as the corresponding reaction
 * time, after the preceding clearance, for each clearance
 */
class PendingClearances(val clearanceQueue: Queue<ClearanceState.PendingClearanceState> = Queue(5)): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.PENDING_CLEARANCES

    companion object: Mapper<PendingClearances>()
}

/**
 * Component for tagging the latest [ClearanceState] an aircraft has been cleared
 */
class ClearanceAct(val actingClearance: ClearanceState.ActingClearance = ClearanceState().ActingClearance()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CLEARANCE_ACT

    companion object: Mapper<ClearanceAct>()
}

/**
 * Component for tagging when an aircraft's latest [PendingClearances] or [CommandTarget] changes
 *
 * The server will send a TCP update to all clients informing them of the updated clearance state
 */
@JsonClass(generateAdapter = true)
class LatestClearanceChanged: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LATEST_CLEARANCE_CHANGED

    companion object: Mapper<LatestClearanceChanged>()
}

/**
 * Component for tagging when an aircraft's [ClearanceAct] changes
 *
 * The system will update command target to follow the new clearance
 */
@JsonClass(generateAdapter = true)
class ClearanceActChanged: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CLEARANCE_ACT_CHANGED

    companion object: Mapper<ClearanceActChanged>()
}

/**
 * Component for tagging an initial arrival spawn
 *
 * Required actions will be performed in the AI system and then removed for the entity
 */
@JsonClass(generateAdapter = true)
class InitialArrivalSpawn: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.INITIAL_ARRIVAL_SPAWN

    companion object: Mapper<InitialArrivalSpawn>()
}

/**
 * Component for tagging aircraft that has just contacted the player, which will enable the acknowledge button as well
 * as the datatag flashing to notify the player; this will be used only on client
 */
class ContactNotification: Component {
    companion object: Mapper<ContactNotification>()
}

/**
 * Component for tagging aircraft that can be handed over to the next sector, including tower and ACC; this component will
 * enable the handover button
 */
data class CanBeHandedOver(val nextSector: Byte = 0): Component {
    companion object: Mapper<CanBeHandedOver>()
}

/**
 * Component for tagging aircraft that did a go around recently (i.e. < 60 seconds); a timer comes with the component
 * to keep track of when to remove this component from the aircraft
 */
@JsonClass(generateAdapter = true)
data class RecentGoAround(var timeLeft: Float = 60f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RECENT_GO_AROUND

    companion object: Mapper<RecentGoAround>()
}

/**
 * Component for tagging aircraft that has just been handed over to the ACC and is pending clearance to their cruise
 * altitude
 */
@JsonClass(generateAdapter = true)
data class PendingCruiseAltitude(var timeLeft: Float = 10f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.PENDING_CRUISE_ALTITUDE

    companion object: Mapper<PendingCruiseAltitude>()
}

/**
 * Component for tagging aircraft that recently departed and are allowed to be less than the minimum radar separation
 * from another simultaneous departure for 90s after departure, provided they are on a minimum 15 degrees divergent
 * heading from each other within 120 seconds of departure
 */
@JsonClass(generateAdapter = true)
data class DivergentDepartureAllowed(var timeLeft: Float = 120f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DIVERGENT_DEPARTURE_ALLOWED

    companion object: Mapper<DivergentDepartureAllowed>()
}