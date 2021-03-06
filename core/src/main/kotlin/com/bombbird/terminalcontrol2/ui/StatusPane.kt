package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Family
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.ConflictManager
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.ashley.has
import ktx.scene2d.*
import kotlin.math.ceil

/** Status pane class to display ongoing events, notifications and other information */
class StatusPane {

    companion object {
        const val WARNING: Byte = 0
        const val ALERT: Byte = 1
        const val NOTIFICATION_ARRIVAL: Byte = 2
        const val NOTIFICATION_DEPARTURE: Byte = 3
        const val INFO: Byte = 4
    }

    lateinit var statusTable: KTableWidget
    private lateinit var statusScroll: KScrollPane
    private lateinit var statusMsgTable: KTableWidget

    private val goAroundContactFamily: Family = allOf(RecentGoAround::class, ContactNotification::class).get()
    private val initialContactFamily: Family = allOf(ContactNotification::class).exclude(RecentGoAround::class).get()

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
     * */
    fun refreshStatusMessages() {
        statusMsgTable.clear()
        getConflictMessages()
        getEmergencyMessages()
        getMissedApproachMessages()
        getPendingRunwayChangeMessages()
        getInitialContactMessages()
        getAircraftRequestMessages()
        getTrafficInfoMessages()
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
                Gdx.app.log("StatusPane", "Unknown message type $msgType")
                "Info"
            }
        }
        val statusLabel = Label(msg, Scene2DSkin.defaultSkin[msgStyle, Label.LabelStyle::class.java]).apply { wrap = true }
        statusMsgTable.row()
        statusMsgTable.add(statusLabel).growX().pad(5f, 15f, 5f, 15f).actor.invalidate()
        statusScroll.layout()
    }

    /** Gets ongoing conflicts and adds them as messages to the status pane */
    private fun getConflictMessages() {
        CLIENT_SCREEN?.also {
            for (i in 0 until it.conflicts.size) {
                it.conflicts[i]?.apply {
                    val name1 = entity1[AircraftInfo.mapper]?.icaoCallsign ?: return@apply
                    val name2 = entity2?.get(AircraftInfo.mapper)?.icaoCallsign
                    val message = "$name1${if (name2 != null) ", $name2" else ""}: ${when (reason) {
                        ConflictManager.Conflict.NORMAL_CONFLICT -> "${MIN_SEP}nm, ${VERT_SEP}ft infringement"
                        ConflictManager.Conflict.SAME_APP_LESS_THAN_10NM ->"2.5nm, ${VERT_SEP}ft infringement - both aircraft less than 10nm final on same approach"
                        ConflictManager.Conflict.PARALLEL_DEP_APP -> "2nm, ${VERT_SEP}ft infringement - aircraft on dependent parallel approach"
                        ConflictManager.Conflict.PARALLEL_INDEP_APP_NTZ -> "Simultaneous approach NTZ infringement"
                        ConflictManager.Conflict.MVA -> "MVA sector infringement"
                        ConflictManager.Conflict.SID_STAR_MVA -> "MVA sector infringement - aircraft deviates from route or is below minimum route altitude restriction"
                        ConflictManager.Conflict.RESTRICTED -> "Restricted area infringement"
                        ConflictManager.Conflict.WAKE_INFRINGE -> "Wake separation infringement"
                        ConflictManager.Conflict.STORM -> "Flying in bad weather"
                        else -> {
                            Gdx.app.log("StatusPane", "Unknown conflict reason $reason")
                            "???"
                        }
                    }}"
                    addMessage(message, WARNING)
                }
            }
        }
    }

    /** Gets ongoing emergencies and adds their status as messages to the status pane */
    private fun getEmergencyMessages() {
        // TODO To be added after implementing emergency aircraft
    }

    /**
     * Gets recent go-arounds that have contacted the player (either through a handover from tower, or directly informed
     * if still under the player's control) and adds them as messages to the status pane
     * */
    private fun getMissedApproachMessages() {
        val goAroundContacts = getEngine(true).getEntitiesFor(goAroundContactFamily)
        for (i in 0 until goAroundContacts.size()) {
            goAroundContacts[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                addMessage("$callsign: Missed approach", ALERT)
            }
        }
    }

    /** Gets pending runway changes for each airport and adds them as messages to the status pane */
    private fun getPendingRunwayChangeMessages() {
        CLIENT_SCREEN?.airports?.values()?.forEach {
            val icao = it.entity[AirportInfo.mapper]?.icaoCode ?: return@forEach
            it.entity[PendingRunwayConfig.mapper]?.apply {
                val minLeft = ceil(timeRemaining / 60)
                val timeLeft = if (minLeft > 0) "$minLeft min${if (minLeft > 1) "s" else ""} left"
                else "${timeRemaining}s left"
                addMessage("$icao: Pending runway change, $timeLeft", ALERT)
            }
        }
    }

    /** Gets initial aircraft contacts and adds them as messages to the status pane */
    private fun getInitialContactMessages() {
        val initialContacts = getEngine(true).getEntitiesFor(initialContactFamily)
        for (i in 0 until initialContacts.size()) {
            initialContacts[i]?.apply {
                val callsign = get(AircraftInfo.mapper)?.icaoCallsign ?: return@apply
                val flightType = get(FlightType.mapper)?.type ?: return@apply
                addMessage("${callsign}: Initial contact", when (flightType) {
                    FlightType.ARRIVAL, FlightType.EN_ROUTE -> NOTIFICATION_ARRIVAL
                    FlightType.DEPARTURE -> NOTIFICATION_DEPARTURE
                    else -> {
                        Gdx.app.log("StatusPane", "Unknown flight type $flightType")
                        NOTIFICATION_ARRIVAL
                    }
                })
            }
        }
    }

    /** Gets aircraft requests and adds them as messages to the status pane */
    private fun getAircraftRequestMessages() {
        // TODO To be added after implementing aircraft requests
    }

    /** Gets traffic info for the game world and airports and adds them as messages to the status pane */
    private fun getTrafficInfoMessages() {
        CLIENT_SCREEN?.apply {
            val trafficMode = when (serverTrafficMode) {
                TrafficMode.NORMAL -> "Normal"
                TrafficMode.ARRIVALS_TO_CONTROL -> "Arrivals to control - $serverTrafficValue"
                TrafficMode.FLOW_RATE -> "Arrival flow rate - ${serverTrafficValue}/hr"
                else -> {
                    Gdx.app.log("StatusPane", "Unknown server traffic mode $serverTrafficMode")
                    "Unknown"
                }
            }
            addMessage("Traffic mode: $trafficMode", INFO)

            airports.values().forEach {
                val icao = it.entity[AirportInfo.mapper]?.icaoCode ?: return@forEach
                val arrClosed = it.entity.has(ArrivalClosed.mapper)
                val depClosed = it.entity[DepartureInfo.mapper]?.closed == true
                if (arrClosed || depClosed) addMessage("$icao: Closed${if (arrClosed && depClosed) ""
                else if (arrClosed) " for arrivals" else " for departures"}", INFO)
            }
        }
    }
}