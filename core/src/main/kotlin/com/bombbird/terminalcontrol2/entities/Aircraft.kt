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
class Aircraft(callsign: String, posX: Float, posY: Float, alt: Float, flightType: Byte, onClient: Boolean = true) {
    val entity = (if (onClient) Constants.CLIENT_ENGINE else Constants.SERVER_ENGINE).entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitudeFt = alt
        }
        with<AircraftInfo> {
            icaoCallsign = callsign
        }
        with<Direction> {
            trackUnitVector = Vector2(Vector2.Y)
        }
        with<Speed>()
        with<IndicatedAirSpeed>()
        with<Acceleration>()
        with<FlightType> {
            type = flightType
        }
        with<CommandTarget>()
        with<TakeoffRoll>()
        // if (onClient) {
            with<RadarData> {
                position.x = posX
                position.y = posY
            }
            with<GenericTextButton> {
                updateStyle("DatatagGreen")
                updateText("Test label")
                xOffset = -textButton.width / 2
                yOffset = 10f
            }
            with<RSSprite> {
                drawable = TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion("aircraftDeparture"))
            }
        // }
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
                    get(Direction.mapper)?.apply {
                        trackUnitVector = Vector2(serialisedAircraft.directionX, serialisedAircraft.directionY)
                    }
                    get(AircraftInfo.mapper)?.apply {
                        icaoType = serialisedAircraft.icaoType
                        aircraftPerf.appSpd = serialisedAircraft.appSpd
                        aircraftPerf.vR = serialisedAircraft.vR
                        aircraftPerf.weightKg = serialisedAircraft.weightKg
                    }
                    get(Speed.mapper)?.apply {
                        speedKts = serialisedAircraft.speedKts
                        vertSpdFpm = serialisedAircraft.vertSpdFpm
                        angularSpdDps = serialisedAircraft.angularSpdDps
                    }
                    get(RadarData.mapper)?.apply {
                        position.x = serialisedAircraft.rX.toFloat()
                        position.y = serialisedAircraft.rY.toFloat()
                        altitude.altitudeFt = serialisedAircraft.rAlt
                        direction.trackUnitVector = Vector2(serialisedAircraft.rDirX / 30000f, serialisedAircraft.rDirY / 30000f)
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
                                val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f)

    /** Gets a [SerialisedAircraftUDP] from current state */
    fun getSerialisableObjectUDP(): SerialisedAircraftUDP {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAircraftUDP()
            val altitude = get(Altitude.mapper) ?: return SerialisedAircraftUDP()
            val acInfo = get(AircraftInfo.mapper) ?: return SerialisedAircraftUDP()
            val direction = get(Direction.mapper) ?: return SerialisedAircraftUDP()
            val speed = get(Speed.mapper) ?: return SerialisedAircraftUDP()
            return SerialisedAircraftUDP(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps
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
                altitudeFt = data.altitude
            }
            get(Direction.mapper)?.apply {
                trackUnitVector = Vector2(data.directionX, data.directionY)
            }
            get(Speed.mapper)?.apply {
                speedKts = data.speedKts
                vertSpdFpm = data.vertSpdFpm
                angularSpdDps = data.angularSpdDps
            }
        }
    }

    /** Object that contains select [Aircraft] data to be sent over TCP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     * */
    class SerialisedAircraft(val x: Float = 0f, val y: Float = 0f,
                             val altitude: Float = 0f,
                             val icaoCallsign: String = "", val icaoType: String = "", val appSpd: Short = 0, val vR: Short = 0, val weightKg: Int = 0,
                             val directionX: Float = 0f, val directionY: Float = 0f,
                             val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                             val rX: Short = 0, val rY: Short = 0, val rAlt: Float = 0f, val rDirX: Short = 0, val rDirY: Short = 0, val rSpd: Short = 0, val rVertSpd: Short = 0, val rAngularSpd: Short = 0,
                             val flightType: Byte = 0)

    /** Gets a [SerialisedAircraft] from current state */
    fun getSerialisableObject(): SerialisedAircraft {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAircraft()
            val altitude = get(Altitude.mapper) ?: return SerialisedAircraft()
            val acInfo = get(AircraftInfo.mapper) ?: return SerialisedAircraft()
            val direction = get(Direction.mapper) ?: return SerialisedAircraft()
            val speed = get(Speed.mapper) ?: return SerialisedAircraft()
            val rData = get(RadarData.mapper) ?: return SerialisedAircraft()
            val flightType = get(FlightType.mapper) ?: return SerialisedAircraft()
            return SerialisedAircraft(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign, acInfo.icaoType, acInfo.aircraftPerf.appSpd, acInfo.aircraftPerf.vR, acInfo.aircraftPerf.weightKg,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps,
                rData.position.x.toInt().toShort(), rData.position.y.toInt().toShort(), rData.altitude.altitudeFt,
                (rData.direction.trackUnitVector.x * 30000).toInt().toShort(), (rData.direction.trackUnitVector.y * 30000).toInt().toShort(),
                rData.speed.speedKts.toInt().toShort(), rData.speed.vertSpdFpm.toInt().toShort(), (rData.speed.angularSpdDps * 100).toInt().toShort(),
                flightType.type
            )
        }
    }
}