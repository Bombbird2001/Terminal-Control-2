package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
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

/** Component for tagging an aircraft on the ground waiting for takeoff (it won't be rendered or updated) */
class WaitingTakeoff: Component {
    companion object: Mapper<WaitingTakeoff>()
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

/** Component for tagging aircraft that should accelerate to their trip speed (> 250 knots) once above 10000 feet */
class AccelerateToAbove250kts: Component {
    companion object: Mapper<AccelerateToAbove250kts>()
}

/** Component for tagging aircraft that should decelerate to 240 knots when nearing 10000 feet */
class DecelerateTo240kts: Component {
    companion object: Mapper<AccelerateToAbove250kts>()
}

/** Component for tagging aircraft that should decelerate to 190 knots when less than 16 nm from the runway threshold */
class AppDecelerateTo190kts: Component {
    companion object: Mapper<AppDecelerateTo190kts>()
}

/**
 * Component for tagging aircraft that should decelerate to their minimum approach speed when less than 5 nm from the
 * runway threshold
 * */
class DecelerateToAppSpd: Component {
    companion object: Mapper<DecelerateToAppSpd>()
}

/**
 * Component for tagging aircraft that has captured the extended runway centreline and glide path in a visual approach,
 * and will alter aircraft AI behaviour to follow the extended centreline track and glide path
 * */
class VisualCaptured(val visApp: Entity = Entity(), val parentApp: Entity = Entity()): Component {
    companion object: Mapper<VisualCaptured>()
}

/**
 * Component for tagging aircraft that have been cleared for an approach with a localizer component
 *
 * The aircraft will monitor its position relative to the approach position origin and capture it when within range
 * */
class LocalizerArmed(val locApp: Entity = Entity()): Component {
    companion object: Mapper<LocalizerArmed>()
}

/**
 * Component for tagging aircraft that has captured the localizer, and will alter aircraft AI behaviour to follow the
 * localizer track
 * */
class LocalizerCaptured(val locApp: Entity = Entity()): Component {
    companion object: Mapper<LocalizerCaptured>()
}

/**
 * Component for tagging aircraft that have been cleared for an approach with a glide slope component
 *
 * The aircraft will monitor its altitude and capture it when it reaches the appropriate altitude
 */
class GlideSlopeArmed(val gsApp: Entity = Entity()): Component {
    companion object: Mapper<GlideSlopeArmed>()
}

/**
 * Component for tagging aircraft that has captured the glide slope, and will alter aircraft AI, physics behaviour to follow
 * the glide slope strictly
 * */
class GlideSlopeCaptured(val gsApp: Entity = Entity()): Component {
    companion object: Mapper<GlideSlopeCaptured>()
}

/**
 * Component for tagging aircraft that have been cleared for a non-precision step down approach, and will alter aircraft
 * AI behaviour to follow the step-down altitudes if the localizer is captured
 * */
class StepDownApproach(val stepDownApp: Entity = Entity()): Component {
    companion object: Mapper<StepDownApproach>()
}

/**
 * Component for tagging aircraft cleared for a circling approach; the aircraft must have captured the localizer, or
 * captured the glideslope, or be cleared for a step-down approach in order for this component to take effect
 *
 * This component will persist until the aircraft is no longer on the approach or is on the final visual segment of the
 * approach
 */
data class CirclingApproach(val circlingApp: Entity = Entity(), var breakoutAlt: Int = 0, var phase: Byte = 0,
                            var phase1Timer: Float = 70f, var phase3Timer: Float = 50f): Component {
    companion object: Mapper<CirclingApproach>()
}

/**
 * Component for tagging the pending [ClearanceState]s an aircraft has been cleared, as well as the corresponding reaction
 * time, after the preceding clearance, for each clearance
 * */
class PendingClearances(val clearanceQueue: Queue<ClearanceState.PendingClearanceState> = Queue(5)): Component {
    companion object: Mapper<PendingClearances>()
}

/**
 * Component for tagging the latest [ClearanceState] an aircraft has been cleared
 * */
class ClearanceAct(val actingClearance: ClearanceState.ActingClearance = ClearanceState.ActingClearance()): Component {
    companion object: Mapper<ClearanceAct>()
}

/**
 * Component for tagging when an aircraft's latest [PendingClearances] or [CommandTarget] changes
 *
 * The server will send a TCP update to all clients informing them of the updated clearance state
 * */
class LatestClearanceChanged: Component {
    companion object: Mapper<LatestClearanceChanged>()
}

/**
 * Component for tagging when an aircraft's [ClearanceAct] changes
 *
 * The system will update command target to follow the new clearance
 * */
class ClearanceActChanged: Component {
    companion object: Mapper<ClearanceActChanged>()
}

/**
 * Component for tagging an initial arrival spawn
 *
 * Required actions will be performed in the AI system and then removed for the entity
 * */
class InitialArrivalSpawn: Component {
    companion object: Mapper<InitialArrivalSpawn>()
}
