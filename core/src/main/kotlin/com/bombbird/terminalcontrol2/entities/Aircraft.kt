package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with
import ktx.scene2d.Scene2DSkin

/** Aircraft class that creates an aircraft entity with the required components on instantiation */
class Aircraft(callsign: String, posX: Float, posY: Float, alt: Float, flightType: Byte) {
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
        with<Speed>()
        with<IndicatedAirSpeed>()
        with<Acceleration>()
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
                        dirUnitVector = Vector2(serialisedAircraft.directionX, serialisedAircraft.directionY)
                    }
                    get(AircraftInfo.mapper)!!.apply {
                        icaoType = serialisedAircraft.icaoType
                        appSpd = serialisedAircraft.appSpd
                        vR = serialisedAircraft.vR
                        weightTons = serialisedAircraft.weightTons
                    }
                    get(Speed.mapper)!!.apply {
                        speedKts = serialisedAircraft.speedKts
                        vertSpdFpm = serialisedAircraft.vertSpdFpm
                        angularSpdDps = serialisedAircraft.angularSpdDps
                    }
                    get(RadarData.mapper)!!.apply {
                        position.x = serialisedAircraft.rX.toFloat()
                        position.y = serialisedAircraft.rY.toFloat()
                        altitude.altitude = serialisedAircraft.rAlt
                        direction.dirUnitVector = Vector2(serialisedAircraft.rDirX / 30000f, serialisedAircraft.rDirY / 30000f)
                        speed.speedKts = serialisedAircraft.rSpd.toFloat()
                        speed.vertSpdFpm = serialisedAircraft.rVertSpd.toFloat()
                        speed.angularSpdDps = serialisedAircraft.rAngularSpd / 100f
                    }
                }
            }
        }
    }

    /** Object that contains select [Aircraft] data to be sent over UDP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     * */
    class SerialisedAircraftUDP(val x: Float = 0f, val y: Float = 0f,
                                val altitude: Float = 0f,
                                val icaoCallsign: String = "",
                                val directionX: Float = 0f, val directionY: Float = 0f,
                                val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                                val rX: Short = 0, val rY: Short = 0, val rAlt: Float = 0f, val rDirX: Short = 0, val rDirY: Short = 0, val rSpd: Short = 0, val rVertSpd: Short = 0, val rAngularSpd: Short = 0)

    /** Gets a [SerialisedAircraftUDP] from current state */
    fun getSerialisableObjectUDP(): SerialisedAircraftUDP {
        entity.apply {
            val position = get(Position.mapper)!!
            val altitude = get(Altitude.mapper)!!
            val acInfo = get(AircraftInfo.mapper)!!
            val direction = get(Direction.mapper)!!
            val speed = get(Speed.mapper)!!
            val rData = get(RadarData.mapper)!!
            return SerialisedAircraftUDP(
                position.x, position.y,
                altitude.altitude,
                acInfo.icaoCallsign,
                direction.dirUnitVector.x, direction.dirUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps,
                rData.position.x.toInt().toShort(), rData.position.y.toInt().toShort(), rData.altitude.altitude,
                (rData.direction.dirUnitVector.x * 30000).toInt().toShort(), (rData.direction.dirUnitVector.y * 30000).toInt().toShort(),
                rData.speed.speedKts.toInt().toShort(), rData.speed.vertSpdFpm.toInt().toShort(), (rData.speed.angularSpdDps * 100).toInt().toShort()
            )
        }
    }

    /** Updates the data of this aircraft with new UDP data from [SerialisedAircraftUDP] */
    fun updateFromUDPData(data: SerialisedAircraftUDP) {
        entity.apply {
            get(Position.mapper)?.apply {
                x = data.x
                y = data.y
            }
            get(Altitude.mapper)?.apply {
                altitude = data.altitude
            }
            get(Direction.mapper)?.apply {
                dirUnitVector = Vector2(data.directionX, data.directionY)
            }
            get(Speed.mapper)?.apply {
                speedKts = data.speedKts
                vertSpdFpm = data.vertSpdFpm
                angularSpdDps = data.angularSpdDps
            }
            get(RadarData.mapper)?.apply {
                position.x = data.rX.toFloat()
                position.y = data.rY.toFloat()
                altitude.altitude = data.rAlt
                direction.dirUnitVector = Vector2(data.rDirX / 30000f, data.rDirY / 30000f)
                speed.speedKts = data.rSpd.toFloat()
                speed.vertSpdFpm = data.rVertSpd.toFloat()
                speed.angularSpdDps = data.rAngularSpd / 100f
            }
        }
    }

    /** Object that contains select [Aircraft] data to be sent over TCP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     * */
    class SerialisedAircraft(val x: Float = 0f, val y: Float = 0f,
                             val altitude: Float = 0f,
                             val icaoCallsign: String = "", val icaoType: String = "", val appSpd: Short = 0, val vR: Short = 0, val weightTons: Float = 0f,
                             val directionX: Float = 0f, val directionY: Float = 0f,
                             val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                             val rX: Short = 0, val rY: Short = 0, val rAlt: Float = 0f, val rDirX: Short = 0, val rDirY: Short = 0, val rSpd: Short = 0, val rVertSpd: Short = 0, val rAngularSpd: Short = 0,
                             val flightType: Byte = 0)

    /** Gets a [SerialisedAircraft] from current state */
    fun getSerialisableObject(): SerialisedAircraft {
        entity.apply {
            val position = get(Position.mapper)!!
            val altitude = get(Altitude.mapper)!!
            val acInfo = get(AircraftInfo.mapper)!!
            val direction = get(Direction.mapper)!!
            val speed = get(Speed.mapper)!!
            val rData = get(RadarData.mapper)!!
            val flightType = get(FlightType.mapper)!!
            return SerialisedAircraft(
                position.x, position.y,
                altitude.altitude,
                acInfo.icaoCallsign, acInfo.icaoType, acInfo.appSpd, acInfo.vR, acInfo.weightTons,
                direction.dirUnitVector.x, direction.dirUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps,
                rData.position.x.toInt().toShort(), rData.position.y.toInt().toShort(), rData.altitude.altitude,
                (rData.direction.dirUnitVector.x * 30000).toInt().toShort(), (rData.direction.dirUnitVector.y * 30000).toInt().toShort(),
                rData.speed.speedKts.toInt().toShort(), rData.speed.vertSpdFpm.toInt().toShort(), (rData.speed.angularSpdDps * 100).toInt().toShort(),
                flightType.type
            )
        }
    }
}