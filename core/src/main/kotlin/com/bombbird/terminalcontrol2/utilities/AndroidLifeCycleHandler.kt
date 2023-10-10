package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.files.saveGame
import com.bombbird.terminalcontrol2.global.GAME

/**
 * Class to handle Android lifecycle events for the game server
 */
class AndroidLifeCycleHandler {
    /** Called when onPause is called on Android app */
    fun onPause() {
        // Perform a save on app pause due to possibility of being killed by Android system
        GAME.gameServer?.let {
            it.postRunnableAfterPause { saveGame(it) }
            it.updateGameRunningStatus(false)
        }
    }

    /** Called when onResume is called on Android app */
    fun onResume() {
        GAME.gameServer?.updateGameRunningStatus(true)
    }
}