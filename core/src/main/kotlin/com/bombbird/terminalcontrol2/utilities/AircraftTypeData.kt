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
     * [thrustKnSLISA]: For turboprop/jet planes only: Max total thrust, in kilo-newtons, produced by the jet engines at sea level under ISA conditions
     *
     * [propPowerKwSLISA]: For turboprop/propeller planes only: Max total power, in kilowatts, produced by the propeller engines at sea level under ISA conditions
     *
     * [propArea]: For turboprop/propeller planes only: Total propeller blade area
     *
     * [minCdTimesRefArea]: The lowest possible value of the product of the drag coefficient and reference area in the
     * drag equation; will be used for most calculations
     *
     * [maxCdTimesRefArea]: The highest possible value of the product of the drag coefficient and reference area in the
     * drag equation; used only when expediting descent or on approach
     * */
    class AircraftPerfData(val wakeCat: Char = 'H', val recat: Char = 'B',
                           val thrustKnSLISA: Short? = 240, val propPowerKwSLISA: Short? = null, val propArea: Float? = null,
                           val minCdTimesRefArea: Float = 0.01f, val maxCdTimesRefArea: Float = 0.1f)
}