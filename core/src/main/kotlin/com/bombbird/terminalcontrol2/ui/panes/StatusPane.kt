package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.settings.TrafficSettings
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.traffic.conflict.Conflict
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import com.bombbird.terminalcontrol2.utilities.AircraftRequest
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.ashley.has
import ktx.scene2d.*
import java.util.*
import kotlin.math.ceil

/** Status pane class to display ongoing events, notifications and other information */
class StatusPane {

    companion object {
        const val WARNING: Byte = 0
        const val ALERT: Byte = 1
        const val NOTIFICATION_ARRIVAL: Byte = 2
        const val NOTIFICATION_DEPARTURE: Byte = 3
        const val INFO: Byte = 4

        private val goAroundContactFamily = allOf(RecentGoAround::class, ContactNotification::class).get()
        private val pointOutFamily = allOf(AircraftPointOutNotification::class, Controllable::class).get()
        private val coordinationRequestFamily = allOf(AircraftHandoverCoordinationRequest::class, Controllable::class).get()
        private val initialContactFamily = allOf(ContactNotification::class).exclude(RecentGoAround::class).get()
        private val emergencyStartedFamily = allOf(EmergencyPending::class, Speed::class).get()
        private val aircraftRequestFamily = allOf(AircraftRequestNotification::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    lateinit var statusTable: KTableWidget
    private lateinit var statusScroll: KScrollPane
    private lateinit var statusMsgTable: KTableWidget

    /**
     * @param widget the widget to add the status pane table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the pane
     * @return a [KTableWidget] used to contain elements of the status pane
     */
    @Scene2dDsl
    fun statusPane(widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        statusTable = widget.table {
            statusScroll = scrollPane("MenuPane") {
                statusMsgTable = table { }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT, grow = true)
        }
        return statusTable
    }

    /**
     * Refreshes the status messages based on the current state of the game; all existing messages will be cleared and
     * replaced with new ones
     */
    fun refreshStatusMessages() {
        statusMsgTable.clear()
        addConflictMessages()
        addEmergencyMessages()
        addMissedApproachMessages()
        addCoordinationMessages()
        addPendingRunwayChangeMessages()
        addInitialContactMessages()
        addAircraftRequestMessages()
        addTrafficInfoMessages()
    }

    /**
     * Adds a message with specified type
     * @param msg the message
     * @param msgType the type of the message to add; will determine the display style of the message
     */
    private fun addMessage(msg: String, msgType: Byte) {
        val msgStyle = "StatusPane" + when (msgType) {
            NOTIFICATION_ARRIVAL -> "Arrival"
            NOTIFICATION_DEPARTURE -> "Departure"
            INFO -> "Info"
            ALERT -> "Alert"
            WARNING -> "Warning"
            else -> {
                FileLog.info("StatusPane", "Unknown message type $msgType")
                "Info"
            }
        }
        val statusLabel = Label(msg, Scene2DSkin.defaultSkin[msgStyle, Label.LabelStyle::class.java]).apply { wrap = true }
        statusMsgTable.row()
        statusMsgTable.add(statusLabel).growX().pad(5f, 15f, 5f, 15f).actor.invalidate()
        statusScroll.layout()
    }

    /** Gets ongoing conflicts and adds them as messages to the status pane */
    private fun addConflictMessages() {
        CLIENT_SCREEN?.also {
            for (i in 0 until it.conflicts.size) {
                it.conflicts[i]?.apply {
                    val name1 = entity1[AircraftInfo.mapper]?.icaoCallsign ?: return@apply
                    val name2 = entity2?.get(AircraftInfo.mapper)?.icaoCallsign
                    val message = "$name1${if (name2 != null) ", $name2" else ""}: ${when (reason) {
                        Conflict.NORMAL_CONFLICT -> "${MIN_SEP}nm, ${VERT_SEP}ft infringement"
                        Conflict.SAME_APP_LESS_THAN_10NM ->"2.5nm, ${VERT_SEP}ft infringement - both aircraft less than 10nm final on same approach"
                        Conflict.PARALLEL_DEP_APP -> "${latSepRequiredNm}nm, ${VERT_SEP}ft infringement - aircraft on dependent parallel approach"
                        Conflict.PARALLEL_INDEP_APP_NTZ -> "Simultaneous approach NTZ infringement"
                        Conflict.MVA -> "MVA sector infringement"
                        Conflict.SID_STAR_MVA -> "MVA sector infringement - aircraft deviates from route or is below minimum route altitude restriction"
                        Conflict.RESTRICTED -> "Restricted area infringement"
                        Conflict.WAKE_INFRINGE -> "Wake separation infringement"
                        Conflict.STORM -> "Flying in bad weather"
                        Conflict.EMERGENCY_SEPARATION_CONFLICT -> "${MIN_SEP}nm, ${VERT_SEP / 2}ft infringement - emergency separation"
                        else -> {
                            FileLog.info("StatusPane", "Unknown conflict reason $reason")
                            "???"
                        }
                    }}"
                    addMessage(message, WARNING)
                }
            }
        }
    }

