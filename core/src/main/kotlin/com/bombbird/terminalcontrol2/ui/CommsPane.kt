package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.CONVO_SIZE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.TRANS_ALT
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import ktx.ashley.get
import ktx.scene2d.*
import java.time.LocalTime
import kotlin.math.roundToInt

class CommsPane {
    companion object {
        const val ARRIVAL: Byte = 0
        const val DEPARTURE: Byte = 1
        const val OTHERS: Byte = 2
        const val ALERT: Byte = 3
        const val WARNING: Byte = 4

        /** Gets the appropriate greeting depending on the time set on user device */
        private val greetingByTime: String
            get() {
                val hourNow = LocalTime.now().hour
                var greeting = " good "
                greeting += when {
                    hourNow < 12 -> "morning"
                    hourNow <= 17 -> "afternoon"
                    else -> "evening"
                }
                return greeting
            }
    }

    lateinit var commsTable: KTableWidget
    private lateinit var labelScroll: KScrollPane
    private lateinit var labelTable: KTableWidget
    private val convoLabels = ArrayDeque<Label>(CONVO_SIZE)

    /**
     * @param widget the widget to add the comms pane table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the pane
     * @return a [KTableWidget] used to contain elements of the comms pane
     */
    @Scene2dDsl
    fun commsPane(widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        commsTable = widget.table {
            labelScroll = scrollPane("MenuPane") {
                // debugAll()
                labelTable = table { }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT, grow = true)
        }
        return commsTable
    }

    /**
     * Adds a message with specified type
     * @param msg the message
     * @param msgType the type of the message to add; will determine the display style of the message
     */
    private fun addMessage(msg: String, msgType: Byte) {
        if (convoLabels.size >= CONVO_SIZE) convoLabels.removeFirst().remove()
        val msgStyle = "CommsPane" + when (msgType) {
            ARRIVAL -> "Arrival"
            DEPARTURE -> "Departure"
            OTHERS -> "Others"
            ALERT -> "Alert"
            WARNING -> "Warning"
            else -> {
                Gdx.app.log("CommsPane", "Unknown message type $msgType")
                "Others"
            }
        }
        val commsLabel = Label(msg, Scene2DSkin.defaultSkin[msgStyle, LabelStyle::class.java]).apply { wrap = true }
        labelTable.row()
        labelTable.add(commsLabel).growX().pad(10f, 15f, 10f, 15f).actor.invalidate()
        labelScroll.layout()
        convoLabels.addLast(commsLabel)
        labelScroll.scrollTo(0f, 0f, 0f, 0f)
    }

