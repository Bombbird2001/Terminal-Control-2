package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.PendingRunwayConfig
import com.bombbird.terminalcontrol2.components.RunwayConfigurationChildren
import com.bombbird.terminalcontrol2.global.GAME
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.remove

/**
 * System for handling traffic matters, such as spawning of aircraft, checking of separation, runway states
 *
 * Used only in GameServer
 */
class TrafficSystem(override val updateTimeS: Float): EntitySystem(), LowFreqUpdate {
    override var timer = 0f

    private val pendingRunwayChangeFamily = allOf(PendingRunwayConfig::class, AirportInfo::class, RunwayConfigurationChildren::class).get()

    /**
     * Main update function, for values that need to be updated frequently
     *
     * For values that can be updated less frequently and are not dependent on [deltaTime], put in [lowFreqUpdate]
     * */
    override fun update(deltaTime: Float) {

    }

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     * */
    override fun lowFreqUpdate() {
        val pendingRunway = engine.getEntitiesFor(pendingRunwayChangeFamily)
        for (i in 0 until pendingRunway.size()) {
            pendingRunway[i]?.apply {
                val pending = get(PendingRunwayConfig.mapper) ?: return@apply
                val arptInfo = get(AirportInfo.mapper) ?: return@apply
                val rwyConfigs = get(RunwayConfigurationChildren.mapper) ?: return@apply
                pending.timeRemaining -= updateTimeS
                if (pending.timeRemaining < 0f) {
                    remove<PendingRunwayConfig>()
                    val config = rwyConfigs.rwyConfigs[pending.pendingId] ?: return@apply
                    val arpt = GAME.gameServer?.airports?.get(arptInfo.arptId) ?: return@apply
                    if (config.rwyAvailabilityScore == 0) return@apply
                    arpt.activateRunwayConfig(config.id)
                    GAME.gameServer?.sendActiveRunwayUpdateToAll(arptInfo.arptId, config.id)
                }
            }
        }
    }
}