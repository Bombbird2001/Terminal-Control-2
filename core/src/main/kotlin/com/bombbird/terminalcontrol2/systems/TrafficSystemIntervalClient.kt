package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.global.GAME

/**
 * Client-side traffic interval update system, set at 1hz
 *
 * Used only in RadarScreen
 */
class TrafficSystemIntervalClient: IntervalSystem(1f) {
    override fun updateInterval() {
        GAME.gameClientScreen?.apply {
            // Play the conflict sound every 1s
            if (conflicts.size > 0) GAME.soundManager.playConflict()
        }
    }
}