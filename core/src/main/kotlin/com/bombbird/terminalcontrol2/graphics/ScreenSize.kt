package com.bombbird.terminalcontrol2.graphics

import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables

/** Utility object to run required functions after a screen resize, to ensure UI fits */
object ScreenSize {

    /** Sets [Variables.WIDTH] and [Variables.HEIGHT] to the new dimension, then calls [calculateUISize] to calculate the new UI size */
    fun updateScreenSizeParameters(newWidth: Int, newHeight: Int) {
        Variables.WIDTH = newWidth.toFloat()
        Variables.HEIGHT = newHeight.toFloat()
        calculateUISize()
    }

    /** Calculates and sets the new UI size to [Variables.UI_WIDTH] and [Variables.UI_HEIGHT]
     *
     * Results will differ on whether the new aspect ratio is larger or smaller than the default world aspect ratio
     * */
    private fun calculateUISize() {
        val ar = Variables.WIDTH / Variables.HEIGHT
        val defaultAr = Constants.WORLD_WIDTH / Constants.WORLD_HEIGHT
        if (ar < defaultAr) {
            // Scale to fill height
            Variables.UI_HEIGHT = Constants.WORLD_HEIGHT
            Variables.UI_WIDTH = Constants.WORLD_HEIGHT / Variables.HEIGHT * Variables.WIDTH
        } else {
            // Scale to fill width
            Variables.UI_WIDTH = Constants.WORLD_WIDTH
            Variables.UI_HEIGHT = Constants.WORLD_WIDTH / Variables.WIDTH * Variables.HEIGHT
        }
    }
}