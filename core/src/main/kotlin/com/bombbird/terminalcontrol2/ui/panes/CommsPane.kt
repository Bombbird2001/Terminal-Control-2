package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.SectorContactable
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.datatag.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagText
import com.bombbird.terminalcontrol2.utilities.*
import com.bombbird.terminalcontrol2.utilities.AircraftRequest.RequestType
import ktx.ashley.get
import ktx.scene2d.*
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
        if (convoLabels.size >= CONVO_SIZE) {
            val toRemove = convoLabels.removeFirst()
            labelTable.getCell(toRemove).pad(0f, 15f, 0f, 15f)
            toRemove.remove()
        }
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
                GAME.soundManager.playWarningUnlessPilotVoice()
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
     * Says the token sentence in TTS using the aircraft's voice
     * @param aircraft the aircraft entity to say the sentence
     * @param sentence the sentence to say
     */
    private fun saySentenceInTTS(aircraft: Entity, sentence: TokenSentence) {
        aircraft[TTSVoice.mapper]?.voice?.let { voice -> GAME.ttsManager.say(sentence.toTTSSentence(), voice) }
    }

    /**
     * Adds a message for initial contact by an aircraft
     * @param aircraft the aircraft entity contacting the player
     * @param retries number of times this function has attempted to be called without success
     */
    fun initialContact(aircraft: Entity, retries: Int = 0) {
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
                // Max of 2 retries
                if (retries < 2) scheduleAction(0.5f) {
                    initialContact(aircraft, retries + 1)
                }
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

        // Check emergency
        val isEmergency = aircraft[EmergencyPending.mapper]?.active == true

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
            .addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, isEmergency)).addComma().addTokens(*altitudeAction)

        // Try to get cleared approach if there is one
        val appName = clearanceState.clearedApp
        val appObj = CLIENT_SCREEN?.airports?.get(arrivalAirport?.arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(appName)
        if (appName != null && appObj != null) {
            if (MathUtils.randomBoolean(0.75f)) {
                val appToken = PronounceableToken(appName, appObj)
                sentence.addComma().addToken(appToken)
            }
        } else {
            // Only say the SID/STAR and ATIS if there is no approach clearance

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
        }

        sentence.addTokens(*inboundTokens)

        addMessage(sentence.toTextSentence(), if (isEmergency) WARNING else getMessageTypeForAircraftType(flightType.type))
        saySentenceInTTS(aircraft, sentence)

        // Play contact sound
        GAME.soundManager.playInitialContact()

        // If datatag is minimised, un-minimise it
        aircraft[Datatag.mapper]?.apply {
            if (minimised) {
                minimised = false
                Gdx.app.postRunnable {
                    updateDatatagText(this, getNewDatatagLabelText(aircraft, minimised))
                    CLIENT_SCREEN?.sendAircraftDatatagPositionUpdateIfControlled(aircraft, xOffset, yOffset, minimised, shouldFlashOrange)
                }
            }
        }
    }

    /**
     * Adds a message for contact after a go around by an aircraft
     * @param aircraft the aircraft entity contacting the player
     * @param retries number of times this function has attempted to be called without success
     */
    fun goAround(aircraft: Entity, retries: Int = 0) {
        val flightType = aircraft[FlightType.mapper] ?: return
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val clearanceState = aircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val goAroundReason = aircraft[RecentGoAround.mapper]?.reason
        if (goAroundReason == null && retries < 2) {
            // If the go-around reason is not present, schedule the contact for 0.5s later
            scheduleAction(0.5f) {
                goAround(aircraft, retries + 1)
            }
            return
        }
        val goAroundReasonStr = getGoAroundReason(goAroundReason)
        val alt = aircraft[Altitude.mapper] ?: return

        // Get the callsign of the player
        val thisSectorInfo = CLIENT_SCREEN?.let {
            // If the player has not received the initial sector data, schedule the contact for 0.5s later
            if (it.sectors.isEmpty) {
                // Max of 2 retries
                if (retries < 2) scheduleAction(0.5f) {
                    goAround(aircraft, retries + 1)
                }
                return
            }
            it.sectors[it.playerSector.toInt()]
        } ?: return
        val yourCallsign = thisSectorInfo.getControllerCallsignFrequency(flightType.type).callsign

        // Check emergency
        val isEmergency = aircraft[EmergencyPending.mapper]?.active == true

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        // Get the current altitude, cleared altitude and the respective actions
        val altitudeAction = getAltitudePhraseology(alt.altitudeFt, clearanceState.clearedAlt)

        // If aircraft is vectored, say heading, else say missed approach procedure
        val lateralClearance = clearanceState.vectorHdg?.let { hdg -> arrayOf(LiteralToken("heading"), HeadingToken(hdg)) } ?: arrayOf()

        val sentence = TokenSentence().addTokens(LiteralToken(yourCallsign), LiteralToken("hello")).addComma()
            .addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, isEmergency)).addComma()
            .addToken(LiteralToken("missed approach$goAroundReasonStr")).addComma().addTokens(*altitudeAction).addComma()
            .addTokens(*lateralClearance)

        addMessage(sentence.toTextSentence(), ARRIVAL)
        saySentenceInTTS(aircraft, sentence)
    }

    /**
     * Adds a message for contact after a missed approach by an aircraft - used if aircraft goes around while still in
     * contact with the player
     * @param aircraft the aircraft entity contacting the player
     * @param reason the reason for the missed approach
     */
    fun missedApproach(aircraft: Entity, reason: Byte) {
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val goAroundReasonStr = getGoAroundReason(reason)

        // Check emergency
        val isEmergency = aircraft[EmergencyPending.mapper]?.active == true

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, isEmergency))
            .addComma().addToken(LiteralToken("missed approach$goAroundReasonStr"))

        addMessage(sentence.toTextSentence(), ARRIVAL)
        saySentenceInTTS(aircraft, sentence)
    }

    /**
     * Adds a message sent by the player to instruct an aircraft to contact another sector, as well as the reply by the
     * aircraft
     * @param aircraft the aircraft to instruct to contact another sector
     * @param newSectorId the new sector to contact
     * @param retries number of times this function has attempted to be called without success
     */
    fun contactOther(aircraft: Entity, newSectorId: Byte, retries: Int = 0) {
        val flightType = aircraft[FlightType.mapper] ?: return
        val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val pos = aircraft[Position.mapper] ?: return

        // Check emergency
        val isEmergency = aircraft[EmergencyPending.mapper]?.active == true

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
                        // Max of 2 retries
                        if (retries < 2) scheduleAction(0.5f) {
                            contactOther(aircraft, newSectorId, retries + 1)
                        }
                        return
                    }
                    it.sectors[newSectorId.toInt()]?.getControllerCallsignFrequency(flightType.type) ?: return
                } ?: return
            }
        }
        val nextCallsign = nextSectorInfo.callsign

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, isEmergency))
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
            .addComma().addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, isEmergency)).addComma()
            .addToken(LiteralToken(bye))

        addMessage(sentence2.toTextSentence(), getMessageTypeForAircraftType(flightType.type))
        if (CONTACT_OTHER_PILOT_READBACK) saySentenceInTTS(aircraft, sentence2)
    }

    /**
     * Adds a message sent by the aircraft declaring an emergency
     * @param aircraft the aircraft declaring an emergency
     * @param type the type of emergency declared
     */
    fun declareEmergency(aircraft: Entity, type: Byte) {
        val acInfo = aircraft[AircraftInfo.mapper] ?: return

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        val emergencyTypeString = when (type) {
            EmergencyPending.BIRD_STRIKE -> if (MathUtils.randomBoolean()) "we had a bird strike" else "declaring emergency due to bird strike"
            EmergencyPending.ENGINE_FAIL -> {
                val side = if (MathUtils.randomBoolean()) "left" else "right"
                if (MathUtils.randomBoolean()) "we have a $side engine failure" else "declaring emergency due to $side engine failure"
            }
            EmergencyPending.HYDRAULIC_FAIL -> if (MathUtils.randomBoolean()) "we have a hydraulic problem" else "declaring emergency due to hydraulic failure"
            EmergencyPending.FUEL_LEAK -> if (MathUtils.randomBoolean()) "we have a fuel leak" else "declaring emergency due to fuel leak"
            EmergencyPending.MEDICAL -> if (MathUtils.randomBoolean()) "we have a medical emergency" else "declaring a medical emergency"
            EmergencyPending.PRESSURE_LOSS -> if (MathUtils.randomBoolean()) "we have a cabin pressure problem" else "declaring emergency due to loss of cabin pressure"
            else -> {
                FileLog.warn("CommsPane", "Unknown emergency type $type")
                return
            }
        }

        val sentence = TokenSentence().addTokens(LiteralToken("Mayday, mayday, mayday,"), CallsignToken(acInfo.icaoCallsign, aircraftWake, false))
            .addComma().addToken(LiteralToken(emergencyTypeString)).addComma()

        if (type == EmergencyPending.PRESSURE_LOSS) sentence.addToken(LiteralToken("performing emergency descent to 10000 feet")).addComma()

        sentence.addToken(LiteralToken(if (MathUtils.randomBoolean()) "requesting return to airport" else "we would like to return to the airport"))

        if (type == EmergencyPending.MEDICAL) sentence.addComma().addToken(LiteralToken("we require medical assistance on landing"))

        addMessage(sentence.toTextSentence(), WARNING)
        saySentenceInTTS(aircraft, sentence)
    }

    /**
     * Adds a message sent by the aircraft when nearing completion of
     * emergency checklists, as well as whether a fuel dump is needed
     * @param aircraft the aircraft declaring an emergency
     * @param needsFuelDump whether the aircraft needs to dump fuel
     */
    fun checklistNearingDone(aircraft: Entity, needsFuelDump: Boolean) {
        val acInfo = aircraft[AircraftInfo.mapper] ?: return

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, true))
            .addComma().addToken(LiteralToken(
                if (MathUtils.randomBoolean()) "we're almost done with checklists"
                else "we need a few more minutes to run checklists"))

        if (needsFuelDump) sentence.addComma().addToken(LiteralToken(
            if (MathUtils.randomBoolean()) "we need to dump fuel after"
            else "we also require fuel dumping"
        ))

        addMessage(sentence.toTextSentence(), WARNING)
        saySentenceInTTS(aircraft, sentence)
    }

    /**
     * Adds a message sent by the aircraft to notify of its fuel dumping status
     * @param aircraft the aircraft declaring an emergency
     * @param dumpEnding whether the aircraft is ending fuel dumping, else it has
     * just started fuel dumping
     */
    fun fuelDumpStatus(aircraft: Entity, dumpEnding: Boolean) {
        val acInfo = aircraft[AircraftInfo.mapper] ?: return

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, true))
            .addComma().addToken(LiteralToken(
                if (dumpEnding) {
                    if (MathUtils.randomBoolean()) "we will need a few more minutes for fuel dumping"
                    else "we are almost done with dumping fuel"
                }
                else {
                    if (MathUtils.randomBoolean()) "we are dumping fuel"
                    else "we're dumping fuel now"
                }
            ))

        addMessage(sentence.toTextSentence(), WARNING)
        saySentenceInTTS(aircraft, sentence)

        if (!dumpEnding) {
            // Add ATC fuel dump broadcast
            // Get the closest airport to aircraft
            val aircraftPos = aircraft[Position.mapper] ?: return
            val aircraftAlt = aircraft[Altitude.mapper] ?: return
            var closestAirport: Airport? = null
            var closestDistPxSq = -1f
            CLIENT_SCREEN?.airports?.also {
                for (i in 0 until it.size) it.getValueAt(i).let { arpt ->
                    val pos = arpt.entity[Position.mapper] ?: return@let
                    val deltaX = pos.x - aircraftPos.x
                    val deltaY = pos.y - aircraftPos.y
                    val radiusSq = deltaX * deltaX + deltaY * deltaY
                    if (closestAirport == null || radiusSq < closestDistPxSq) {
                        closestAirport = arpt
                        closestDistPxSq = radiusSq
                    }
                }
            }

            val finalAirportPos = closestAirport?.entity?.get(Position.mapper) ?: return
            val airportName = closestAirport?.entity?.get(AirportInfo.mapper)?.name ?: return

            val trackDir = getRequiredTrack(finalAirportPos.x, finalAirportPos.y, aircraftPos.x, aircraftPos.y)
            val directionString = when {
                330 <= trackDir || trackDir <= 30 -> "north"
                withinRange(trackDir, 30f, 60f) -> "north-east"
                withinRange(trackDir, 60f, 120f) -> "east"
                withinRange(trackDir, 120f, 150f) -> "south-east"
                withinRange(trackDir, 150f, 210f) -> "south"
                withinRange(trackDir, 210f, 240f) -> "south-west"
                withinRange(trackDir, 240f, 300f) -> "west"
                withinRange(trackDir, 300f, 330f) -> "north-west"
                else -> "unknown direction"
            }

            val sentence2 = TokenSentence().addTokens(LiteralToken("Attention all aircraft, fuel dumping in progress"),
                NumberToken(pxToNm(sqrt(closestDistPxSq.toDouble()).toFloat()).roundToInt()), LiteralToken("miles"),
                LiteralToken(directionString), LiteralToken("of"), LiteralToken(airportName))
                .addComma().addToken(AltitudeToken(aircraftAlt.altitudeFt))

            addMessage(sentence2.toTextSentence(), OTHERS)
        }
    }

    /**
     * Adds a message sent by the aircraft when it is ready for approach, and
     * may need to remain on the runway after landing
     * @param aircraft the aircraft declaring an emergency
     * @param immobilizeOnLanding whether the aircraft needs to remain on the runway after landing
     */
    fun readyForApproach(aircraft: Entity, immobilizeOnLanding: Boolean) {
        val acInfo = aircraft[AircraftInfo.mapper] ?: return

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        val sentence = TokenSentence().addTokens(CallsignToken(acInfo.icaoCallsign, aircraftWake, true),
            LiteralToken("is ready for approach"))

        if (immobilizeOnLanding) sentence.addComma().addToken(LiteralToken(
            if (MathUtils.randomBoolean()) "we are unable to taxi off the runway after landing"
            else "we need to remain on the runway after landing"
        ))

        addMessage(sentence.toTextSentence(), WARNING)
        saySentenceInTTS(aircraft, sentence)
    }

    /**
     * Adds a message sent by the aircraft when it has a request
     *
     * [aircraft] the aircraft entity requesting
     * [requestType] the type of request
     * [params] the string parameters for the request
     */
    fun aircraftRequest(aircraft: Entity, requestType: RequestType, params: Array<String>) {
        val flightType = aircraft[FlightType.mapper] ?: return
        val acInfo = aircraft[AircraftInfo.mapper] ?: return

        // Get the wake category of the aircraft
        val aircraftWake = getWakePhraseology(acInfo.aircraftPerf.wakeCategory)

        // Check emergency
        val isEmergency = aircraft[EmergencyPending.mapper]?.active == true

        val sentence = TokenSentence().addToken(CallsignToken(acInfo.icaoCallsign, aircraftWake, isEmergency)).addComma()

        when (requestType) {
            RequestType.HIGH_SPEED_CLIMB -> sentence.addToken(LiteralToken("request high speed climb"))
            RequestType.DIRECT -> sentence.addTokens(LiteralToken("request direct"), WaypointToken(params[0]))
            RequestType.FURTHER_CLIMB -> {
                sentence.addToken(LiteralToken(
                    if (MathUtils.randomBoolean()) "request further climb"
                    else "request higher"
                ))
            }
            RequestType.WEATHER_AVOIDANCE -> sentence.addTokens(
                LiteralToken("request heading"),
                HeadingToken(params[0].toShort()),
                LiteralToken(if (MathUtils.randomBoolean()) "due weather" else "to avoid weather")
            )
            RequestType.CANCEL_APPROACH_WEATHER -> sentence.addToken(
                LiteralToken("request to cancel approach due to weather")
            )
            RequestType.CANCEL_APPROACH_MECHANICAL -> sentence.addToken(
                LiteralToken("request to cancel approach due to mechanical issues")
            )
            else -> {}
        }

        addMessage(sentence.toTextSentence(), getMessageTypeForAircraftType(flightType.type))
        saySentenceInTTS(aircraft, sentence)
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

    /**
     * Gets the appropriate go-around reason string for the input reason
     * @param reason the reason for the go-around
     * @return the string denoting the reason for the go-around
     */
    private fun getGoAroundReason(reason: Byte?): String {
        return when (reason) {
            RecentGoAround.RWY_NOT_IN_SIGHT -> ", runway not in sight"
            RecentGoAround.RWY_NOT_CLEAR -> ", runway not clear"
            RecentGoAround.TOO_HIGH -> " due to being too high"
            RecentGoAround.TOO_FAST -> " due to being too fast"
            RecentGoAround.UNSTABLE -> ", unstable approach"
            RecentGoAround.TRAFFIC_TOO_CLOSE -> ", traffic too close"
            RecentGoAround.STRONG_TAILWIND -> " due to strong tailwind"
            RecentGoAround.RWY_CLOSED -> " due to runway closed"
            RecentGoAround.WINDSHEAR -> " due to windshear"
            RecentGoAround.WAKE_TURBULENCE -> " due to wake turbulence"
            RecentGoAround.STRONG_CROSSWIND -> " due to strong crosswind"
            else -> {
                FileLog.warn("CommsPane", "Unknown go-around reason $reason")
                ""
            }
        }
    }
}