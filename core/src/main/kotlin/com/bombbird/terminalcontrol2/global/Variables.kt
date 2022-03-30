package com.bombbird.terminalcontrol2.global

/** Global variables for use, can be modified */
object Variables {

    /** Screen size retrieved with Gdx.graphics */
    var WIDTH = 0f
    var HEIGHT = 0f

    /** The UI size used in menu screens (must be fully visible), calculated taking into account the screen aspect ratio */
    var UI_WIDTH = 0f
    var UI_HEIGHT = 0f

    /** Index of background image used in menu screens */
    var BG_INDEX = 0

    /** Magnetic heading deviation in the current game - positive for Westward deviation and negative for Eastward deviation */
    var MAG_HDG_DEV = 0f
}