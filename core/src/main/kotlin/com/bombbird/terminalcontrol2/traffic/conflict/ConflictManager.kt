package com.bombbird.terminalcontrol2.traffic.conflict

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.ThunderStorm
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.WakeManager
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.collections.GdxArray
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Helper class for managing conflicts between entities */
class ConflictManager {
    companion object {
        const val PENALTY_DURATION_S = 3 // 1 point is deducted for every conflict every 3 seconds
    }

    private var timer = PENALTY_DURATION_S
    private var prevConflictNoWakeCount = 0
    private val conflicts = GdxArray<Conflict>(CONFLICT_SIZE)
    private val potentialConflicts = GdxArray<PotentialConflict>(CONFLICT_SIZE)

    val wakeManager = WakeManager()

    /**
     * Main function to check for all types of conflicts, given the conflict sector distribution for each entity, as well
     * as the full array of conflict-able entities themselves
     *
     * The subtraction of score, sending of updates to clients will also be done in this function
     * @param conflictLevels the conflict level sectors where entities should have already been distributed into, for
     * checking of separation between each entity
     * @param conflictAbles the array of entities that can come into conflict with other entities, for checking of other
     * conflicts such as MVA, restricted areas
     */
    fun checkAllConflicts(conflictLevels: Array<GdxArray<Entity>>, conflictAbles: ImmutableArray<Entity>) {
        // Clear the existing list of conflicts/potential conflicts first
        prevConflictNoWakeCount = getCurrentNonWakeConflicts()
        conflicts.clear()
        potentialConflicts.clear()

        // Check aircraft separation with one another
        checkAircraftSeparationMinimaConflict(conflictLevels)

        // Check MVA, restricted areas
        checkMVARestrictedConflict(conflictAbles)

        // Check wake separation
        wakeManager.checkWakeConflicts(conflictAbles, conflicts)

        // Check aircraft flying in thunderstorms
        GAME.gameServer?.storms?.let {
            checkThunderStormConflict(conflictAbles, it)
        }

        // Send all conflicts to clients using TCP
        GAME.gameServer?.sendConflicts(conflicts, potentialConflicts)

        // Subtract score
        updateScore()
    }

    /** Subtracts the game score corresponding to the number of conflicts and new conflicts, and updates the clients if needed */
    private fun updateScore() {
        GAME.gameServer?.apply {
            // For every new conflict (excluding wake conflicts), subtract 5% of score
            val currScore = score
            val newConflicts = getCurrentNonWakeConflicts() - prevConflictNoWakeCount
            if (newConflicts > 0) score = floor(score * 0.95f.pow(newConflicts)).roundToInt()
            // When the 3s timer is up, subtract 1 from score for every conflict
            timer--
            if (timer <= 0) {
                score -= conflicts.size
                timer += PENALTY_DURATION_S
            }
            // Score cannot go below 0
            if (score < 0) score = 0
            if (score != currScore) sendScoreUpdate()
        }
    }

    /**
     * Checks for conflicts between aircraft, using the level distribution to reduce the comparisons required; any conflicts
     * found will be added to the list of conflicts/potential conflicts for later use
     * @param conflictLevels the conflict level sectors where entities should have already been distributed into, for
     * checking of separation between each entity
     */
    private fun checkAircraftSeparationMinimaConflict(conflictLevels: Array<GdxArray<Entity>>) {
        // Iterate through each layer to check for conflicts between the entities in the same level and 1 level above
        for (i in conflictLevels.indices) {
            val aircraft = conflictLevels[i]
            // Check all aircraft within this layer with one another
            for (j in 0 until aircraft.size) {
                for (k in j + 1 until aircraft.size) checkAircraftConflict(aircraft[j], aircraft[k])
                // If a layer exists above, check with each aircraft in the above layer
                if (i + 1 < conflictLevels.size) {
                    val aboveAircraft = conflictLevels[i + 1]
                    for (k in 0 until aboveAircraft.size) checkAircraftConflict(aircraft[j], aboveAircraft[k])
                }
            }
        }
    }

