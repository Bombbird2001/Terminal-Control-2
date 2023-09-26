package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/**
 * Component for tagging takeoff rolling mode
 *
 * Aircraft will accelerate at a constant rate [targetAccMps2], rotate at vR
 */
data class TakeoffRoll(var targetAccMps2: Float = 2f, var rwy: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.TAKEOFF_ROLL

    companion object {
        val mapper = object : Mapper<TakeoffRoll>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging initial takeoff climb mode
 *
 * Aircraft will maintain vR + (15 to 20) and climb at max allowed rate till [accelAltFt], where it will accelerate
 */
@JsonClass(generateAdapter = true)
data class TakeoffClimb(var accelAltFt: Float = 1500f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.TAKEOFF_CLIMB

    companion object {
        val mapper = object: Mapper<TakeoffClimb>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging landing mode
 *
 * Aircraft will decelerate at a constant rate till ~45 knots, then decelerate at a reduced rate, then de-spawn at 25-30 knots
 */
data class LandingRoll(var rwy: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LANDING_ROLL

    companion object {
        val mapper = object: Mapper<LandingRoll>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
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
 */
@JsonClass(generateAdapter = true)
data class CommandTarget(var targetHdgDeg: Float = 360f, var turnDir: Byte = TURN_DEFAULT, var targetAltFt: Int = 0,
                         var targetIasKt: Short = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_TARGET

    companion object {
        val mapper = object: Mapper<CommandTarget>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)

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
 */
@JsonClass(generateAdapter = true)
data class CommandVector(var heading: Short = 360, var turnDir: Byte = CommandTarget.TURN_DEFAULT): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_VECTOR

    companion object {
        val mapper = object: Mapper<CommandVector>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging the initial climb leg an aircraft is flying
 *
 * [heading] is the heading the aircraft will fly
 *
 * [minAltFt] is the altitude above which the aircraft will move on to the next leg
 *
 * This component will persist until the aircraft no longer flies in initClimb mode
 */
@JsonClass(generateAdapter = true)
data class CommandInitClimb(var heading: Short = 360, var minAltFt: Int = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_INIT_CLIMB

    companion object {
        val mapper = object: Mapper<CommandInitClimb>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
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
 */
@JsonClass(generateAdapter = true)
data class CommandDirect(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null, val maxSpdKt: Short?,
                         val flyOver: Boolean = false, val turnDir: Byte = CommandTarget.TURN_DEFAULT): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_DIRECT

    companion object {
        val mapper = object: Mapper<CommandDirect>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging a holding leg an aircraft is flying
 *
 * This component will persist until the aircraft is no longer in holding mode
 */
@JsonClass(generateAdapter = true)
data class CommandHold(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null, var maxSpdKt: Short? = null,
                       var inboundHdg: Short = 360, var legDist: Byte = 5, var legDir: Byte = CommandTarget.TURN_RIGHT,
                       var currentEntryProc: Byte = 0, var entryDone: Boolean = false, var oppositeTravelled: Boolean = false,
                       var flyOutbound: Boolean = true): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_HOLD

    companion object {
        val mapper = object: Mapper<CommandHold>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Command for tagging an aircraft that is expediting its climb or descent */
@JsonClass(generateAdapter = true)
class CommandExpedite: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_EXPEDITE

    companion object {
        val mapper = object: Mapper<CommandExpedite>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging an aircraft that is flying according to continuous descent approach (CDA) operations */
@JsonClass(generateAdapter = true)
class CommandCDA: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.COMMAND_CDA

    companion object {
        val mapper = object: Mapper<CommandCDA>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for storing an aircraft's most recent [minAltFt], [maxAltFt] and [maxSpdKt], since the route class does not store
 * previous legs and cannot provide information about past restrictions
 */
@JsonClass(generateAdapter = true)
data class LastRestrictions(var minAltFt: Int? = null, var maxAltFt: Int? = null, var maxSpdKt: Short? = null): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LAST_RESTRICTIONS

    companion object {
        val mapper = object: Mapper<LastRestrictions>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that is pending to start a visual approach
 *
 * The aircraft will check its current navigation status as well as position relative to the approach position origin
 * and capture it when within range
 */
data class VisualArmed(var visApp: Entity = Entity(), var parentApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.VISUAL_ARMED

    companion object {
        val mapper = object: Mapper<VisualArmed>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that has captured the extended runway centreline and glide path in a visual approach,
 * and will alter aircraft AI behaviour to follow the extended centreline track and glide path
 */
data class VisualCaptured(var visApp: Entity = Entity(), var parentApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.VISUAL_CAPTURED

    companion object {
        val mapper = object: Mapper<VisualCaptured>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that have been cleared for an approach with a localizer component
 *
 * The aircraft will monitor its position relative to the approach position origin and capture it when within range
 */
data class LocalizerArmed(var locApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LOCALIZER_ARMED

    companion object {
        val mapper = object: Mapper<LocalizerArmed>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that has captured the localizer, and will alter aircraft AI behaviour to follow the
 * localizer track
 */
data class LocalizerCaptured(var locApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LOCALIZER_CAPTURED

    companion object {
        val mapper = object: Mapper<LocalizerCaptured>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that have been cleared for an approach with a glide slope component
 *
 * The aircraft will monitor its altitude and capture it when it reaches the appropriate altitude
 */
data class GlideSlopeArmed(var gsApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.GLIDE_SLOPE_ARMED

    companion object {
        val mapper = object: Mapper<GlideSlopeArmed>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that has captured the glide slope, and will alter aircraft AI, physics behaviour to follow
 * the glide slope strictly
 */
data class GlideSlopeCaptured(var gsApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.GLIDE_SLOPE_CAPTURED

    companion object {
        val mapper = object: Mapper<GlideSlopeCaptured>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft that have been cleared for a non-precision step down approach, and will alter aircraft
 * AI behaviour to follow the step-down altitudes if the localizer is captured
 */
data class StepDownApproach(var stepDownApp: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.STEP_DOWN_APPROACH

    companion object {
        val mapper = object: Mapper<StepDownApproach>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging aircraft cleared for a circling approach; the aircraft must have captured the localizer, or
 * captured the glideslope, or be cleared for a step-down approach in order for this component to take effect
 *
 * This component will persist until the aircraft is no longer on the approach or is on the final visual segment of the
 * approach
 */
data class CirclingApproach(var circlingApp: Entity = Entity(), var breakoutAlt: Int = 0, var phase: Byte = 0,
                            var phase1Timer: Float = 70f, var phase3Timer: Float = 50f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CIRCLING_APPROACH

    companion object {
        val mapper = object: Mapper<CirclingApproach>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}
