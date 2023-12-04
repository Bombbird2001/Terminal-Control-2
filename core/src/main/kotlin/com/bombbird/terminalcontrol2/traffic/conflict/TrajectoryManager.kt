package com.bombbird.terminalcontrol2.traffic.conflict

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.utils.ArrayMap
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.CONFLICT_SIZE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.traffic.getACCStartAltitude
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.ashley.hasNot
import ktx.ashley.plusAssign
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Helper class for checking trajectory point conflicts */
class TrajectoryManager {
    private val predictedConflicts = GdxArrayMap<String, PredictedConflict>(CONFLICT_SIZE)

    /**
     * Checks for aircraft separation and MVA/restricted area conflicts for all trajectory points provided in
     * [allTrajectoryPoints]
     */
    fun checkTrajectoryConflicts(allTrajectoryPoints: Array<Array<GdxArray<Entity>>>) {
        // Clear all conflicts
        predictedConflicts.clear()

        // Check aircraft separation conflict
        checkAllTrajectoryPointConflicts(allTrajectoryPoints)

        // Check MVA/restricted area conflict
        checkAllTrajectoryMVAConflicts(allTrajectoryPoints)

        // Send all predicted conflicts to clients using TCP
        GAME.gameServer?.sendPredictedConflicts(predictedConflicts)

        resolveACCConflicts()
    }

    private fun checkAllTrajectoryPointConflicts(allTrajectoryPoints: Array<Array<GdxArray<Entity>>>) {
        // Check conflicts between all trajectory points
        for (i in allTrajectoryPoints.indices) {
            for (j in allTrajectoryPoints[i].indices) {
                val pointList = allTrajectoryPoints[i][j]
                for (k in 0 until pointList.size - 1) {
                    checkTrajectoryPointConflict(pointList[k], pointList[k + 1])
                    // If a layer exists above, check with each aircraft in the above layer
                    if (j + 1 < allTrajectoryPoints[i].size) {
                        val abovePoints = allTrajectoryPoints[i][j + 1]
                        for (l in 0 until abovePoints.size) checkTrajectoryPointConflict(pointList[k], abovePoints[l])
                    }
                }
            }
        }
    }

