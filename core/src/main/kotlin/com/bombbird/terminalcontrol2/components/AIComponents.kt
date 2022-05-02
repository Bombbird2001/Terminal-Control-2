package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
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
 * Aircraft will decelerate at a constant rate till ~45 knots, then decelerate at a reduced rate, then de-spawn at 30 knots
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

