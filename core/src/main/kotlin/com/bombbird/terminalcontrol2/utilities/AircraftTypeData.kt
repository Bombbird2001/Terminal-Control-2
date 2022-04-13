package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx

/** Helper object for aircraft performance data */
object AircraftTypeData {
    val aircraftPerfMap = HashMap<String, AircraftPerfData>()

    /** Gets the aircraft performance data for the specified aircraft ICAO type */
    fun getAircraftPerf(icaoType: String): AircraftPerfData {
        return aircraftPerfMap[icaoType] ?: run {
            Gdx.app.log("AircraftTypeData", "No performance data found for $icaoType, returning default data")
            return AircraftPerfData()
        }
    }

    /** Aircraft performance data class
     *
     * [wakeCat]: ICAO wake turbulence category: L, M, H, J
     *
     * [recat]: ICAO recat wake category: F, E, D, C, B, A
     *
     * [thrustKnISA]: Max total thrust produced by the engines at sea level under ISA conditions
     *
     * [propPowerISA]: For turboprop/propeller planes only: Max total power produced by the engines at sea level under ISA conditions
     *
     * [minCdTimesRefArea]: The lowest possible value of the product of the drag coefficient and reference area in the drag equation
     *
     * [maxCdTimesRefArea]: The highest possible value of the product of the drag coefficient and reference area in the drag equation
     * */
    class AircraftPerfData(val wakeCat: Char = 'H', val recat: Char = 'B', val thrustKnISA: Int = 240, val propPowerISA: Int = -1, val minCdTimesRefArea: Int = 0, val maxCdTimesRefArea: Int = 0)
}