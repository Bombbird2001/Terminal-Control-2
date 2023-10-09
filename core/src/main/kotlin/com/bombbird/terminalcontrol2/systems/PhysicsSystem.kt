package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.getAppAltAtPos
import com.bombbird.terminalcontrol2.traffic.updateWakeTrailState
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*
import ktx.math.plusAssign
import ktx.math.times
import kotlin.math.max
import kotlin.math.tan

/**
 * Main physics update system, which handles physics aspects such as displacement, velocity, acceleration, etc.
 *
 * I love physics
 *
 * Used only in GameServer
 */
class PhysicsSystem: EntitySystem() {
    companion object {
        private val positionUpdateFamily: Family = allOf(Position::class, Altitude::class, Speed::class, Direction::class)
            .exclude(WaitingTakeoff::class).get()
        private val windAffectedFamily: Family = allOf(AffectedByWind::class, Position::class)
            .exclude(TakeoffRoll::class, LandingRoll::class).get()
        private val speedUpdateFamily: Family = allOf(Speed::class, Acceleration::class)
            .exclude(WaitingTakeoff::class).get()
        private val cmdTargetAltFamily: Family = allOf(AircraftInfo::class, Altitude::class, Speed::class, Acceleration::class, CommandTarget::class)
            .exclude(GlideSlopeCaptured::class, TakeoffRoll::class, LandingRoll::class).get()
        private val cmdTargetSpdFamily: Family = allOf(AircraftInfo::class, Altitude::class, Speed::class, Acceleration::class, CommandTarget::class).get()
        private val cmdTargetHeadingFamily: Family = allOf(IndicatedAirSpeed::class, Direction::class, Speed::class, Acceleration::class, CommandTarget::class)
            .exclude(TakeoffRoll::class, LandingRoll::class, WaitingTakeoff::class).get()
        private val glideSlopeCapturedFamily: Family = allOf(Altitude::class, Speed::class, GlideSlopeCaptured::class).get()
        private val gsFamily: Family = allOf(Position::class, Altitude::class, GroundTrack::class, Speed::class, Direction::class, Acceleration::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val positionUpdateFamilyEntities = FamilyWithListener.newServerFamilyWithListener(positionUpdateFamily)
    private val windAffectedFamilyEntities = FamilyWithListener.newServerFamilyWithListener(windAffectedFamily)
    private val speedUpdateFamilyEntities = FamilyWithListener.newServerFamilyWithListener(speedUpdateFamily)
    private val cmdTargetAltFamilyEntities = FamilyWithListener.newServerFamilyWithListener(cmdTargetAltFamily)
    private val cmdTargetSpdFamilyEntities = FamilyWithListener.newServerFamilyWithListener(cmdTargetSpdFamily)
    private val cmdTargetHeadingFamilyEntities = FamilyWithListener.newServerFamilyWithListener(cmdTargetHeadingFamily)
    private val glideSlopeCapturedFamilyEntities = FamilyWithListener.newServerFamilyWithListener(glideSlopeCapturedFamily)
    private val gsFamilyEntities = FamilyWithListener.newServerFamilyWithListener(gsFamily)

    /**
     * Main update function, for values that need to be updated frequently
     *
     * For values that can be updated less frequently and are not dependent on [deltaTime], put in [PhysicsSystemInterval]
     */
    override fun update(deltaTime: Float) {
        // Update position with speed, direction
        val positionUpdates = positionUpdateFamilyEntities.getEntities()
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

                // Update wake travel distance if component present
                get(WakeTrail.mapper)?.also {
                    it.distNmCounter += spd.speedKts / 3600 * deltaTime // Convert knots to nautical miles per second
                    if (it.distNmCounter > WAKE_DOT_SPACING_NM) {
                        it.distNmCounter -= WAKE_DOT_SPACING_NM
                        updateWakeTrailState(this, engine.getSystem())
                    }
                }
            }
        }

        // Position affected by wind
        val windAffected = windAffectedFamilyEntities.getEntities()
        for (i in 0 until windAffected.size()) {
            windAffected[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                pos.x += wind.windVectorPxps.x * deltaTime
                pos.y += wind.windVectorPxps.y * deltaTime
            }
        }

        // Update speed with acceleration
        val speedUpdates = speedUpdateFamilyEntities.getEntities()
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
        val cmdAltitude = cmdTargetAltFamilyEntities.getEntities()
        for (i in 0 until cmdAltitude.size()) {
            cmdAltitude[i]?.apply {
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply

                // Reach target altitude within 10 seconds, capped by aircraft performance constraints
                var targetVS = get(CommandTargetVertSpd.mapper)?.targetVertSpdFpm ?: ((cmd.targetAltFt - alt.altitudeFt) / 10 * 60)
                targetVS = MathUtils.clamp(targetVS, aircraftInfo.minVs, aircraftInfo.maxVs) // Clamp to min, max VS (from aircraft performance)
                val maxVsToUse = if (has(CommandExpedite.mapper)) MAX_VS_EXPEDITE else MAX_VS
                targetVS = MathUtils.clamp(targetVS, -maxVsToUse, maxVsToUse) // Clamp to ensure no crazy rate of climb/descent
                remove<CommandTargetVertSpd>()

                // Reach target vertical speed within 3 seconds, but is capped between -0.25G and 0.25G
                val targetVAcc = (targetVS - spd.vertSpdFpm) / 3
                acc.dVertSpdMps2 = MathUtils.clamp(fpmToMps(targetVAcc), MIN_VERT_ACC, MAX_VERT_ACC) // Clamp to min 0.75G, max 1.25G
            }
        }

        // Set speed behaviour using target values from CommandTarget
        val cmdIas = cmdTargetSpdFamilyEntities.getEntities()
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
        val gsCaptured = glideSlopeCapturedFamilyEntities.getEntities()
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
                alt.altitudeFt = getAppAltAtPos(gsApp, pos.x, pos.y, pxpsToKt(track.len())) ?: return@apply
                val gsKtComponentToAppTrack = pxpsToKt(track.dot(appTrack))
                spd.vertSpdFpm = ktToFpm(gsKtComponentToAppTrack * tan(Math.toRadians(glideAngle.toDouble())).toFloat())
                acc.dVertSpdMps2 = 0f
            }
        }

        // Set heading behaviour using target values from CommandTarget
        val cmdHeading = cmdTargetHeadingFamilyEntities.getEntities()
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
                val maxTurnRate = if (ias.iasKt > HALF_TURN_RATE_THRESHOLD_IAS) MAX_HIGH_SPD_ANGULAR_SPD else MAX_LOW_SPD_ANGULAR_SPD
                targetAngSpd = MathUtils.clamp(targetAngSpd, -maxTurnRate, maxTurnRate)

                // Reach target angular speed within 1.5 seconds
                val targetAngAcc = (targetAngSpd - spd.angularSpdDps) / 1.5f
                acc.dAngularSpdDps2 = MathUtils.clamp(targetAngAcc, -MAX_ANGULAR_ACC, MAX_ANGULAR_ACC) // Clamp to min, max angular acceleration
            }
        }

        // Calculate GS of the aircraft
        val gs = gsFamilyEntities.getEntities()
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
                    dir.trackUnitVector * max(ktToPxps(speed.speedKts) + tailwind, 0f)
                } else {
                    val tasVector = dir.trackUnitVector * ktToPxps(speed.speedKts.toInt())
                    affectedByWind?.windVectorPxps?.let {
                        if (it.x.isNaN() || it.y.isNaN()) return@let
                        tasVector.plusAssign(it)
                    }
                    tasVector
                }
            }
        }
    }
}