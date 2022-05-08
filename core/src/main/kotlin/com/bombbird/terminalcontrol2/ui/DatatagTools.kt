package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.MathTools
import ktx.ashley.get
import ktx.math.plus
import ktx.math.times
import ktx.scene2d.Scene2DSkin
import kotlin.math.roundToInt

/** Helper object for dealing with [Datatag] matters */
object DatatagTools {
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
    fun updateText(datatag: Datatag, newText: Array<String>) {
        for (i in 0 until datatag.labelArray.size) {
            datatag.labelArray[i].apply {
                setText(newText[i])
                pack()
            }
        }
        updateDatatagSize(datatag)
    }

    /** Updates the style for the background [Datatag.imgButton] */
    fun updateStyle(datatag: Datatag, flightType: Byte) {
        val noBG = if (true) "NoBG" else "" // TODO change depending on datatag display setting
        datatag.imgButton.style = Scene2DSkin.defaultSkin.get(when (flightType) {
            FlightType.DEPARTURE -> "DatatagGreen$noBG"
            FlightType.ARRIVAL -> "DatatagBlue$noBG"
            FlightType.EN_ROUTE -> "DatatagGray$noBG"
            else -> {
                Gdx.app.log("Datatag", "Unknown flight type $flightType")
                "DatatagNoBG"
            }
        }, ImageButton.ImageButtonStyle::class.java)
    }

    /** Updates the label style to use smaller fonts when radar is zoomed out */
    fun updateLabelSize(datatag: Datatag, smaller: Boolean) {
        datatag.labelArray.forEach {  label ->
            label.style = Scene2DSkin.defaultSkin.get(if (smaller) "DatatagSmall" else "Datatag", LabelStyle::class.java)
            label.pack()
        }
        updateDatatagSize(datatag)
        datatag.smallLabelFont = smaller
    }

    /** Updates the spacing, in px, between each line label */
    fun updateLineSpacing(datatag: Datatag, newSpacing: Short) {
        datatag.lineSpacing = newSpacing
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
            height += datatag.lineSpacing
        }
        datatag.imgButton.setSize(maxWidth + LABEL_PADDING * 2, height + LABEL_PADDING * 2)
        datatag.clickSpot.setSize(maxWidth + LABEL_PADDING * 2, height + LABEL_PADDING * 2)
    }

    /** Adds a dragListener and changeListener to the background [Datatag.clickSpot] */
    fun addInputListeners(datatag: Datatag) {
        datatag.clickSpot.apply {
            Constants.GAME.gameClientScreen?.addToConstZoomStage(this) // Add to uiStage in order for drag gesture to be detected by inputMultiplexer
            zIndex = 0
            addListener(object: DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    datatag.xOffset += (x - this@apply.width / 2)
                    datatag.yOffset += (y - this@apply.height / 2)
                    datatag.dragging = true
                    event?.handle()
                }
            })
            addListener(object: ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    if (datatag.dragging) {
                        datatag.dragging = false
                        return
                    }
                    Constants.CLIENT_SCREEN?.setSelectedAircraft()
                }
            })
        }
    }

    /** Gets a new array of strings for the label text, depending on the aircraft's [Controllable] state and whether it
     * has [MinimisedDatatag]
     * */
    fun getNewLabelText(entity: Entity): Array<String> {
        val minimisedDatatag = entity[MinimisedDatatag.mapper]
        val controllable = entity[Controllable.mapper]
        return if (minimisedDatatag != null || controllable == null || controllable.sectorId == SectorInfo.TOWER || controllable.sectorId == SectorInfo.CENTRE) getMinimisedLabelText(entity)
        else getExpandedLabelText(entity) // TODO Additional check for whether aircraft is in your sector
    }

    /** Gets a nw array of strings for the minimised datatag, based on the player's datatag format */
    private fun getMinimisedLabelText(entity: Entity): Array<String> {
        val labelText = arrayOf("", "", "", "")
        // Temporary label format TODO change based on datatag format in use
        val aircraftInfo = entity[AircraftInfo.mapper] ?: return labelText
        val radarData = entity[RadarData.mapper] ?: return labelText
        val cmdTarget = entity[CommandTarget.mapper] ?: return labelText
        val affectedByWind = entity[AffectedByWind.mapper]

        val callsign = aircraftInfo.icaoCallsign
        val recat = aircraftInfo.aircraftPerf.recat
        val alt = (radarData.altitude.altitudeFt / 100).roundToInt()
        val groundSpd = (radarData.direction.trackUnitVector.times(radarData.speed.speedKts) + (affectedByWind?.windVectorPx?.times(MathTools.pxpsToKt(1f)) ?: Vector2())).len().roundToInt()
        val cmdAlt = (cmdTarget.targetAltFt / 100).roundToInt()
        val icaoType = aircraftInfo.icaoType

        labelText[0] = "$callsign/$recat"
        labelText[1] = if (System.currentTimeMillis() % 4000 < 2000) "$alt $groundSpd" else "$cmdAlt $icaoType"

        return labelText
    }

    /** Gets a nw array of strings for the expanded datatag, based on the player's datatag format */
    private fun getExpandedLabelText(entity: Entity): Array<String> {
        val labelText = arrayOf("", "", "", "")
        // Temporary label format TODO change based on datatag format in use
        val aircraftInfo = entity[AircraftInfo.mapper] ?: return labelText
        val radarData = entity[RadarData.mapper] ?: return labelText
        val cmdTarget = entity[CommandTarget.mapper] ?: return labelText
        val cmdRoute = entity[CommandRoute.mapper]
        val affectedByWind = entity[AffectedByWind.mapper]

        val callsign = aircraftInfo.icaoCallsign
        val acInfo = "${aircraftInfo.icaoType}/${aircraftInfo.aircraftPerf.wakeCategory}/${aircraftInfo.aircraftPerf.recat}"
        val alt = (radarData.altitude.altitudeFt / 100).roundToInt()
        val vs = if (radarData.speed.vertSpdFpm > 150) '^' else if (radarData.speed.vertSpdFpm < -150) 'v' else '='
        val cmdAlt = (cmdTarget.targetAltFt / 100).roundToInt()
        val hdg = MathTools.modulateHeading((MathTools.convertWorldAndRenderDeg(radarData.direction.trackUnitVector.angleDeg()) + Variables.MAG_HDG_DEV).roundToInt().toFloat()).roundToInt()
        val cmdHdg = cmdTarget.targetHdgDeg.roundToInt()
        val clearedAlt = "=> Cleared alt"
        val groundSpd = (radarData.direction.trackUnitVector.times(radarData.speed.speedKts) + (affectedByWind?.windVectorPx?.times(MathTools.pxpsToKt(1f)) ?: Vector2())).len().roundToInt()
        val directWpt = cmdRoute?.route?.legs?.let {
            if (it.size == 0) null else Constants.CLIENT_SCREEN?.waypoints?.get((it[0] as? Route.WaypointLeg)?.wptId)
        }?.entity?.get(WaypointInfo.mapper)?.wptName ?: ""
        val sidStar = cmdRoute?.primaryName ?: ""

        labelText[0] = "$callsign $acInfo"
        labelText[1] = "$alt $vs $cmdAlt $clearedAlt"
        labelText[2] = "$hdg $cmdHdg $directWpt $sidStar"
        labelText[3] = "$groundSpd ${cmdTarget.targetIasKt}"

        return labelText
    }
}