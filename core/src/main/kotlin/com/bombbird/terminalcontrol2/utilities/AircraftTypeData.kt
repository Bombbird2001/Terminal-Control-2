package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils

/** Helper object for aircraft performance data */
object AircraftTypeData {
    val aircraftPerfMap = HashMap<String, AircraftPerfData>()

    /** Gets the aircraft performance data for the specified aircraft ICAO type */
    fun getAircraftPerf(icaoType: String): AircraftPerfData {
        return aircraftPerfMap[icaoType] ?: run {
            Gdx.app.log("AircraftTypeData", "No performance data found for $icaoType, returning default data")
            return AircraftPerfData(minCdTimesRefArea = 14.4144f, maxCdTimesRefArea = 74.256f)
        }
    }

    /** Aircraft performance data class
     *
     * [wakeCategory]: ICAO wake turbulence category: L, M, H, J
     *
     * [recat]: ICAO recat wake category: F, E, D, C, B, A
     *
     * [thrustNSLISA]: For turboprop/jet planes only: Max total thrust, in newtons, produced by the jet engines at sea level under ISA conditions
     *
     * [propPowerWSLISA]: For turboprop/propeller planes only: Max total power, in watts, produced by the propeller engines at sea level under ISA conditions
     *
     * [propArea]: For turboprop/propeller planes only: Total propeller blade area
     *
     * [minCdTimesRefArea]: The lowest possible value of the product of the drag coefficient and reference area in the
     * drag equation; will be used for most calculations
     *
     * [maxCdTimesRefArea]: The highest possible value of the product of the drag coefficient and reference area, in metres^2, in the
     * drag equation; used only when expediting descent or on approach
     *
     * [maxIas]: The maximum IAS that the aircraft can fly at (Vmo; below the crossover altitude)
     *
     * [maxMach]: The maximum mach number that the aircraft can fly at (Mmo; above the crossover altitude)
     *
     * [appSpd]: The final aircraft approach IAS
     *
     * [vR]: The rotation IAS, where the aircraft lifts off the runway and begins climbing (technically the aircraft
     * only starts climbing once a certain speed after [vR] has been reached, but to keep the terminology simple we'll
     * use Vr)
     *
     * [climbOutSpeed]: The IAS at which the aircraft will maintain during its initial climb
     *
     * [tripIas]: The IAS at which aircraft will climb/descend/cruise at above 10000 feet and below the crossover altitude
     *
     * [tripMach]: The mach number at which aircraft will climb/descend/cruise at above the crossover altitude
     *
     * [massKg]: Mass of the plane
     * */
    data class AircraftPerfData(val wakeCategory: Char = 'H', val recat: Char = 'B',
                                val thrustNSLISA: Int? = 1026000, val propPowerWSLISA: Int? = null, val propArea: Float? = null,
                                val minCdTimesRefArea: Float = 10.92f, val maxCdTimesRefArea: Float = 87.36f,
                                var maxIas: Short = 340, var maxMach: Float = 0.89f) {

        var appSpd: Short
        var vR: Short
        var climbOutSpeed: Short
        var tripIas: Short
        var tripMach: Float
        var maxAlt: Int
        var massKg: Int

        init {
            // TODO random generation of load factor
            appSpd = 149
            vR = 170
            climbOutSpeed = (vR + MathUtils.random(5, 10)).toShort()
            tripIas = (maxIas * MathUtils.random(0.9f, 0.985f)).toInt().toShort()
            tripMach = maxMach * MathUtils.random(0.915f, 0.945f)
            maxAlt = 43100
            massKg = 259600
        }
    }
}