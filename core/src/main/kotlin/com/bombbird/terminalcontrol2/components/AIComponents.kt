package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import ktx.ashley.Mapper

/** Component for tagging takeoff rolling mode
 *
 * Aircraft will accelerate at a constant rate [targetAccMps2], rotate at vR
 * */
data class TakeoffRoll(var targetAccMps2: Float): Component {
    companion object: Mapper<TakeoffRoll>()
}

/** Component for tagging initial takeoff climb mode
 *
 * Aircraft will maintain vR + (15 to 20) and climb at max allowed rate till [accelFtAGL], where it will accelerate
 * */
data class TakeoffClimb(var accelFtAGL: Float): Component {
    companion object: Mapper<TakeoffClimb>()
}

/** Component for tagging landing mode
 *
 * Aircraft will decelerate at a constant rate till ~45 knots, then decelerate at a reduced rate, then de-spawn at 30 knots
 * */
class Landing: Component {
    companion object: Mapper<Landing>()
}

/** Component for tagging the basic AI control modes of the aircraft
 *
 * [targetHdgDeg] is the heading the plane will turn to and maintain
 *
 * [targetAltFt] is the altitude the plane will climb/descend to and maintain
 *
 * [targetIasKt] is the indicated airspeed the plane will speed up/slow down to and maintain
 *
 * These basic parameters can be automatically altered by more advanced modes, such as Direct (to waypoint), Hold (at waypoint),
 * Climb via SID/Descend via STAR (to altitude), and SID/STAR speed restrictions in order to achieve the required behaviour
 * */
data class CommandTarget(var targetHdgDeg: Float, var targetAltFt: Float, var targetIasKt: Short): Component {
    companion object: Mapper<CommandTarget>()
}

/** Component for tagging aircraft that must turn right */
class TurnRight: Component {
    companion object: Mapper<TurnRight>()
}

/** Component for tagging aircraft that must turn left */
class TurnLeft: Component {
    companion object: Mapper<TurnLeft>()
}
