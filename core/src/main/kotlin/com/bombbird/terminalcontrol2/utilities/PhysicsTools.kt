package com.bombbird.terminalcontrol2.utilities

/** PHYSICSSSSSSSSSSSSSSSSSSSSSSS
 *
 * Note that for all calculations that involve atmospheric conditions, the atmosphere will be assumed to follow ISA
 * conditions; this is to prevent me from going crazy
 * */
object PhysicsTools {
    /** Constants */
    const val SOUND_SPEED_SL_ISA = 340.29f // Speed of sound at sea level in ISA conditions, unit is metres per second
    const val AIR_SPECIFIC_HEATS_RATIO = 1.4f // Ratio of specific heats for air (no unit)
    const val AIR_GAS_CONSTANT = 287.05f // Gas constant of air, unit is joules per kilogram per Kelvin
    const val LAPSE_RATE_ISA = 0.0065f // Rate at which temperature decreases as altitude increases in ISA conditions, unit is Kelvin per metre
    const val TEMPERATURE_SL_ISA = 288.15f // Temperature at sea level in ISA conditions, unit is Kelvin
    const val GRAVITY_ACCELERATION = 9.81f // Gravitational acceleration, unit is metres per second^2

    /** Calculates the temperature, in Kelvin, at an altitude above sea level in feet */
    fun calculateTempAtAlt(altitude: Float): Float {
        return TEMPERATURE_SL_ISA - LAPSE_RATE_ISA * MathTools.ftToM(altitude)
    }

    /** Calculates the maximum thrust by the engines given its [AircraftTypeData.AircraftPerfData]
     *
     * [tas] must be provided for turboprop/propeller aircraft in order for the propeller thrust to be calculated
     * */
    fun calculateMaxThrustAtConditions(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitude: Float, tas: Float?): Float {
        // TODO
        return 0f
    }
}