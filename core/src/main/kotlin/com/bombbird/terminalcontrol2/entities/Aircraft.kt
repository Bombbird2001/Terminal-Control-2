package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.MAX_ALT
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.addDatatagInputListeners
import com.bombbird.terminalcontrol2.ui.updateDatatagStyle
import com.bombbird.terminalcontrol2.ui.updateDatatagText
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.with
import ktx.scene2d.Scene2DSkin
import kotlin.math.roundToInt

/** Aircraft class that creates an aircraft entity with the required components on instantiation */
class Aircraft(callsign: String, posX: Float, posY: Float, alt: Float, flightType: Byte, onClient: Boolean = true) {
    val entity = getEngine(onClient).entity {
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
        with<AffectedByWind>()
        if (onClient) {
            with<RadarData> {
                position.x = posX
                position.y = posY
            }
            with<Datatag> {
                updateDatatagStyle(this, flightType)
                updateDatatagText(this, arrayOf("Test line 1", "Test line 2", "", "Test line 3"))
                addDatatagInputListeners(this, this@Aircraft)
                xOffset = -imgButton.width / 2
                yOffset = 13f
            }
            with<RSSprite> {
                drawable = TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion(when (flightType) {
                    FlightType.ARRIVAL, FlightType.EN_ROUTE -> "aircraftEnroute"
                    FlightType.DEPARTURE -> "aircraftTower"
                    else -> {
                        Gdx.app.log("Aircraft", "Unknown flight type $flightType")
                        "aircraftTower"
                    }
                }))
            }
            with<SRColor> {
                color = when (flightType) {
                    FlightType.ARRIVAL -> Color(0f, 0.702f, 1f, 1f)
                    FlightType.DEPARTURE -> Color(0.067f, 1f, 0f, 1f)
                    else -> Color(1f, 1f, 1f, 0f)
                }
            }
        }
        with<Controllable> {
            sectorId = when (flightType) {
                FlightType.DEPARTURE -> SectorInfo.TOWER
                FlightType.ARRIVAL, FlightType.EN_ROUTE -> SectorInfo.CENTRE
                else -> {
                    Gdx.app.log("Aircraft", "Unknown flight type $flightType")
                    SectorInfo.CENTRE
                }
            }
        }
        if (flightType == FlightType.DEPARTURE) {
            with<ContactFromTower> {
                altitudeFt = (alt + MathUtils.random(600, 1700)).toInt()
            }
            with<ContactToCentre> {
                altitudeFt = (MAX_ALT - MathUtils.random(500, 900))
            }
        } else if (flightType == FlightType.ARRIVAL) {
            with<ContactFromCentre> {
                altitudeFt = (MAX_ALT + MathUtils.random(-500, 800))
            }
        }
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
                        position.x = serialisedAircraft.x
                        position.y = serialisedAircraft.y
                        altitude.altitudeFt = serialisedAircraft.altitude
                        direction.trackUnitVector = Vector2(serialisedAircraft.directionX, serialisedAircraft.directionY)
                        speed.speedKts = serialisedAircraft.speedKts
                        speed.vertSpdFpm = serialisedAircraft.vertSpdFpm
                        speed.angularSpdDps = serialisedAircraft.angularSpdDps
                    }
                    this += ClearanceAct(ClearanceState(serialisedAircraft.routePrimaryName,
                        Route.fromSerialisedObject(serialisedAircraft.commandRoute), Route.fromSerialisedObject(serialisedAircraft.commandHiddenLegs),
                        serialisedAircraft.vectorHdg, serialisedAircraft.commandAlt, serialisedAircraft.clearedIas
                    ))
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
                                val targetHdgDeg: Short = 0, val targetAltFt: Short = 0, val targetIasKt: Short = 0)

    /** Gets a [SerialisedAircraftUDP] from current state */
    fun getSerialisableObjectUDP(): SerialisedAircraftUDP {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAircraftUDP()
            val altitude = get(Altitude.mapper) ?: return SerialisedAircraftUDP()
            val acInfo = get(AircraftInfo.mapper) ?: return SerialisedAircraftUDP()
            val direction = get(Direction.mapper) ?: return SerialisedAircraftUDP()
            val speed = get(Speed.mapper) ?: return SerialisedAircraftUDP()
            val cmdTarget = get(CommandTarget.mapper) ?: return SerialisedAircraftUDP()
            return SerialisedAircraftUDP(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps,
                cmdTarget.targetHdgDeg.toInt().toShort(), (cmdTarget.targetAltFt / 100).roundToInt().toShort(), cmdTarget.targetIasKt
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
            get(CommandTarget.mapper)?.apply {
                targetHdgDeg = data.targetHdgDeg.toFloat()
                targetAltFt = data.targetAltFt * 100f
                targetIasKt = data.targetIasKt
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
                             val flightType: Byte = 0,
                             val routePrimaryName: String = "", val commandRoute: Route.SerialisedRoute = Route.SerialisedRoute(), val commandHiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                             val vectorHdg: Short? = null, val commandAlt: Int = 0, val clearedIas: Short = 0 // Vector HDG will be null if aircraft is flying route
    )

    /** Gets a [SerialisedAircraft] from current state */
    fun getSerialisableObject(): SerialisedAircraft {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAircraft()
            val altitude = get(Altitude.mapper) ?: return SerialisedAircraft()
            val acInfo = get(AircraftInfo.mapper) ?: return SerialisedAircraft()
            val direction = get(Direction.mapper) ?: return SerialisedAircraft()
            val speed = get(Speed.mapper) ?: return SerialisedAircraft()
            val flightType = get(FlightType.mapper) ?: return SerialisedAircraft()
            val latestClearance = get(PendingClearances.mapper)?.clearanceArray?.last()?.second ?: get(ClearanceAct.mapper)?.clearance ?: return SerialisedAircraft()
            return SerialisedAircraft(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign, acInfo.icaoType, acInfo.aircraftPerf.appSpd, acInfo.aircraftPerf.vR, acInfo.aircraftPerf.weightKg,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps,
                flightType.type,
                latestClearance.routePrimaryName, latestClearance.route.getSerialisedObject(), latestClearance.hiddenLegs.getSerialisedObject(),
                latestClearance.vectorHdg, latestClearance.clearedAlt, latestClearance.clearedIas
            )
        }
    }
}