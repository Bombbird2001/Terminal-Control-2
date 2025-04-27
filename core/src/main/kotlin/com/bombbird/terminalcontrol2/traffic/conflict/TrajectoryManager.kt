package com.bombbird.terminalcontrol2.traffic.conflict

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ArrayMap
import com.badlogic.gdx.utils.Pool
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.TrajectoryPoint
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.getACCStartAltitude
import com.bombbird.terminalcontrol2.traffic.getConflictStartAltitude
import com.bombbird.terminalcontrol2.traffic.getSectorIndexForAlt
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.ashley.hasNot
import ktx.ashley.plusAssign
import ktx.ashley.remove
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import ktx.math.plus
import ktx.math.times
import kotlin.math.*

/** Helper class for checking trajectory point conflicts */
class TrajectoryManager {
    companion object {
        /**
         * Gets the trajectory point positions, given the position [startX], [startY], [currTrack], [targetTrackPxps]
         * vector and the required [turnDir], the aircraft [altitude], [tasKt] and [groundSpeedPxps], and the [directWpt]
         * if any, up to [requiredTimeS] (in seconds)
         */
        fun getTrajectoryPointList(currTrack: Float, targetTrackPxps: Vector2, turnDir: Byte, altitude: Float, tasKt: Float,
                                   groundSpeedPxps: Float, startX: Float, startY: Float, directWpt: Waypoint?, requiredTimeS: Float): GdxArray<Position> {
            val pointList = GdxArray<Position>()
            val targetTrack = modulateHeading(convertWorldAndRenderDeg(targetTrackPxps.angleDeg()))
            val deltaHeading = findDeltaHeading(currTrack, targetTrack, turnDir)
            if (abs(deltaHeading) > 5) {
                // Calculate arc if aircraft is turning > 5 degrees
                // Turn rate in degrees/second
                var turnRate = if (calculateIASFromTAS(altitude, tasKt) > HALF_TURN_RATE_THRESHOLD_IAS) MAX_HIGH_SPD_ANGULAR_SPD else MAX_LOW_SPD_ANGULAR_SPD
                // In px: r = v/w - turnRate must be converted to radians/second, GS is in px/second
                val turnRadiusPx = groundSpeedPxps / Math.toRadians(turnRate.toDouble()).toFloat()
                val centerOffsetAngle = convertWorldAndRenderDeg(currTrack + 90)
                val deltaX = turnRadiusPx * cos(Math.toRadians(centerOffsetAngle.toDouble())).toFloat()
                val deltaY = turnRadiusPx * sin(Math.toRadians(centerOffsetAngle.toDouble())).toFloat()
                val turnCenter = Vector2()
                val centerToCircum = Vector2()
                if (deltaHeading > 0) {
                    // Turning right
                    turnCenter.x = startX + deltaX
                    turnCenter.y = startY + deltaY
                    centerToCircum.x = -deltaX
                    centerToCircum.y = -deltaY
                } else {
                    // Turning left
                    turnCenter.x = startX - deltaX
                    turnCenter.y = startY - deltaY
                    centerToCircum.x = deltaX
                    centerToCircum.y = deltaY
                    turnRate = -turnRate
                }
                var remainingAngle = deltaHeading
                var prevPos = Vector2()
                var prevTargetTrack = targetTrack
                var i = TRAJECTORY_UPDATE_INTERVAL_S
                while (i <= requiredTimeS) {
                    if (remainingAngle / turnRate > TRAJECTORY_UPDATE_INTERVAL_S) {
                        remainingAngle -= turnRate * TRAJECTORY_UPDATE_INTERVAL_S
                        centerToCircum.rotateDeg(-turnRate * TRAJECTORY_UPDATE_INTERVAL_S)
                        val newVector = Vector2(turnCenter)
                        prevPos = newVector.add(centerToCircum)
                        val directWptPos = directWpt?.entity?.get(Position.mapper)
                        if (directWptPos != null) {
                            // Do additional turn checking - this track is the absolute track between points and already includes wind
                            val newTrack = modulateHeading(getRequiredTrack(prevPos.x, prevPos.y, directWptPos.x, directWptPos.y))
                            remainingAngle += newTrack - prevTargetTrack // Add the difference in target track to remaining angle
                            if (newTrack < 16 && newTrack > 0 && prevTargetTrack <= 360 && prevTargetTrack > 344) remainingAngle += 360f // In case new track rotates right past 360 hdg
                            if (newTrack <= 360 && newTrack > 344 && prevTargetTrack < 16) remainingAngle -= 360f // In case new track rotates left past 360 hdg
                            prevTargetTrack = newTrack
                        }
                    } else {
                        val remainingTime = TRAJECTORY_UPDATE_INTERVAL_S - remainingAngle / turnRate
                        centerToCircum.rotateDeg(-remainingAngle)
                        val newVector = Vector2(turnCenter)
                        if (abs(remainingAngle) > 0.1) prevPos = newVector.add(centerToCircum)
                        remainingAngle = 0f
                        val straightVector = Vector2(Vector2.Y).scl(groundSpeedPxps * remainingTime).rotateDeg(-prevTargetTrack)
                        prevPos.add(straightVector)
                    }
                    pointList.add(Position(prevPos.x, prevPos.y))
                    i += TRAJECTORY_UPDATE_INTERVAL_S
                }
            } else {
                // Straight trajectory - just add ground track in px/s * time(s)
                var i = TRAJECTORY_UPDATE_INTERVAL_S
                while (i <= requiredTimeS) {
                    val trackVectorPx = targetTrackPxps * i
                    pointList.add(Position(startX + trackVectorPx.x, startY + trackVectorPx.y))
                    i += TRAJECTORY_UPDATE_INTERVAL_S
                }
            }

            return pointList
        }
    }

