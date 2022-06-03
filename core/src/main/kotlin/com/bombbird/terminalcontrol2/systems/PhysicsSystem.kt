package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.getAppAltAtPos
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*
import ktx.math.plusAssign
import ktx.math.times
import kotlin.math.max
import kotlin.math.tan

/** Main physics update system, which handles physics aspects such as displacement, velocity, acceleration, etc.
 *
 * I love physics
 *
 * Used only in GameServer
 * */
class PhysicsSystem(override val updateTimeS: Float): EntitySystem(), LowFreqUpdate {
    override var timer = 0f

    private val positionUpdateFamily: Family = allOf(Position::class, Altitude::class, Speed::class, Direction::class).get()
    private val windAffectedFamily: Family = allOf(AffectedByWind::class, Position::class).exclude(TakeoffRoll::class, LandingRoll::class).get()
    private val speedUpdateFamily: Family = allOf(Speed::class, Acceleration::class).get()
    private val cmdTargetAltFamily: Family = allOf(AircraftInfo::class, Altitude::class, Speed::class, Acceleration::class, CommandTarget::class)
        .exclude(GlideSlopeCaptured::class, TakeoffRoll::class, LandingRoll::class).get()
    private val cmdTargetSpdFamily: Family = allOf(AircraftInfo::class, Altitude::class, Speed::class, Acceleration::class, CommandTarget::class).get()
    private val cmdTargetHeadingFamily: Family = allOf(IndicatedAirSpeed::class, Direction::class, Speed::class, Acceleration::class, CommandTarget::class)
        .exclude(TakeoffRoll::class, LandingRoll::class).get()
    private val glideSlopeCapturedFamily: Family = allOf(Altitude::class, Speed::class, GlideSlopeCaptured::class, ClearanceAct::class).get()
    private val gsFamily: Family = allOf(Position::class, Altitude::class, GroundTrack::class, Speed::class, Direction::class, Acceleration::class).get()
    private val tasToIasFamily: Family = allOf(Speed::class, IndicatedAirSpeed::class, Altitude::class).exclude(TakeoffRoll::class).get()
    private val accLimitFamily: Family = allOf(Speed::class, Altitude::class, Acceleration::class, AircraftInfo::class).get()
    private val affectedByWindFamily: Family = allOf(Position::class, AffectedByWind::class).get()

