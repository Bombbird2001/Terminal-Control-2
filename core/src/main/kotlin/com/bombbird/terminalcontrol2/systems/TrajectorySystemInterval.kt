package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.TrajectoryPoint
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.conflict.TrajectoryManager
import com.bombbird.terminalcontrol2.traffic.getConflictStartAltitude
import com.bombbird.terminalcontrol2.traffic.getSectorIndexForAlt
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.ashley.remove
import ktx.collections.GdxArray
import kotlin.math.*

/**
 * Very low frequency trajectory prediction system
 *
 * Used only on GameServer
 */
class TrajectorySystemInterval: IntervalSystem(TRAJECTORY_UPDATE_INTERVAL_S) {
    companion object {
        private val aircraftTrajectoryFamily = allOf(AircraftInfo::class, GroundTrack::class, Speed::class, Position::class, Altitude::class, CommandTarget::class)
            .exclude(WaitingTakeoff::class, LandingRoll::class, TakeoffRoll::class).get()
        private val temporaryAltitudeFamily = allOf(ACCTempAltitude::class, Controllable::class).get()
        private val weatherDeviationFamily = allOf(WeatherAvoidanceInfo::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val aircraftTrajectoryEntities = FamilyWithListener.newServerFamilyWithListener(aircraftTrajectoryFamily)
    private val temporaryAltitudeEntities = FamilyWithListener.newServerFamilyWithListener(temporaryAltitudeFamily)
    private val weatherDeviationEntities = FamilyWithListener.newServerFamilyWithListener(weatherDeviationFamily)

    val startingAltitude = getConflictStartAltitude()
    /** This stores the trajectory position divided in their respective conflict altitude levels, per time interval */
    var trajectoryTimeStates = Array<Array<GdxArray<TrajectoryPoint>>>(0) {
        Array(0) {
            GdxArray()
        }
    }
    private val trajectoryManager = TrajectoryManager()

    override fun updateInterval() {
        val weatherDeviation = weatherDeviationEntities.getEntities()
        for (i in 0 until weatherDeviation.size()) {
            weatherDeviation[i]?.apply {
                remove<WeatherAvoidanceInfo>()
            }
        }

        trajectoryManager.freePooledTrajectoryPoints(trajectoryTimeStates)

        // Clear all trajectory points
        for (i in trajectoryTimeStates.indices) {
            for (j in trajectoryTimeStates[i].indices) {
                trajectoryTimeStates[i][j].clear()
            }
        }

        // Calculate and add trajectory points of all aircraft
        val aircraftTrajectory = aircraftTrajectoryEntities.getEntities()
        for (i in 0 until aircraftTrajectory.size()) {
            aircraftTrajectory[i]?.apply {
                val points = trajectoryManager.calculateTrajectory(this)
                for (j in 0 until points.size) {
                    val point = points[j]
                    val alt = point.entity[Altitude.mapper]?.altitudeFt ?: continue
                    val expectedSector = getSectorIndexForAlt(alt, startingAltitude)
                    if (expectedSector >= 0 && expectedSector < trajectoryTimeStates[j].size)
                        trajectoryTimeStates[j][expectedSector].add(point)
                }
            }
        }

        // Check for conflicts among predicted points
        trajectoryManager.checkTrajectoryConflicts(trajectoryTimeStates)

        // Check for aircraft that can be cleared towards their original target altitude after clear of traffic
        trajectoryManager.resolveTempAltitudes(temporaryAltitudeEntities.getEntities(), trajectoryTimeStates)
    }

    /** Creates the conflict level array upon loading world data */
    fun initializeConflictLevelArray(vertSep: Int) {
        trajectoryTimeStates = Array((MAX_TRAJECTORY_ADVANCE_TIME_S / TRAJECTORY_UPDATE_INTERVAL_S.toInt()).roundToInt()) {
            Array(ceil((TRAJECTORY_MAX_ALT + 1000f) / vertSep).roundToInt() - startingAltitude / vertSep) {
                GdxArray()
            }
        }
    }
}