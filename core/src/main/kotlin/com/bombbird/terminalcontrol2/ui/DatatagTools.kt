package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import com.bombbird.terminalcontrol2.utilities.modulateHeading
import com.bombbird.terminalcontrol2.utilities.pxpsToKt
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.ashley.has
import ktx.scene2d.Scene2DSkin
import kotlin.math.roundToInt

/** Helper file for dealing with [Datatag] matters */

// Spacing between each line on the datatag
const val LABEL_PADDING = 7

// Datatag value keys
const val CALLSIGN = "Callsign"
const val CALLSIGN_RECAT = "Callsign + Wake"
const val ICAO_TYPE = "Aircraft type"
const val ICAO_TYPE_WAKE = "Aircraft type + Wake"
const val ALTITUDE_FULL = "Full altitude info"
const val ALTITUDE = "Current altitude"
const val CLEARED_ALT = "Cleared altitude"
const val HEADING = "Heading"
const val LAT_CLEARED = "Cleared waypoint/heading"
const val SIDSTARAPP_CLEARED = "Cleared SID/STAR/Approach"
const val GROUND_SPEED = "Ground speed"
const val CLEARED_IAS = "Cleared speed"
const val AIRPORT = "Airport"

/** Updates the text for the labels of the [datatag], and sets the new sizes accordingly */
fun updateDatatagText(datatag: Datatag, newText: Array<String>) {
    for (i in 0 until datatag.labelArray.size) {
        datatag.labelArray[i].apply {
            setText(newText[i])
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
    else if (showBorder) when (flightType) {
        FlightType.DEPARTURE -> "Green"
        FlightType.ARRIVAL -> "Blue"
        FlightType.EN_ROUTE -> "Gray"
        else -> {
            Log.info("Datatag", "Unknown flight type $flightType")
            ""
        }
    } else ""
    datatag.imgButton.style = Scene2DSkin.defaultSkin.get("Datatag${colour}${background}", ImageButton.ImageButtonStyle::class.java)
    datatag.currentDatatagStyle = "Datatag${colour}${background}"

    // Try to fix when datatag remains orange
    if (!datatag.flashingOrange && colour == "Orange") {
        updateDatatagStyle(datatag, flightType, false)
    }
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
    CLIENT_SCREEN?.sendAircraftDatatagPositionUpdate(aircraft, datatag.xOffset, datatag.yOffset, datatag.minimised, flash)
    if (flash) {
        datatag.flashTimer.scheduleTask(object: Timer.Task() {
            override fun run() {
                // Every 1 second, update the datatag flashing orange status, and call updateDatatagStyle
                datatag.flashingOrange = !datatag.flashingOrange
                updateDatatagStyle(datatag, aircraft.entity[FlightType.mapper]?.type ?: return, CLIENT_SCREEN?.selectedAircraft == aircraft)
            }
        }, 0f, 1f)
    } else {
        datatag.flashTimer.clear()
        datatag.flashing = false
        datatag.flashingOrange = false
        updateDatatagStyle(datatag, aircraft.entity[FlightType.mapper]?.type ?: return, CLIENT_SCREEN?.selectedAircraft == aircraft)
    }
}

/** Updates the label style to use smaller fonts when radar is zoomed out */
fun updateDatatagLabelSize(datatag: Datatag, smaller: Boolean) {
    datatag.labelArray.forEach {  label ->
        label.style = Scene2DSkin.defaultSkin.get(if (smaller) "DatatagSmall" else "Datatag", LabelStyle::class.java)
        label.pack()
    }
    updateDatatagSize(datatag)
    datatag.smallLabelFont = smaller
}

/**
 * Updates the spacing, in px, between each line label to the new global set datatag spacing
 * @param datatag the datatag to update
 * */
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
                CLIENT_SCREEN?.sendAircraftDatatagPositionUpdate(aircraft, datatag.xOffset, datatag.yOffset, datatag.minimised, datatag.flashing)
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
                Gdx.app.postRunnable { updateDatatagText(datatag, getNewDatatagLabelText(aircraft.entity, datatag.minimised)) }
            } else datatag.tapTimer.scheduleTask(object : Timer.Task() {
                override fun run() {
                    datatag.clicks = 0
                }
            }, 0.2f)
            Gdx.app.postRunnable {
                CLIENT_SCREEN?.setUISelectedAircraft(aircraft)
                if (!datatag.renderLast) {
                    CLIENT_SCREEN?.aircraft?.values()?.forEach { it.entity[Datatag.mapper]?.renderLast = false }
                    remove()
                    CLIENT_SCREEN?.addToConstZoomStage(this) // Re-add to uiStage in order for position to be increased
                    datatag.renderLast = true
                }
            }
        }
    }
}

