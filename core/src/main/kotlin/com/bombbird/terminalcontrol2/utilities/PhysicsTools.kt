package com.bombbird.terminalcontrol2.utilities

/** PHYSICSSSSSSSSSSSSSSSSSSSSSSS */
object PhysicsTools {
    /** Some constants */
    const val SOUND_SPEED_SL_ISA = 340.29f // Speed of sound at sea level in ISA conditions, unit is metres per second
    const val AIR_SPECIFIC_HEATS_RATIO = 1.4f // Ratio of specific heats for air (no unit)
    const val AIR_GAS_CONSTANT = 287.05f // Gas constant of air, unit is joules per kilogram per kelvin

    fun calculateMaxThrustAtConditions(aircraftPerfData: AircraftTypeData.AircraftPerfData): Float {
        return 0f
    }
}