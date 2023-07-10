package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.utilities.calculateIASFromTAS
import com.bombbird.terminalcontrol2.utilities.getClosestAirportWindVector
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get

/**
 * A lightweight [PhysicsSystemInterval] that only runs certain required calculations on the client device
 *
 * Used only in RadarScreen
 */
class PhysicsSystemIntervalClient: IntervalSystem(1f) {
    companion object {
        private val tasToIasFamily: Family = allOf(Speed::class, IndicatedAirSpeed::class, Altitude::class)
            .exclude(TakeoffRoll::class).get()
        private val affectedByWindFamily: Family = allOf(Position::class, AffectedByWind::class).get()
    }

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in [PhysicsSystemClient]
     */
    override fun updateInterval() {
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