package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.global.GAME

/**
 * Class to handle Android lifecycle events for the game server
 */
class AndroidLifeCycleHandler {
    /** Called when onPause is called on Android app */
    fun onPause() {
        GAME.gameServer?.updateGameRunningStatus(false)
    }

    /** Called when onResume is called on Android app */
    fun onResume() {
        GAME.gameServer?.updateGameRunningStatus(true)
    }
}