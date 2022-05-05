package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.navigation.Route
import ktx.ashley.Mapper

/** Component for tagging takeoff rolling mode
 *
 * Aircraft will accelerate at a constant rate [targetAccMps2], rotate at vR
 * */
data class TakeoffRoll(var targetAccMps2: Float = 2f): Component {
    companion object: Mapper<TakeoffRoll>()
}

/** Component for tagging initial takeoff climb mode
 *
 * Aircraft will maintain vR + (15 to 20) and climb at max allowed rate till [accelAltFt], where it will accelerate
 * */
data class TakeoffClimb(var accelAltFt: Float = 1500f): Component {
    companion object: Mapper<TakeoffClimb>()
}

/** Component for tagging landing mode
 *
 * Aircraft will decelerate at a constant rate till ~45 knots, then decelerate at a reduced rate, then de-spawn at 25-30 knots
 * */
class LandingRoll: Component {
    companion object: Mapper<LandingRoll>()
}

/** Component for tagging the basic AI control modes of the aircraft
 *
 * [targetHdgDeg] is the heading the plane will turn to and maintain
 *
 * [turnDir] is the direction to turn ([TURN_DEFAULT] will turn in the quickest direction)
 *
 * [targetAltFt] is the altitude the plane will climb/descend to and maintain
 *
 * [expedite] is whether the aircraft should allow targeting of increased maximum vertical speed
 *
 * [targetIasKt] is the indicated airspeed the plane will speed up/slow down to and maintain
 *
 * These basic parameters can be automatically altered by more advanced modes, such as Direct (to waypoint), Hold (at waypoint),
 * Climb via SID/Descend via STAR (to altitude), and SID/STAR speed restrictions in order to achieve the required behaviour
 * */
data class CommandTarget(var targetHdgDeg: Float = 360f, var turnDir: Byte = TURN_DEFAULT, var targetAltFt: Float = 0f, var expedite: Boolean = false, var targetIasKt: Short = 0): Component {
    companion object: Mapper<CommandTarget>() {
        val TURN_DEFAULT: Byte = 0
        val TURN_LEFT: Byte = -1
        val TURN_RIGHT: Byte = 1
    }
}

/** Component for tagging the route that an aircraft has been instructed to fly
 *
 * [primaryName] is the name of the SID/STAR that the route is derived from
 *
 * [route] is the active route the aircraft is flying - the first leg in the array is the active leg which the aircraft
 * will target; components will also be tagged to the aircraft to implement the actual behaviour
 *
 * [hiddenLegs] is an array of legs that are removed from the original route due to the use of transitions, but may be
 * added back to the route should the transition change
 * */
data class CommandRoute(var primaryName: String = "", var route: Route = Route(), var hiddenLegs: Route = Route()): Component {
    companion object: Mapper<CommandRoute>()
}

/** Component for tagging the vector leg an aircraft is flying
 *
 * [heading] is the heading the aircraft will fly
 * */
data class CommandVector(var heading: Short = 360): Component {
    companion object: Mapper<CommandVector>()
}

/** Component for tagging the initial climb leg an aircraft is flying
 *
 * [heading] is the heading the aircraft will fly
 *
 * [minAltFt] is the altitude above which the aircraft will move on to the next leg
 * */
data class CommandInitClimb(var heading: Short = 360, var minAltFt: Int = 0): Component {
    companion object: Mapper<CommandInitClimb>()
}

/** Component for tagging the waypoint leg an aircraft is flying
 *
 * [wptId] is the ID of the waypoint
 *
 * [maxAltFt], [minAltFt] and [maxSpdKt] are the maximum altitude, minimum altitude and maximum IAS restrictions
 * respectively that the leg has and the aircraft is subject to
 *
 * [flyOver] denotes whether the aircraft needs to overfly the waypoint before moving to the next leg
 *
 * [turnDir] specifies whether a forced turn direction is required when turning onto this leg
 * */
data class CommandDirect(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null, val maxSpdKt: Short?,
                         val flyOver: Boolean = false, val turnDir: Byte = CommandTarget.TURN_DEFAULT): Component {
    companion object: Mapper<CommandDirect>()
}

/** Component for tagging a holding leg an aircraft is flying */
data class CommandHold(var wptId: Short = 0, val maxAltFt: Int? = null, val minAltFt: Int? = null, val maxSpdKt: Short? = null,
                       val inboundHdg: Short = 360, val legDist: Byte = 5, val legDir: Byte = CommandTarget.TURN_RIGHT): Component {
    companion object: Mapper<CommandHold>()
}

/** Component for tagging an aircraft that is flying according to continuous descent approach (CDA) operations */
class CommandCDA: Component {
    companion object: Mapper<CommandCDA>()
}