    /**
     * Represents a predicted conflict key using aircraft callsign
     */
    class ConflictPair(val aircraft1: String, val aircraft2: String?, val advanceTimeS: Int) {
        override fun equals(other: Any?): Boolean {
            if (other !is ConflictPair) return false
            return (aircraft1 == other.aircraft1 && aircraft2 == other.aircraft2) ||
                    (aircraft1 == other.aircraft2 && aircraft2 == other.aircraft1)
        }

        override fun hashCode(): Int {
            if (aircraft2 == null) return aircraft1.hashCode()
            // Fix the ordering of string for calculating hashcode by putting the larger string first
            if (aircraft1 > aircraft2) return "$aircraft1$aircraft2".hashCode()
            return "$aircraft2$aircraft1".hashCode()
        }
    }

    /**
     * Represents a predicted conflict entry between two aircraft
     */
    private class PredictedConflictEntry(val key: ConflictPair, val value: PredictedConflict)

    private val predictedConflicts = GdxArrayMap<ConflictPair, PredictedConflict>(false, CONFLICT_SIZE)

    private val trajectoryPool = object : Pool<TrajectoryPoint>() {
        override fun newObject(): TrajectoryPoint {
            return TrajectoryPoint()
        }
    }

    /**
     * Frees [allTrajectoryPoints] back to the pool
     */
    fun freePooledTrajectoryPoints(allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>) {
        for (i in allTrajectoryPoints.indices) {
            for (j in 0 until allTrajectoryPoints[i].size) {
                for (k in 0 until allTrajectoryPoints[i][j].size) {
                    trajectoryPool.free(allTrajectoryPoints[i][j][k])
                }
            }
        }
    }

    /**
     * Checks for aircraft separation and MVA/restricted area conflicts for all trajectory points provided in
     * [allTrajectoryPoints]
     */
    fun checkTrajectoryConflicts(allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>) {
        // Clear all conflicts
        predictedConflicts.clear()

        // Check aircraft separation conflict
        predictedConflicts.putAll(checkAllTrajectoryPointConflicts(allTrajectoryPoints))

        // Check MVA/restricted area conflict
        predictedConflicts.putAll(checkAllTrajectoryMVAConflicts(allTrajectoryPoints))

        // Check thunderstorm conflicts
        checkStormConflicts(allTrajectoryPoints)

        // Send all predicted conflicts to clients using TCP
        GAME.gameServer?.sendPredictedConflicts(predictedConflicts)

        resolveACCConflicts()
    }

