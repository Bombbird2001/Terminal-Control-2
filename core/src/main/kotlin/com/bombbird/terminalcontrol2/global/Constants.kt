package com.bombbird.terminalcontrol2.global

import com.badlogic.ashley.core.Engine
import com.bombbird.terminalcontrol2.TerminalControl2
import ktx.collections.GdxArray

/** Global constants for use, cannot/should not be modified */
object Constants {

    /** Default world size without corrections */
    const val WORLD_WIDTH = 1920f
    const val WORLD_HEIGHT = 1080f

    /** Menu button sizes */
    const val BIG_BUTTON_WIDTH = 600f
    const val BIG_BUTTON_HEIGHT = 130f
    const val BOTTOM_BUTTON_MARGIN = 90f

    /** radarScreen defaults */
    const val DEFAULT_ZOOM_NM = 100
    const val MIN_ZOOM_NM = 10
    const val MAX_ZOOM_NM = 150
    const val DEFAULT_ZOOM_IN_NM = 30
    const val ZOOM_THRESHOLD_NM = 65
    const val CAM_ANIM_TIME = 0.3f
    const val RWY_WIDTH_PX_ZOOM_1 = 5f
    const val RWY_WIDTH_CHANGE_PX_PER_ZOOM = 3f
    const val AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 = 20f
    const val AIRCRAFT_BLIP_LENGTH_CHANGE_PX_PER_ZOOM = 4f

    /** List of available airports (can be modified, but don't) */
    val AVAIL_AIRPORTS = GdxArray<String>(arrayOf("TCTP", "TCWS", "TCTT", "TCBB", "TCHH", "TCBD", "TCMD", "TCPG"))

    /** The current game instance, engine (can be modified, but don't) */
    lateinit var GAME: TerminalControl2
    val ENGINE: Engine
        get() = GAME.engine

    /** Server target refresh rates (in Hz) */
    const val SERVER_UPDATE_RATE = 60 // Server game loop
    const val SERVER_TO_CLIENT_UPDATE_RATE_FAST = 20 // Frequently updated data such as aircraft position, navigation, etc.
    const val SERVER_TO_CLIENT_UPDATE_RATE_SLOW = 0.1f // Not so frequently updated data such as thunderstorm cells
}