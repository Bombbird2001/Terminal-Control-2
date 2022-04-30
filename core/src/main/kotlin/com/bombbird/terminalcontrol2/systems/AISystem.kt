package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.utilities.MathTools
import com.bombbird.terminalcontrol2.utilities.PhysicsTools
import ktx.ashley.addComponent
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.remove
import kotlin.math.min

/** Main AI system, which handles aircraft flight controls, implementing behaviour for various basic and advanced flight modes
 *
 * Flight modes will directly alter [CommandTarget], which will then interact with PhysicsSystem to execute the required behaviour
 * */
class AISystem: EntitySystem() {
    /** Main update function */
    override fun update(deltaTime: Float) {
        // Set acceleration for takeoff
        val takeoffAccFamily = allOf(Acceleration::class, AircraftInfo::class, TakeoffRoll::class, Speed::class, Direction::class, AffectedByWind::class).get()
        val takeoffAcc = engine.getEntitiesFor(takeoffAccFamily)
        for (i in 0 until takeoffAcc.size()) {
            takeoffAcc[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                ias.iasKt = PhysicsTools.calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
                if (ias.iasKt >= aircraftInfo.aircraftPerf.vR + PhysicsTools.calculateIASFromTAS(alt.altitudeFt, MathTools.pxpsToKt(wind.windVector.dot(dir.trackUnitVector)))) {
                    // Transition to takeoff climb mode
                    remove<TakeoffRoll>()
                    addComponent<TakeoffClimb>(engine) {
                        accelAltFt = alt.altitudeFt + MathUtils.random(1500, 2000)
                    }
                    return@apply
                }
                val acc = get(Acceleration.mapper) ?: return@apply
                val takeoffRoll = get(TakeoffRoll.mapper) ?: return@apply
                acc.dSpeedMps2 = min(takeoffRoll.targetAccMps2, aircraftInfo.maxAcc)
            }
        }

        // Set initial takeoff climb, transition to acceleration
        val takeoffClimbFamily = allOf(Altitude::class, CommandTarget::class, TakeoffClimb::class).get()
        val takeoffClimb = engine.getEntitiesFor(takeoffClimbFamily)
        for (i in 0 until takeoffClimb.size()) {
            takeoffClimb[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val tkOff = get(TakeoffClimb.mapper) ?: return@apply
                if (alt.altitudeFt > tkOff.accelAltFt) {
                    // Climbed past acceleration altitude, set new target IAS and remove takeoff climb component
                    cmd.targetIasKt = 250
                    cmd.targetHdgDeg = 92f
                    cmd.targetAltFt = 10000f
                    remove<TakeoffClimb>()
                }
            }
        }
    }
}