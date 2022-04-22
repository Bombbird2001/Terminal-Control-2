package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.bombbird.terminalcontrol2.components.Datatag
import com.bombbird.terminalcontrol2.global.Constants
import ktx.scene2d.Scene2DSkin

/** Helper object for dealing with [Datatag] matters */
object DatatagTools {
    const val LABEL_PADDING = 7

    /** Updates the text for the labels of the [datatag], and sets the new sizes accordingly */
    fun updateText(datatag: Datatag, newText: Array<String>) {
        for (i in 0 until datatag.labelArray.size) {
            datatag.labelArray[i].apply {
                setText(newText[i])
                pack()
            }
        }
        updateImgButtonSize(datatag)
    }

    /** Updates the style for the background [Datatag.imgButton] */
    fun updateStyle(datatag: Datatag, newStyle: String) {
        datatag.imgButton.style = Scene2DSkin.defaultSkin.get(newStyle, ImageButton.ImageButtonStyle::class.java)
    }

    /** Updates the spacing, in px, between each line label */
    fun updateLineSpacing(datatag: Datatag, newSpacing: Short) {
        datatag.lineSpacing = newSpacing
        updateImgButtonSize(datatag)
    }

    /** Re-calculates and updates the size of the background [Datatag.imgButton] */
    private fun updateImgButtonSize(datatag: Datatag) {
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

    /** Adds a dragListener to the background [Datatag.clickSpot] */
    fun addDragListener(datatag: Datatag) {
        datatag.clickSpot.apply {
            Constants.GAME.gameClientScreen?.addToUIStage(this) // Add to uiStage in order for drag gesture to be detected by inputMultiplexer
            zIndex = 0
            addListener(object: DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    datatag.xOffset += (x - this@apply.width / 2)
                    datatag.yOffset += (y - this@apply.height / 2)
                    datatag.dragging = true
                    event?.handle()
                }
            })
        }
    }
}