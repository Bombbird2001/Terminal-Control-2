package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.utilities.MathTools
import com.bombbird.terminalcontrol2.utilities.MetarTools
import com.bombbird.terminalcontrol2.utilities.PhysicsTools
import ktx.ashley.*
import ktx.math.times

/** Main physics update system, which handles physics aspects such as displacement, velocity, acceleration, etc.
 *
 * I love physics
 * */
class PhysicsSystem: EntitySystem(), LowFreqUpdate {
    /** Main update function, for values that need to be updated frequently
     *
     * For values that can be updated less frequently and are not dependent on [deltaTime], put in [lowFreqUpdate]
     * */
    override fun update(deltaTime: Float) {
        // Update position with speed, direction
        val positionUpdateFamily = allOf(Position::class, Altitude::class, Speed::class, Direction::class).get()
        val positionUpdates = engine.getEntitiesFor(positionUpdateFamily)
        for (i in 0 until positionUpdates.size()) {
            positionUpdates[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val velVector = dir.trackUnitVector.times(MathTools.ktToPxps(spd.speedKts) * deltaTime)
                pos.x += velVector.x
                pos.y += velVector.y
                dir.trackUnitVector.rotateDeg(-spd.angularSpdDps * deltaTime)
                alt.altitudeFt += spd.vertSpdFpm / 60 * deltaTime
            }
        }

        // Position affected by wind
        val windAffectedFamily = allOf(AffectedByWind::class, Position::class).get()
        val windAffected = engine.getEntitiesFor(windAffectedFamily)
        for (i in 0 until windAffected.size()) {
            windAffected[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                pos.x += wind.windVector.x * deltaTime
                pos.y += wind.windVector.y * deltaTime
            }
        }

        // Update speed with acceleration
        val speedUpdateFamily = allOf(Speed::class, Acceleration::class).get()
        val speedUpdates = engine.getEntitiesFor(speedUpdateFamily)
        for (i in 0 until speedUpdates.size()) {
            speedUpdates[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                spd.speedKts += MathTools.mpsToKt(acc.dSpeedMps2) * deltaTime
                spd.vertSpdFpm += MathTools.mpsToFpm(acc.dVertSpdMps2) * deltaTime
                spd.angularSpdDps += acc.dAngularSpdDps2 * deltaTime
            }
        }

        // Set altitude, speed behaviour using target values from CommandTarget
        val cmdTargetFamily = allOf(AircraftInfo::class, Altitude::class, Speed::class, Acceleration::class, CommandTarget::class).exclude(TakeoffRoll::class).get()
        val cmdTarget = engine.getEntitiesFor(cmdTargetFamily)
        for (i in 0 until cmdTarget.size()) {
            cmdTarget[i]?.apply {
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply

                // Reach target altitude within 10 seconds, capped by aircraft performance constraints
                var targetVS = (cmd.targetAltFt - alt.altitudeFt) / 10 * 60
                targetVS = MathUtils.clamp(targetVS, aircraftInfo.minVs, aircraftInfo.maxVs) // Clamp to min, max VS (from aircraft performance)
                targetVS = MathUtils.clamp(targetVS, -Constants.MAX_VS, Constants.MAX_VS) // Clamp to ensure no crazy rate of climb/descent

                // Reach target vertical speed within 3 seconds, but is capped between -0.25G and 0.25G
                val targetVAcc = (targetVS - spd.vertSpdFpm) / 3
                acc.dVertSpdMps2 = MathUtils.clamp(MathTools.fpmToMps(targetVAcc), Constants.MIN_VERT_ACC, Constants.MAX_VERT_ACC) // Clamp to min 0.75G, max 1.25G

                // Reach target speed within 7 seconds, capped by aircraft performance constraints
                var targetAcc = MathTools.ktToMps(PhysicsTools.calculateTASFromIAS(alt.altitudeFt, cmd.targetIasKt.toFloat()) - spd.speedKts) / 7
                targetAcc = MathUtils.clamp(targetAcc, aircraftInfo.minAcc, aircraftInfo.maxAcc) // Clamp to min, max aircraft acceleration
                targetAcc = MathUtils.clamp(targetAcc, -Constants.MAX_ACC, Constants.MAX_ACC) // Clamp to min, max acceleration

                // Reach target acceleration within 3 seconds
                var targetJerk = (targetAcc - acc.dSpeedMps2) / 3
                targetJerk = MathUtils.clamp(targetJerk, -Constants.MAX_JERK, Constants.MAX_JERK) // Clamp to min, max jerk

                // Apply jerk to acceleration
                acc.dSpeedMps2 += targetJerk * deltaTime
            }
        }

        // Set heading behaviour using target values from CommandTarget
        val headingFamily = allOf(IndicatedAirSpeed::class, Direction::class, Speed::class, Acceleration::class, CommandTarget::class).exclude(TakeoffRoll::class).get()
        val heading = engine.getEntitiesFor(headingFamily)
        for (i in 0 until heading.size()) {
            heading[i]?.apply {
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply

                // Calculate the change in heading required
                val deltaHeading = MathTools.findDeltaHeading(MathTools.convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()), cmd.targetHdgDeg - Variables.MAG_HDG_DEV, cmd.turnDir)

                // Reach target heading within 5 seconds, capped by turn rate limit
                var targetAngSpd = deltaHeading / 5
                val maxTurnRate = if (ias.iasKt > 250) Constants.MAX_HIGH_SPD_ANGULAR_SPD else Constants.MAX_LOW_SPD_ANGULAR_SPD
                targetAngSpd = MathUtils.clamp(targetAngSpd, -maxTurnRate, maxTurnRate)

                // Reach target angular speed within 3 seconds
                val targetAngAcc = (targetAngSpd - spd.angularSpdDps) / 3
                acc.dAngularSpdDps2 = MathUtils.clamp(targetAngAcc, -Constants.MAX_ANGULAR_ACC, Constants.MAX_ANGULAR_ACC) // Clamp to min, max angular acceleration
            }
        }
    }

    /** Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     * */
    override fun lowFreqUpdate() {
        // Calculate the IAS of the aircraft
        val tasToIasFamily = allOf(Speed::class, IndicatedAirSpeed::class, Altitude::class).exclude(TakeoffRoll::class).get()
        val tasToIas = engine.getEntitiesFor(tasToIasFamily)
        for (i in 0 until tasToIas.size()) {
            tasToIas[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                ias.iasKt = PhysicsTools.calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
            }
        }

        // Calculate the min, max acceleration of the aircraft
        val accLimitFamily = allOf(Speed::class, Altitude::class, Acceleration::class, AircraftInfo::class).get()
        val accLimit = engine.getEntitiesFor(accLimitFamily)
        for (i in 0 until accLimit.size()) {
            accLimit[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                aircraftInfo.maxAcc = PhysicsTools.calculateMaxAcceleration(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, false)
                aircraftInfo.minAcc = PhysicsTools.calculateMinAcceleration(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, false)

                // TODO distinguish between aircraft on approach/expediting and those not, and those on fixed VS (i.e. priority to VS)
                aircraftInfo.maxVs = PhysicsTools.calculateMaxVerticalSpd(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, acc.dSpeedMps2, false)
                aircraftInfo.minVs = PhysicsTools.calculateMinVerticalSpd(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, acc.dSpeedMps2, false)

//                println("Altitude: ${alt.altitudeFt}ft")
//                println("VS: ${spd.vertSpdFpm}fpm")
//                println("IAS: ${PhysicsTools.calculateIASFromTAS(alt.altitudeFt, spd.speedKts)}kts")
//                println("TAS: ${spd.speedKts}kts")
//                println("Acc: ${acc.dSpeedMps2}")
//                println("Min acc: ${aircraftInfo.minAcc}m/s2")
//                println("Max acc: ${aircraftInfo.maxAcc}m/s2")
//                println("Min VS: ${aircraftInfo.minVs}ft/min")
//                println("Max VS: ${aircraftInfo.maxVs}ft/min")
            }
        }

        // Update the wind vector (to that of the METAR of the nearest airport)
        val affectedByWindFamily = allOf(Position::class, AffectedByWind::class).get()
        val affectedByWind = engine.getEntitiesFor(affectedByWindFamily)
        for (i in 0 until affectedByWind.size()) {
            affectedByWind[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                wind.windVector = MetarTools.getClosestAirportWindVector(pos.x, pos.y)
            }
        }
    }
}