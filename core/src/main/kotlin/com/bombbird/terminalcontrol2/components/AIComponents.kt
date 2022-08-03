package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/**
 * Component for tagging takeoff rolling mode
 *
 * Aircraft will accelerate at a constant rate [targetAccMps2], rotate at vR
 * */
data class TakeoffRoll(var targetAccMps2: Float = 2f, var rwy: Entity = Entity()): Component {
    companion object: Mapper<TakeoffRoll>()
}

/**
 * Component for tagging initial takeoff climb mode
 *
 * Aircraft will maintain vR + (15 to 20) and climb at max allowed rate till [accelAltFt], where it will accelerate
 * */
@JsonClass(generateAdapter = true)
data class TakeoffClimb(var accelAltFt: Float = 1500f): Component {
    companion object: Mapper<TakeoffClimb>()
}

/**
 * Component for tagging landing mode
 *
 * Aircraft will decelerate at a constant rate till ~45 knots, then decelerate at a reduced rate, then de-spawn at 25-30 knots
 * */
data class LandingRoll(var rwy: Entity = Entity()): Component {
    companion object: Mapper<LandingRoll>()
}

/**
 * Component for tagging the basic AI control modes of the aircraft
 *
 * [targetHdgDeg] is the heading the plane will turn to and maintain
 *
 * [turnDir] is the direction to turn ([TURN_DEFAULT] will turn in the quickest direction)
 *
 * [targetAltFt] is the altitude the plane will climb/descend to and maintain
 *
 * [targetIasKt] is the indicated airspeed the plane will speed up/slow down to and maintain
 *
 * These basic parameters can be automatically altered by more advanced modes, such as Direct (to waypoint), Hold (at waypoint),
 * Climb via SID/Descend via STAR (to altitude), and SID/STAR speed restrictions in order to achieve the required behaviour
 * */
@JsonClass(generateAdapter = true)
data class CommandTarget(var targetHdgDeg: Float = 360f, var turnDir: Byte = TURN_DEFAULT, var targetAltFt: Int = 0, var targetIasKt: Short = 0): Component {
    companion object: Mapper<CommandTarget>() {
        const val TURN_DEFAULT: Byte = 0
        const val TURN_LEFT: Byte = -1
        const val TURN_RIGHT: Byte = 1
    }
}

/**
 * Component for tagging the vector leg an aircraft is flying
 *
 * [heading] is the heading the aircraft will fly
 *
 * [turnDir] is the turn direction for the vector leg
 *
 * This component does not persist; it is removed after setting the [CommandTarget] parameters
 * */
@JsonClass(generateAdapter = true)
data class CommandVector(var heading: Short = 360, var turnDir: Byte = CommandTarget.TURN_DEFAULT): Component {
    companion object: Mapper<CommandVector>()
}

/**
 * Component for tagging the initial climb leg an aircraft is flying
 *
 * [heading] is the heading the aircraft will fly
 *
 * [minAltFt] is the altitude above which the aircraft will move on to the next leg
 *
 * This component will persist until the aircraft no longer flies in initClimb mode
 * */
@JsonClass(generateAdapter = true)
data class CommandInitClimb(var heading: Short = 360, var minAltFt: Int = 0): Component {
    companion object: Mapper<CommandInitClimb>()
}

/**
 * Component for tagging the waypoint leg an aircraft is flying
 *
 * [wptId] is the ID of the waypoint
 *
 * [maxAltFt], [minAltFt] and [maxSpdKt] are the maximum altitude, minimum altitude and maximum IAS restrictions
 * respectively that the leg has and the aircraft is subject to
 *
 * [flyOver] denotes whether the aircraft needs to overfly the waypoint before moving to the next leg
 *
 * [turnDir] specifies whether a forced turn direction is required when turning onto this leg
 *
 * This component will persist until the aircraft no longer flies in waypoint mode
 * */
@JsonClass(generateAdapter = true)
data class CommandDirect(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null, val maxSpdKt: Short?,
                         val flyOver: Boolean = false, val turnDir: Byte = CommandTarget.TURN_DEFAULT): Component {
    companion object: Mapper<CommandDirect>()
}

/**
 * Component for tagging a holding leg an aircraft is flying
 *
 * This component will persist until the aircraft is no longer in holding mode
 * */
@JsonClass(generateAdapter = true)
data class CommandHold(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null, var maxSpdKt: Short? = null,
                       var inboundHdg: Short = 360, var legDist: Byte = 5, var legDir: Byte = CommandTarget.TURN_RIGHT,
                       var currentEntryProc: Byte = 0, var entryDone: Boolean = false, var oppositeTravelled: Boolean = false, var flyOutbound: Boolean = true): Component {
    companion object: Mapper<CommandHold>()
}

/** Command for tagging an aircraft that is expediting its climb or descent */
@JsonClass(generateAdapter = true)
class CommandExpedite: Component {
    companion object: Mapper<CommandExpedite>()
}

/** Component for tagging an aircraft that is flying according to continuous descent approach (CDA) operations */
@JsonClass(generateAdapter = true)
class CommandCDA: Component {
    companion object: Mapper<CommandCDA>()
}

/**
 * Component for storing an aircraft's most recent [minAltFt], [maxAltFt] and [maxSpdKt], since the route class does not store
 * previous legs and cannot provide information about past restrictions
 * */
@JsonClass(generateAdapter = true)
data class LastRestrictions(var minAltFt: Int? = null, var maxAltFt: Int? = null, var maxSpdKt: Short? = null): Component {
    companion object: Mapper<LastRestrictions>()
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
