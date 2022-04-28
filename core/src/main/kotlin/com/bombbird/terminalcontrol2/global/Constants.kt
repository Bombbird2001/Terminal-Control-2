package com.bombbird.terminalcontrol2.global

import com.badlogic.ashley.core.Engine
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.utilities.PhysicsTools
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

    /** Physics */
    const val MAX_VERT_ACC = 0.25f * PhysicsTools.GRAVITY_ACCELERATION_MPS2
    const val MIN_VERT_ACC = -0.25f * PhysicsTools.GRAVITY_ACCELERATION_MPS2
    const val MAX_LOW_SPD_ANGULAR_SPD = 3f
    const val MAX_HIGH_SPD_ANGULAR_SPD = 1.5f
    const val MAX_ANGULAR_ACC = 0.3f
    const val MAX_JERK = 1f
    const val MAX_VS = 4000f
    const val MAX_ACC = 3.2f

    /** List of available airports (can be modified, but don't) */
    val AVAIL_AIRPORTS = GdxArray<String>(arrayOf("TCTP", "TCWS", "TCTT", "TCBB", "TCHH", "TCBD", "TCMD", "TCPG"))

    /** The current game instance (can be modified, but don't), client engine, and server engine (if [TerminalControl2.gameServer] exists, else throws a [RuntimeException] when accessed) */
    lateinit var GAME: TerminalControl2
    val CLIENT_ENGINE: Engine
        get() = GAME.engine
    val SERVER_ENGINE: Engine
        get() {
            return GAME.gameServer?.engine ?: throw RuntimeException("Attempted to access a non-existent gameServer's engine")
        }
    val CLIENT_SCREEN: RadarScreen?
        get() = GAME.gameClientScreen

    /** Server target refresh rates (in Hz) */
    const val UPDATE_RATE_LOW_FREQ = 1 // Low frequency update rate
    const val SERVER_UPDATE_RATE = 60 // Server game loop
    const val SERVER_TO_CLIENT_UPDATE_RATE_FAST = 20 // Frequently updated data such as aircraft position, navigation, etc.
    const val SERVER_TO_CLIENT_UPDATE_RATE_SLOW = 0.1f // Not so frequently updated data such as thunderstorm cells
    const val SERVER_METAR_UPDATE_INTERVAL_MINS = 5 // Check for METAR update every 5 minutes

    /** Client buffer sizes */
    const val WRITE_BUFFER_SIZE = 4096
    const val READ_BUFFER_SIZE = 4096

    /** Zoom threshold to switch between small and large datatag fonts */
    const val DATATAG_ZOOM_THRESHOLD = 1.4f
}