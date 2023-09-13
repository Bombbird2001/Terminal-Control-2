package com.bombbird.terminalcontrol2.ui.panes

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
import com.bombbird.terminalcontrol2.ui.datatag.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagText
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.scene2d.*
import java.time.LocalTime

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
                FileLog.info("CommsPane", "Unknown message type $msgType")
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
        val departureAirport = aircraft[DepartureAirport.mapper]

        val sentence = TokenSentence()

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
        val inboundTokens = if (clearedHdg != null) {
            arrayOf(TokenSentence.COMMA_TOKEN, LiteralToken("heading"), HeadingToken(clearedHdg))
        } else if (clearedDirect != null && clearedDirect is Route.WaypointLeg && MathUtils.randomBoolean()) {
            arrayOf(TokenSentence.COMMA_TOKEN, LiteralToken("inbound"),
                WaypointToken(CLIENT_SCREEN?.waypoints?.get(clearedDirect.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName ?: ""))
        } else arrayOf()

        sentence.addTokens(LiteralToken(yourCallsign), LiteralToken(randomGreeting)).addComma()
            .addTokens(CallsignToken(acInfo.icaoCallsign), LiteralToken(aircraftWake)).addComma().addTokens(*altitudeAction)

        // Get current SID/STAR name
        val depArrToken = LiteralToken(if (flightType.type == FlightType.DEPARTURE) "departure" else "arrival")
        val sidStarName = clearanceState.routePrimaryName
        val sidStarObj = CLIENT_SCREEN?.airports?.get(arrivalAirport?.arptId)?.entity?.get(STARChildren.mapper)?.starMap?.get(sidStarName) ?:
        CLIENT_SCREEN?.airports?.get(departureAirport?.arptId)?.entity?.get(SIDChildren.mapper)?.sidMap?.get(sidStarName)
        val sidStarToken = if (sidStarObj == null) LiteralToken("") else PronounceableToken(sidStarName, sidStarObj)
        if (clearedHdg == null) when (MathUtils.random(3)) {
            0 -> sentence.addTokens(LiteralToken("on the"), sidStarToken, depArrToken)
            1 -> sentence.addComma().addToken(sidStarToken)
            2 -> sentence.addComma().addTokens(sidStarToken, depArrToken)
        }

        sentence.addTokens(*inboundTokens)

        // Get current airport ATIS letter
        val informationToken = LiteralToken("information")
        val weHaveToken = LiteralToken("we have")
        val atisLetter = CLIENT_SCREEN?.airports?.get(arrivalAirport?.arptId)?.entity?.get(MetarInfo.mapper)?.letterCode
        if (atisLetter != null && MathUtils.randomBoolean(0.7f)) {
            val atisToken = AtisToken(atisLetter)
            when (MathUtils.random(3)) {
                0 -> sentence.addComma().addTokens(informationToken, atisToken)
                1 -> sentence.addComma().addTokens(weHaveToken, atisToken)
                2 -> sentence.addComma().addTokens(weHaveToken, informationToken, atisToken)
                else -> sentence.addTokens(LiteralToken("with"), atisToken)
            }
        }

        addMessage(sentence.toTextSentence(), getMessageTypeForAircraftType(flightType.type))
        aircraft[TTSVoice.mapper]?.voice?.let { voice -> GAME.ttsManager.say(sentence.toTTSSentence(), voice) }

        // Play contact sound
        GAME.soundManager.playInitialContact()

        // If datatag is minimised, un-minimise it
        aircraft[Datatag.mapper]?.apply {
            if (minimised) {
                minimised = false
                Gdx.app.postRunnable {
                    updateDatatagText(this, getNewDatatagLabelText(aircraft, minimised))
                    CLIENT_SCREEN?.sendAircraftDatatagPositionUpdateIfControlled(aircraft, xOffset, yOffset, minimised, flashing)
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
        val lateralClearance = clearanceState.vectorHdg?.let { hdg -> arrayOf(LiteralToken("heading"), HeadingToken(hdg)) } ?: arrayOf()

        val sentence = TokenSentence().addTokens(LiteralToken(yourCallsign), LiteralToken("hello")).addComma()
            .addTokens(CallsignToken(acInfo.icaoCallsign), LiteralToken(aircraftWake)).addComma()
            .addToken(LiteralToken("missed approach")).addComma().addTokens(*altitudeAction).addComma()
            .addTokens(*lateralClearance)

        addMessage(sentence.toTextSentence(), ARRIVAL)
        aircraft[TTSVoice.mapper]?.voice?.let { voice -> GAME.ttsManager.say(sentence.toTTSSentence(), voice) }
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
        val lateralClearance = clearanceState.vectorHdg?.let { hdg -> arrayOf(LiteralToken("heading"), HeadingToken(hdg)) } ?: arrayOf()

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign), LiteralToken(aircraftWake))
            .addComma().addToken(LiteralToken("missed approach")).addComma().addTokens(*altitudeAction)
            .addComma().addTokens(*lateralClearance)

        addMessage(sentence.toTextSentence(), ARRIVAL)
        aircraft[TTSVoice.mapper]?.voice?.let { voice -> GAME.ttsManager.say(sentence.toTTSSentence(), voice) }
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

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign), LiteralToken(aircraftWake))
            .addComma().addTokens(LiteralToken("contact"), LiteralToken(nextCallsign),
                FrequencyToken(nextSectorInfo.frequency))

        addMessage(sentence.toTextSentence(), OTHERS)

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

        val sentence2 = TokenSentence().addTokens(LiteralToken(switchMsg), FrequencyToken(nextSectorInfo.frequency))
            .addComma().addTokens(CallsignToken(acInfo.icaoCallsign), LiteralToken(aircraftWake)).addComma()
            .addToken(LiteralToken(bye))

        addMessage(sentence2.toTextSentence(), getMessageTypeForAircraftType(flightType.type))
        // println(sentence2.toTTSSentence())
        // aircraft[TTSVoice.mapper]?.voice?.let { voice -> GAME.ttsManager.say(sentence2.toTTSSentence(), voice) }
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
    private fun getAltitudePhraseology(currAltFt: Float, clearedAltFt: Int): Array<CommsToken> {
        val altDiff = clearedAltFt - currAltFt
        val shorten = MathUtils.randomBoolean()
        val forToken = LiteralToken("for")
        val throughToken = LiteralToken("through")
        val levellingToken = LiteralToken("levelling off")
        val atToken = LiteralToken("at")
        val currAltToken = AltitudeToken(currAltFt)
        val clearedAltToken = AltitudeToken(clearedAltFt.toFloat())
        return when {
            MathUtils.randomBoolean(0.1f) -> arrayOf()
            altDiff < -400 -> {
                val descendingToken = LiteralToken("descending")
                if (shorten) arrayOf(descendingToken, currAltToken, forToken, clearedAltToken)
                else arrayOf(descendingToken, throughToken, currAltToken, forToken, clearedAltToken)
            }
            altDiff > 400 -> {
                val climbingToken = LiteralToken("climbing")
                if (shorten) arrayOf(climbingToken, currAltToken, forToken, clearedAltToken)
                else arrayOf(climbingToken, throughToken, currAltToken, forToken, clearedAltToken)
            }
            altDiff >= -50 && altDiff <= 50 -> if (shorten) arrayOf(clearedAltToken) else arrayOf(atToken, clearedAltToken)
            else -> if (shorten) arrayOf(levellingToken, clearedAltToken) else arrayOf(levellingToken, atToken, clearedAltToken)
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
                FileLog.info("CommsPane", "Unknown flight type $flightType")
                OTHERS
            }
        }
    }
}