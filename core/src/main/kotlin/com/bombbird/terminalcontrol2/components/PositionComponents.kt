package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import ktx.ashley.Mapper

/** Component for lateral position on radarScreen
 *
 * Units for [x] and [y] are px
 * */
class Position(var x: Float = 0f, var y: Float = 0f): Component {
    companion object: Mapper<Position>()
}

/** Component for direction
 *
 * [dirUnitVector] is rotated with respect to the screen, with positive [Vector2.x] towards the right and positive [Vector2.y] towards the top
 * */
class Direction(var dirUnitVector: Vector2 = Vector2()): Component {
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
class Speed(var speedKts: Float = 0f, var vertSpdFpm: Float = 0f, var angularSpdDps: Float = 0f): Component {
    companion object: Mapper<Speed>()
}

/** Component for altitude
 *
 * Unit for [altitude] is feet
 * */
class Altitude(var altitude: Float = 0f): Component {
    companion object: Mapper<Altitude>()
}

/** Component for lateral, vertical and angular acceleration
 *
 * Unit for [dSpeed] is metres per second^2
 *
 * Unit for [dVertSpd] is metres per second^2
 *
 * Unit for [dAngularSpd] is degrees per second^2
 * */
class Acceleration(var dSpeed: Float = 0f, var dVertSpd: Float = 0f, var dAngularSpd: Float = 0f): Component {
    companion object: Mapper<Acceleration>()
}

/** Component for radar returns (delayed reporting of [Position], [Direction], [Speed] and [Altitude]) */
class RadarData(val position: Position = Position(), val direction: Direction = Direction(), val speed: Speed = Speed(), val altitude: Altitude = Altitude()): Component {
    companion object: Mapper<RadarData>()
}

/** Component for tagging entities that get affected by wind velocity */
class AffectedByWind: Component {
    companion object: Mapper<AffectedByWind>()
}
