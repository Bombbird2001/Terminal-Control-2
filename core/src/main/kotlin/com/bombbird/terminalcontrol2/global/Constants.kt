package com.bombbird.terminalcontrol2.global

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.utilities.GRAVITY_ACCELERATION_MPS2
import ktx.collections.GdxArray

/** Global constants for use, cannot/should not be modified */

/** Default world size without corrections */
const val WORLD_WIDTH = 1920f
const val WORLD_HEIGHT = 1080f

/** Menu button sizes */
const val BUTTON_WIDTH_BIG = 600f
const val BUTTON_WIDTH_MEDIUM = 250f
const val BUTTON_HEIGHT_BIG = 130f
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
const val MAX_VERT_ACC = 0.25f * GRAVITY_ACCELERATION_MPS2
const val MIN_VERT_ACC = -0.25f * GRAVITY_ACCELERATION_MPS2
const val MAX_LOW_SPD_ANGULAR_SPD = 3f
const val MAX_HIGH_SPD_ANGULAR_SPD = 1.5f
const val MAX_ANGULAR_ACC = 1f
const val MAX_JERK = 1f
const val MAX_VS = 4000f
const val MAX_VS_EXPEDITE = 6000f
const val MAX_ACC = 3f

/** Approach constants */
const val VIS_MAX_DIST_NM: Byte = 10
const val VIS_GLIDE_ANGLE_DEG = 3f
const val LOC_INNER_ARC_DIST_NM: Byte = 10
const val LOC_INNER_ARC_ANGLE_DEG = 35f
const val LOC_OUTER_ARC_ANGLE_DEG = 10f
const val NO_APP_SELECTION = "Approach"
const val TRANS_PREFIX = "Via "
const val NO_TRANS_SELECTION = "..."

/** List of available airports (can be modified, but don't) */
val AVAIL_AIRPORTS = GdxArray<String>(arrayOf("TCTP", "TCWS", "TCTT", "TCBB", "TCHH", "TCBD", "TCMD", "TCPG"))

/** Application platform type */
val APP_TYPE = Gdx.app.type

/** The current game instance (can be modified, but don't), client engine, and server engine (if [TerminalControl2.gameServer] exists, else throws a [RuntimeException] when accessed) */
lateinit var GAME: TerminalControl2
private val CLIENT_ENGINE: Engine
    get() = GAME.engine
private val SERVER_ENGINE: Engine
    get() {
        return GAME.gameServer?.engine ?: throw RuntimeException("Attempted to access a non-existent gameServer's engine")
    }
val CLIENT_SCREEN: RadarScreen?
    get() = GAME.gameClientScreen

/** Returns the appropriate engine given [onClient] */
fun getEngine(onClient: Boolean): Engine {
    return if (onClient) CLIENT_ENGINE else SERVER_ENGINE
}

/** Server target refresh rates (in Hz) */
const val SERVER_UPDATE_RATE = 60 // Server game loop
const val SERVER_TO_CLIENT_UPDATE_RATE_FAST = 20 // Frequently updated data such as aircraft position, navigation, etc.
const val SERVER_TO_CLIENT_UPDATE_RATE_SLOW = 0.1f // Not so frequently updated data such as thunderstorm cells
const val SERVER_METAR_UPDATE_INTERVAL_MINS = 5 // Check for METAR update every 5 minutes

/** Client buffer sizes */
const val CLIENT_WRITE_BUFFER_SIZE = 4096
const val CLIENT_READ_BUFFER_SIZE = 8192

/** Default Gdx collections initial sizes */
const val AIRPORT_SIZE = 6
const val AIRCRAFT_SIZE = 35
const val SECTOR_COUNT_SIZE = 5
const val PUBLISHED_HOLD_SIZE = 30

/** Zoom threshold to switch between small and large datatag fonts */
const val DATATAG_ZOOM_THRESHOLD = 1.4f