    /** Main update function, for values that need to be updated frequently
     *
     * For values that can be updated less frequently and are not dependent on [deltaTime], put in [lowFreqUpdate]
     * */
    override fun update(deltaTime: Float) {
        // Update position with speed, direction
        val positionUpdates = engine.getEntitiesFor(positionUpdateFamily)
        for (i in 0 until positionUpdates.size()) {
            positionUpdates[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val velVector = dir.trackUnitVector.times(ktToPxps(spd.speedKts) * deltaTime)
                pos.x += velVector.x
                pos.y += velVector.y
                dir.trackUnitVector.rotateDeg(-spd.angularSpdDps * deltaTime)
                alt.altitudeFt += spd.vertSpdFpm / 60 * deltaTime
            }
        }

        // Position affected by wind
        val windAffected = engine.getEntitiesFor(windAffectedFamily)
        for (i in 0 until windAffected.size()) {
            windAffected[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                pos.x += wind.windVectorPxps.x * deltaTime
                pos.y += wind.windVectorPxps.y * deltaTime
            }
        }

        // Update speed with acceleration
        val speedUpdates = engine.getEntitiesFor(speedUpdateFamily)
        for (i in 0 until speedUpdates.size()) {
            speedUpdates[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                spd.speedKts += mpsToKt(acc.dSpeedMps2) * deltaTime
                spd.vertSpdFpm += mpsToFpm(acc.dVertSpdMps2) * deltaTime
                spd.angularSpdDps += acc.dAngularSpdDps2 * deltaTime
            }
        }

        // Set altitude behaviour using target values from CommandTarget
        val cmdAltitude = engine.getEntitiesFor(cmdTargetAltFamily)
        for (i in 0 until cmdAltitude.size()) {
            cmdAltitude[i]?.apply {
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply

                // Reach target altitude within 10 seconds, capped by aircraft performance constraints
                var targetVS = (cmd.targetAltFt - alt.altitudeFt) / 10 * 60
                targetVS = MathUtils.clamp(targetVS, aircraftInfo.minVs, aircraftInfo.maxVs) // Clamp to min, max VS (from aircraft performance)
                val maxVsToUse = if (cmd.expedite) MAX_VS_EXPEDITE else MAX_VS
                targetVS = MathUtils.clamp(targetVS, -maxVsToUse, maxVsToUse) // Clamp to ensure no crazy rate of climb/descent

                // Reach target vertical speed within 3 seconds, but is capped between -0.25G and 0.25G
                val targetVAcc = (targetVS - spd.vertSpdFpm) / 3
                acc.dVertSpdMps2 = MathUtils.clamp(fpmToMps(targetVAcc), MIN_VERT_ACC, MAX_VERT_ACC) // Clamp to min 0.75G, max 1.25G
            }
        }

        // Set speed behaviour using target values from CommandTarget
        val cmdIas = engine.getEntitiesFor(cmdTargetSpdFamily)
        for (i in 0 until cmdIas.size()) {
            cmdIas[i]?.apply {
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply

                // Reach target speed within 7 seconds, capped by aircraft performance constraints
                var targetAcc = ktToMps(calculateTASFromIAS(alt.altitudeFt, cmd.targetIasKt.toFloat()) - spd.speedKts) / 7
                targetAcc = MathUtils.clamp(targetAcc, aircraftInfo.minAcc, aircraftInfo.maxAcc) // Clamp to min, max aircraft acceleration
                targetAcc = MathUtils.clamp(targetAcc, -MAX_ACC, MAX_ACC) // Clamp to min, max acceleration

                // Reach target acceleration within 3 seconds
                var targetJerk = (targetAcc - acc.dSpeedMps2) / 3
                targetJerk = MathUtils.clamp(targetJerk, -MAX_JERK, MAX_JERK) // Clamp to min, max jerk

                // Apply jerk to acceleration
                acc.dSpeedMps2 += targetJerk * deltaTime
            }
        }

        // Update properties for aircraft on the glide slope
        val gsCaptured = engine.getEntitiesFor(glideSlopeCapturedFamily)
        for (i in 0 until gsCaptured.size()) {
            gsCaptured[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val gsApp = get(GlideSlopeCaptured.mapper)?.gsApp ?: return@apply
                val appTrack = gsApp[Direction.mapper]?.trackUnitVector ?: return@apply
                val glideAngle = gsApp[GlideSlope.mapper]?.glideAngle ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val track = get(GroundTrack.mapper)?.trackVectorPxps ?: return@apply
                val actingClearance = get(ClearanceAct.mapper)?.actingClearance ?: return@apply
                alt.altitudeFt = getAppAltAtPos(gsApp, pos.x, pos.y, pxpsToKt(track.len())) ?: return@apply
                val gsKtComponentToAppTrack = pxpsToKt(track.dot(appTrack))
                spd.vertSpdFpm = ktToFpm(gsKtComponentToAppTrack * tan(Math.toRadians(glideAngle.toDouble())).toFloat())
                acc.dVertSpdMps2 = 0f
                val prevClearedAlt = actingClearance.actingClearance.clearedAlt
                actingClearance.actingClearance.clearedAlt = 3000 // TODO set to missed approach procedure altitude
                val pendingClearances = get(PendingClearances.mapper)
                if (prevClearedAlt != actingClearance.actingClearance.clearedAlt && (pendingClearances == null || pendingClearances.clearanceQueue.isEmpty)) this += LatestClearanceChanged()
            }
        }

        // Set heading behaviour using target values from CommandTarget
        val cmdHeading = engine.getEntitiesFor(cmdTargetHeadingFamily)
        for (i in 0 until cmdHeading.size()) {
            cmdHeading[i]?.apply {
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply

                // Calculate the change in heading required
                val deltaHeading = findDeltaHeading(convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()), cmd.targetHdgDeg - MAG_HDG_DEV, cmd.turnDir)

                // Reach target heading within 5 seconds, capped by turn rate limit
                var targetAngSpd = deltaHeading / 5
                val maxTurnRate = if (ias.iasKt > 250) MAX_HIGH_SPD_ANGULAR_SPD else MAX_LOW_SPD_ANGULAR_SPD
                targetAngSpd = MathUtils.clamp(targetAngSpd, -maxTurnRate, maxTurnRate)

                // Reach target angular speed within 1.5 seconds
                val targetAngAcc = (targetAngSpd - spd.angularSpdDps) / 1.5f
                acc.dAngularSpdDps2 = MathUtils.clamp(targetAngAcc, -MAX_ANGULAR_ACC, MAX_ANGULAR_ACC) // Clamp to min, max angular acceleration
            }
        }

