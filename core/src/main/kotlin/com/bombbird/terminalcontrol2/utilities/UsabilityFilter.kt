package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.global.GAME
import java.time.LocalTime

/** Helper interface that contains data and functions to determine whether a SID, STAR or approach is valid for use
 * under the given conditions */
interface UsabilityFilter {
    companion object {
        const val DAY_AND_NIGHT: Byte = 0
        const val DAY_ONLY: Byte = 1
        const val NIGHT_ONLY: Byte = 2

        /**
         * Checks whether it is night given the current game's night mode settings and the time on the host device
         *
         * This method should only be used on the host server
         * @return whether night mode operations are active for the game
         * */
        fun isNight(): Boolean {
            GAME.gameServer?.apply {
                if (nightModeStart == -1 && nightModeEnd == -1) return false
                if (nightModeStart == nightModeEnd) return true
                val timeNow = LocalTime.now()
                val timeValue = timeNow.hour * 100 + timeNow.minute
                // For start and end that falls within the same day e.g. 0100 to 0700
                if (timeValue in nightModeStart..nightModeEnd) return true
                // For start on previous and end next day e.g. 2200 to 0600
                if (timeValue in nightModeStart..2359 && nightModeEnd < nightModeStart) return true
            }
            return false
        }
    }

    val timeRestriction: Byte

    /**
     * Checks whether the current entity is usable at the current time
     *
     * This method should only be used on the host server to determine spawning information
     * @return whether this entity can be used for the current time
     * */
    fun isUsableForDayNight(): Boolean {
        if (timeRestriction == DAY_AND_NIGHT) return true
        val night = isNight()
        if (night && timeRestriction == NIGHT_ONLY) return true
        if (!night && timeRestriction == DAY_ONLY) return true
        return false
    }
}