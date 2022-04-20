package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import ktx.ashley.Mapper

/** Component for lateral position on radarScreen
 *
 * Units for [x] and [y] are px
 * */
data class Position(var x: Float = 0f, var y: Float = 0f): Component {
    companion object: Mapper<Position>()
}

/** Component for direction (uses track - already takes into account magnetic heading deviation)
 *
 * [trackUnitVector] is rotated with respect to the screen, with positive [Vector2.x] towards the right and positive [Vector2.y] towards the top
 * */
data class Direction(var trackUnitVector: Vector2 = Vector2()): Component {
    companion object: Mapper<Direction>()
}

/** Component for lateral, vertical and angular speeds
 *
 * Unit for [speedKts] is knots
 *
 * Unit for [vertSpdFpm] is feet per minute
 *
 * Unit for [angularSpdDps] is degrees per second, positive means direction is turning clockwise
 * */
data class Speed(var speedKts: Float = 0f, var vertSpdFpm: Float = 0f, var angularSpdDps: Float = 0f): Component {
    companion object: Mapper<Speed>()
}

/** Component for altitude
 *
 * Unit for [altitudeFt] is feet
 * */
data class Altitude(var altitudeFt: Float = 0f): Component {
    companion object: Mapper<Altitude>()
}

/** Component for lateral, vertical and angular acceleration
 *
 * Unit for [dSpeedMps2] is metres per second^2
 *
 * Unit for [dVertSpdMps2] is metres per second^2
 *
 * Unit for [dAngularSpdDps2] is degrees per second^2
 * */
data class Acceleration(var dSpeedMps2: Float = 0f, var dVertSpdMps2: Float = 0f, var dAngularSpdDps2: Float = 0f): Component {
    companion object: Mapper<Acceleration>()
}

/** Component for radar returns (delayed reporting of [Position], [Direction], [Speed] and [Altitude]) */
data class RadarData(val position: Position = Position(), val direction: Direction = Direction(), val speed: Speed = Speed(), val altitude: Altitude = Altitude()): Component {
    companion object: Mapper<RadarData>()
}

/** Component for indicated air speed
 *
 * Note: [Speed.speedKts] stores true airspeed
 *
 * [iasKt] is calculated by some complicated equation which I will not put here (see PhysicsTools for the actual implementation);
 * technically the appropriate term to use for the purpose of this game is CAS (calibrated air speed), but we will assume
 * the difference between [iasKt] and CAS is negligible and use them interchangeably
 * */
data class IndicatedAirSpeed(var iasKt: Float = 0f): Component {
    companion object: Mapper<IndicatedAirSpeed>()
}

/** Component for tagging entities that get affected by wind velocity
 *
 * To prevent on-ground entities from being affected by wind, add the [OnGround] component to the entity
 * */
class AffectedByWind: Component {
    companion object: Mapper<AffectedByWind>()
}

/** Component for tagging on ground entities (will not be affected by wind even if tagged with it) */
class OnGround: Component {
    companion object: Mapper<OnGround>()
}
