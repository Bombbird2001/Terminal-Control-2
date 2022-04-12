package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.addComponent
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with
import ktx.scene2d.Scene2DSkin

/** Aircraft class that creates an aircraft entity with the required components on instantiation */
class Aircraft(callsign: String, posX: Float, posY: Float, alt: Float, flightType: Int) {
    val entity = Constants.SERVER_ENGINE.entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitude = alt
        }
        with<AircraftInfo> {
            icaoCallsign = callsign
        }
        with<Direction> {
            dirUnitVector = Vector2(Vector2.Y)
        }
        with<Speed> {

        }
        with<Acceleration> {

        }
        with<RadarData> {
            position.x = posX
            position.y = posY
        }
        with<FlightType> {
            type = flightType
        }
        with<RSSprite> {
            drawable = TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion("aircraftDeparture"))
        }
        // TODO Add controllable component
    }

    companion object {
        /** De-serialises a [SerialisedAircraft] and creates a new [Aircraft] object from it */
        fun fromSerialisedObject(serialisedAircraft: SerialisedAircraft): Aircraft {
            return Aircraft(
                serialisedAircraft.icaoCallsign,
                serialisedAircraft.x, serialisedAircraft.y,
                serialisedAircraft.altitude,
                serialisedAircraft.flightType
            ).apply {
                entity.apply {
                    get(Direction.mapper)!!.apply {
                        dirUnitVector = serialisedAircraft.direction
                    }
                    get(AircraftInfo.mapper)!!.apply {
                        icaoType = serialisedAircraft.icaoType
                        appSpd = serialisedAircraft.appSpd
                        vR = serialisedAircraft.vR
                        weightTons = serialisedAircraft.weightTons
                    }
                    addComponent<Speed>(Constants.CLIENT_ENGINE) {
                        speedKts = serialisedAircraft.speedKts
                        vertSpdFpm = serialisedAircraft.vertSpdFpm
                        angularSpdDps = serialisedAircraft.angularSpdDps
                    }
                    addComponent<Acceleration>(Constants.CLIENT_ENGINE) {
                        dSpeed = serialisedAircraft.dSpeed
                        dVertSpd = serialisedAircraft.dVertSpd
                        dAngularSpd = serialisedAircraft.dAngularSpd
                    }
                    addComponent<RadarData>(Constants.CLIENT_ENGINE) {
                        position.x = serialisedAircraft.rX
                        position.y = serialisedAircraft.rY
                        altitude.altitude = serialisedAircraft.rAlt
                        direction.dirUnitVector = serialisedAircraft.rDir
                        speed.speedKts = serialisedAircraft.rSpd
                        speed.vertSpdFpm = serialisedAircraft.rVertSpd
                        speed.angularSpdDps = serialisedAircraft.rAngularSpd
                    }
                }
            }
        }
    }

    /** An easily serialisable representation of [Aircraft] */
    class SerialisedAircraft(val x: Float = 0f, val y: Float = 0f,
                             val altitude: Float = 0f,
                             val icaoCallsign: String = "", val icaoType: String = "", val appSpd: Int = 0, val vR: Int = 0, val weightTons: Float = 0f,
                             val direction: Vector2 = Vector2(),
                             val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                             val dSpeed: Float = 0f, val dVertSpd: Float = 0f, val dAngularSpd: Float = 0f,
                             val rX: Float = 0f, val rY: Float = 0f, val rAlt: Float = 0f, val rDir: Vector2 = Vector2(), val rSpd: Float = 0f, val rVertSpd: Float = 0f, val rAngularSpd: Float = 0f,
                             val flightType: Int = 0)

    /** Gets a [SerialisedAircraft] from current state */
    fun getSerialisableObject(): SerialisedAircraft {
        entity.apply {
            val position = get(Position.mapper)!!
            val altitude = get(Altitude.mapper)!!
            val acInfo = get(AircraftInfo.mapper)!!
            val direction = get(Direction.mapper)!!
            val speed = get(Speed.mapper)!!
            val acceleration = get(Acceleration.mapper)!!
            val rData = get(RadarData.mapper)!!
            val flightType = get(FlightType.mapper)!!
            return SerialisedAircraft(
                position.x, position.y,
                altitude.altitude,
                acInfo.icaoCallsign, acInfo.icaoType, acInfo.appSpd, acInfo.vR, acInfo.weightTons,
                direction.dirUnitVector,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps,
                acceleration.dSpeed, acceleration.dVertSpd, acceleration.dAngularSpd,
                rData.position.x, rData.position.y, rData.altitude.altitude, rData.direction.dirUnitVector, rData.speed.speedKts, rData.speed.vertSpdFpm, rData.speed.angularSpdDps,
                flightType.type
            )
        }
    }
}