    private fun checkAllTrajectoryPointConflicts(allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>): GdxArrayMap<ConflictPair, PredictedConflict> {
        val predictedConflicts = GdxArrayMap<ConflictPair, PredictedConflict>(false, CONFLICT_SIZE)

        // Check conflicts between all trajectory points
        for (i in allTrajectoryPoints.indices) {
            for (j in allTrajectoryPoints[i].indices) {
                val pointList = allTrajectoryPoints[i][j]
                for (k in 0 until pointList.size - 1) {
                    val entry1 = checkTrajectoryPointConflict(pointList[k], pointList[k + 1])
                    if (entry1 != null &&
                        (!predictedConflicts.containsKey(entry1.key)
                                || predictedConflicts[entry1.key].advanceTimeS > entry1.key.advanceTimeS)) {
                        predictedConflicts[entry1.key] = entry1.value
                    }
                    // If a layer exists above, check with each aircraft in the above layer
                    if (j + 1 < allTrajectoryPoints[i].size) {
                        val abovePoints = allTrajectoryPoints[i][j + 1]
                        for (l in 0 until abovePoints.size) {
                            val entry2 = checkTrajectoryPointConflict(pointList[k], abovePoints[l])
                            if (entry2 != null &&
                                (!predictedConflicts.containsKey(entry2.key)
                                        || predictedConflicts[entry2.key].advanceTimeS > entry2.key.advanceTimeS)) {
                                predictedConflicts[entry2.key] = entry2.value
                            }
                        }
                    }
                }
            }
        }

        return predictedConflicts
    }

    /**
     * Checks if two trajectory points are in conflict; if so adds them to the list of predicted conflicts
     */
    private fun checkTrajectoryPointConflict(point1: TrajectoryPoint, point2: TrajectoryPoint): PredictedConflictEntry? {
        val aircraft1 = point1.entity[TrajectoryPointInfo.mapper]?.aircraft ?: return null
        val aircraft2 = point2.entity[TrajectoryPointInfo.mapper]?.aircraft ?: return null
        val advanceTimeS = point1.entity[TrajectoryPointInfo.mapper]?.advanceTimingS ?: return null

        if (checkIsAircraftConflictInhibited(aircraft1, aircraft2)) return null

        val conflictMinimaRequired = getMinimaRequired(aircraft1, aircraft2)

        val alt1 = point1.entity[Altitude.mapper] ?: return null
        val alt2 = point2.entity[Altitude.mapper] ?: return null
        val pos1 = point1.entity[Position.mapper] ?: return null
        val pos2 = point2.entity[Position.mapper] ?: return null
        val acInfo1 = aircraft1[AircraftInfo.mapper] ?: return null
        val acInfo2 = aircraft2[AircraftInfo.mapper] ?: return null

        // If lateral separation is less than minima, and vertical separation less than minima, predicted conflict exists
        val distPx = calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y)
        if (distPx < nmToPx(conflictMinimaRequired.latMinima) &&
            abs(alt1.altitudeFt - alt2.altitudeFt) < conflictMinimaRequired.vertMinima - 25) {
            val entryKey = ConflictPair(acInfo1.icaoCallsign, acInfo2.icaoCallsign, advanceTimeS)
            val entry = PredictedConflict(aircraft1, aircraft2, null, advanceTimeS.toShort(), (pos1.x + pos2.x) / 2,
                (pos1.y + pos2.y) / 2, (alt1.altitudeFt + alt2.altitudeFt) / 2, conflictMinimaRequired.conflictReason)
            return PredictedConflictEntry(entryKey, entry)
        }

