package com.bombbird.terminalcontrol2.traffic

import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.global.MAX_ALT
import com.bombbird.terminalcontrol2.global.VERT_SEP
import ktx.collections.GdxArray
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Helper class for managing wake separation between entities */
class WakeManager {

    private val startingAltitude = floor(getLowestAirportElevation() / VERT_SEP).roundToInt() * VERT_SEP
    private val wakeLevels = Array<GdxArray<WakeZone>>(ceil((MAX_ALT + 1500f) / VERT_SEP).roundToInt() - startingAltitude / VERT_SEP) {
        GdxArray()
    }

    /**
     * Adds a wake zone to the respective wake level array, based on its wake altitude
     * @param wakeZone the wake zone to add
     */
    fun addWakeZone(wakeZone: WakeZone) {

    }

    /**
     * Removes the input wake zone from its respective wake level array
     * @param wakeZone the wake zone to add
     */
    fun removeWakeZone(wakeZone: WakeZone) {

    }
}