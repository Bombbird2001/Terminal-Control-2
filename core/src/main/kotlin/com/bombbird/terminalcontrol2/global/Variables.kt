package com.bombbird.terminalcontrol2.global

/** Global variables for use, can be modified */

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

/** Magnetic heading deviation in the current game - positive for Westward deviation and negative for Eastward deviation */
var MAG_HDG_DEV = 0f

/** Minimum, maximum altitude that can be cleared */
var MIN_ALT = 2000
var MAX_ALT = 20000

/** Radar separation required under normal circumstances */
var MIN_SEP = 3f

/** Transition altitude, level */
var TRANS_ALT = 18000
var TRANS_LVL = 180

/** Radar refresh rate */
var RADAR_REFRESH_INTERVAL_S = 0.5f

/** Datatag style to use */
var DATATAG_STYLE_ID = 0