        return null
    }

    /**
     * Checks all trajectory points for MVA/restricted area conflicts, returns a map of predicted conflicts
     */
    private fun checkAllTrajectoryMVAConflicts(allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>): GdxArrayMap<ConflictPair, PredictedConflict> {
        // Check conflicts between all trajectory points and MVA/restricted areas
        val mvaConflicts = GdxArrayMap<ConflictPair, PredictedConflict>(false, CONFLICT_SIZE)

        for (i in allTrajectoryPoints.indices) {
            for (j in allTrajectoryPoints[i].indices) {
                val pointList = allTrajectoryPoints[i][j]
                for (k in 0 until pointList.size) {
                    val mvaConflict = checkTrajectoryPointMVARestrictedConflict(pointList[k].entity)
                    if (mvaConflict != null) {
                        val trajInfo = pointList[k].entity[TrajectoryPointInfo.mapper] ?: continue
                        val pos = pointList[k].entity[Position.mapper] ?: continue
                        val alt = pointList[k].entity[Altitude.mapper] ?: continue
                        val entryKey = ConflictPair(trajInfo.aircraft[AircraftInfo.mapper]?.icaoCallsign ?: continue,
                            null, trajInfo.advanceTimingS)
                        val existingEntry = mvaConflicts[entryKey]
                        if (existingEntry == null || existingEntry.advanceTimeS > trajInfo.advanceTimingS)
                            mvaConflicts[entryKey] = PredictedConflict(trajInfo.aircraft, null,
                                mvaConflict.minAltSectorIndex, trajInfo.advanceTimingS.toShort(), pos.x, pos.y,
                                alt.altitudeFt, mvaConflict.reason)
                    }
                }
            }
        }

        return mvaConflicts
    }

    private fun checkStormConflicts(allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>) {
        for (i in allTrajectoryPoints.indices) {
            for (j in allTrajectoryPoints[i].indices) {
                val pointList = allTrajectoryPoints[i][j]
                // Exclude the first 10 seconds of predicted points
                for (k in 2 until pointList.size) {
                    val point = pointList[k].entity
                    val pos = point[Position.mapper] ?: continue
                    val alt = point[Altitude.mapper]?.altitudeFt ?: continue

                    val redZones = getRedCellCountAtPosition(pos.x, pos.y, alt, 1)

                    if (redZones >= 3) {
                        val aircraft = point[TrajectoryPointInfo.mapper]?.aircraft ?: continue
                        val devHdg = getStormDeviationHeading(aircraft)
                        println("${aircraft[AircraftInfo.mapper]?.icaoCallsign}: Deviate to $devHdg")
                        aircraft += WeatherAvoidanceInfo(devHdg)
                    }
                }
            }
        }
    }

    private fun getStormDeviationHeading(aircraft: Entity): Short? {
        val speed = aircraft[Speed.mapper] ?: return null
        val direction = aircraft[Direction.mapper]?.trackUnitVector?.angleDeg() ?: return null
        val acInfo = aircraft[AircraftInfo.mapper] ?: return null
        val startHeading = ((modulateHeading(convertWorldAndRenderDeg(direction) + MAG_HDG_DEV)) / 5).roundToInt() * 5
        println("${acInfo.icaoCallsign}: Start heading - $startHeading")

        // Max 70 degree deviation in steps of 10 degrees
        for (absDev in 1..7) {
            if (speed.angularSpdDps >= 1) {
                // Check right-hand turn first
                val newHdg = modulateHeading(startHeading + absDev * 10f)
                val newTrajectory = calculateTrajectoryToHeading(aircraft, newHdg)
                if (isTrajectoryClearOfStorm(newTrajectory)) return newHdg.roundToInt().toShort()

                // Check left-hand turn
                val newHdg2 = modulateHeading(startHeading - absDev * 10f)
                val newTrajectory2 = calculateTrajectoryToHeading(aircraft, newHdg2)
                if (isTrajectoryClearOfStorm(newTrajectory2)) return newHdg2.roundToInt().toShort()
            } else if (speed.angularSpdDps <= -1) {
                // Check left-hand turn first
                val newHdg = modulateHeading(startHeading - absDev * 10f)
                val newTrajectory = calculateTrajectoryToHeading(aircraft, newHdg)
                if (isTrajectoryClearOfStorm(newTrajectory)) return newHdg.roundToInt().toShort()

                // Check right-hand turn
                val newHdg2 = modulateHeading(startHeading + absDev * 10f)
                val newTrajectory2 = calculateTrajectoryToHeading(aircraft, newHdg2)
                if (isTrajectoryClearOfStorm(newTrajectory2)) return newHdg2.roundToInt().toShort()
            } else {
                // Check both
                val newHdg = modulateHeading(startHeading + absDev * 10f)
                val newTrajectory = calculateTrajectoryToHeading(aircraft, newHdg)
                val rightOk = isTrajectoryClearOfStorm(newTrajectory)

                val newHdg2 = modulateHeading(startHeading - absDev * 10f)
                val newTrajectory2 = calculateTrajectoryToHeading(aircraft, newHdg2)
                val leftOk = isTrajectoryClearOfStorm(newTrajectory2)

                if (leftOk && rightOk) {
                    return (if (MathUtils.randomBoolean()) newHdg else newHdg2).roundToInt().toShort()
                } else if (leftOk) {
                    return newHdg2.roundToInt().toShort()
                } else if (rightOk) {
                    return newHdg.roundToInt().toShort()
                }
            }
        }

        return null
    }

    private fun isTrajectoryClearOfStorm(trajectory: GdxArray<TrajectoryPoint>): Boolean {
        for (i in 0 until trajectory.size) {
            val point = trajectory[i].entity
            val pos = point[Position.mapper] ?: continue
            val alt = point[Altitude.mapper]?.altitudeFt ?: continue

            val zones = getAllZoneCountAtPosition(pos.x, pos.y, alt, 2)
            if (zones >= 1) return false
        }

        return true
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
                val ac1NewClearance = ac1LatestClearance.copyWithNewAltitude(newAlts.first)
                addNewClearanceToPendingClearances(conflict.aircraft1, ac1NewClearance, 0)
            }
            if (ac2ClearedAlt != newAlts.second && conflict.aircraft2[Controllable.mapper]?.sectorId == SectorInfo.CENTRE) {
                val ac2NewClearance = ac2LatestClearance.copyWithNewAltitude(newAlts.second)
                addNewClearanceToPendingClearances(conflict.aircraft2, ac2NewClearance, 0)
            }
        }
    }

    /**
     * Checks all input [conflictAircraft] assigned a temporary altitude to see if they can be cleared to their original
     * target altitude
     */
    fun resolveTempAltitudes(conflictAircraft: ImmutableArray<Entity>, allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>) {
        for (i in 0 until conflictAircraft.size()) {
            val aircraft = conflictAircraft[i] ?: continue
            val controllable = aircraft[Controllable.mapper] ?: continue
            if (controllable.sectorId != SectorInfo.CENTRE) {
                aircraft.remove<ACCTempAltitude>()
                continue
            }
            val finalAlt = aircraft[ACCTempAltitude.mapper]?.finalAltFt ?: continue
            val currClearance = getLatestClearanceState(aircraft) ?: continue
            val currClearedAlt = currClearance.clearedAlt

            val offsetLevels = abs((currClearedAlt - finalAlt) / 1000f).toInt()
            if (offsetLevels == 0) {
                aircraft.remove<ACCTempAltitude>()
                continue
            }

            // Binary search for the altitude that aircraft can be cleared to
            var from = 0
            var to = offsetLevels
            while (from < to) {
                val curr = (from + to + 1) / 2 // Ceiling
                if (checkNewAltitudeClearOfConflict(aircraft, curr * 1000, allTrajectoryPoints)) {
                    // No conflict, try closer to final alt
                    from = curr
                } else {
                    // Conflict, try closer to current cleared alt
                    to = curr - 1
                }
            }
            if (from == 0) {
                // Still have conflict, can't do anything
                continue
            }

            // Clear aircraft to new altitude
            val newClearance = currClearance.copyWithNewAltitude(currClearance.clearedAlt + (from * 1000 * (if (finalAlt > currClearedAlt) 1 else -1)))
            addNewClearanceToPendingClearances(aircraft, newClearance, 0)
        }
    }

    /**
     * Calculates the new trajectory with a [newAltitude] and checks if using it will cause conflict with the current
     * trajectory points; returns true if no conflict, else false
     */
    private fun checkNewAltitudeClearOfConflict(aircraft: Entity, newAltitude: Int,
                                                allTrajectoryPoints: Array<Array<GdxArray<TrajectoryPoint>>>): Boolean {
        val traj = calculateTrajectory(aircraft, newAltitude)
        for (i in 0 until traj.size) {
            val point = traj[i]
            val alt = point.entity[Altitude.mapper]?.altitudeFt ?: continue
            val altLevel = getSectorIndexForAlt(alt, getConflictStartAltitude())
            val allPointsInCurrentTimePoint = allTrajectoryPoints[i]
            // Check with all points in current layer
            for (j in 0 until allPointsInCurrentTimePoint[altLevel].size) {
                val entry = checkTrajectoryPointConflict(point, allPointsInCurrentTimePoint[altLevel][j])
                if (entry != null) {
                    // Conflict exists
                    return false
                }
            }
            // If a layer exists above, check with each point in the above layer
            if (altLevel + 1 < allPointsInCurrentTimePoint.size) {
                val abovePoints = allPointsInCurrentTimePoint[altLevel + 1]
                for (k in 0 until abovePoints.size) {
                    val entry2 = checkTrajectoryPointConflict(point, abovePoints[k])
                    if (entry2 != null) {
                        // Conflict exists
                        return false
                    }
                }
            }
            // If a layer exists below, check with each point in the below layer
            if (altLevel - 1 >= 0) {
                val belowPoints = allPointsInCurrentTimePoint[altLevel - 1]
                for (k in 0 until belowPoints.size) {
                    val entry2 = checkTrajectoryPointConflict(point, belowPoints[k])
                    if (entry2 != null) {
                        // Conflict exists
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Gets all trajectory points of an [aircraft] based on its current target
     * heading to turn to, with an optional [customTargetAlt]
     */
    fun calculateTrajectory(aircraft: Entity, customTargetAlt: Int = -1): GdxArray<TrajectoryPoint> {
        val trajPointList = GdxArray<TrajectoryPoint>()

        val cmdTarget = aircraft[CommandTarget.mapper] ?: return trajPointList
        return calculateTrajectoryToHeading(aircraft, cmdTarget.targetHdgDeg, customTargetAlt, ignoreDirectWaypoint = false)
    }

    /**
     * Gets all trajectory points of an [aircraft] given a [targetHeading] to
     * turn to, with an optional [customTargetAlt]. If [ignoreDirectWaypoint] is
     * true, will use only [targetHeading] for calculation, and will not follow
     * through to the waypoint
     */
    fun calculateTrajectoryToHeading(aircraft: Entity, targetHeading: Float,
                                     customTargetAlt: Int = -1,
                                     ignoreDirectWaypoint: Boolean = true): GdxArray<TrajectoryPoint> {
        val trajPointList = GdxArray<TrajectoryPoint>()

        // Calculate simple linear trajectory, plus arc if aircraft is turning > 5 degrees
        val cmdTarget = aircraft[CommandTarget.mapper] ?: return trajPointList
        val groundTrack = aircraft[GroundTrack.mapper] ?: return trajPointList
        val altitude = aircraft[Altitude.mapper]?.altitudeFt ?: return trajPointList
        val speed = aircraft[Speed.mapper] ?: return trajPointList
        val aircraftPos = aircraft[Position.mapper] ?: return trajPointList
        val winds = aircraft[AffectedByWind.mapper] ?: return trajPointList
        val currTrack = convertWorldAndRenderDeg(groundTrack.trackVectorPxps.angleDeg())
        val groundSpeedPxps = groundTrack.trackVectorPxps.len()
        // This is the vector the aircraft is turning towards, excluding effects of wind
        val targetTrueHeadingPxps = Vector2(Vector2.Y).rotateDeg(-(targetHeading - MAG_HDG_DEV)).scl(ktToPxps(speed.speedKts))
        // This is the track vector the aircraft is turning towards, including effects of wind
        val targetTrackPxps = targetTrueHeadingPxps + winds.windVectorPxps
        val wpt = if (ignoreDirectWaypoint) null else getServerOrClientWaypointMap()?.get(aircraft[CommandDirect.mapper]?.wptId)
        val pointList = getTrajectoryPointList(currTrack, targetTrackPxps, cmdTarget.turnDir, altitude, speed.speedKts,
            groundSpeedPxps, aircraftPos.x, aircraftPos.y, wpt, MAX_TRAJECTORY_ADVANCE_TIME_S)

        // Calculate altitude at each point
        val gsCap = aircraft[GlideSlopeCaptured.mapper]?.gsApp
        for (index in 1 .. pointList.size) {
            val positionPoint = pointList[index - 1]
            val timeS = index * TRAJECTORY_UPDATE_INTERVAL_S // Time from now in seconds
            var targetAlt = if (customTargetAlt == -1) cmdTarget.targetAltFt.toFloat() else customTargetAlt.toFloat()
            if (gsCap != null) targetAlt = (gsCap[ApproachInfo.mapper]?.rwyObj?.entity?.get(Altitude.mapper)?.altitudeFt ?: -100f) - 10f
            val pointAltitude = if (altitude > targetAlt) {
                // Descending
                max(altitude + speed.vertSpdFpm * timeS / 60, targetAlt).toInt()
            } else {
                // Climbing
                min(altitude + speed.vertSpdFpm * timeS / 60, targetAlt).toInt()
            }
            val trajPoint = trajectoryPool.obtain()
            trajPoint.init(aircraft, positionPoint.x, positionPoint.y, pointAltitude.toFloat(), timeS.roundToInt())
            trajPointList.add(trajPoint)
        }

        return trajPointList
    }
}