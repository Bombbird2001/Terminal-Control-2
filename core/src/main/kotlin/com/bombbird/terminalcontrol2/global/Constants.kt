package com.bombbird.terminalcontrol2.global

import com.bombbird.terminalcontrol2.TerminalControl2
import ktx.collections.GdxArray

/** Global constants for use, cannot/should not be modified */
object Constants {

    /** Default world size without corrections */
    const val WORLD_WIDTH = 1440f
    const val WORLD_HEIGHT = 810f

    /** Menu button sizes */
    const val BIG_BUTTON_WIDTH = 450f
    const val BIG_BUTTON_HEIGHT = 100f
    const val BOTTOM_BUTTON_MARGIN = 70f

    /** Unit conversions */
    const val NM_TO_PX = 20f

    /** radarScreen defaults */
    const val DEFAULT_ZOOM_NM = 100
    const val MIN_ZOOM_NM = 10
    const val MAX_ZOOM_NM = 150
    const val CAM_ANIM_TIME = 0.5f

    /** List of available airports (can be modified, but don't) */
    val AVAIL_AIRPORTS = GdxArray<String>(arrayOf("TCTP", "TCWS", "TCTT", "TCBB", "TCHH", "TCBD", "TCMD", "TCPG"))

    /** The current game instance (can be modified, but don't) */
    lateinit var GAME: TerminalControl2
}