package com.bombbird.terminalcontrol2.global

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

    /** List of available airports (can be modified, but don't) */
    val AVAIL_AIRPORTS = GdxArray<String>(arrayOf("TCTP", "TCWS", "TCTT", "TCBB", "TCHH", "TCBD", "TCMD", "TCPG"))
}