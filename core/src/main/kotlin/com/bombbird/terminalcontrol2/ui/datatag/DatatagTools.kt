package com.bombbird.terminalcontrol2.ui.datatag

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.isMobile
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import com.bombbird.terminalcontrol2.utilities.modulateHeading
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.get
import ktx.ashley.has
import ktx.scene2d.Scene2DSkin
import kotlin.math.roundToInt

/** Helper file for dealing with [Datatag] matters */

// Spacing between each line on the datatag
const val LABEL_PADDING = 7

/** Updates the text for the labels of the [datatag], and sets the new sizes accordingly */
fun updateDatatagText(datatag: Datatag, newText: Array<String>) {
    for (i in 0 until datatag.labelArray.size) {
        datatag.labelArray[i].apply {
            setText(if (i < newText.size) newText[i] else "")
            pack()
        }
    }
    updateDatatagSize(datatag)
}

/** Updates the style for the background [Datatag.imgButton] */
fun updateDatatagStyle(datatag: Datatag, flightType: Byte, selected: Boolean) {
    val background = if ((selected && DATATAG_BACKGROUND != DATATAG_BACKGROUND_OFF) || (DATATAG_BACKGROUND == DATATAG_BACKGROUND_ALWAYS)) "" else "NoBG"
    val showBorder = ((selected && DATATAG_BORDER != DATATAG_BORDER_OFF) || (DATATAG_BORDER == DATATAG_BORDER_ALWAYS))
    val colour = if (datatag.flashingOrange) "Orange"
    else if (datatag.emergency) "Red"
    else if (showBorder) when (flightType) {
        FlightType.DEPARTURE -> "Green"
        FlightType.ARRIVAL -> "Blue"
        FlightType.EN_ROUTE -> "Gray"
        else -> {
            FileLog.info("Datatag", "Unknown flight type $flightType")
            ""
        }
    } else ""
    datatag.imgButton.style = Scene2DSkin.defaultSkin.get("Datatag${colour}${background}", ImageButton.ImageButtonStyle::class.java)
    datatag.currentDatatagStyle = "Datatag${colour}${background}"
}

/**
 * Sets the flashing status of the datatag
 * @param datatag the datatag to set flash
 * @param aircraft the aircraft the datatag belongs to
 * @param flash whether to start or stop the flash
 */
fun setDatatagFlash(datatag: Datatag, aircraft: Aircraft, flash: Boolean) {
    if (datatag.flashing == flash) return
    datatag.flashing = flash
    CLIENT_SCREEN?.sendAircraftDatatagPositionUpdateIfControlled(aircraft.entity, datatag.xOffset, datatag.yOffset, datatag.minimised, flash)
    if (flash) {
        datatag.flashTimer.scheduleTask(object: Timer.Task() {
            override fun run() {
                // Every 1 second, update the datatag flashing orange status, and call updateDatatagStyle
                datatag.flashingOrange = !datatag.flashingOrange && datatag.flashing
                updateDatatagStyle(datatag, aircraft.entity[FlightType.mapper]?.type ?: return, CLIENT_SCREEN?.selectedAircraft == aircraft)
            }
        }, 0f, 1f)
    } else {
        datatag.flashTimer.clear()
        datatag.flashingOrange = false
        updateDatatagStyle(datatag, aircraft.entity[FlightType.mapper]?.type ?: return, CLIENT_SCREEN?.selectedAircraft == aircraft)
    }
}

/**
 * Updates the label style to use smaller fonts when radar is zoomed out
 * @param datatag the datatag to update
 * @param smaller whether to use smaller fonts
 */
fun updateDatatagLabelSize(datatag: Datatag, smaller: Boolean) {
    datatag.labelArray.forEach { label ->
        val styleToUse = "${if (smaller) "DatatagSmall" else "Datatag"}${if (isMobile()) "Mobile" else ""}"
        label.style = Scene2DSkin.defaultSkin.get(styleToUse, LabelStyle::class.java)
        label.pack()
    }
    updateDatatagSize(datatag)
    datatag.smallLabelFont = smaller
}

/**
 * Updates the spacing, in px, between each line label to the new global set datatag spacing
 * @param datatag the datatag to update
 */
fun updateDatatagLineSpacing(datatag: Datatag) {
    updateDatatagSize(datatag)
}

