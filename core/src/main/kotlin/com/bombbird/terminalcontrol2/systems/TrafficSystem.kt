package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.traffic.*
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.hasNot
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
    private val runwayTakeoffFamily = allOf(RunwayInfo::class).get()

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
                    TrafficMode.NORMAL, TrafficMode.ARRIVALS_TO_CONTROL -> {
                        val arrivalCount = engine.getEntitiesFor(arrivalFamily).filter { it[FlightType.mapper]?.type == FlightType.ARRIVAL }.size
                        // Min 50sec for >= 4 planes diff, max 80sec for <= 1 plane diff
                        arrivalSpawnTimerS = 90f - 10 * (trafficValue - arrivalCount)
                        arrivalSpawnTimerS = MathUtils.clamp(arrivalSpawnTimerS, 50f, 80f)
                        if (arrivalCount >= trafficValue.toInt()) return
                    }
                    TrafficMode.FLOW_RATE -> {
                        arrivalSpawnTimerS = -previousArrivalOffsetS // Subtract the additional (or less) time before spawning previous aircraft
                        val defaultRate = 3600f / trafficValue
                        arrivalSpawnTimerS += defaultRate // Add the constant rate timing
                        previousArrivalOffsetS = defaultRate * MathUtils.random(-0.1f, 0.1f)
                        arrivalSpawnTimerS += previousArrivalOffsetS
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

        val runwayTakeoff = engine.getEntitiesFor(runwayTakeoffFamily)
        for (i in 0 until runwayTakeoff.size()) {
            runwayTakeoff[i]?.apply {
                val airport = get(RunwayInfo.mapper)?.airport ?: return@apply
                if (airport.entity.hasNot(AirportNextDeparture.mapper))
                if (!checkSameRunwayTraffic(this)) return@apply
                get(OppositeRunway.mapper)?.let { if (!checkOppRunwayTraffic(it.oppRwy)) return@apply }
                get(DependentParallelRunway.mapper)?.let {
                    for (j in 0 until it.depParRwys.size)
                        if (!checkDependentParallelRunwayTraffic(it.depParRwys[j])) return@apply
                }
                get(DependentOppositeRunway.mapper)?.let {
                    for (j in 0 until it.depOppRwys.size)
                        if (!checkDependentOppositeRunwayTraffic(it.depOppRwys[j])) return@apply
                }
                get(CrossingRunway.mapper)?.let {
                    for (j in 0 until it.crossRwys.size)
                        if (!checkCrossingRunwayTraffic(it.crossRwys[j])) return@apply
                }
                // TODO Check for departure backlog and whether next departure timer is up
                // All related runway checks passed - clear next departure for takeoff
                airport.entity[AirportNextDeparture.mapper]?.let { clearForTakeoff(it.aircraft, this) }
            }
        }
    }
}