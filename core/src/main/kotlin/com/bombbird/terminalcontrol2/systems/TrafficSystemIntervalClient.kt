package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.*

/**
 * Client-side traffic interval update system, set at 1hz
 *
 * Used only in RadarScreen
 */
class TrafficSystemIntervalClient: IntervalSystem(1f) {
    companion object {
        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    override fun updateInterval() {
        val rs = GAME.gameClientScreen

        // Play the conflict sound every 1s
        if (rs != null && rs.conflicts.size > 0) GAME.soundManager.playWarning()
    }
}