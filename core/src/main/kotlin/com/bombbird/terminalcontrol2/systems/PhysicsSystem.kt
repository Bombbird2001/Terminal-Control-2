package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.utilities.MathTools
import com.bombbird.terminalcontrol2.utilities.PhysicsTools
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.math.times
import kotlin.math.min

/** Main physics update system, which handles physics aspects such as positioning, velocity, acceleration, etc.
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
        val positionUpdateFamily = allOf(Position::class, Speed::class, Direction::class).get()
        val positionUpdates = Constants.SERVER_ENGINE.getEntitiesFor(positionUpdateFamily)
        for (i in 0 until positionUpdates.size()) {
            positionUpdates[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val velVector = dir.dirUnitVector.times(MathTools.ktToPxps(spd.speedKts) * deltaTime)
                pos.x += velVector.x
                pos.y += velVector.y
                dir.dirUnitVector.rotateDeg(-spd.angularSpdDps * deltaTime)
            }
        }

        // Update speed with acceleration
        val speedUpdateFamily = allOf(Speed::class, Acceleration::class).get()
        val speedUpdates = Constants.SERVER_ENGINE.getEntitiesFor(speedUpdateFamily)
        for (i in 0 until speedUpdates.size()) {
            speedUpdates[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                spd.speedKts += MathTools.mpsToKt(acc.dSpeed) * deltaTime
                spd.vertSpdFpm += MathTools.mpsToFpm(acc.dVertSpd) * deltaTime
                spd.angularSpdDps += acc.dAngularSpd * deltaTime
            }
        }

        // Set acceleration for takeoff
        val takeoffAccFamily = allOf(Acceleration::class, AircraftInfo::class, TakeoffRoll::class).get()
        val takeoffAcc = Constants.SERVER_ENGINE.getEntitiesFor(takeoffAccFamily)
        for (i in 0 until takeoffAcc.size()) {
            takeoffAcc[i]?.apply {
                val acc = get(Acceleration.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val takeoffRoll = get(TakeoffRoll.mapper) ?: return@apply
                acc.dSpeed = min(takeoffRoll.targetAccMps2, aircraftInfo.maxAcc)
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
        val tasToIasFamily = allOf(Speed::class, IndicatedAirSpeed::class, Altitude::class).get()
        val tasToIas = Constants.SERVER_ENGINE.getEntitiesFor(tasToIasFamily)
        for (i in 0 until tasToIas.size()) {
            tasToIas[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                ias.ias = PhysicsTools.calculateIASFromTAS(alt.altitude, spd.speedKts)
            }
        }

        // Calculate the min, max acceleration of the aircraft
        val accLimitFamily = allOf(Speed::class, Altitude::class, AircraftInfo::class).get()
        val accLimit = Constants.SERVER_ENGINE.getEntitiesFor(accLimitFamily)
        for (i in 0 until accLimit.size()) {
            accLimit[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                aircraftInfo.maxAcc = PhysicsTools.calculateMaxAcceleration(aircraftInfo.aircraftPerf, alt.altitude, spd.speedKts, false)
                aircraftInfo.minAcc = PhysicsTools.calculateMinAcceleration(aircraftInfo.aircraftPerf, alt.altitude, spd.speedKts, false)
            }
        }
    }
}