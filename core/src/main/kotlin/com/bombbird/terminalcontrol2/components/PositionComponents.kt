package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/**
 * Component for lateral position on radarScreen
 * @param x the x coordinate, in px, of the entity on radarScreen
 * @param y the y coordinate, in px, of the entity on radarScreen
 */
@JsonClass(generateAdapter = true)
data class Position(var x: Float = 0f, var y: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.POSITION

    companion object {
        val mapper = object: Mapper<Position>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising Position mapper")
        }
    }
}

/**
 * Component for mapping an additional user defined position for whatever needs
 *
 * Note that this component is likely not used for any built-in game logic or rendering - it is solely to carry additional
 * positional data to be retrieved for other purposes
 * @param x the x coordinate, in px, on radarScreen
 * @param y the y coordinate, in px, on radarScreen
 */
@JsonClass(generateAdapter = true)
data class CustomPosition(var x: Float = 0f, var y: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CUSTOM_POSITION

    companion object {
        val mapper = object: Mapper<CustomPosition>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising CustomPosition mapper")
        }
    }
}

/**
 * Component for direction (uses track - already takes into account magnetic heading deviation)
 * @param trackUnitVector the direction unit vector rotated with respect to the screen, with positive [Vector2.x]
 * towards the right and positive [Vector2.y] towards the top
 */
@JsonClass(generateAdapter = true)
data class Direction(var trackUnitVector: Vector2 = Vector2()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DIRECTION

    companion object {
        val mapper = object: Mapper<Direction>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising Direction mapper")
        }
    }
}

/**
 * Component for lateral, vertical and angular speeds
 * @param speedKts the speed, in knots
 * @param vertSpdFpm the vertical speed, in feet per minute
 * @param angularSpdDps the angular speed, in degrees per second, positive means a clockwise turn
 */
@JsonClass(generateAdapter = true)
data class Speed(var speedKts: Float = 0f, var vertSpdFpm: Float = 0f, var angularSpdDps: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.SPEED

    companion object {
        val mapper = object: Mapper<Speed>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising Speed mapper")
        }
    }
}

/**
 * Component for altitude
 * @param altitudeFt the altitude, in feet
 */
@JsonClass(generateAdapter = true)
data class Altitude(var altitudeFt: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ALTITUDE

    companion object {
        val mapper = object: Mapper<Altitude>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising Altitude mapper")
        }
    }
}

/**
 * Component for lateral, vertical and angular acceleration
 * @param dSpeedMps2 the horizontal acceleration, in metres per second^2
 * @param dVertSpdMps2, the vertical acceleration, in metres per second^2
 * @param dAngularSpdDps2, the angular acceleration, in degrees per second^2
 */
@JsonClass(generateAdapter = true)
data class Acceleration(var dSpeedMps2: Float = 0f, var dVertSpdMps2: Float = 0f, var dAngularSpdDps2: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ACCELERATION

    companion object {
        val mapper = object: Mapper<Acceleration>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising Acceleration mapper")
        }
    }
}

/** Component for radar returns (delayed reporting of [Position], [Direction], [Speed], [Altitude] and ground speed) */
data class RadarData(val position: Position = Position(), val direction: Direction = Direction(), val speed: Speed = Speed(),
                     val altitude: Altitude = Altitude(), var groundSpeed: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<RadarData>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising RadarData mapper")
        }
    }
}

/**
 * Component for indicated air speed
 *
 * Note: [Speed.speedKts] stores true airspeed
 *
 * [iasKt] is calculated by some complicated equation which I will not put here (see PhysicsTools for the actual implementation);
 * technically the appropriate term to use for the purpose of this game is CAS (calibrated air speed), but we will assume
 * the difference between [iasKt] and CAS is negligible and use them interchangeably
 * @param iasKt the indicated airspeed, in knots, of the aircraft
 */
@JsonClass(generateAdapter = true)
data class IndicatedAirSpeed(var iasKt: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.INDICATED_AIR_SPEED

    companion object {
        val mapper = object: Mapper<IndicatedAirSpeed>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising IndicatedAirSpeed mapper")
        }
    }
}

/**
 * Component for ground speed - the magnitude of the sum of the aircraft's velocity vector and the wind velocity vector
 * @param trackVectorPxps the ground track vector, in pixels per second, of the aircraft
 */
@JsonClass(generateAdapter = true)
data class GroundTrack(var trackVectorPxps: Vector2 = Vector2()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.GROUND_TRACK

    companion object {
        val mapper = object: Mapper<GroundTrack>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GroundTrack mapper")
        }
    }
}

/**
 * Component for tagging entities that get affected by wind velocity
 *
 * To prevent on-ground entities from being affected by wind, add the [OnGround] component to the entity
 *
 * Entities tagged with [TakeoffRoll] or [LandingRoll] will also not be affected by wind even if this component is tagged
 * @param windVectorPxps the velocity vector of the wind, in px per second
 */
@JsonClass(generateAdapter = true)
data class AffectedByWind(var windVectorPxps: Vector2 = Vector2()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.AFFECTED_BY_WIND

    companion object {
        val mapper = object: Mapper<AffectedByWind>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising AffectedByWind mapper")
        }
    }
}

/** Component for tagging on ground entities (will not be affected by wind even if tagged with it) */
@JsonClass(generateAdapter = true)
class OnGround: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ON_GROUND

    companion object {
        val mapper = object: Mapper<OnGround>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising OnGround mapper")
        }
    }
}

/** Component for tagging the positions of glide slope circles */
class GlideSlopeCircle(var positions: Array<Position>): Component {
    companion object {
        val mapper = object: Mapper<GlideSlopeCircle>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GlideSlopeCircle mapper")
        }
    }
}
