package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IntervalSystem
import com.badlogic.gdx.utils.ArrayMap
import com.bombbird.terminalcontrol2.components.Controllable
import com.bombbird.terminalcontrol2.components.PendingRunwayConfig
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.DISCORD_UPDATE_INTERVAL_S
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import ktx.ashley.allOf
import ktx.ashley.get

/**
 * System that is responsible solely for transmission of data which can happen at a lower rate than [DataSystemClient]
 *
 * Used only in RadarScreen
 */
class DataSystemIntervalClient: IntervalSystem(1f) {
    companion object {
        private val pendingRunwayChangeFamily: Family = allOf(PendingRunwayConfig::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val pendingRunwayChangeFamilyEntities = FamilyWithListener.newClientFamilyWithListener(pendingRunwayChangeFamily)

    private var discordTimer = DISCORD_UPDATE_INTERVAL_S - 1

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in [DataSystemClient]
     */
    override fun updateInterval() {
        // Update pending runway change timer for status pane display
        val pendingRunwayChange = pendingRunwayChangeFamilyEntities.getEntities()
        for (i in 0 until pendingRunwayChange.size()) {
            pendingRunwayChange[i]?.apply {
                get(PendingRunwayConfig.mapper)?.let {
                    it.timeRemaining -= interval
                    if (it.timeRemaining < 0) it.timeRemaining = 0f
                }
            }
        }

        // Refresh the status pane when selected, once per second
        CLIENT_SCREEN?.uiPane?.mainInfoObj?.also {
            if (it.isStatusPaneSelected()) it.statusPaneObj.refreshStatusMessages()
        }

        discordTimer += interval
        if (discordTimer >= DISCORD_UPDATE_INTERVAL_S) {
            discordTimer -= DISCORD_UPDATE_INTERVAL_S

            CLIENT_SCREEN?.let {
                val acInControl = ArrayMap.Entries(it.aircraft).filter { entry ->
                    entry.value.entity[Controllable.mapper]?.sectorId == it.playerSector
                }.size
                GAME.discordHandler.updateInGame(it.mainName, acInControl, it.sectors.size, it.maxPlayers,
                    it.isPublicMultiplayer())
            }
        }
    }
}