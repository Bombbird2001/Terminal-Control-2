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
     * */
    data class AircraftPerfData(val wakeCategory: Char = 'H', val recat: Char = 'B',
                           val thrustNSLISA: Int? = 1026000, val propPowerWSLISA: Int? = null, val propArea: Float? = null,
                           val minCdTimesRefArea: Float = 10.92f, val maxCdTimesRefArea: Float = 87.36f) {

        var appSpd: Short
        var vR: Short
        var weightKg: Int

        init {
            // TODO random generation of load factor
            appSpd = 149
            vR = 160
            weightKg = 259600
        }
    }
}