    /** Gets ongoing emergencies and adds their status as messages to the status pane */
    private fun addEmergencyMessages() {
        val emergencyStarted = getEngine(true).getEntitiesFor(emergencyStartedFamily)
        for (i in 0 until emergencyStarted.size()) {
            emergencyStarted[i]?.apply {
                val emergency = get(EmergencyPending.mapper) ?: return@apply
                val speed = get(Speed.mapper) ?: return@apply
                if (!emergency.active) return@apply
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                val emergencyTypeString = when (val emergencyType = emergency.type) {
                    EmergencyPending.BIRD_STRIKE -> "Bird strike"
                    EmergencyPending.ENGINE_FAIL -> "Engine failure"
                    EmergencyPending.HYDRAULIC_FAIL -> "Hydraulic failure"
                    EmergencyPending.FUEL_LEAK -> "Fuel leak"
                    EmergencyPending.MEDICAL -> "Medical emergency"
                    EmergencyPending.PRESSURE_LOSS -> "Loss of pressure"
                    else -> {
                        FileLog.info("StatusPane", "Unknown emergency type $emergencyType")
                        "???"
                    }
                }
                val statusString = when {
                    speed.speedKts < 60 && has(ImmobilizeOnLanding.mapper) -> ", landed, staying on runway"
                    speed.speedKts < 60 -> ", landed"
                    has(ReadyForApproachClient.mapper) && has(ImmobilizeOnLanding.mapper) -> ", ready for approach, will stay on runway"
                    has(ReadyForApproachClient.mapper) -> ", ready for approach"
                    get(RequiresFuelDump.mapper)?.active == true -> ", dumping fuel"
                    get(RequiresFuelDump.mapper)?.active == false -> ", running checklists, requires fuel dump"
                    else -> ", running checklists"
                }
                addMessage("$callsign: $emergencyTypeString$statusString", WARNING)
            }
        }
    }

    /**
     * Gets recent go-arounds that have contacted the player (either through a handover from tower, or directly informed
     * if still under the player's control) and adds them as messages to the status pane
     */
    private fun addMissedApproachMessages() {
        val goAroundContacts = getEngine(true).getEntitiesFor(goAroundContactFamily)
        for (i in 0 until goAroundContacts.size()) {
            goAroundContacts[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                addMessage("$callsign: Missed approach", ALERT)
            }
        }
    }

    /** Gets point out and coordination requests and adds them as messages to the status pane */
    private fun addCoordinationMessages() {
        val pointOuts = getEngine(true).getEntitiesFor(pointOutFamily)
        for (i in 0 until pointOuts.size()) {
            pointOuts[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                if (controllable.sectorId != CLIENT_SCREEN?.playerSector) return@apply

                addMessage("$callsign: Point out", ALERT)
            }
        }

        val coordinationRequests = getEngine(true).getEntitiesFor(coordinationRequestFamily)
        for (i in 0 until coordinationRequests.size()) {
            coordinationRequests[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                if (controllable.sectorId != CLIENT_SCREEN?.playerSector) return@apply
                val request = get(AircraftHandoverCoordinationRequest.mapper) ?: return@apply

                addMessage("$callsign: Coordination request from Sector ${request.requestingSectorId + 1}", ALERT)
            }
        }
    }

    /** Gets pending runway changes for each airport and adds them as messages to the status pane */
    private fun addPendingRunwayChangeMessages() {
        val airportEntries = Entries(CLIENT_SCREEN?.airports ?: return)
        airportEntries.forEach {
            val arpt = it.value
            val icao = arpt.entity[AirportInfo.mapper]?.icaoCode ?: return@forEach
            arpt.entity[PendingRunwayConfig.mapper]?.apply {
                val minLeft = ceil(timeRemaining / 60)
                val timeLeft = if (minLeft > 0) "$minLeft min${if (minLeft > 1) "s" else ""} left"
                else "${timeRemaining}s left"
                addMessage("$icao: Pending runway change, $timeLeft", ALERT)
            }
        }
    }

