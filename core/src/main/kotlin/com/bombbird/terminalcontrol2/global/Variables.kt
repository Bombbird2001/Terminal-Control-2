package com.bombbird.terminalcontrol2.global

import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.util.UUID

/** Global variables for use, can be modified */
const val UNCONTROLLED_AIRCRAFT_TRAIL_OFF: Byte = 0
const val UNCONTROLLED_AIRCRAFT_TRAIL_SELECTED: Byte = 1
const val UNCONTROLLED_AIRCRAFT_TRAIL_SHOW: Byte = 2
const val SHOW_DIST_TO_GO_HIDE: Byte = 3
const val SHOW_DIST_TO_GO_ARRIVALS: Byte = 4
const val SHOW_DIST_TO_GO_ALL: Byte = 5
const val COMMS_OFF: Byte = 6
const val COMMS_SOUND_EFFECTS: Byte = 7
const val COMMS_PILOT_VOICES: Byte = 8
const val DATATAG_BACKGROUND_OFF: Byte = 9
const val DATATAG_BACKGROUND_SELECTED: Byte = 10
const val DATATAG_BACKGROUND_ALWAYS: Byte = 11
const val DATATAG_BORDER_OFF: Byte = 12
const val DATATAG_BORDER_SELECTED: Byte = 13
const val DATATAG_BORDER_ALWAYS: Byte = 14

/** Screen size retrieved with Gdx.graphics */
var WIDTH = 0f
var HEIGHT = 0f

/** The UI size used in menu screens (must be fully visible), calculated taking into account the screen aspect ratio */
var UI_WIDTH = 0f
var UI_HEIGHT = 0f

/** Index of background image used in menu screens */
var BG_INDEX = 0

/** Server, client TCP/UDP ports */
var TCP_PORT = 57773
var UDP_PORT = 57779

/** List of available airports */
val AVAIL_AIRPORTS = GdxArrayMap<String, String?>().apply {
    put("TCTP", null)
    put("TCWS", null)
    put("TCTT", null)
    put("TCBB", null)
    put("TCHH", null)
    put("TCBD", null)
    put("TCMD", null)
    put("TCPG", null)
}

/** Magnetic heading deviation in the current game - positive for Westward deviation and negative for Eastward deviation */
var MAG_HDG_DEV = 0f

/** Minimum, maximum, and additional user defined altitudes that can be cleared */
var MIN_ALT = 2000
var MAX_ALT = 20000
val INTERMEDIATE_ALTS = GdxArray<Int>(6)

/** Radar separation required under normal circumstances */
var MIN_SEP = 3f
var VERT_SEP = 1000

/** Transition altitude, level */
var TRANS_ALT = 18000
var TRANS_LVL = 180

/** Max arrival count for this map */
var MAX_ARRIVALS: Byte = 20

/** Display settings */
var TRAJECTORY_DURATION_S = 90
var RADAR_REFRESH_INTERVAL_S = 2f
var TRAIL_DURATION_S = 90
var SHOW_UNCONTROLLED_AIRCRAFT_TRAIL = UNCONTROLLED_AIRCRAFT_TRAIL_SELECTED
var RANGE_RING_INTERVAL_NM = 0
var SHOW_MVA_ALTITUDE = true
var REALISTIC_ILS_DISPLAY = true
var COLOURFUL_STYLE = true
var SHOW_DIST_TO_GO = SHOW_DIST_TO_GO_ALL

/** Datatag settings */
var DATATAG_STYLE_ID: Byte = 0
var DATATAG_BACKGROUND: Byte = DATATAG_BACKGROUND_ALWAYS
var DATATAG_BORDER: Byte = DATATAG_BORDER_ALWAYS
var DATATAG_ROW_SPACING_PX: Byte = 4

/** Sound settings */
var COMMUNICATIONS_SOUND = COMMS_PILOT_VOICES
var ALERT_SOUND_ON = true

/** Advanced trajectory alert settings */
var ADV_TRAJECTORY_DURATION_S = 0
var APW_DURATION_S = 0
var STCA_DURATION_S = 0

/** Autosave settings */
var AUTOSAVE_INTERVAL_MIN = 2

/** Player UUID */
lateinit var myUuid: UUID

/** Build version */
var GAME_VERSION = "Unknown"
var BUILD_VERSION = 0