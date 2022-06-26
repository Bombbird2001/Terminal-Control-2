package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.traffic.createRandomArrival
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
    private val arrivalFamily = allOf(AircraftInfo::class, ArrivalAirport::class).get()

    /**
     * Main update function, for values that need to be updated frequently
     *
     * For values that can be updated less frequently and are not dependent on [deltaTime], put in [lowFreqUpdate]
     * */
    override fun update(deltaTime: Float) {
        checkLowFreqUpdate(deltaTime)
    }

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     * */
    override fun lowFreqUpdate() {
        // Arrival spawning timer
        GAME.gameServer?.apply {
            arrivalSpawnTimerS -= updateTimeS
            if (arrivalSpawnTimerS < 0) {
                when (trafficMode) {
                    TrafficMode.ARRIVALS_TO_CONTROL -> {
                        val arrivalCount = engine.getEntitiesFor(arrivalFamily).filter { it[FlightType.mapper]?.type == FlightType.ARRIVAL }.size
                        // Min 50sec for >= 4 planes diff, max 80sec for <= 1 plane diff
                        arrivalSpawnTimerS = 90f - 10 * (planesToControl - arrivalCount)
                        arrivalSpawnTimerS = MathUtils.clamp(arrivalSpawnTimerS, 50f, 80f)
                        if (arrivalCount >= planesToControl.toInt()) return
                    }
                    TrafficMode.FLOW_RATE -> {
                        // TODO Implement flow rate spawning
                    }
                    else -> Gdx.app.log("TrafficSystem", "Invalid traffic mode $trafficMode")
                }
                createRandomArrival(airports.values().toArray(), this)
            }
        }

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
                    GAME.gameServer?.sendPendingRunwayUpdateToAll(arptInfo.arptId, null)
                    GAME.gameServer?.sendActiveRunwayUpdateToAll(arptInfo.arptId, config.id)
                }
            }
        }
    }
}