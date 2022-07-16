package com.bombbird.terminalcontrol2.graphics

import com.bombbird.terminalcontrol2.global.*


/** Utility object to run required functions after a screen resize, to ensure UI fits */
object ScreenSize {

    /**
     * Sets [WIDTH] and [HEIGHT] to the new dimension, then calls [calculateUISize] to calculate the new UI size
     * @param newWidth the updated width retrieved from libGdx
     * @param newHeight the updated height retrieved from libGdx
     * */
    fun updateScreenSizeParameters(newWidth: Int, newHeight: Int) {
        WIDTH = newWidth.toFloat()
        HEIGHT = newHeight.toFloat()
        calculateUISize()
    }

    /**
     * Calculates and sets the new UI size to [UI_WIDTH] and [UI_HEIGHT]
     *
     * Results will differ on whether the new aspect ratio is larger or smaller than the default world aspect ratio
     * */
    private fun calculateUISize() {
        val ar = WIDTH / HEIGHT
        val defaultAr = WORLD_WIDTH / WORLD_HEIGHT
        if (ar < defaultAr) {
            // Scale to fill height
            UI_HEIGHT = WORLD_HEIGHT
            UI_WIDTH = WORLD_HEIGHT / HEIGHT * WIDTH
        } else {
            // Scale to fill width
            UI_WIDTH = WORLD_WIDTH
            UI_HEIGHT = WORLD_WIDTH / WIDTH * HEIGHT
        }
    }
}