/**
 * Gets a new array of strings for the label text, depending on the aircraft's [Controllable] state and whether it
 * is minimised
 * @param entity the aircraft to generate the datatag for
 * @param minimised whether the datatag should be minimised
 * @return an array of string denoting each line in the datatag
 * */
fun getNewDatatagLabelText(entity: Entity, minimised: Boolean): Array<String> {
    val controllable = entity[Controllable.mapper]
    return if (minimised || controllable == null || controllable.sectorId != CLIENT_SCREEN?.playerSector) getMinimisedLabelText(entity)
    else getExpandedLabelText(entity)
}

/**
 * Gets a new array of strings for the minimised datatag, based on the player's datatag format
 * @param entity the aircraft to generate the minimised datatag for
 * @return an array of string denoting each line in the datatag
 * */
private fun getMinimisedLabelText(entity: Entity): Array<String> {
    val labelText = arrayOf("", "", "", "")
    // Temporary label format TODO change based on datatag format in use
    val aircraftInfo = entity[AircraftInfo.mapper] ?: return labelText
    val radarData = entity[RadarData.mapper] ?: return labelText
    val groundTrack = entity[GroundTrack.mapper] ?: return labelText
    val latestClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState

    val callsign = aircraftInfo.icaoCallsign
    val recat = aircraftInfo.aircraftPerf.recat
    val alt = (radarData.altitude.altitudeFt / 100).roundToInt()
    val groundSpd = pxpsToKt(groundTrack.trackVectorPxps.len()).roundToInt()
    val clearedAlt = if (entity.has(VisualCaptured.mapper)) "VIS"
    else if (entity.has(GlideSlopeCaptured.mapper)) "GS"
    else latestClearance?.let { it.clearedAlt / 100 }?.toString() ?: ""
    val icaoType = aircraftInfo.icaoType

    labelText[0] = "$callsign/$recat"
    labelText[1] = if (System.currentTimeMillis() % 4000 < 2500) "$alt $groundSpd" else "$clearedAlt $icaoType"

    return labelText
}

/**
 * Gets a new array of strings for the expanded datatag, based on the player's datatag format
 * @param entity the aircraft to generate the minimised datatag for
 * @return an array of string denoting each line in the datatag
 * */
private fun getExpandedLabelText(entity: Entity): Array<String> {
    val labelText = arrayOf("", "", "", "")
    // Temporary label format TODO change based on datatag format in use
    val aircraftInfo = entity[AircraftInfo.mapper] ?: return labelText
    val radarData = entity[RadarData.mapper] ?: return labelText
    val cmdTarget = entity[CommandTarget.mapper] ?: return labelText
    val groundTrack = entity[GroundTrack.mapper] ?: return labelText
    val latestClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState

    val callsign = aircraftInfo.icaoCallsign
    val acInfo = "${aircraftInfo.icaoType}/${aircraftInfo.aircraftPerf.wakeCategory}/${aircraftInfo.aircraftPerf.recat}"
    val alt = (radarData.altitude.altitudeFt / 100).roundToInt()
    val vs = if (radarData.speed.vertSpdFpm > 150) '^' else if (radarData.speed.vertSpdFpm < -150) 'v' else '='
    val clearedAlt = latestClearance?.clearedAlt?.let { "${if (latestClearance.expedite) "=>>" else "=>"} ${it / 100}" } ?: ""
    val cmdAlt = if (entity.has(GlideSlopeCaptured.mapper)) "GS" else if (entity.has(VisualCaptured.mapper)) "VIS"
    else (cmdTarget.targetAltFt / 100f).roundToInt().toString()
    val hdg = modulateHeading((convertWorldAndRenderDeg(radarData.direction.trackUnitVector.angleDeg()) + MAG_HDG_DEV).roundToInt().toFloat()).roundToInt()
    val cmdHdg = cmdTarget.targetHdgDeg.roundToInt()
    val groundSpd = pxpsToKt(groundTrack.trackVectorPxps.len()).roundToInt()
    val clearedLateral = if (entity.has(LocalizerCaptured.mapper)) "LOC" else if (entity.has(VisualCaptured.mapper)) "VIS"
    else latestClearance?.route?.let {
        if (it.size == 0) null else CLIENT_SCREEN?.waypoints?.get((it[0] as? Route.WaypointLeg)?.wptId)
    }?.entity?.get(WaypointInfo.mapper)?.wptName ?: latestClearance?.vectorHdg?.toString() ?: ""
    val sidStar = latestClearance?.routePrimaryName ?: ""

    labelText[0] = "$callsign $acInfo"
    labelText[1] = "$alt $vs $cmdAlt $clearedAlt"
    labelText[2] = "$hdg $cmdHdg $clearedLateral $sidStar"
    labelText[3] = "$groundSpd ${latestClearance?.clearedIas}"

    return labelText
}
