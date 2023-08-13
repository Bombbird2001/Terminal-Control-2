package com.bombbird.terminalcontrol2.entities

import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.DARK_GREEN
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.removeEntityOnMainThread
import ktx.ashley.with

/** Range ring class for displaying rings in intervals set by the user */
class RangeRing(radiusNm: Int) {
    private val topLabel: RangeRingLabel
    private val bottomLabel: RangeRingLabel

    val entity = getEngine(true).entityOnMainThread(true) {
        with<Position> {
            x = 0f
            y = 0f
        }
        with<GCircle> {
            radius = nmToPx(radiusNm)
        }
        with<SRColor> {
            color = DARK_GREEN
        }
        topLabel = RangeRingLabel(radiusNm, true)
        bottomLabel = RangeRingLabel(radiusNm, false)
    }

    /** Removes all entities belonging to this range ring from the client engine */
    fun removeRing() {
        val clientEngine = getEngine(true)
        clientEngine.removeEntityOnMainThread(entity, true)
        clientEngine.removeEntityOnMainThread(topLabel.entity, true)
        clientEngine.removeEntityOnMainThread(bottomLabel.entity, true)
    }

    /** Inner label class to store information for the 2 labels for each range ring */
    private class RangeRingLabel(radiusNm: Int, top: Boolean) {
        val entity = getEngine(true).entityOnMainThread(true) {
            with<Position> {
                x = 0f
                y = if (top) nmToPx(radiusNm) else -nmToPx(radiusNm)
            }
            with<GenericLabel> {
                updateText("${radiusNm}nm")
                updateStyle("RangeRing")
                xOffset = -label.prefWidth / 2
                yOffset = if (top) 10f else -10 - label.height
            }
            with<ConstantZoomSize>()
        }
    }
}