    /**
     * Checks for conflicts between 2 entities, depending on their current state; if conflict is found, a new conflict
     * instance will be added to the conflict array
     * @param aircraft1 the first entity
     * @param aircraft2 the second entity
     */
    private fun checkAircraftConflict(aircraft1: Entity, aircraft2: Entity) {
        if (checkIsAircraftConflictInhibited(aircraft1, aircraft2)) return

        val alt1 = aircraft1[Altitude.mapper] ?: return
        val alt2 = aircraft2[Altitude.mapper] ?: return
        val pos1 = aircraft1[Position.mapper] ?: return
        val pos2 = aircraft2[Position.mapper] ?: return

        val conflictMinimaRequired = getMinimaRequired(aircraft1, aircraft2)

        // If lateral separation is less than minima, and vertical separation less than minima, conflict exists
        val distPx = calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y)
        if (distPx < nmToPx(conflictMinimaRequired.latMinima) &&
            abs(alt1.altitudeFt - alt2.altitudeFt) < conflictMinimaRequired.vertMinima - 25) {
            conflicts.add(Conflict(aircraft1, aircraft2, null, conflictMinimaRequired.latMinima,
                conflictMinimaRequired.conflictReason))
        }

        // If no conflict, but separation is less than minima + 2nm, add to potential conflicts
        else if (distPx < nmToPx(conflictMinimaRequired.latMinima + 2) && abs(alt1.altitudeFt - alt2.altitudeFt) < conflictMinimaRequired.vertMinima)
            potentialConflicts.add(PotentialConflict(aircraft1, aircraft2, conflictMinimaRequired.latMinima))
    }

    /**
     * Checks for conflicts between an entity and MVA sectors, restricted areas; if conflict is found, a new conflict
     * instance will be added to the conflict array
     * @param conflictAbles the list of entities to check for MVA, restricted area conflict
     */
    private fun checkMVARestrictedConflict(conflictAbles: ImmutableArray<Entity>) {
        conflictAbles.forEach {
            checkAircraftMVARestrictedConflict(it)?.let { conflict -> conflicts.add(conflict) }
        }
    }

    /**
     * Gets the number of ongoing conflicts excluding wake conflicts
     * @return the number of conflicts that are not wake conflicts
     */
    private fun getCurrentNonWakeConflicts(): Int {
        var count = 0
        for (i in 0 until conflicts.size)
            if (conflicts[i].reason != Conflict.WAKE_INFRINGE) count++
        return count
    }

    /**
     * Checks for [conflictAbles] flying in [storms]; if found, a new conflict
     * instance will be added to the conflict array
     */
    private fun checkThunderStormConflict(
        conflictAbles: ImmutableArray<Entity>,
        storms: Array<ThunderStorm?>
    ) {
        for (conflictAble in conflictAbles) {
            val pos = conflictAble[Position.mapper] ?: continue
            val alt = conflictAble[Altitude.mapper] ?: continue
            val speed = conflictAble[Speed.mapper] ?: continue

            storms.forEach { storm ->
                val stormPos = storm?.entity[Position.mapper] ?: return@forEach
                val stormAlt = storm.entity[Altitude.mapper] ?: return@forEach

                // Aircraft must be flying at or below the storm top altitude
                if (alt.altitudeFt > stormAlt.altitudeFt) return@forEach

                // Check if the aircraft is within the storm limits
                val aircraftXIndex = floor((pos.x - stormPos.x) / THUNDERSTORM_CELL_SIZE_PX).toInt()
                val aircraftYIndex = floor((pos.y - stormPos.y) / THUNDERSTORM_CELL_SIZE_PX).toInt()

                var redZones = 0

                for (i in -1..1) {
                    for (j in -1..1) {
                        val xIndex = aircraftXIndex + i
                        val yIndex = aircraftYIndex + j

                        if (xIndex < -THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
                            || xIndex >= THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
                            || yIndex < -THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
                            || yIndex >= THUNDERSTORM_CELL_SIZE_PX) continue

                        val stormCells = storm.entity[ThunderStormCellChildren.mapper]?.cells ?: continue
                        val cell = stormCells[xIndex]?.get(yIndex) ?: continue
                        val cellInfo = cell.entity[ThunderCellInfo.mapper] ?: continue

                        when {
                            cellInfo.intensity >= THUNDERSTORM_CONFLICT_THRESHOLD -> {
                                redZones++
                                speed.vertSpdFpm += MathUtils.randomSign() * MathUtils.random(300, 500)
                            }
                            cellInfo.intensity >= THUNDERSTORM_CONFLICT_THRESHOLD - 2 -> {
                                speed.vertSpdFpm += MathUtils.randomSign() * MathUtils.random(100, 300)
                            }
                            cellInfo.intensity >= THUNDERSTORM_CONFLICT_THRESHOLD - 4 -> {
                                speed.vertSpdFpm += MathUtils.random(-100, 100)
                            }
                        }
                    }
                }

                if (redZones >= 5) {
                    conflicts.add(Conflict(
                        conflictAble, null, null,
                        3f,Conflict.STORM)
                    )
                }
            }
        }
    }
}
