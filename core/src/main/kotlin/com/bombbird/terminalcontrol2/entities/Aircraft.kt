package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.ARRIVAL_BLUE
import com.bombbird.terminalcontrol2.global.DEPARTURE_GREEN
import com.bombbird.terminalcontrol2.global.MAX_ALT
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.addDatatagInputListeners
import com.bombbird.terminalcontrol2.ui.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.updateDatatagStyle
import com.bombbird.terminalcontrol2.ui.updateDatatagText
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.getAircraftIcon
import ktx.ashley.*
import ktx.scene2d.Scene2DSkin
import kotlin.math.roundToInt

/** Aircraft class that creates an aircraft entity with the required components on instantiation */
class Aircraft(callsign: String, posX: Float, posY: Float, alt: Float, icaoAircraftType: String, flightType: Byte, onClient: Boolean = true) {
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
            icaoType = icaoAircraftType
            if (!onClient) aircraftPerf = AircraftTypeData.getAircraftPerf(icaoAircraftType, flightType)
        }
        with<Direction> {
            trackUnitVector = Vector2(Vector2.Y)
        }
        with<Speed>()
        with<IndicatedAirSpeed>()
        with<GroundTrack>()
        with<Acceleration>()
        with<FlightType> {
            type = flightType
        }
        with<CommandTarget>()
        with<AffectedByWind>()
        if (onClient) {
            with<RadarData> {
                position.x = posX
                position.y = posY
            }
            with<Datatag> {
                updateDatatagStyle(this, flightType, false)
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
                    FlightType.ARRIVAL -> ARRIVAL_BLUE
                    FlightType.DEPARTURE -> DEPARTURE_GREEN
                    else -> Color.WHITE
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
                altitudeFt = alt.roundToInt() + MathUtils.random(600, 1100)
            }
            with<ContactToCentre> {
                altitudeFt = (MAX_ALT - MathUtils.random(500, 900))
            }
        } else if (flightType == FlightType.ARRIVAL) {
            with<ContactFromCentre> {
                altitudeFt = (MAX_ALT + MathUtils.random(400, 1500))
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
                serialisedAircraft.icaoType,
                serialisedAircraft.flightType
            ).apply {
                entity.apply {
                    get(Direction.mapper)?.apply {
                        trackUnitVector.x = serialisedAircraft.directionX
                        trackUnitVector.y = serialisedAircraft.directionY
                    }
                    get(AircraftInfo.mapper)?.apply {
                        aircraftPerf.maxAlt = serialisedAircraft.maxAlt
                    }
                    get(Speed.mapper)?.apply {
                        speedKts = serialisedAircraft.speedKts
                        vertSpdFpm = serialisedAircraft.vertSpdFpm
                        angularSpdDps = serialisedAircraft.angularSpdDps
                    }
                    get(GroundTrack.mapper)?.apply {
                        trackVectorPxps.x = serialisedAircraft.trackX
                        trackVectorPxps.y = serialisedAircraft.trackY
                    }
                    get(CommandTarget.mapper)?.apply {
                        targetHdgDeg = serialisedAircraft.targetHdgDeg.toFloat()
                        targetAltFt = serialisedAircraft.targetAltFt * 100
                        targetIasKt = serialisedAircraft.targetIasKt
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
                    this += ClearanceAct(ClearanceState.ActingClearance(ClearanceState(serialisedAircraft.routePrimaryName,
                        Route.fromSerialisedObject(serialisedAircraft.commandRoute), Route.fromSerialisedObject(serialisedAircraft.commandHiddenLegs),
                        serialisedAircraft.vectorHdg, serialisedAircraft.vectorTurnDir,
                        serialisedAircraft.commandAlt, serialisedAircraft.clearedIas,
                        serialisedAircraft.minIas, serialisedAircraft.maxIas, serialisedAircraft.optimalIas,
                    )))
                    serialisedAircraft.arrivalArptId?.let { this += ArrivalAirport(it) }
                    get(Controllable.mapper)?.apply {
                        sectorId = serialisedAircraft.controlSectorId
                    }
                    get(RSSprite.mapper)?.drawable = getAircraftIcon(serialisedAircraft.flightType, serialisedAircraft.controlSectorId)
                    if (serialisedAircraft.gsCap) this += GlideSlopeCaptured()
                    if (serialisedAircraft.locCap) this += LocalizerCaptured()
                    if (serialisedAircraft.visCap) this += VisualCaptured()
                    if (serialisedAircraft.waitingTakeoff) this += WaitingTakeoff()
                    get(Datatag.mapper)?.let { updateDatatagText(it, getNewDatatagLabelText(this, it.minimised))}
                }
            }
        }
    }

    /**
     * Object that contains select [Aircraft] data to be sent over UDP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     * */
    class SerialisedAircraftUDP(val x: Float = 0f, val y: Float = 0f,
                                val altitude: Float = 0f,
                                val icaoCallsign: String = "",
                                val directionX: Float = 0f, val directionY: Float = 0f,
                                val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                                val trackX: Float = 0f, val trackY: Float = 0f,
                                val targetHdgDeg: Short = 0, val targetAltFt: Short = 0, val targetIasKt: Short = 0,
                                val gsCap: Boolean = false, val locCap: Boolean = false, val visCap: Boolean = false,
                                val waitingTakeoff: Boolean = false
    )

    /** Gets a [SerialisedAircraftUDP] from current state */
    fun getSerialisableObjectUDP(): SerialisedAircraftUDP {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAircraftUDP()
            val altitude = get(Altitude.mapper) ?: return SerialisedAircraftUDP()
            val acInfo = get(AircraftInfo.mapper) ?: return SerialisedAircraftUDP()
            val direction = get(Direction.mapper) ?: return SerialisedAircraftUDP()
            val speed = get(Speed.mapper) ?: return SerialisedAircraftUDP()
            val gs = get(GroundTrack.mapper) ?: return SerialisedAircraftUDP()
            val cmdTarget = get(CommandTarget.mapper) ?: return SerialisedAircraftUDP()
            return SerialisedAircraftUDP(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps, gs.trackVectorPxps.x, gs.trackVectorPxps.y,
                cmdTarget.targetHdgDeg.toInt().toShort(), (cmdTarget.targetAltFt / 100f).roundToInt().toShort(), cmdTarget.targetIasKt,
                has(GlideSlopeCaptured.mapper), has(LocalizerCaptured.mapper),
                has(VisualCaptured.mapper) || (get(CirclingApproach.mapper)?.phase ?: 0) >= 1,
                has(WaitingTakeoff.mapper)
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
                trackUnitVector.x = data.directionX
                trackUnitVector.y = data.directionY
            }
            get(Speed.mapper)?.apply {
                speedKts = data.speedKts
                vertSpdFpm = data.vertSpdFpm
                angularSpdDps = data.angularSpdDps
            }
            get(GroundTrack.mapper)?.apply {
                trackVectorPxps.x = data.trackX
                trackVectorPxps.y = data.trackY
            }
            get(CommandTarget.mapper)?.apply {
                targetHdgDeg = data.targetHdgDeg.toFloat()
                targetAltFt = data.targetAltFt * 100
                targetIasKt = data.targetIasKt
            }
            if (data.gsCap) this += GlideSlopeCaptured()
            else remove<GlideSlopeCaptured>()
            if (data.locCap) this += LocalizerCaptured()
            else remove<LocalizerCaptured>()
            if (data.visCap) this += VisualCaptured()
            else remove<VisualCaptured>()
            if (data.waitingTakeoff) this += WaitingTakeoff()
            else remove<WaitingTakeoff>()
        }
    }

    /**
     * Object that contains select [Aircraft] data to be sent over TCP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     * */
    class SerialisedAircraft(val x: Float = 0f, val y: Float = 0f,
                             val altitude: Float = 0f,
                             val icaoCallsign: String = "", val icaoType: String = "", val maxAlt: Int = 0,
                             val directionX: Float = 0f, val directionY: Float = 0f,
                             val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                             val trackX: Float = 0f, val trackY: Float = 0f,
                             val targetHdgDeg: Short = 0, val targetAltFt: Short = 0, val targetIasKt: Short = 0,
                             val flightType: Byte = 0,
                             val routePrimaryName: String = "", val commandRoute: Route.SerialisedRoute = Route.SerialisedRoute(), val commandHiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                             val vectorHdg: Short? = null, val vectorTurnDir: Byte? = null, val commandAlt: Int = 0, val clearedIas: Short = 0, // Vector HDG will be null if aircraft is flying route
                             val minIas: Short = 0, val maxIas: Short = 0, val optimalIas: Short = 0,
                             val arrivalArptId: Byte? = null,
                             val controlSectorId: Byte = 0,
                             val gsCap: Boolean = false, val locCap: Boolean = false, val visCap: Boolean = false,
                             val waitingTakeoff: Boolean = false
    )

    /** Gets a [SerialisedAircraft] from current state */
    fun getSerialisableObject(): SerialisedAircraft {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAircraft()
            val altitude = get(Altitude.mapper) ?: return SerialisedAircraft()
            val acInfo = get(AircraftInfo.mapper) ?: return SerialisedAircraft()
            val direction = get(Direction.mapper) ?: return SerialisedAircraft()
            val speed = get(Speed.mapper) ?: return SerialisedAircraft()
            val track = get(GroundTrack.mapper) ?: return SerialisedAircraft()
            val cmdTarget = get(CommandTarget.mapper) ?: return SerialisedAircraft()
            val flightType = get(FlightType.mapper) ?: return SerialisedAircraft()
            val clearance = get(PendingClearances.mapper)?.clearanceQueue?.last()?.clearanceState ?: get(ClearanceAct.mapper)?.actingClearance?.actingClearance ?: return SerialisedAircraft()
            val arrArptId = get(ArrivalAirport.mapper)?.arptId
            val controllable = get(Controllable.mapper) ?: return SerialisedAircraft()
            return SerialisedAircraft(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign, acInfo.icaoType, acInfo.aircraftPerf.maxAlt,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps, track.trackVectorPxps.x, track.trackVectorPxps.y,
                cmdTarget.targetHdgDeg.toInt().toShort(), (cmdTarget.targetAltFt / 100f).roundToInt().toShort(), cmdTarget.targetIasKt,
                flightType.type,
                clearance.routePrimaryName, clearance.route.getSerialisedObject(), clearance.hiddenLegs.getSerialisedObject(),
                clearance.vectorHdg, clearance.vectorTurnDir, clearance.clearedAlt, clearance.clearedIas,
                clearance.minIas, clearance.maxIas, clearance.optimalIas,
                arrArptId,
                controllable.sectorId,
                has(GlideSlopeCaptured.mapper), has(LocalizerCaptured.mapper),
                has(VisualCaptured.mapper) || (get(CirclingApproach.mapper)?.phase ?: 0) >= 1,
                has(WaitingTakeoff.mapper)
            )
        }
    }
}