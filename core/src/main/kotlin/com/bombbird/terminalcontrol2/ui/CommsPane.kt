package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.utils.Timer
import com.badlogic.gdx.utils.Timer.Task
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.SectorContactable
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.getACCSectorForPosition
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.scene2d.*
import java.time.LocalTime
import kotlin.math.roundToInt

/** Communications pane class to display all "communications" between player and aircraft */
class CommsPane {
    companion object {
        const val ARRIVAL: Byte = 0
        const val DEPARTURE: Byte = 1
        const val OTHERS: Byte = 2
        const val ALERT: Byte = 3
        const val ALERT_NO_SOUND: Byte = 4
        const val WARNING: Byte = 5

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
    fun addMessage(msg: String, msgType: Byte) {
        if (convoLabels.size >= CONVO_SIZE) convoLabels.removeFirst().remove()
        val msgStyle = "CommsPane" + when (msgType) {
            ARRIVAL -> "Arrival"
            DEPARTURE -> "Departure"
            OTHERS -> "Others"
            ALERT -> {
                GAME.soundManager.playAlert()
                "Alert"
            }
            ALERT_NO_SOUND -> {
                "Alert"
            }
            WARNING -> {
                GAME.soundManager.playWarning()
                "Warning"
            }
            else -> {
                Log.info("CommsPane", "Unknown message type $msgType")
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
        val clearanceState = aircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val alt = aircraft[Altitude.mapper] ?: return
        val arrivalAirport = aircraft[ArrivalAirport.mapper]

        // Get the callsign of the player
        val thisSector = CLIENT_SCREEN?.let {
            // If the player has not received the initial sector data, schedule the contact for 0.5s later
            if (it.sectors.isEmpty) {
                Timer.schedule(object: Task() {
                    override fun run() {
                        initialContact(aircraft)
                    }
                }, 0.5f)
                return
            }
            it.sectors[it.playerSector.toInt()]
        } ?: return

        val yourCallsign = thisSector.getControllerCallsignFrequency(flightType.type).callsign

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        // Get the current altitude, cleared altitude and the respective actions
        val altitudeAction = getAltitudePhraseology(alt.altitudeFt, clearanceState.clearedAlt)

        // Get a random greeting
        val randomGreeting = getRandomGreeting()

        // Get current inbound heading/direct
        val clearedHdg = if (flightType.type != FlightType.DEPARTURE) clearanceState.vectorHdg else null
        val clearedDirect = if (flightType.type != FlightType.DEPARTURE && clearanceState.route.size > 0) clearanceState.route[0]
        else null
        val inbound = if (clearedHdg != null) ", heading ${if (clearedHdg < 100) "0" else ""}$clearedHdg"
        else if (clearedDirect != null && clearedDirect is Route.WaypointLeg && MathUtils.randomBoolean()) {
            ", inbound ${CLIENT_SCREEN?.waypoints?.get(clearedDirect.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName}"
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
        val atisLetter = CLIENT_SCREEN?.airports?.get(arrivalAirport?.arptId)?.entity?.get(MetarInfo.mapper)?.letterCode
        val atis = if (atisLetter != null && MathUtils.randomBoolean(0.7f)) {
            when (MathUtils.random(3)) {
                0 -> ", information $atisLetter"
                1 -> ", we have $atisLetter"
                2 -> ", we have information $atisLetter"
                else -> " with $atisLetter"
            }
        } else ""

        val preFinalMsg = "$yourCallsign $randomGreeting, ${acInfo.icaoCallsign} $aircraftWake, $altitudeAction $starSid$inbound$atis"
        addMessage(removeExtraCharacters(preFinalMsg), getMessageTypeForAircraftType(flightType.type))

        // Play contact sound
        GAME.soundManager.playInitialContact()

        // If datatag is minimised, un-minimise it
        aircraft[Datatag.mapper]?.apply {
            if (minimised) {
                minimised = false
                Gdx.app.postRunnable {
                    updateDatatagText(this, getNewDatatagLabelText(aircraft, minimised))
                    CLIENT_SCREEN?.sendAircraftDatatagPositionUpdate(aircraft, xOffset, yOffset, minimised, flashing)
                }
            }
        }
    }

    /**
     * Adds a message for contact after a go around by an aircraft
     * @param aircraft the aircraft entity contacting the player
     */
    fun goAround(aircraft: Entity) {
        val flightType = aircraft[FlightType.mapper] ?: return
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val clearanceState = aircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val alt = aircraft[Altitude.mapper] ?: return

        // Get the callsign of the player
        val thisSectorInfo = CLIENT_SCREEN?.let {
            // If the player has not received the initial sector data, schedule the contact for 0.5s later
            if (it.sectors.isEmpty) {
                Timer.schedule(object: Task() {
                    override fun run() {
                        goAround(aircraft)
                    }
                }, 0.5f)
                return
            }
            it.sectors[it.playerSector.toInt()]
        } ?: return
        val yourCallsign = thisSectorInfo.getControllerCallsignFrequency(flightType.type).callsign

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        // Get the current altitude, cleared altitude and the respective actions
        val altitudeAction = getAltitudePhraseology(alt.altitudeFt, clearanceState.clearedAlt)

        // If aircraft is vectored, say heading, else say missed approach procedure
        val lateralClearance = if (clearanceState.vectorHdg != null) "heading ${clearanceState.vectorHdg}"
        else ""

        val preFinalMsg = "$yourCallsign hello, ${acInfo.icaoCallsign} $aircraftWake, missed approach, $altitudeAction, $lateralClearance"
        addMessage(removeExtraCharacters(preFinalMsg), ARRIVAL)
    }

    /**
     * Adds a message for contact after a missed approach by an aircraft - used if aircraft goes around while still in
     * contact with the player
     * @param aircraft the aircraft entity contacting the player
     */
    fun missedApproach(aircraft: Entity) {
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val clearanceState = aircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val alt = aircraft[Altitude.mapper] ?: return

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        // Get the current altitude, cleared altitude and the respective actions
        val altitudeAction = getAltitudePhraseology(alt.altitudeFt, clearanceState.clearedAlt)

        // If aircraft is vectored, say heading, else say missed approach procedure
        val lateralClearance = if (clearanceState.vectorHdg != null) "heading ${clearanceState.vectorHdg}"
        else ""

        val preFinalMsg = "${acInfo.icaoCallsign} $aircraftWake, missed approach, $altitudeAction, $lateralClearance"
        addMessage(removeExtraCharacters(preFinalMsg), ARRIVAL)
    }

    /**
     * Adds a message sent by the player to instruct an aircraft to contact another sector, as well as the reply by the
     * aircraft
     * @param aircraft the aircraft to instruct to contact another sector
     * @param newSectorId the new sector to contact
     */
    fun contactOther(aircraft: Entity, newSectorId: Byte) {
        val flightType = aircraft[FlightType.mapper] ?: return
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val pos = aircraft[Position.mapper] ?: return

        // Controller segment
        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        // Get the callsign and frequency of the next controller
        val nextSectorInfo: SectorContactable.ControllerInfo = when (newSectorId) {
            SectorInfo.TOWER -> {
                // For tower, get the tower information for the runway the aircraft is cleared for approach to
                // If not present, get that of the first runway in the airport
                // If something still goes wrong, the default values below are used
                var towerCallsign = "Tower"
                var towerFreq = "118.70"
                aircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { app ->
                    val arpt = CLIENT_SCREEN?.airports?.get(aircraft[ArrivalAirport.mapper]?.arptId)?.entity
                    val rwy = arpt?.get(ApproachChildren.mapper)?.approachMap?.get(app)?.entity?.get(ApproachInfo.mapper)?.rwyObj ?:
                    arpt?.get(RunwayChildren.mapper)?.rwyMap?.firstValue()
                    rwy?.entity?.get(RunwayInfo.mapper)?.let {
                        towerCallsign = it.tower
                        towerFreq = it.freq
                    }
                }

                SectorContactable.ControllerInfo(towerCallsign, towerFreq)
            }
            SectorInfo.CENTRE -> {
                val accSectorObj = getACCSectorForPosition(pos.x, pos.y)
                accSectorObj?.getControllerCallsignFrequency(flightType.type) ?: SectorContactable.ControllerInfo("Control", "121.5")
            }
            else -> {
                CLIENT_SCREEN?.let {
                    // If the player has not received the initial sector data, schedule the contact for 0.5s later
                    if (it.sectors.isEmpty) {
                        Timer.schedule(object: Task() {
                            override fun run() {
                                goAround(aircraft)
                            }
                        }, 0.5f)
                        return
                    }
                    it.sectors[newSectorId.toInt()]?.getControllerCallsignFrequency(flightType.type) ?: return
                } ?: return
            }
        }
        val nextCallsign = nextSectorInfo.callsign

        val finalMsg = "${acInfo.icaoCallsign} $aircraftWake, contact $nextCallsign on ${nextSectorInfo.frequency}"
        addMessage(removeExtraCharacters(finalMsg), OTHERS)

        // Aircraft read-back segment
        val switchMsg = when (MathUtils.random(2)) {
            0 -> "$nextCallsign on"
            1 -> "Over to"
            else -> ""
        }

        // Random bye bye
        val bye = when (MathUtils.random(4)) {
            0 -> "bye"
            1 -> "bye bye"
            2 -> "good day"
            3 -> "see you"
            else -> ""
        }

        val preFinalMsg = "$switchMsg ${nextSectorInfo.frequency}, ${acInfo.icaoCallsign} $aircraftWake, $bye"
        addMessage(removeExtraCharacters(preFinalMsg), getMessageTypeForAircraftType(flightType.type))
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

    /**
     * Gets the appropriate phraseology for the aircraft's wake category
     * @param wakeCat the wake category of the aircraft
     * @return the string to append to the back of the aircraft callsign if applicable
     */
    private fun getWakePhraseology(wakeCat: Char): String {
        return when (wakeCat) {
            AircraftTypeData.AircraftPerfData.WAKE_SUPER -> "super"
            AircraftTypeData.AircraftPerfData.WAKE_HEAVY -> "heavy"
            else -> ""
        }
    }

    /**
     * Gets the appropriate altitude phraseology depending on aircraft's current and cleared altitude
     * @param currAltFt the current altitude of the aircraft
     * @param clearedAltFt the altitude the aircraft is cleared to fly to
     * @return the string denoting the altitude information transmitted by the aircraft
     */
    private fun getAltitudePhraseology(currAltFt: Float, clearedAltFt: Int): String {
        val currAltitude = currAltFt.let { currAlt ->
            val roundedFL = (currAlt / 100f).roundToInt()
            if (roundedFL * 100 > TRANS_ALT) "FL$roundedFL"
            else "${roundedFL * 100} feet"
        }

        val clearedAlt = clearedAltFt.let { clearedAlt ->
            val roundedFL = (clearedAlt / 100f).roundToInt()
            if (roundedFL * 100 > TRANS_ALT) "FL$roundedFL"
            else "${roundedFL * 100} feet"
        }

        val altDiff = clearedAltFt - currAltFt
        val shorten = MathUtils.randomBoolean()
        return when {
            MathUtils.randomBoolean(0.1f) -> ""
            altDiff < -400 -> if (shorten) "descending $currAltitude for $clearedAlt" else "descending through $currAltitude for $clearedAlt"
            altDiff > 400 -> if (shorten) "climbing $currAltitude for $clearedAlt" else "climbing through $currAltitude for $clearedAlt"
            altDiff >= -50 && altDiff <= 50 -> if (shorten) " $clearedAlt" else "at $clearedAlt"
            else -> if (shorten) "levelling off $clearedAlt" else "levelling off at $clearedAlt"
        }
    }

    /**
     * Gets a random greeting by the aircraft, which may depend on the time of the day
     * @return the greeting transmitted by the aircraft
     */
    private fun getRandomGreeting(): String {
        return when (MathUtils.random(2)) {
            0 -> ""
            1 -> "hello"
            else -> greetingByTime
        }
    }

    /**
     * Gets the appropriate message type to use for the input flight type
     * @param flightType the type of the flight of the aircraft
     * @return a byte denoting the type of message to add to the pane
     */
    private fun getMessageTypeForAircraftType(flightType: Byte): Byte {
        return when (flightType) {
            FlightType.ARRIVAL -> ARRIVAL
            FlightType.DEPARTURE -> DEPARTURE
            FlightType.EN_ROUTE -> OTHERS
            else -> {
                Log.info("CommsPane", "Unknown flight type $flightType")
                OTHERS
            }
        }
    }
}