    /**
     * Checks if two trajectory points are in conflict; if so adds them to the list of predicted conflicts
     */
    private fun checkTrajectoryPointConflict(point1: Entity, point2: Entity) {
        val aircraft1 = point1[TrajectoryPointInfo.mapper]?.aircraft ?: return
        val aircraft2 = point2[TrajectoryPointInfo.mapper]?.aircraft ?: return
        val advanceTimeS = point1[TrajectoryPointInfo.mapper]?.advanceTimingS ?: return

        if (checkIsAircraftConflictInhibited(aircraft1, aircraft2)) return

        val conflictMinimaRequired = getMinimaRequired(aircraft1, aircraft2)

        val alt1 = point1[Altitude.mapper] ?: return
        val alt2 = point2[Altitude.mapper] ?: return
        val pos1 = point1[Position.mapper] ?: return
        val pos2 = point2[Position.mapper] ?: return
        val acInfo1 = aircraft1[AircraftInfo.mapper] ?: return
        val acInfo2 = aircraft2[AircraftInfo.mapper] ?: return

        // If lateral separation is less than minima, and vertical separation less than minima, predicted conflict exists
        val distPx = calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y)
        if (distPx < nmToPx(conflictMinimaRequired.latMinima) &&
            abs(alt1.altitudeFt - alt2.altitudeFt) < conflictMinimaRequired.vertMinima - 25) {
            val entryKey = "${acInfo1.icaoCallsign}${acInfo2.icaoCallsign}"
            val entryKey2 = "${acInfo2.icaoCallsign}${acInfo1.icaoCallsign}"
            val existingEntry = predictedConflicts[entryKey] ?: predictedConflicts[entryKey2]
            if (existingEntry == null || existingEntry.advanceTimeS > advanceTimeS)
                predictedConflicts[entryKey] = PredictedConflict(aircraft1, aircraft2, advanceTimeS.toShort(),
                    (pos1.x + pos2.x) / 2, (pos1.y + pos2.y) / 2, (alt1.altitudeFt + alt2.altitudeFt) / 2)
        }
    }

    /**
     * Checks all trajectory points for MVA/restricted area conflicts; if so adds them to the list of predicted conflicts
     */
    private fun checkAllTrajectoryMVAConflicts(allTrajectoryPoints: Array<Array<GdxArray<Entity>>>) {
        // Check conflicts between all trajectory points and MVA/restricted areas
        for (i in allTrajectoryPoints.indices) {
            for (j in allTrajectoryPoints[i].indices) {
                val pointList = allTrajectoryPoints[i][j]
                for (k in 0 until pointList.size) {
                    if (checkTrajectoryPointMVARestrictedConflict(pointList[k])) {
                        val trajInfo = pointList[k][TrajectoryPointInfo.mapper] ?: continue
                        val pos = pointList[k][Position.mapper] ?: continue
                        val alt = pointList[k][Altitude.mapper] ?: continue
                        val entryKey = trajInfo.aircraft[AircraftInfo.mapper]?.icaoCallsign ?: continue
                        val existingEntry = predictedConflicts[entryKey]
                        if (existingEntry == null || existingEntry.advanceTimeS > trajInfo.advanceTimingS)
                            predictedConflicts[entryKey] = PredictedConflict(trajInfo.aircraft, null,
                                trajInfo.advanceTimingS.toShort(), pos.x, pos.y, alt.altitudeFt)
                    }
                }
            }
        }
    }

    /** Re-clear altitude for conflicts that has not been resolved */
    private fun resolveACCConflicts() {
        val aircraftConflicts = ArrayMap.Entries(predictedConflicts).filter { it.value.altFt >= getACCStartAltitude() }.map { it.value }

        for (conflict in aircraftConflicts) {
            // Ignore MVA conflicts
            if (conflict.aircraft2 == null) continue
            
            // Store aircraft's cleared altitude prior to modification, if not already present
            val ac1LatestClearance = getLatestClearanceState(conflict.aircraft1) ?: continue
            val ac2LatestClearance = getLatestClearanceState(conflict.aircraft2) ?: continue
            val ac1ClearedAlt = ac1LatestClearance.clearedAlt
            if (conflict.aircraft1.hasNot(ACCTempAltitude.mapper)) conflict.aircraft1 += ACCTempAltitude(ac1ClearedAlt)
            val ac2ClearedAlt = ac2LatestClearance.clearedAlt
            if (conflict.aircraft2.hasNot(ACCTempAltitude.mapper)) conflict.aircraft2 += ACCTempAltitude(ac2ClearedAlt)
            
            val ac1Alt = conflict.aircraft1[Altitude.mapper]?.altitudeFt?.roundToInt() ?: continue
            val ac2Alt = conflict.aircraft2[Altitude.mapper]?.altitudeFt?.roundToInt() ?: continue
            val conflictAlt = conflict.altFt.roundToInt()

            // Calculate new altitude clearances to avoid conflict
            val newAlts = when {
                (ac1ClearedAlt < ac1Alt && ac2ClearedAlt > ac2Alt) -> {
                    // Case 1: 1st aircraft is descending from above, 2nd aircraft climbing from below
                    Pair(ceil(conflict.altFt / 1000).toInt() * 1000, floor(conflict.altFt / 1000).toInt() * 1000)
                }
                (ac1ClearedAlt > ac1Alt && ac2ClearedAlt < ac2Alt) -> {
                    // Reverse of case 1
                    Pair(floor(conflict.altFt / 1000).toInt() * 1000, ceil(conflict.altFt / 1000).toInt() * 1000)
                }
                (ac1ClearedAlt >= ac1Alt && ac2ClearedAlt >= ac2Alt && ac1Alt != ac2Alt) -> {
                    // Case 2: Both aircraft climbing from below, but will intersect due to vertical speed differences
                    // Clear the aircraft below to 1000 feet below the conflict alt floor
                    if (ac1Alt < ac2Alt) Pair(floor(conflict.altFt / 1000).toInt() * 1000 - 1000, ac2ClearedAlt)
                    else Pair(ac1ClearedAlt, floor(conflict.altFt / 1000).toInt() * 1000 - 1000)
                }
                (ac1ClearedAlt <= ac1Alt && ac2ClearedAlt <= ac2Alt && ac1Alt != ac2Alt) -> {
                    // Case 3: Both aircraft descending from above, but will intersect due to vertical speed differences
                    // Clear the aircraft above to 1000 feet above the conflict alt ceiling
                    if (ac1Alt > ac2Alt) Pair(ceil(conflict.altFt / 1000).toInt() * 1000 + 1000, ac2ClearedAlt)
                    else Pair(ac1ClearedAlt, ceil(conflict.altFt / 1000).toInt() * 1000 + 1000)
                }
                (ac1Alt == ac1ClearedAlt && ac2Alt == ac2ClearedAlt) -> {
                    // Case 4: Both aircraft flying level at same altitude
                    val ac1FlightType = conflict.aircraft1[FlightType.mapper] ?: continue
                    val ac2FlightType = conflict.aircraft2[FlightType.mapper] ?: continue
                    if (ac1FlightType.type == FlightType.ARRIVAL && ac2FlightType.type == FlightType.DEPARTURE) {
                        Pair(floor(conflict.altFt / 1000).toInt() * 1000, ceil(conflict.altFt / 1000).toInt() * 1000)
                    } else if (ac1FlightType.type == FlightType.DEPARTURE && ac2FlightType.type == FlightType.ARRIVAL) {
                        Pair(ceil(conflict.altFt / 1000).toInt() * 1000, floor(conflict.altFt / 1000).toInt() * 1000)
                    } else if (ac1FlightType.type == FlightType.ARRIVAL && ac2FlightType.type == FlightType.ARRIVAL) {
                        // Clear ac1 to 1000 feet lower
                        Pair(floor(conflict.altFt / 1000).toInt() * 1000 - 1000, ac2ClearedAlt)
                    } else if (ac1FlightType.type == FlightType.DEPARTURE && ac2FlightType.type == FlightType.DEPARTURE) {
                        // Clear ac1 to 1000 feet higher
                        Pair(ceil(conflict.altFt / 1000).toInt() * 1000 + 1000, ac2ClearedAlt)
                    } else Pair(ac1ClearedAlt, ac2ClearedAlt)
                }
                else -> Pair(ac1ClearedAlt, ac2ClearedAlt)
            }

            // Set the new cleared altitudes only if the current cleared altitude is different, and the aircraft is
            // under ACC control
            if (ac1ClearedAlt != newAlts.first && conflict.aircraft1[Controllable.mapper]?.sectorId == SectorInfo.CENTRE) {
                val ac1NewClearance = ac1LatestClearance.copy(clearedAlt = newAlts.first)
                addNewClearanceToPendingClearances(conflict.aircraft1, ac1NewClearance, 0)
            }
            if (ac2ClearedAlt != newAlts.second && conflict.aircraft2[Controllable.mapper]?.sectorId == SectorInfo.CENTRE) {
                val ac2NewClearance = ac2LatestClearance.copy(clearedAlt = newAlts.second)
                addNewClearanceToPendingClearances(conflict.aircraft2, ac2NewClearance, 0)
            }
        }
    }

    /**
     * Checks all input [aircraft] assigned a temporary altitude to see if they can be cleared to their original
     * target altitude
     */
    fun checkAircraftConflictResolved(aircraft: ImmutableArray<Entity>) {
        for (i in 0 until aircraft.size()) {
            // TODO
        }
    }
}