package com.bombbird.terminalcontrol2.traffic.conflict

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.TrajectoryPointInfo
import com.bombbird.terminalcontrol2.global.CONFLICT_SIZE
import com.bombbird.terminalcontrol2.utilities.calculateDistanceBetweenPoints
import com.bombbird.terminalcontrol2.utilities.checkIsAircraftConflictInhibited
import com.bombbird.terminalcontrol2.utilities.getMinimaRequired
import com.bombbird.terminalcontrol2.utilities.nmToPx
import ktx.ashley.get
import ktx.collections.GdxArray
import kotlin.math.abs

/** Helper class for checking trajectory point conflicts */
class TrajectoryManager {
    private val predictedConflicts = GdxArray<PredictedConflict>(CONFLICT_SIZE)

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

    }

    private fun checkAllTrajectoryPointConflicts(allTrajectoryPoints: Array<Array<GdxArray<Entity>>>) {
        // Check conflicts between all trajectory points
        for (i in allTrajectoryPoints.indices) {
            for (j in allTrajectoryPoints[i].indices) {
                val pointList = allTrajectoryPoints[i][j]
                for (k in 0 until pointList.size - 1) {
                    checkTrajectoryPointConflict(pointList[k], pointList[k + 1])
                }
            }
        }
    }

    /**
     * Checks if two trajectory points are in conflict; if so adds them to the list of potential conflicts
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

        // If lateral separation is less than minima, and vertical separation less than minima, predicted conflict exists
        val distPx = calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y)
        if (distPx < nmToPx(conflictMinimaRequired.latMinima) &&
            abs(alt1.altitudeFt - alt2.altitudeFt) < conflictMinimaRequired.vertMinima - 25) {
            predictedConflicts.add(PredictedConflict(aircraft1, aircraft2, advanceTimeS.toShort(),
                (pos1.x + pos2.x) / 2, (pos1.y + pos2.y) / 2))
        }
    }
}