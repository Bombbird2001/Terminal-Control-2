package com.bombbird.terminalcontrol2.traffic.conflict

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.TrajectoryPointInfo
import com.bombbird.terminalcontrol2.global.CONFLICT_SIZE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import kotlin.math.abs

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
            val existingEntry = predictedConflicts[entryKey]
            if (existingEntry == null || existingEntry.advanceTimeS > advanceTimeS)
                predictedConflicts[entryKey] = PredictedConflict(aircraft1, aircraft2, advanceTimeS.toShort(),
                    (pos1.x + pos2.x) / 2, (pos1.y + pos2.y) / 2)
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
                        val entryKey = trajInfo.aircraft[AircraftInfo.mapper]?.icaoCallsign ?: continue
                        val existingEntry = predictedConflicts[entryKey]
                        if (existingEntry == null || existingEntry.advanceTimeS > trajInfo.advanceTimingS)
                            predictedConflicts[entryKey] = PredictedConflict(trajInfo.aircraft, null,
                                trajInfo.advanceTimingS.toShort(), pos.x, pos.y)
                    }
                }
            }
        }
    }
}