    /** Gets initial aircraft contacts and adds them as messages to the status pane */
    private fun addInitialContactMessages() {
        val initialContacts = getEngine(true).getEntitiesFor(initialContactFamily)
        for (i in 0 until initialContacts.size()) {
            initialContacts[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                val flightType = get(FlightType.mapper)?.type ?: return@apply
                addMessage("$callsign: Initial contact", when (flightType) {
                    FlightType.ARRIVAL, FlightType.EN_ROUTE -> NOTIFICATION_ARRIVAL
                    FlightType.DEPARTURE -> NOTIFICATION_DEPARTURE
                    else -> {
                        FileLog.info("StatusPane", "Unknown flight type $flightType")
                        NOTIFICATION_ARRIVAL
                    }
                })
            }
        }
    }

    /** Gets aircraft requests and adds them as messages to the status pane */
    private fun addAircraftRequestMessages() {
        val aircraftRequests = getEngine(true).getEntitiesFor(aircraftRequestFamily)
        for (i in 0 until aircraftRequests.size()) {
            aircraftRequests[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                val flightType = get(FlightType.mapper)?.type ?: return@apply
                val requestTypes = get(AircraftRequestNotification.mapper)?.requestTypes ?: return@apply
                if (requestTypes.isEmpty) return@apply
                val requestString = requestTypes.joinToString(", ") {
                    if (it == null) return@joinToString ""
                    "request " + when (it) {
                        AircraftRequest.RequestType.NONE -> "your sanity"
                        AircraftRequest.RequestType.DIRECT -> "direct"
                        AircraftRequest.RequestType.FURTHER_CLIMB -> "further climb"
                        AircraftRequest.RequestType.HIGH_SPEED_CLIMB -> "high speed climb"
                        AircraftRequest.RequestType.WEATHER_AVOIDANCE -> "weather avoidance"
                        AircraftRequest.RequestType.CANCEL_APPROACH_WEATHER -> "to cancel approach due to weather"
                        AircraftRequest.RequestType.CANCEL_APPROACH_MECHANICAL -> "to cancel approach due to mechanical issues"
                    }
                }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
                addMessage("$callsign: $requestString", when (flightType) {
                    FlightType.ARRIVAL, FlightType.EN_ROUTE -> NOTIFICATION_ARRIVAL
                    FlightType.DEPARTURE -> NOTIFICATION_DEPARTURE
                    else -> {
                        FileLog.info("StatusPane", "Unknown flight type $flightType")
                        NOTIFICATION_ARRIVAL
                    }
                })
            }
        }
    }

    /** Gets traffic info for the game world and airports and adds them as messages to the status pane */
    private fun addTrafficInfoMessages() {
        CLIENT_SCREEN?.apply {
            val trafficMode = when (serverTrafficMode) {
                TrafficMode.NORMAL -> TrafficSettings.NORMAL
                TrafficMode.ARRIVALS_TO_CONTROL -> TrafficSettings.ARRIVALS_TO_CONTROL
                TrafficMode.FLOW_RATE -> TrafficSettings.ARRIVAL_FLOW_RATE
                else -> {
                    FileLog.info("StatusPane", "Unknown server traffic mode $serverTrafficMode")
                    "Unknown"
                }
            }
            addMessage("Traffic mode: $trafficMode", INFO)
            Entries(airports).forEach {
                val arpt = it.value
                val icao = arpt.entity[AirportInfo.mapper]?.icaoCode ?: return@forEach
                val arrClosed = arpt.entity.has(ArrivalClosed.mapper)
                val depClosed = arpt.entity[DepartureInfo.mapper]?.closed == true
                if (arrClosed || depClosed) {
                    addMessage(
                        "$icao: Closed${if (arrClosed && depClosed) "" else if (arrClosed) " for arrivals" else " for departures"}",
                        INFO
                    )
                }
                if (!arrClosed && serverTrafficMode != TrafficMode.NORMAL) {
                    val arrivalStats = arpt.entity[AirportArrivalStats.mapper] ?: return@forEach
                    addMessage("$icao: ${arrivalStats.targetTrafficValue}${
                        if (serverTrafficMode == TrafficMode.FLOW_RATE) "/hr"
                        else " arrival${if (arrivalStats.targetTrafficValue != 1) "s" else ""}"
                    }", INFO)
                }
            }
        }
    }
}