package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import ktx.ashley.Mapper

/** Component for lateral position on radarScreen */
class Position(var x: Float = 0f, var y: Float = 0f): Component {
    companion object: Mapper<Position>()
}

/** Component for direction */
class Direction(var dirUnitVector: Vector2 = Vector2()): Component {
    companion object: Mapper<Direction>()
}

/** Component for lateral, vertical and angular speeds */
class Speed(var speedKts: Float = 0f, var vertSpdFpm: Float = 0f, var angularSpdDps: Float = 0f): Component {
    companion object: Mapper<Speed>()
}

/** Component for altitude */
class Altitude(var altitude: Float = 0f): Component {
    companion object: Mapper<Altitude>()
}

/** Component for lateral, vertical and angular acceleration */
class Acceleration(var dSpeed: Float = 0f, var dVertSpd: Float = 0f, var dAngularSpd: Float = 0f): Component {
    companion object: Mapper<Acceleration>()
}

/** Component for radar returns (delayed reporting of [Position], [Direction], [Speed] and [Altitude]) */
class RadarData(val position: Position = Position(), val direction: Direction = Direction(), val speed: Speed = Speed(), val altitude: Altitude = Altitude()): Component {
    companion object: Mapper<RadarData>()
}