        // Calculate GS of the aircraft
        val gs = engine.getEntitiesFor(gsFamily)
        for (i in 0 until gs.size()) {
            gs[i]?.apply {
                val groundTrack = get(GroundTrack.mapper) ?: return@apply
                val speed = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val takeoffRoll = get(TakeoffRoll.mapper)
                val landingRoll = get(LandingRoll.mapper)
                val affectedByWind = get(AffectedByWind.mapper)

                groundTrack.trackVectorPxps = if (takeoffRoll != null || landingRoll != null) {
                    val tailwind = affectedByWind?.windVectorPxps?.dot(dir.trackUnitVector) ?: 0f
                    dir.trackUnitVector * ktToPxps(max(speed.speedKts + tailwind, 0f))
                } else {
                    val tasVector = dir.trackUnitVector * ktToPxps(speed.speedKts.toInt())
                    if (affectedByWind != null) tasVector.plusAssign(affectedByWind.windVectorPxps)
                    tasVector
                }
            }
        }

        checkLowFreqUpdate(deltaTime)
    }

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     * */
    override fun lowFreqUpdate() {
        // Calculate the IAS of the aircraft
        val tasToIas = engine.getEntitiesFor(tasToIasFamily)
        for (i in 0 until tasToIas.size()) {
            tasToIas[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                ias.iasKt = calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
            }
        }

        // Calculate the min, max acceleration of the aircraft
        val accLimit = engine.getEntitiesFor(accLimitFamily)
        for (i in 0 until accLimit.size()) {
            accLimit[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val takingOff = has(TakeoffRoll.mapper) || has(LandingRoll.mapper)
                val takeoffClimb = has(TakeoffClimb.mapper)
                val approach = has(LocalizerCaptured.mapper) || has(GlideSlopeCaptured.mapper) || has(VisualCaptured.mapper)

                val fixedVs = if (has(GlideSlopeCaptured.mapper)) {
                    spd.vertSpdFpm
                } else {
                    val cmd = get(CommandTarget.mapper)
                    if (cmd == null) 0f
                    // Minimum vertical speed of 500fpm if more than 100ft away from target altitude
                    else if (cmd.targetAltFt > alt.altitudeFt + 100) 500f
                    else if (cmd.targetAltFt < alt.altitudeFt - 100) -500f
                    else 0f
                }
                // TODO distinguish between aircraft expediting and those not
                aircraftInfo.maxAcc = calculateMaxAcceleration(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, fixedVs, approach, takingOff, takeoffClimb)
                aircraftInfo.minAcc = calculateMinAcceleration(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, fixedVs, approach, takingOff, takeoffClimb)
                aircraftInfo.maxVs = calculateMaxVerticalSpd(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, acc.dSpeedMps2, approach, takingOff, takeoffClimb)
                aircraftInfo.minVs = calculateMinVerticalSpd(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, acc.dSpeedMps2, approach, takingOff, takeoffClimb)
            }
        }

        // Update the wind vector (to that of the METAR of the nearest airport)
        val affectedByWind = engine.getEntitiesFor(affectedByWindFamily)
        for (i in 0 until affectedByWind.size()) {
            affectedByWind[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                wind.windVectorPxps = getClosestAirportWindVector(pos.x, pos.y)
            }
        }
    }
}