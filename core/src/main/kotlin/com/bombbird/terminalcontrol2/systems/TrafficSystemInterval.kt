package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IntervalSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.traffic.*
import com.bombbird.terminalcontrol2.traffic.conflict.ConflictManager
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*
import ktx.collections.GdxArray
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * System for handling traffic matters, such as spawning of aircraft, checking of separation, runway states
 *
 * Used only in GameServer
 */
class TrafficSystemInterval: IntervalSystem(1f) {
    companion object {
        private val pendingRunwayChangeFamily = allOf(PendingRunwayConfig::class, AirportInfo::class, RunwayConfigurationChildren::class).get()
        private val arrivalFamily = allOf(AircraftInfo::class, ArrivalAirport::class).get()
        private val runwayTakeoffFamily = allOf(RunwayInfo::class).get()
        private val closestArrivalFamily = allOf(Position::class, AircraftInfo::class)
            .oneOf(LocalizerCaptured::class, GlideSlopeCaptured::class, VisualCaptured::class).get()
        private val conflictAbleFamily = allOf(Position::class, Altitude::class, ConflictAble::class)
            .exclude(WaitingTakeoff::class, TakeoffRoll::class, LandingRoll::class).get()
        private val despawnFamily = allOf(Position::class, AircraftInfo::class, Controllable::class)
            .exclude(WaitingTakeoff::class, TakeoffRoll::class, LandingRoll::class).get()
        private val timeSinceDepFamily = allOf(TimeSinceLastDeparture::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val pendingRunwayChangeFamilyEntities = FamilyWithListener.newServerFamilyWithListener(pendingRunwayChangeFamily)
    private val arrivalFamilyEntities = FamilyWithListener.newServerFamilyWithListener(arrivalFamily)
    private val runwayTakeoffFamilyEntities = FamilyWithListener.newServerFamilyWithListener(runwayTakeoffFamily)
    private val closestArrivalFamilyEntities = FamilyWithListener.newServerFamilyWithListener(closestArrivalFamily)
    private val conflictAbleFamilyEntities = FamilyWithListener.newServerFamilyWithListener(conflictAbleFamily)
    private val despawnFamilyEntities = FamilyWithListener.newServerFamilyWithListener(despawnFamily)
    private val timeSinceDepFamilyEntities = FamilyWithListener.newServerFamilyWithListener(timeSinceDepFamily)

    private val startingAltitude = getConflictStartAltitude()
    private var conflictLevels = Array<GdxArray<Entity>>(0) {
        GdxArray()
    }
    val conflictManager = ConflictManager()
    private var lastIsNight: Boolean? = null

    /**
     * Update function for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     */
    override fun updateInterval() {
        GAME.gameServer?.apply {
            // Arrival spawning timer
            arrivalSpawnTimerS -= interval
            if (arrivalSpawnTimerS < 0) {
                when (trafficMode) {
                    TrafficMode.NORMAL, TrafficMode.ARRIVALS_TO_CONTROL -> {
                        val arrivalCount = arrivalFamilyEntities.getEntities().filter { it[FlightType.mapper]?.type == FlightType.ARRIVAL }.size
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
                    else -> FileLog.info("TrafficSystem", "Invalid traffic mode $trafficMode")
                }
                createRandomArrival(Entries(airports).map { it.value }, this)
            }

            // I Am God achievement counter; engine update rate already takes into account game speed up
            if (trafficMode == TrafficMode.FLOW_RATE && trafficValue >= 119.9f && playersInGame == 1.toByte())
                GAME.achievementManager.incrementGodCounter(interval.toInt())

            // Check runway configurations when night mode changes
            val currIsNight = UsabilityFilter.isNight()
            if (lastIsNight != currIsNight) {
                lastIsNight = currIsNight
                for (i in 0 until airports.size) {
                    val arptEntity = airports.getValueAt(i).entity
                    calculateRunwayConfigScores(arptEntity)
                    checkRunwayConfigSelection(arptEntity)
                }
                sendNightModeUpdate(currIsNight)
            }
        }

        // Closest arrival to runway checker
        val closestRunwayArrival = closestArrivalFamilyEntities.getEntities()
        for (i in 0 until closestRunwayArrival.size()) {
            closestRunwayArrival[i]?.apply {
                val approach = get(LocalizerArmed.mapper)?.locApp ?: get(LocalizerCaptured.mapper)?.locApp ?:
                get(GlideSlopeCaptured.mapper)?.gsApp ?: get(VisualArmed.mapper)?.visApp ?:
                get(VisualCaptured.mapper)?.visApp ?: return@apply
                val rwyObj = approach[ApproachInfo.mapper]?.rwyObj?.entity ?: return@apply
                val rwyThrPos = rwyObj[CustomPosition.mapper] ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                if (has(LandingRoll.mapper)) return@apply
                val distPx = calculateDistanceBetweenPoints(pos.x, pos.y, rwyThrPos.x, rwyThrPos.y)
                // If no next arrival has been determined yet, add this arrival
                if (rwyObj.hasNot(RunwayNextArrival.mapper)) rwyObj += RunwayNextArrival(this, distPx)
                else rwyObj[RunwayNextArrival.mapper]?.let {
                    if (distPx < it.distFromThrPx || it.aircraft.has(LandingRoll.mapper)) {
                        // Update closest aircraft if it is closer than the current closest aircraft,
                        // or if the current closest aircraft has already touched down
                        it.aircraft = this
                        it.distFromThrPx = distPx
                    }
                }
            }
        }

        // Update pending runway change timer
        val pendingRunway = pendingRunwayChangeFamilyEntities.getEntities()
        for (i in 0 until pendingRunway.size()) {
            pendingRunway[i]?.apply {
                val pending = get(PendingRunwayConfig.mapper) ?: return@apply
                val arptInfo = get(AirportInfo.mapper) ?: return@apply
                val rwyConfigs = get(RunwayConfigurationChildren.mapper) ?: return@apply
                pending.timeRemaining -= interval
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

        // Time since departure update
        val timeSinceDep = timeSinceDepFamilyEntities.getEntities()
        for (i in 0 until timeSinceDep.size()) {
            timeSinceDep[i]?.apply {
                val timeSinceLastDeparture = get(TimeSinceLastDeparture.mapper) ?: return@apply
                timeSinceLastDeparture.time += interval
            }
        }

        // Departure spawning timer
        val runwayTakeoff = runwayTakeoffFamilyEntities.getEntities()
        for (i in 0 until runwayTakeoff.size()) {
            runwayTakeoff[i]?.apply {
                // First increment the previous departure and arrival timers if present
                get(RunwayPreviousDeparture.mapper)?.let { it.timeSinceDepartureS += 1 }
                get(RunwayPreviousArrival.mapper)?.let { it.timeSinceTouchdownS += 1 }

                // Airport checks - departure timer
                val airport = get(RunwayInfo.mapper)?.airport ?: return@apply
                // Create random departure if airport does not have one queued
                val depInfo = airport.entity[DepartureInfo.mapper] ?: return@apply
                val maxAdvDep = airport.entity[MaxAdvancedDepartures.mapper] ?: return@apply
                val timeSinceLastDep = airport.entity[TimeSinceLastDeparture.mapper] ?: return@apply
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

                // If too many departures have departed already, do not depart
                if (depInfo.backlog <= -maxAdvDep.maxAdvanceDepartures) return@apply

                // If insufficient time has passed since last departure, do not depart
                val minimumTime = 60 + calculateAdditionalTimeToNextDeparture(depInfo.backlog, maxAdvDep.maxAdvanceDepartures)
                if (timeSinceLastDep.time < minimumTime) return@apply

                // Check for go-around - minimum 80s as stated in RecentGoAround component
                if (airport.entity.has(RecentGoAround.mapper)) return@apply

                // Runway checks
                // Check self and all related runways
                if (hasNot(ActiveTakeoff.mapper)) return@apply // Not active for departures
                if (!checkSameRunwayTraffic(this, airport)) return@apply
                get(OppositeRunway.mapper)?.let { if (!checkOppRunwayTraffic(it.oppRwy, airport)) return@apply }
                get(DependentParallelRunway.mapper)?.let {
                    for (j in 0 until it.depParRwys.size)
                        if (!checkDependentParallelRunwayTraffic(it.depParRwys[j], airport)) return@apply
                }
                get(DependentOppositeRunway.mapper)?.let {
                    for (j in 0 until it.depOppRwys.size)
                        if (!checkDependentOppositeRunwayTraffic(it.depOppRwys[j], airport)) return@apply
                }
                get(CrossingRunway.mapper)?.let {
                    for (j in 0 until it.crossRwys.size)
                        if (!checkCrossingRunwayTraffic(it.crossRwys[j])) return@apply
                }
                get(DepartureDependency.mapper)?.let {
                    for (j in 0 until it.dependencies.size)
                        if (!checkDepartureDependencyTraffic(it.dependencies[j])) return@apply
                }

                // Runway checks passed
                val nextDep = airport.entity[AirportNextDeparture.mapper] ?: return@apply

                // Get random SID, check takeoff protection zone for it
                val sid = randomSid(this) ?: return@apply
                val initAlt = sid.rwyInitialClimbs.get(get(RunwayInfo.mapper)?.rwyName) ?: 3000
                if (!isTakeoffProtectionZoneClear(this, initAlt)) return@apply

                clearForTakeoff(nextDep.aircraft, this, sid)
            }
        }

        // Despawn checker
        val checkDespawn = despawnFamilyEntities.getEntities()
        for (i in 0 until checkDespawn.size()) {
            checkDespawn[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply

                // Check that aircraft is under control only by center/ACC
                if (controllable.sectorId != SectorInfo.CENTRE) return@apply
                // Check that aircraft is not in the control sector
                val primarySector = GAME.gameServer?.primarySector ?: return@apply
                if (primarySector.contains(pos.x, pos.y)) return@apply
                // Check if aircraft is more than 20nm away the intersection between primary sector and line joining
                // airport position and present position
                val sectorExitPoint = findClosestIntersectionBetweenSegmentAndPolygon(0f, 0f, pos.x, pos.y,
                    primarySector.vertices, 0f) ?: return@apply
                if (calculateDistanceBetweenPoints(pos.x, pos.y, sectorExitPoint.x, sectorExitPoint.y) <= nmToPx(20)) return@apply

                despawnAircraft(this)
            }
        }

        // Update the levels of each conflict-able entity
        updateConflictLevels()

        // Traffic separation checking
        val conflictAble = conflictAbleFamilyEntities.getEntities()
        conflictManager.checkAllConflicts(conflictLevels, conflictAble)
    }

    /** Creates the conflict level array upon loading world data (MAX_ALT required) */
    fun initializeConflictLevelArray(maxAlt: Int, vertSep: Int) {
        conflictLevels = Array(ceil((maxAlt + 1500f) / vertSep).roundToInt() - startingAltitude / vertSep) {
            GdxArray()
        }
    }

    /** Updates the conflict levels of each entity with the ConflictAble component using their altitude */
    private fun updateConflictLevels() {
        // Traffic separation checking
        val conflictAble = conflictAbleFamilyEntities.getEntities()

        // Update the levels of each conflict-able entity
        for (i in 0 until conflictAble.size()) {
            conflictAble[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val conflict = get(ConflictAble.mapper) ?: return@apply
                val expectedSector = getSectorIndexForAlt(alt.altitudeFt, startingAltitude)
                if (expectedSector != conflict.conflictLevel) {
                    if (conflict.conflictLevel >= 0 && conflict.conflictLevel < conflictLevels.size)
                        conflictLevels[conflict.conflictLevel].removeValue(this, false)
                    if (expectedSector >= 0 && expectedSector < conflictLevels.size)
                        conflictLevels[expectedSector].add(this)
                    conflict.conflictLevel = expectedSector
                }
            }
        }
    }

    /**
     * Gets the array of entities within specified range of the input spawn altitude
     * @param spawnAlt the spawn altitude to check range from
     * @param lowerRange how much lower than the spawn altitude to check - must be positive
     * @param upperRange how much higher than the spawn altitude to check - must be positive
     */
    fun getEntitiesWithinArrivalSpawnAltitude(spawnAlt: Float, lowerRange: Float, upperRange: Float): GdxArray<Entity> {
        updateConflictLevels()
        val lowerRangeChecked = if (lowerRange < 0) {
            FileLog.warn("TrafficSystemInterval", "Lower range $lowerRange is negative, setting to 0")
            0f
        } else lowerRange
        val upperRangeChecked = if (upperRange < 0) {
            FileLog.warn("TrafficSystemInterval", "Upper range $upperRange is negative, setting to 0")
            0f
        } else upperRange
        val lowerLevel = getSectorIndexForAlt(spawnAlt - lowerRangeChecked, startingAltitude)
        val upperLevel = getSectorIndexForAlt(spawnAlt + upperRangeChecked, startingAltitude)
        val entities = GdxArray<Entity>()
        for (i in lowerLevel..upperLevel) {
            if (i < 0 || i >= conflictLevels.size) continue
            entities.addAll(conflictLevels[i])
        }

        return entities
    }

    /**
     * Adds a wake zone to the respective wake level array, based on its wake altitude; this is a wrapper function for
     * delegation to [ConflictManager.wakeManager]
     * @param wakeZone the wake zone to add
     */
    fun addWakeZone(wakeZone: WakeZone) {
        conflictManager.wakeManager.addWakeZone(wakeZone)
    }

    /**
     * Removes the input wake zone from its respective wake level array; this is a wrapper function for
     * delegation to [ConflictManager.wakeManager]
     * @param wakeZone the wake zone to add
     */
    fun removeWakeZone(wakeZone: WakeZone) {
        conflictManager.wakeManager.removeWakeZone(wakeZone)
    }

    /**
     * Removes the aircraft from conflict levels on despawn
     * @param aircraft the aircraft being despawned
     */
    fun removeAircraftOnDespawn(aircraft: Entity) {
        aircraft[ConflictAble.mapper]?.let { conflict ->
            if (conflict.conflictLevel >= 0 && conflict.conflictLevel < conflictLevels.size)
                conflictLevels[conflict.conflictLevel].removeValue(aircraft, false)
        }
    }

    /**
     * Removes all wake zones for the aircraft
     * @param aircraft the aircraft to remove wake zones from
     */
    fun removeAircraftWakeZones(aircraft: Entity) {
        aircraft[WakeTrail.mapper]?.wakeZones?.let { wakeZones ->
            for (point in Queue.QueueIterator(wakeZones)) {
                point.second?.let {
                    removeWakeZone(it)
                    engine.removeEntityOnMainThread(it.entity, false)
                }
            }
        }
    }
}