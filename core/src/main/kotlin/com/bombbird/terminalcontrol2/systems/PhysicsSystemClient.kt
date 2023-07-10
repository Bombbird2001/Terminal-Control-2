package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.math.times

/**
 * A lightweight [PhysicsSystem] that only runs certain required calculations on the client device
 *
 * Used only in RadarScreen
 */
class PhysicsSystemClient: EntitySystem() {
    companion object {
        private val positionUpdateFamily: Family = allOf(Position::class, Altitude::class, Speed::class, Direction::class)
            .exclude(WaitingTakeoff::class).get()
        private val windAffectedFamily: Family = allOf(AffectedByWind::class, Position::class)
            .exclude(TakeoffRoll::class, LandingRoll::class).get()
    }

    /**
     * Main update function, for values that need to be updated frequently
     *
     * For values that can be updated less frequently and are not dependent on [deltaTime], put in [PhysicsSystemIntervalClient]
     */
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
    }
}