/** Re-calculates and updates the size of the background [Datatag.imgButton] and [Datatag.clickSpot] */
private fun updateDatatagSize(datatag: Datatag) {
    var maxWidth = 0f
    var height = 0f
    var firstLabel = true
    for (label in datatag.labelArray) {
        if (label.width > maxWidth) maxWidth = label.width
        if (label.text.isNullOrEmpty()) continue
        height += label.height
        if (firstLabel) {
            firstLabel = false
            continue
        }
        height += DATATAG_ROW_SPACING_PX
    }
    val changeInWidth = maxWidth + LABEL_PADDING * 2 - datatag.clickSpot.width
    val changeInHeight = height + LABEL_PADDING * 2 - datatag.clickSpot.height
    datatag.xOffset -= (changeInWidth * if (datatag.xOffset < -datatag.clickSpot.width / 2) 1 else 0)
    datatag.yOffset -= (changeInHeight * if (datatag.yOffset < -datatag.clickSpot.height / 2) 1 else 0)
    datatag.imgButton.setSize(maxWidth + LABEL_PADDING * 2, height + LABEL_PADDING * 2)
    datatag.clickSpot.setSize(maxWidth + LABEL_PADDING * 2, height + LABEL_PADDING * 2)
}

/** Adds a dragListener and changeListener to the background [Datatag.clickSpot] */
fun addDatatagInputListeners(datatag: Datatag, aircraft: Aircraft) {
    datatag.clickSpot.apply {
        CLIENT_SCREEN?.addToConstZoomStage(this) // Add to uiStage in order for drag gesture to be detected by inputMultiplexer
        addListener(object: DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                if (aircraft.entity.has(WaitingTakeoff.mapper)) return
                datatag.xOffset += (x - this@apply.width / 2)
                datatag.yOffset += (y - this@apply.height / 2)
                datatag.dragging = true
                event?.handle()
            }

            override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                CLIENT_SCREEN?.sendAircraftDatatagPositionUpdateIfControlled(aircraft.entity, datatag.xOffset, datatag.yOffset, datatag.minimised, datatag.flashing)
                event?.handle()
            }
        })
        addChangeListener { _, _ ->
            if (aircraft.entity.has(WaitingTakeoff.mapper)) return@addChangeListener
            if (datatag.dragging) {
                datatag.dragging = false
                return@addChangeListener
            }
            datatag.clicks++
            if (datatag.clicks >= 2) {
                val controllable = aircraft.entity[Controllable.mapper]
                if (controllable != null && controllable.sectorId == CLIENT_SCREEN?.playerSector) datatag.minimised = !datatag.minimised
                datatag.clicks = 0
                datatag.tapTimer.clear()
                Gdx.app.postRunnable {
                    updateDatatagText(datatag, getNewDatatagLabelText(aircraft.entity, datatag.minimised))
                    CLIENT_SCREEN?.sendAircraftDatatagPositionUpdateIfControlled(aircraft.entity, datatag.xOffset, datatag.yOffset, datatag.minimised, datatag.flashing)
                }
            } else datatag.tapTimer.scheduleTask(object : Timer.Task() {
                override fun run() {
                    datatag.clicks = 0
                }
            }, 0.2f)
            CLIENT_SCREEN?.setUISelectedAircraft(aircraft)
            Gdx.app.postRunnable {
                if (!datatag.renderLast) {
                    CLIENT_SCREEN?.aircraft?.let { aircraftMap ->
                        Entries(aircraftMap).forEach { it.value.entity[Datatag.mapper]?.renderLast = false }
                    }
                    remove()
                    CLIENT_SCREEN?.addToConstZoomStage(this) // Re-add to uiStage in order for position to be increased
                    datatag.renderLast = true
                }
            }
        }
    }
}

/**
 * Gets a new array of strings for the label text, depending on whether it is minimised
 * @param entity the aircraft to generate the datatag for
 * @param minimised whether the datatag should be minimised
 * @return an array of string denoting each line in the datatag
 */
fun getNewDatatagLabelText(entity: Entity, minimised: Boolean): Array<String> {
    return if (minimised) getMinimisedLabelText(entity)
    else getExpandedLabelText(entity)
}

/**
 * Gets a new array of strings for the minimised datatag, based on the player's datatag format
 * @param entity the aircraft to generate the minimised datatag for
 * @return an array of string denoting each line in the datatag
 */
private fun getMinimisedLabelText(entity: Entity): Array<String> {
    val datatagMap = updateDatatagValueMap(entity)
    return DATATAG_LAYOUTS[DATATAG_STYLE_NAME]?.generateTagText(datatagMap, true)?.split("\n")?.toTypedArray() ?: arrayOf()
}

/**
 * Gets a new array of strings for the expanded datatag, based on the player's datatag format
 * @param entity the aircraft to generate the minimised datatag for
 * @return an array of string denoting each line in the datatag
 */
