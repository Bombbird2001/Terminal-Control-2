package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.navigation.isApproachCaptured
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.ashley.has

/** A lower frequency [PhysicsSystem] used to deal with physics calculations that can be done at a lower rate */
class PhysicsSystemInterval: IntervalSystem(1f) {
    companion object {
        private val tasToIasFamily: Family = allOf(Speed::class, IndicatedAirSpeed::class, Altitude::class)
            .exclude(TakeoffRoll::class).get()
        private val accLimitFamily: Family = allOf(Speed::class, Altitude::class, Acceleration::class, AircraftInfo::class).get()
        private val affectedByWindFamily: Family = allOf(Position::class, AffectedByWind::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val tasToIasFamilyEntities = FamilyWithListener.newServerFamilyWithListener(tasToIasFamily)
    private val accLimitFamilyEntities = FamilyWithListener.newServerFamilyWithListener(accLimitFamily)
    private val affectedByWindFamilyEntities = FamilyWithListener.newServerFamilyWithListener(affectedByWindFamily)

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in [PhysicsSystem]
     */
    override fun updateInterval() {
        // Calculate the IAS of the aircraft
        val tasToIas = tasToIasFamilyEntities.getEntities()
        for (i in 0 until tasToIas.size()) {
            tasToIas[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                ias.iasKt = calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
            }
        }

        // Calculate the min, max acceleration of the aircraft
        val accLimit = accLimitFamilyEntities.getEntities()
        for (i in 0 until accLimit.size()) {
            accLimit[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val takingOff = has(TakeoffRoll.mapper) || has(LandingRoll.mapper) || has(WaitingTakeoff.mapper)
                val takeoffGoAround = has(TakeoffClimb.mapper) || has(RecentGoAround.mapper)
                val approach = isApproachCaptured(this)
                val expediting = has(CommandExpedite.mapper)

                val fixedVs = if (has(GlideSlopeCaptured.mapper)) spd.vertSpdFpm
                else {
                    val cmd = get(CommandTarget.mapper)
                    if (cmd == null) 0f
                    // Minimum vertical speed of 500fpm if more than 100ft away from target altitude
                    else if (cmd.targetAltFt > alt.altitudeFt + 100) 500f
                    else if (cmd.targetAltFt < alt.altitudeFt - 100) -500f
                    else 0f
                }
                aircraftInfo.maxAcc = calculateMaxAcceleration(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, fixedVs, approach, takingOff, takeoffGoAround)
                aircraftInfo.minAcc = calculateMinAcceleration(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, fixedVs, approach || expediting, takingOff, takeoffGoAround)
                aircraftInfo.maxVs = calculateMaxVerticalSpd(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, acc.dSpeedMps2, approach, takingOff, takeoffGoAround)
                aircraftInfo.minVs = calculateMinVerticalSpd(aircraftInfo.aircraftPerf, alt.altitudeFt, spd.speedKts, acc.dSpeedMps2, approach || expediting, takingOff, takeoffGoAround)
            }
        }

        // Update the wind vector (to that of the METAR of the nearest airport)
        val affectedByWind = affectedByWindFamilyEntities.getEntities()
        for (i in 0 until affectedByWind.size()) {
            affectedByWind[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                wind.windVectorPxps = getInterpolatedWindVector(pos.x, pos.y)
            }
        }
    }
}