package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IntervalSystem
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.TrajectoryPoint
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.getConflictStartAltitude
import com.bombbird.terminalcontrol2.traffic.getSectorIndexForAlt
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.math.plus
import ktx.math.times
import kotlin.math.*

/**
 * Very low frequency trajectory prediction system
 *
 * Used only on GameServer
 */
class TrajectorySystemInterval: IntervalSystem(TRAJECTORY_UPDATE_INTERVAL_S) {
    companion object {
        private val aircraftTrajectoryFamily = allOf(AircraftInfo::class, GroundTrack::class, Speed::class, Position::class, Altitude::class, CommandTarget::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val aircraftTrajectoryEntities = FamilyWithListener.newServerFamilyWithListener(aircraftTrajectoryFamily)

    private val startingAltitude = getConflictStartAltitude()
    /** This stores the trajectory position divided in their respective conflict altitude levels, per time interval */
    var trajectoryTimeStates = Array<Array<GdxArray<Entity>>>(0) {
        Array(0) {
            GdxArray()
        }
    }

    override fun updateInterval() {
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
                calculateTrajectory(this)
            }
        }
    }

    /** Gets all trajectory points of an aircraft */
    private fun calculateTrajectory(aircraft: Entity, customTargetAlt: Int = -1) {
        val pointList = GdxArray<Position>()

        // Calculate simple linear trajectory, plus arc if aircraft is turning > 5 degrees
        val requiredTime = MAX_TRAJECTORY_ADVANCE_TIME_S
        val cmdTarget = aircraft[CommandTarget.mapper] ?: return
        val groundTrack = aircraft[GroundTrack.mapper] ?: return
        val speed = aircraft[Speed.mapper] ?: return
        val aircraftPos = aircraft[Position.mapper] ?: return
        val winds = aircraft[AffectedByWind.mapper] ?: return
        val targetHeading = cmdTarget.targetHdgDeg
        val currTrack = convertWorldAndRenderDeg(groundTrack.trackVectorPxps.angleDeg())
        val currHdg = modulateHeading(currTrack + MAG_HDG_DEV)
        val groundSpeedPxps = groundTrack.trackVectorPxps.len()
        // This is the vector the aircraft is turning towards, excluding effects of wind
        val targetTrueHeadingPxps = Vector2(Vector2.Y).rotateDeg(-(targetHeading + MAG_HDG_DEV)).scl(ktToPxps(speed.speedKts))
        // This is the track heading the aircraft is turning towards, including effects of wind
        var targetTrack = convertWorldAndRenderDeg((targetTrueHeadingPxps + winds.windVectorPxps).angleDeg())
        targetTrack = modulateHeading(targetTrack)
        val deltaHeading = findDeltaHeading(currHdg, targetHeading, cmdTarget.turnDir)
        if (abs(deltaHeading) > 5) {
            // Calculate arc if aircraft is turning > 5 degrees
            // Turn rate in degrees/second
            var turnRate = if (speed.speedKts > HALF_TURN_RATE_THRESHOLD_IAS) MAX_HIGH_SPD_ANGULAR_SPD else MAX_LOW_SPD_ANGULAR_SPD
            // In px: r = v/w - turnRate must be converted to radians/second, GS is in px/second
            val turnRadiusPx = groundSpeedPxps / Math.toRadians(turnRate.toDouble()).toFloat()
            val centerOffsetAngle = convertWorldAndRenderDeg(currTrack + 90)
            val deltaX = turnRadiusPx * cos(Math.toRadians(centerOffsetAngle.toDouble())).toFloat()
            val deltaY = turnRadiusPx * sin(Math.toRadians(centerOffsetAngle.toDouble())).toFloat()
            val turnCenter = Vector2()
            val centerToCircum = Vector2()
            if (deltaHeading > 0) {
                // Turning right
                turnCenter.x = aircraftPos.x + deltaX
                turnCenter.y = aircraftPos.y + deltaY
                centerToCircum.x = -deltaX
                centerToCircum.y = -deltaY
            } else {
                // Turning left
                turnCenter.x = aircraftPos.x - deltaX
                turnCenter.y = aircraftPos.y - deltaY
                centerToCircum.x = deltaX
                centerToCircum.y = deltaY
                turnRate = -turnRate
            }
            var remainingAngle = deltaHeading
            var prevPos = Vector2()
            var prevTargetTrack = targetTrack
            var i = TRAJECTORY_UPDATE_INTERVAL_S
            while (i <= requiredTime) {
                if (remainingAngle / turnRate > TRAJECTORY_UPDATE_INTERVAL_S) {
                    remainingAngle -= turnRate * TRAJECTORY_UPDATE_INTERVAL_S
                    centerToCircum.rotateDeg(-turnRate * TRAJECTORY_UPDATE_INTERVAL_S)
                    val newVector = Vector2(turnCenter)
                    prevPos = newVector.add(centerToCircum)
                    val directWptPos = aircraft[CommandDirect.mapper]?.let {
                        GAME.gameServer?.waypoints?.get(it.wptId)?.entity?.get(Position.mapper)
                    }
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
            while (i <= requiredTime) {
                val trackVectorPx = groundTrack.trackVectorPxps * i
                pointList.add(Position(aircraftPos.x + trackVectorPx.x, aircraftPos.y + trackVectorPx.y))
                i += TRAJECTORY_UPDATE_INTERVAL_S
            }
        }

        // Calculate altitude at each point
        val altitude = aircraft[Altitude.mapper]?.altitudeFt ?: return
        // val acInfo = aircraft[AircraftInfo.mapper] ?: return
        val gsCap = aircraft[GlideSlopeCaptured.mapper]?.gsApp
        var index = 1
        for (positionPoint in pointList) {
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
            val expectedSector = getSectorIndexForAlt(pointAltitude.toFloat(), startingAltitude)
            if (expectedSector >= 0 && expectedSector < trajectoryTimeStates[index - 1].size)
                trajectoryTimeStates[index - 1][expectedSector].add(TrajectoryPoint(aircraft, positionPoint.x,
                    positionPoint.y, pointAltitude.toFloat(), timeS.roundToInt()).entity)
            index++
        }
    }

    /** Creates the conflict level array upon loading world data (MAX_ALT required) */
    fun initializeConflictLevelArray(maxAlt: Int, vertSep: Int) {
        trajectoryTimeStates = Array((MAX_TRAJECTORY_ADVANCE_TIME_S / TRAJECTORY_UPDATE_INTERVAL_S.toInt()).roundToInt()) {
            Array(ceil((maxAlt + 1500f) / vertSep).roundToInt() - startingAltitude / vertSep) {
                GdxArray()
            }
        }
    }
}