private fun getExpandedLabelText(entity: Entity): Array<String> {
    val datatagMap = updateDatatagValueMap(entity)
    return DATATAG_LAYOUTS[DATATAG_STYLE_NAME]?.generateTagText(datatagMap, false)?.split("\n")?.toTypedArray() ?: arrayOf()
}

/**
 * Gets all properties required for the datatag to display in a HashMap
 * @param entity the aircraft to generate the HashMap for
 */
private fun updateDatatagValueMap(entity: Entity): HashMap<String, String> {
    val datatagMap = entity[Datatag.mapper]?.datatagInfoMap ?: return hashMapOf()

    val acInfo = entity[AircraftInfo.mapper]
    val radarData = entity[RadarData.mapper]
    val cmdTarget = entity[CommandTarget.mapper]
    val latestClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState
    val callsign = acInfo?.icaoCallsign ?: "<Callsign>"
    val wake = acInfo?.aircraftPerf?.wakeCategory ?: "<Wake>"
    val recat = acInfo?.aircraftPerf?.recat ?: "<Recat>"
    val icaoType = acInfo?.icaoType ?: "<ICAO Type>"
    val currAlt = radarData?.altitude?.altitudeFt?.let { (it / 100).roundToInt() }
    val currAltStr = if (currAlt != null) "$currAlt" else "<Alt>"
    val vs = radarData?.speed?.vertSpdFpm?.let { if (it > 150) "^" else if (it < -150) "v" else "=" } ?: "<VS>"
    val cmdAlt = if (entity.has(GlideSlopeCaptured.mapper)) "GS" else if (entity.has(VisualCaptured.mapper)) "VIS"
    else (cmdTarget?.targetAltFt?.let { (it / 100f).roundToInt().toString() }) ?: "<Cmd Alt>"
    val expedite = latestClearance?.expedite?.let { if (it) "=>>" else "=>" } ?: "<Expedite>"
    val clearedAlt = latestClearance?.clearedAlt?.let { "${it / 100}" } ?: "<Cleared Alt>"
    val hdg = radarData?.direction?.trackUnitVector?.angleDeg()?.let { modulateHeading((convertWorldAndRenderDeg(it) + MAG_HDG_DEV).roundToInt().toFloat()).roundToInt().toString() } ?: "<Hdg>"
    val clearedLat = if (entity.has(LocalizerCaptured.mapper)) "LOC" else if (entity.has(VisualCaptured.mapper)) "VIS"
    else latestClearance?.route?.let { if (it.size > 0) {
        (it[0] as? Route.WaypointLeg)?.wptId?.let { wptId -> GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName } ?:
        (it[0] as? Route.HoldLeg)?.wptId?.let { wptId -> GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName }
    } else null} ?: latestClearance?.vectorHdg?.toString() ?: ""
    val sidStarApp = latestClearance?.clearedApp ?: latestClearance?.routePrimaryName ?: ""
    val gs = radarData?.groundSpeed?.roundToInt()?.toString() ?: "<GS>"
    val clearedIas = latestClearance?.clearedIas?.toString() ?: "<Cleared IAS>"
    val arptId = entity[DepartureAirport.mapper]?.arptId ?: entity[ArrivalAirport.mapper]?.arptId
    val arptName = GAME.gameClientScreen?.airports?.get(arptId)?.entity?.get(AirportInfo.mapper)?.icaoCode ?: "<Arpt>"

    datatagMap.apply {
        put(DatatagConfig.CALLSIGN, callsign)
        put(DatatagConfig.CALLSIGN_RECAT, "$callsign/$recat")
        put(DatatagConfig.CALLSIGN_WAKE, "$callsign/$wake")
        put(DatatagConfig.ICAO_TYPE, icaoType)
        put(DatatagConfig.ICAO_TYPE_RECAT, "$icaoType/$recat")
        put(DatatagConfig.ICAO_TYPE_WAKE, "$icaoType/$wake")
        put(DatatagConfig.ALTITUDE, currAltStr)
        put(DatatagConfig.ALTITUDE_TREND, vs)
        put(DatatagConfig.CMD_ALTITUDE, cmdAlt)
        put(DatatagConfig.EXPEDITE, expedite)
        put(DatatagConfig.CLEARED_ALT, clearedAlt)
        put(DatatagConfig.HEADING, hdg)
        put(DatatagConfig.LAT_CLEARED, clearedLat)
        put(DatatagConfig.SIDSTARAPP_CLEARED, sidStarApp)
        put(DatatagConfig.GROUND_SPEED, gs)
        put(DatatagConfig.CLEARED_IAS, clearedIas)
        put(DatatagConfig.AIRPORT, arptName)
    }

    return datatagMap
}
