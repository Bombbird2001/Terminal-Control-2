package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.traffic.*
import com.bombbird.terminalcontrol2.utilities.calculateDistanceBetweenPoints
import ktx.ashley.*

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
    private val airportTimingFamily = allOf(AirportInfo::class, DepartureInfo::class).get()
    private val closestArrivalFamily = allOf(Position::class, AircraftInfo::class).oneOf(LocalizerCaptured::class, GlideSlopeCaptured::class, VisualCaptured::class).get()

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

        // Update pending runway change timer
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

        // Airport departure backlog timer
        val airportDeparture = engine.getEntitiesFor(airportTimingFamily)
        for (i in 0 until airportDeparture.size()) {
            airportDeparture[i]?.apply {
                get(DepartureInfo.mapper)?.let { it.prevDepTimeS += 1 }
            }
        }

        // Departure spawning timer
        val runwayTakeoff = engine.getEntitiesFor(runwayTakeoffFamily)
        for (i in 0 until runwayTakeoff.size()) {
            runwayTakeoff[i]?.apply {
                // First increment the previous departure and arrival timers if present
                get(RunwayPreviousDeparture.mapper)?.let { it.timeSinceDepartureS += 1 }
                get(RunwayPreviousArrival.mapper)?.let { it.timeSinceTouchdownS += 1 }

                // Airport checks - departure timer
                val airport = get(RunwayInfo.mapper)?.airport ?: return@apply
                // Create random departure if airport does not have one queued
                val depInfo = airport.entity[DepartureInfo.mapper] ?: return@apply
                if (depInfo.closed) {
                    // Closed for departures - remove any existing next departure from the airport
                    val nextDepCallsign = airport.entity[AirportNextDeparture.mapper]?.aircraft?.get(AircraftInfo.mapper)?.icaoCallsign
                    if (nextDepCallsign != null) {
                        airport.entity.remove<AirportNextDeparture>()
                        GAME.gameServer?.aircraft?.removeKey(nextDepCallsign)
                    }
                    return@apply
                }
                // Otherwise, generate a new next departure if not present
                else if (airport.entity.hasNot(AirportNextDeparture.mapper)) createRandomDeparture(airport.entity, GAME.gameServer ?: return@apply)
                val nextDep = airport.entity[AirportNextDeparture.mapper] ?: return@apply
                // If the next departure is not ready yet, skip
                if (depInfo.prevDepTimeS < calculateTimeToNextDeparture(depInfo.backlog)) return@apply

                // Runway checks
                // Check self and all related runways
                if (hasNot(ActiveTakeoff.mapper)) return@apply // Not active for departures
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

                // All related checks passed - clear next departure for takeoff
                clearForTakeoff(nextDep.aircraft, this)
            }
        }

        // Closest arrival to runway checker
        val closestRunwayArrival = engine.getEntitiesFor(closestArrivalFamily)
        for (i in 0 until closestRunwayArrival.size()) {
            closestRunwayArrival[i]?.apply {
                val approach = get(LocalizerCaptured.mapper)?.locApp ?: get(GlideSlopeCaptured.mapper)?.gsApp ?:
                get(VisualCaptured.mapper)?.visApp ?: return@apply
                val rwyObj = approach[ApproachInfo.mapper]?.rwyObj?.entity ?: return@apply
                val rwyThrPos = rwyObj[CustomPosition.mapper] ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val distPx = calculateDistanceBetweenPoints(pos.x, pos.y, rwyThrPos.x, rwyThrPos.y)
                // If no next arrival has been determined yet, add this arrival
                if (rwyObj.hasNot(RunwayNextArrival.mapper)) rwyObj += RunwayNextArrival(this, distPx)
                else rwyObj[RunwayNextArrival.mapper]?.let {
                    if (distPx < it.distFromThrPx) {
                        it.aircraft = this
                        it.distFromThrPx = distPx
                    }
                }
            }
        }
    }
}