package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.Acceleration
import com.bombbird.terminalcontrol2.components.Direction
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.Speed
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.utilities.MathTools
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.math.times

/** Main physics update system, which handles physics aspects such as positioning, velocity, acceleration, etc.
 *
 * I love physics
 * */
class PhysicsSystem: EntitySystem() {
    override fun update(deltaTime: Float) {
        // Update position with speed, direction
        val positionUpdateFamily = allOf(Position::class, Speed::class, Direction::class).get()
        for (positionUpdate in Constants.SERVER_ENGINE.getEntitiesFor(positionUpdateFamily)) {
            positionUpdate?.apply {
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
        for (speedUpdate in Constants.SERVER_ENGINE.getEntitiesFor(speedUpdateFamily)) {
            speedUpdate?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                spd.speedKts += MathTools.mpsToKt(acc.dSpeed) * deltaTime
                spd.vertSpdFpm += MathTools.mpsToFpm(acc.dVertSpd) * deltaTime
                spd.angularSpdDps += acc.dAngularSpd * deltaTime
            }
        }
    }

}