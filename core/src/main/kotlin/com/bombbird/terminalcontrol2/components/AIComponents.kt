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
 * Aircraft will maintain vR + (15 to 20) and climb at max allowed rate till [accelFtAGL], where it will transition to normal climb mode
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
