package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Queue.QueueIterator
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.datatag.*
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.getAircraftIcon
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.*
import ktx.scene2d.Scene2DSkin
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

/** Aircraft class that creates an aircraft entity with the required components on instantiation */
class Aircraft(callsign: String, posX: Float, posY: Float, alt: Float, icaoAircraftType: String, flightType: Byte,
               onClient: Boolean = true): SerialisableEntity<Aircraft.SerialisedAircraft> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
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
            aircraftPerf = AircraftTypeData.getAircraftPerf(icaoAircraftType, flightType)
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
        with<InitialClientDatatagPosition>()
        with<TrailInfo>()
        with<TTSVoice> {
            voice = GAME.ttsManager.getRandomVoice()
        }
        if (onClient) {
            with<RadarData> {
                position.x = posX
                position.y = posY
            }
            with<Datatag> {
                updateDatatagStyle(this, flightType, false)
                updateDatatagText(this, arrayOf("Test line 1", "Test line 2", "", "Test line 3"))
                xOffset = -imgButton.width / 2
                yOffset = 13f
            }
            with<RSSprite> {
                drawable = TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion(when (flightType) {
                    FlightType.ARRIVAL, FlightType.EN_ROUTE -> "aircraftEnroute"
                    FlightType.DEPARTURE -> "aircraftTower"
                    else -> {
                        FileLog.info("Aircraft", "Unknown flight type $flightType")
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
            with<RouteSegment>()
            with<TTSVoice> {
                voice = getServerElseRandomVoice(callsign)
                setVoiceOnServerIfHost(callsign, voice)
            }
        } else {
            with<ConflictAble>()
            with<WakeTrail>()
            if (flightType == FlightType.ARRIVAL) with<ArrivalRouteZone>()
            else if (flightType == FlightType.DEPARTURE) with<DepartureRouteZone>()
        }
        with<Controllable> {
            sectorId = when (flightType) {
                FlightType.DEPARTURE -> SectorInfo.TOWER
                FlightType.ARRIVAL, FlightType.EN_ROUTE -> SectorInfo.CENTRE
                else -> {
                    FileLog.info("Aircraft", "Unknown flight type $flightType")
                    SectorInfo.CENTRE
                }
            }
        }
    }

    /** Empty aircraft constructor in case of missing information */
    constructor(): this("EMPTY", 0f, 0f, 0f, "B77W", FlightType.ARRIVAL)

    /**
     * Gets the server-side voice if the client is also the host, or a random voice if the client is not the host
     * @param callsign the callsign of the aircraft
     */
    private fun getServerElseRandomVoice(callsign: String): String? {
        val serverVoice = GAME.gameServer?.aircraft?.get(callsign)?.entity?.get(TTSVoice.mapper)?.voice
            ?: return GAME.ttsManager.getRandomVoice()
        return GAME.ttsManager.checkVoiceElseRandomVoice(serverVoice) ?: GAME.ttsManager.getRandomVoice()
    }

    /**
     * Updates the voice stored on server-side if client is also the host
     * @param callsign the callsign of the aircraft
     * @param voice the voice to set
     */
    private fun setVoiceOnServerIfHost(callsign: String, voice: String?) {
        GAME.gameServer?.let {
            it.aircraft[callsign]?.entity?.get(TTSVoice.mapper)?.voice = voice
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
            ).also {
                it.entity.apply {
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
                    this += ClearanceAct(ClearanceState(serialisedAircraft.routePrimaryName,
                        Route.fromSerialisedObject(serialisedAircraft.commandRoute), Route.fromSerialisedObject(serialisedAircraft.commandHiddenLegs),
                        serialisedAircraft.vectorHdg, serialisedAircraft.vectorTurnDir,
                        serialisedAircraft.commandAlt, serialisedAircraft.expedite, serialisedAircraft.clearedIas,
                        serialisedAircraft.minIas, serialisedAircraft.maxIas, serialisedAircraft.optimalIas,
                    ).ActingClearance())
                    val arrAirportId = serialisedAircraft.arrivalArptId?.also { arrId -> this += ArrivalAirport(arrId) }
                    serialisedAircraft.departureArptId?.let { depId -> this += DepartureAirport(depId, 0) }
                    val controllable = get(Controllable.mapper)?.apply {
                        sectorId = serialisedAircraft.controlSectorId
                        controllerUUID = serialisedAircraft.controllerUUID?.let { controlId -> UUID.fromString(controlId) }
                    }
                    get(RSSprite.mapper)?.drawable = getAircraftIcon(serialisedAircraft.flightType, serialisedAircraft.controlSectorId)
                    if (serialisedAircraft.gsCap) this += GlideSlopeCaptured()
                    if (serialisedAircraft.locCap) this += LocalizerCaptured()
                    if (serialisedAircraft.visCapRwy != null) {
                        val visApp = CLIENT_SCREEN?.airports?.get(arrAirportId)?.entity?.get(RunwayChildren.mapper)
                            ?.rwyMap?.get(serialisedAircraft.visCapRwy)?.entity?.get(VisualApproach.mapper)?.visual
                        if (visApp != null) this += VisualCaptured(visApp)
                    }
                    if (serialisedAircraft.contactToCentre) this += ContactToCentre()
                    if (serialisedAircraft.recentGoAround) this += RecentGoAround()

                    val datatag = get(Datatag.mapper)
                    if (serialisedAircraft.waitingTakeoff) this += WaitingTakeoff()
                    else if (datatag != null) addDatatagInputListeners(datatag, it)
                    datatag?.let { tag ->
                        tag.minimised = controllable?.sectorId != CLIENT_SCREEN?.playerSector || serialisedAircraft.initialDatatagMinimised
                        tag.emergency = serialisedAircraft.isActiveEmergency
                        updateDatatagStyle(tag, serialisedAircraft.flightType, false)
                        updateDatatagText(tag, getNewDatatagLabelText(this, tag.minimised))
                        updateDatatagLabelSize(tag, true)
                        if (serialisedAircraft.initialDatatagFlashing &&
                            controllable?.sectorId == GAME.gameClientScreen?.playerSector)
                            setDatatagFlash(tag, it, true)

                        tag.xOffset = serialisedAircraft.initialDatatagXOffset
                        tag.yOffset = serialisedAircraft.initialDatatagYOffset
                    }

                    get(TrailInfo.mapper)?.apply {
                        val trailX = serialisedAircraft.trailX
                        val trailY = serialisedAircraft.trailY
                        for (i in trailX.indices) positions.addLast(Position(trailX[i], trailY[i]))
                    }

                    if (serialisedAircraft.isActiveEmergency) this += EmergencyPending(true, 0, 0)
                    if (serialisedAircraft.isEmergencyReadyForApproach) this += ReadyForApproachClient()
                }
            }
        }
    }

    /**
     * Object that contains select [Aircraft] data to be sent over UDP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     */
    class SerialisedAircraftUDP(val x: Float = 0f, val y: Float = 0f,
                                val altitude: Float = 0f,
                                val icaoCallsign: String = "",
                                val directionX: Float = 0f, val directionY: Float = 0f,
                                val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                                val trackX: Float = 0f, val trackY: Float = 0f,
                                val targetHdgDeg: Short = 0, val targetAltFt: Short = 0, val targetIasKt: Short = 0,
                                val gsCap: Boolean = false, val locCap: Boolean = false, val visCapRwy: Byte? = null,
                                val contactToCentre: Boolean = false,
                                val recentGoAroundReason: Byte? = null
    )

    /**
     * Returns a default empty [SerialisedAircraftUDP] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    private fun emptySerialisableUDPObject(missingComponent: String): SerialisedAircraftUDP {
        FileLog.info("Aircraft", "Empty serialised UDP aircraft returned due to missing $missingComponent component")
        return SerialisedAircraftUDP()
    }

    /** Gets a [SerialisedAircraftUDP] from current state */
    fun getSerialisableObjectUDP(): SerialisedAircraftUDP {
        entity.apply {
            val position = get(Position.mapper) ?: return emptySerialisableUDPObject("Position")
            val altitude = get(Altitude.mapper) ?: return emptySerialisableUDPObject("Altitude")
            val acInfo = get(AircraftInfo.mapper) ?: return emptySerialisableUDPObject("AircraftInfo")
            val direction = get(Direction.mapper) ?: return emptySerialisableUDPObject("Direction")
            val speed = get(Speed.mapper) ?: return emptySerialisableUDPObject("Speed")
            val gs = get(GroundTrack.mapper) ?: return emptySerialisableUDPObject("GroundTrack")
            val cmdTarget = get(CommandTarget.mapper) ?: return emptySerialisableUDPObject("CommandTarget")
            return SerialisedAircraftUDP(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps, gs.trackVectorPxps.x, gs.trackVectorPxps.y,
                cmdTarget.targetHdgDeg.toInt().toShort(), (cmdTarget.targetAltFt / 100f).roundToInt().toShort(), cmdTarget.targetIasKt,
                has(GlideSlopeCaptured.mapper), has(LocalizerCaptured.mapper),
                get(VisualCaptured.mapper)?.visApp?.get(ApproachInfo.mapper)?.rwyId ?: (get(CirclingApproach.mapper)?.let { app ->
                    if (app.phase >= 1) app.circlingApp[ApproachInfo.mapper]?.rwyId
                    else null
                }),
                has(ContactToCentre.mapper),
                get(RecentGoAround.mapper)?.reason
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
            if (data.visCapRwy != null) {
                val visApp = CLIENT_SCREEN?.airports?.get(get(ArrivalAirport.mapper)?.arptId)?.entity?.get(RunwayChildren.mapper)
                    ?.rwyMap?.get(data.visCapRwy)?.entity?.get(VisualApproach.mapper)?.visual
                if (visApp != null) this += VisualCaptured(visApp)
            }
            else remove<VisualCaptured>()
            if (data.contactToCentre) this += ContactToCentre()
            else remove<ContactToCentre>()
            if (data.recentGoAroundReason != null) {
                if (!has(RecentGoAround.mapper)) {
                    this += RecentGoAround(reason = data.recentGoAroundReason)
                    val sectorId = get(Controllable.mapper)?.sectorId
                    if (sectorId != null && sectorId == GAME.gameClientScreen?.playerSector) {
                        // Missed approach message if the aircraft is in the player's sector
                        GAME.gameClientScreen?.uiPane?.commsPane?.missedApproach(this)
                    }
                }
            }
            else remove<RecentGoAround>()
        }
    }

    /**
     * Object that contains select [Aircraft] data to be sent over TCP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     */
    class SerialisedAircraft(val x: Float = 0f, val y: Float = 0f,
                             val altitude: Float = 0f,
                             val icaoCallsign: String = "", val icaoType: String = "", val maxAlt: Int = 0,
                             val directionX: Float = 0f, val directionY: Float = 0f,
                             val speedKts: Float = 0f, val vertSpdFpm: Float = 0f, val angularSpdDps: Float = 0f,
                             val trackX: Float = 0f, val trackY: Float = 0f,
                             val targetHdgDeg: Short = 0, val targetAltFt: Short = 0, val targetIasKt: Short = 0,
                             val flightType: Byte = 0,
                             val isActiveEmergency: Boolean = false, val isEmergencyReadyForApproach: Boolean = false,
                             val routePrimaryName: String = "", val commandRoute: Route.SerialisedRoute = Route.SerialisedRoute(), val commandHiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                             val vectorHdg: Short? = null, val vectorTurnDir: Byte? = null, // Vector HDG will be null if aircraft is flying route
                             val commandAlt: Int = 0, val expedite: Boolean = false, val clearedIas: Short = 0,
                             val minIas: Short = 0, val maxIas: Short = 0, val optimalIas: Short = 0,
                             val arrivalArptId: Byte? = null, val departureArptId: Byte? = null,
                             val controlSectorId: Byte = 0, val controllerUUID: String? = null,
                             val gsCap: Boolean = false, val locCap: Boolean = false, val visCapRwy: Byte? = null,
                             val waitingTakeoff: Boolean = false,
                             val contactToCentre: Boolean = false,
                             val recentGoAround: Boolean = false,
                             val initialDatatagXOffset: Float = 0f, val initialDatatagYOffset: Float = 0f,
                             val initialDatatagMinimised: Boolean = false, val initialDatatagFlashing: Boolean = false,
                             val trailX: FloatArray = floatArrayOf(), val trailY: FloatArray = floatArrayOf()
    )

    /**
     * Returns a default empty [SerialisedAircraft] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedAircraft {
        FileLog.info("Aircraft", "Empty serialised aircraft returned due to missing $missingComponent component")
        return SerialisedAircraft()
    }

    /** Gets a [SerialisedAircraft] from current state */
    override fun getSerialisableObject(): SerialisedAircraft {
        entity.apply {
            val position = get(Position.mapper) ?: return emptySerialisableObject("Position")
            val altitude = get(Altitude.mapper) ?: return emptySerialisableObject("Altitude")
            val acInfo = get(AircraftInfo.mapper) ?: return emptySerialisableObject("AircraftInfo")
            val direction = get(Direction.mapper) ?: return emptySerialisableObject("Direction")
            val speed = get(Speed.mapper) ?: return emptySerialisableObject("Speed")
            val track = get(GroundTrack.mapper) ?: return emptySerialisableObject("GroundTrack")
            val cmdTarget = get(CommandTarget.mapper) ?: return emptySerialisableObject("CommandTarget")
            val flightType = get(FlightType.mapper) ?: return emptySerialisableObject("FlightType")
            val clearance = get(PendingClearances.mapper)?.clearanceQueue?.last()?.clearanceState ?:
            get(ClearanceAct.mapper)?.actingClearance?.clearanceState ?:
            return emptySerialisableObject("PendingClearances")
            val arrArptId = get(ArrivalAirport.mapper)?.arptId
            val depArptId = get(DepartureAirport.mapper)?.arptId
            val controllable = get(Controllable.mapper) ?: return emptySerialisableObject("Controllable")
            val initialDatatagPosition = get(InitialClientDatatagPosition.mapper) ?:
            return emptySerialisableObject("InitialClientDatatagPosition")
            val trails = get(TrailInfo.mapper) ?: return emptySerialisableObject("TrailInfo")
            val trailArray = ArrayList<Position>()
            for (trail in QueueIterator(trails.positions)) trailArray.add(trail)
            val isActiveEmergency = get(EmergencyPending.mapper)?.active == true
            val isReadyForApproach = isActiveEmergency && hasNot(RunningChecklists.mapper) && hasNot(RequiresFuelDump.mapper)
            return SerialisedAircraft(
                position.x, position.y,
                altitude.altitudeFt,
                acInfo.icaoCallsign, acInfo.icaoType, acInfo.aircraftPerf.maxAlt,
                direction.trackUnitVector.x, direction.trackUnitVector.y,
                speed.speedKts, speed.vertSpdFpm, speed.angularSpdDps, track.trackVectorPxps.x, track.trackVectorPxps.y,
                cmdTarget.targetHdgDeg.toInt().toShort(), (cmdTarget.targetAltFt / 100f).roundToInt().toShort(), cmdTarget.targetIasKt,
                flightType.type, isActiveEmergency, isReadyForApproach,
                clearance.routePrimaryName, clearance.route.getSerialisedObject(), clearance.hiddenLegs.getSerialisedObject(),
                clearance.vectorHdg, clearance.vectorTurnDir, clearance.clearedAlt, has(CommandExpedite.mapper), clearance.clearedIas,
                clearance.minIas, clearance.maxIas, clearance.optimalIas,
                arrArptId, depArptId,
                controllable.sectorId, controllable.controllerUUID?.toString(),
                has(GlideSlopeCaptured.mapper), has(LocalizerCaptured.mapper),
                get(VisualCaptured.mapper)?.visApp?.get(ApproachInfo.mapper)?.rwyId ?: (get(CirclingApproach.mapper)?.let { app ->
                    if (app.phase >= 1) app.circlingApp[ApproachInfo.mapper]?.rwyId
                    else null
                }),
                has(WaitingTakeoff.mapper),
                has(ContactToCentre.mapper),
                has(RecentGoAround.mapper),
                initialDatatagPosition.xOffset, initialDatatagPosition.yOffset,
                initialDatatagPosition.minimised, initialDatatagPosition.flashing,
                trailArray.map { it.x }.toFloatArray(), trailArray.map { it.y }.toFloatArray()
            )
        }
    }
}