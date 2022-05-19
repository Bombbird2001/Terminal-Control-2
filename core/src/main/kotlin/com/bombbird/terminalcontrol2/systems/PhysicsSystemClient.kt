package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
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
 * */
class PhysicsSystemClient: EntitySystem(), LowFreqUpdate {
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
                val velVector = dir.trackUnitVector.times(ktToPxps(spd.speedKts) * deltaTime)
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
                pos.x += wind.windVectorPxps.x * deltaTime
                pos.y += wind.windVectorPxps.y * deltaTime
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
                ias.iasKt = calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
            }
        }

        // Update the wind vector (to that of the METAR of the nearest airport)
        val affectedByWindFamily = allOf(Position::class, AffectedByWind::class).get()
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