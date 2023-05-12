package com.bombbird.terminalcontrol2.global

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.utilities.GRAVITY_ACCELERATION_MPS2
import java.math.BigInteger

/** Global constants for use, cannot/should not be modified */

/** Default world size without corrections */
const val WORLD_WIDTH = 1920f
const val WORLD_HEIGHT = 1080f

/** Menu button sizes */
const val BUTTON_WIDTH_MAIN = 400f
const val BUTTON_WIDTH_BIG = 600f
const val BUTTON_WIDTH_MEDIUM = 250f
const val BUTTON_WIDTH_SMALL = 190f
const val BUTTON_HEIGHT_MAIN = 160f
const val BUTTON_HEIGHT_BIG = 130f
const val BOTTOM_BUTTON_MARGIN = 90f

/** RadarScreen defaults */
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
const val AIRCRAFT_TRAIL_LENGTH_PX_ZOOM_1 = 3.5f
const val AIRCRAFT_TRAIL_LENGTH_CHANGE_PX_PER_ZOOM = 0.7f

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

/** Control state constants */
const val TRACK_EXTRAPOLATE_TIME_S = 30f

/** Aircraft initial spawn */
const val ARRIVAL_SPAWN_EXTEND_DIST_NM = 0.5f

/** Route zone deviation tolerances */
const val ROUTE_RNP_NM = 1.5f

/** Wake turbulence constants */
const val WAKE_WIDTH_NM = 0.15f
const val WAKE_DOT_SPACING_NM = 0.5f
const val MAX_WAKE_DOTS = 16

/** List of available airports */
val AVAIL_AIRPORTS = arrayOf("TCTP", "TCWS", "TCTT", "TCBB", "TCHH", "TCBD", "TCMD", "TCPG")

/** Application platform type */
val APP_TYPE = Gdx.app?.type

/**
 * The current game instance (can be modified, but don't), client engine, and server engine (if [TerminalControl2.gameServer]
 * exists, else throws a [RuntimeException] when accessed)
 * */
lateinit var GAME: TerminalControl2
val isGameInitialised: Boolean
    get() = ::GAME.isInitialized
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

/** Constant addresses */
const val LOCALHOST = "127.0.0.1"

/** Relay server HTTPS endpoint port */
const val RELAY_ENDPOINT_PORT = 57775
const val RELAY_GAMES_PATH = "/games"
const val RELAY_GAME_AUTH_PATH = "/gameAuth"
const val RELAY_GAME_CREATE_PATH = "/gameCreate"

/** Encryption constants */
/** 2048-bit DH prime from RFC 7919 ffdhe2048 */
val DIFFIE_HELLMAN_PRIME = BigInteger(
    "FFFFFFFFFFFFFFFFADF85458A2BB4A9AAFDC5620273D3CF1" +
            "D8B9C583CE2D3695A9E13641146433FBCC939DCE249B3EF9" +
            "7D2FE363630C75D8F681B202AEC4617AD3DF1ED5D5FD6561" +
            "2433F51F5F066ED0856365553DED1AF3B557135E7F57C935" +
            "984F0C70E0E68B77E2A689DAF3EFE8721DF158A136ADE735" +
            "30ACCA4F483A797ABC0AB182B324FB61D108A94BB2C8E3FB" +
            "B96ADAB760D7F4681D4F42A3DE394DF4AE56EDE76372BB19" +
            "0B07A7C8EE0A6D709E02FCE1CDF7E2ECC03404CD28342F61" +
            "9172FE9CE98583FF8E4F1232EEF28183C3FE3B1B4C6FAD73" +
            "3BB5FCBC2EC22005C58EF1837D1683B2C6F34A26C1B2EFFA" +
            "886B423861285C97FFFFFFFFFFFFFFFF", 16)
val DIFFIE_HELLMAN_GENERATOR: BigInteger = BigInteger.valueOf(2)

/** Server target refresh rates (in Hz) */
const val SERVER_UPDATE_RATE = 60 // Server game loop
const val SERVER_TO_CLIENT_UPDATE_RATE_FAST = 10 // Frequently updated data such as aircraft position, navigation, etc.
const val SERVER_TO_CLIENT_UPDATE_RATE_SLOW = 0.1f // Not so frequently updated data such as thunderstorm cells
const val SERVER_METAR_UPDATE_INTERVAL_MINS = 5 // Check for METAR update every 5 minutes

/** Buffer sizes */
const val CLIENT_WRITE_BUFFER_SIZE = 4096
const val CLIENT_READ_BUFFER_SIZE = 16384
const val SERVER_WRITE_BUFFER_SIZE = CLIENT_READ_BUFFER_SIZE
const val SERVER_READ_BUFFER_SIZE = CLIENT_WRITE_BUFFER_SIZE
const val RELAY_BUFFER_SIZE = CLIENT_READ_BUFFER_SIZE
const val SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT = 20

/** Default Gdx collections initial sizes */
const val AIRPORT_SIZE = 6
const val AIRCRAFT_SIZE = 35
const val SECTOR_COUNT_SIZE = 5
const val PUBLISHED_HOLD_SIZE = 30
const val PLAYER_SIZE = 6
const val CONVO_SIZE = 16
const val CONFLICT_SIZE = 6
const val TRANSITION_SIZE = 6
const val RUNWAY_SIZE = 6

/** Zoom threshold to switch between small and large datatag fonts */
const val DATATAG_ZOOM_THRESHOLD = 1f

/** Trail dot update rate */
const val TRAIL_DOT_UPDATE_INTERVAL_S = 10
const val MAX_TRAIL_DOTS = 240 / TRAIL_DOT_UPDATE_INTERVAL_S

/** Threshold altitude for low/high holding */
const val HOLD_THRESHOLD_ALTITUDE = 14050

/** Threshold IAS for reduced turn rate */
const val HALF_TURN_RATE_THRESHOLD_IAS = 251