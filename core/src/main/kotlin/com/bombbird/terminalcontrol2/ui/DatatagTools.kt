package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.utilities.MathTools
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
    fun updateStyle(datatag: Datatag, newStyle: String) {
        datatag.imgButton.style = Scene2DSkin.defaultSkin.get(newStyle, ImageButton.ImageButtonStyle::class.java)
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

    /** Gets a new array of strings for the label text, based on the player's datatag format */
    fun getNewLabelText(aircraftInfo: AircraftInfo, radarData: RadarData, cmdTarget: CommandTarget, affectedByWind: AffectedByWind?): Array<String> {
        val labelText = arrayOf("", "", "", "")
        // Temporary label format TODO change based on datatag format in use
        val callsign = aircraftInfo.icaoCallsign
        val acInfo = "${aircraftInfo.icaoType}/${aircraftInfo.aircraftPerf.wakeCategory}/${aircraftInfo.aircraftPerf.recat}"
        val alt = (radarData.altitude.altitudeFt / 100).roundToInt()
        val vs = if (radarData.speed.vertSpdFpm > 150) '^' else if (radarData.speed.vertSpdFpm < -150) 'v' else '='
        val cmdAlt = (cmdTarget.targetAltFt / 100).roundToInt()
        val clearedAlt = "=> Cleared alt"
        val groundSpd = (radarData.direction.trackUnitVector.times(radarData.speed.speedKts) + (affectedByWind?.windVector?.times(MathTools.pxpsToKt(1f)) ?: Vector2())).len().roundToInt()
        labelText[0] = "$callsign $acInfo"
        labelText[1] = "$alt $vs $cmdAlt $clearedAlt"
        labelText[2] = "Cleared SID/STAR/APP"
        labelText[3] = "$groundSpd ${cmdTarget.targetIasKt}"

        return labelText
    }
}