    /**
     * Adds a message for initial contact by an aircraft
     * @param aircraft the aircraft entity contacting the player
     */
    fun initialContact(aircraft: Entity) {
        val flightType = aircraft[FlightType.mapper] ?: return
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val clearanceState = aircraft[ClearanceAct.mapper]?.actingClearance?.actingClearance ?: return
        val alt = aircraft[Altitude.mapper] ?: return
        val arrivalAirport = aircraft[ArrivalAirport.mapper]

        // Get the callsign of the player
        val thisSectorInfo = GAME.gameClientScreen?.let { it.sectors[it.playerSector.toInt()] }?.entity?.get(SectorInfo.mapper) ?: return
        val yourCallsign = when (flightType.type) {
            FlightType.ARRIVAL, FlightType.EN_ROUTE -> thisSectorInfo.arrivalCallsign
            FlightType.DEPARTURE -> thisSectorInfo.departureCallsign
            else -> {
                Gdx.app.log("CommsPane", "Unknown flight type ${flightType.type}")
                thisSectorInfo.arrivalCallsign
            }
        }

        // Random whether to say "with you" or not
        val withYou = if (MathUtils.randomBoolean(0.3f)) "with you" else ""

        // Get the wake category of the aircraft
        val aircraftWake = when (acInfo.aircraftPerf.wakeCategory) {
            AircraftTypeData.AircraftPerfData.WAKE_SUPER -> "super"
            AircraftTypeData.AircraftPerfData.WAKE_HEAVY -> "heavy"
            else -> ""
        }

        // Get the current altitude, cleared altitude and the respective actions
        val currAltFt = alt.altitudeFt
        val currAltitude = currAltFt.let { currAlt ->
            val roundedFL = (currAlt / 100f).roundToInt()
            if (roundedFL * 100 > TRANS_ALT) "FL$roundedFL"
            else "${roundedFL * 100} feet"
        }

        val clearedAltFt = clearanceState.clearedAlt
        val clearedAlt = clearedAltFt.let { clearedAlt ->
            val roundedFL = (clearedAlt / 100f).roundToInt()
            if (roundedFL * 100 > TRANS_ALT) "FL$roundedFL"
            else "${roundedFL * 100} feet"
        }

        val altDiff = clearedAltFt - currAltFt
        val altitudeAction = when {
            MathUtils.randomBoolean(0.1f) -> ""
            altDiff < -400 -> "descending through $currAltitude for $clearedAlt"
            altDiff > 400 -> "climbing through $currAltitude for $clearedAlt"
            altDiff >= -50 && altDiff <= 50 -> "at $clearedAlt"
            else -> "levelling off at $clearedAlt"
        }

        // Get a random greeting
        val randomGreeting = when (MathUtils.random(2)) {
            0 -> ""
            1 -> "hello"
            else -> greetingByTime
        }

        // Get current inbound heading/direct
        val clearedHdg = if (flightType.type != FlightType.DEPARTURE) clearanceState.vectorHdg else null
        val clearedDirect = if (flightType.type != FlightType.DEPARTURE && clearanceState.route.size > 0) clearanceState.route[0]
        else null
        val inbound = if (clearedHdg != null) ", heading ${if (clearedHdg < 100) "0" else ""}$clearedHdg"
        else if (clearedDirect != null && clearedDirect is Route.WaypointLeg && MathUtils.randomBoolean()) {
            ", inbound ${GAME.gameClientScreen?.waypoints?.get(clearedDirect.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName}"
        } else ""

        // Get current SID/STAR name
        val depArr = if (flightType.type == FlightType.DEPARTURE) "departure" else "arrival"
        val starSid = if (clearedHdg != null) "" else when (MathUtils.random(3)) {
            0 -> "on the ${clearanceState.routePrimaryName} $depArr"
            1 -> ", ${clearanceState.routePrimaryName}"
            2 -> ", ${clearanceState.routePrimaryName} $depArr"
            else -> ""
        }

        // Get current airport ATIS letter
        val atisLetter = GAME.gameClientScreen?.airports?.get(arrivalAirport?.arptId)?.entity?.get(MetarInfo.mapper)?.letterCode
        val atis = if (atisLetter != null && MathUtils.randomBoolean(0.7f)) {
            when (MathUtils.random(3)) {
                0 -> ", information $atisLetter"
                1 -> ", we have $atisLetter"
                2 -> ", we have information $atisLetter"
                else -> " with $atisLetter"
            }
        } else ""

        val preFinalMsg = "$yourCallsign $randomGreeting, ${acInfo.icaoCallsign} $aircraftWake $withYou, $altitudeAction $starSid$inbound$atis"
        addMessage(removeExtraCharacters(preFinalMsg), when (flightType.type) {
            FlightType.ARRIVAL -> ARRIVAL
            FlightType.DEPARTURE -> DEPARTURE
            FlightType.EN_ROUTE -> OTHERS
            else -> {
                Gdx.app.log("CommsPane", "Unknown flight type ${flightType.type}")
                OTHERS
            }
        })
    }

    /**
     * Removes additional spaces, commas, trailing and leading commas and whitespace from the input string and returns the corrected string
     * @param msg the message to correct
     * @return a new message without additional characters
     */
    private fun removeExtraCharacters(msg: String): String {
        var correctMsg = msg
        // First replace any spaces before a comma
        val commaSpaceRegex = " +,".toRegex()
        correctMsg = correctMsg.replace(commaSpaceRegex, ",")

        // Next replace multi-spaces with a single space
        val multiSpaceRegex = " +".toRegex()
        correctMsg = correctMsg.replace(multiSpaceRegex, " ")

        // Next replace multi-commas with a single comma
        val multiCommaRegex = ",+".toRegex()
        correctMsg = correctMsg.replace(multiCommaRegex, ",")

        // Remove trailing commas
        val trailingCommaRegex = ", *\$".toRegex()
        correctMsg = correctMsg.replace(trailingCommaRegex, "")

        // Return the string with leading or trailing whitespaces removed if any
        return correctMsg.trim()
    }
}