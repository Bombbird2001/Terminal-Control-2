package com.bombbird.terminalcontrol2.utilities

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** PHYSICSSSSSSSSSSSSSSSSSSSSSSS
 *
 * Note that for all calculations that involve atmospheric conditions, the atmosphere will be assumed to follow ISA
 * conditions; this is to prevent me from going crazy
 * */
object PhysicsTools {
    /** Constants */
    const val AIR_PRESSURE_PA_SL_ISA = 101325 // Pressure of air at sea level in ISA conditions, unit is pascals
    const val SOUND_SPEED_MPS_SL_ISA = 340.29f // Speed of sound at sea level in ISA conditions, unit is metres per second
    const val AIR_SPECIFIC_HEATS_RATIO = 1.4f // Ratio of specific heats for air (no unit)
    const val AIR_GAS_CONSTANT_JPKGPK = 287.05f // Gas constant of air, unit is joules per kilogram per Kelvin
    const val LAPSE_RATE_KPM_ISA = -0.0065f // Rate at which temperature decreases as altitude increases in ISA conditions, unit is Kelvin per metre
    const val TEMPERATURE_K_SL_ISA = 288.15f // Temperature at sea level in ISA conditions, unit is Kelvin
    const val GRAVITY_ACCELERATION_MPS2 = 9.80665f // Gravitational acceleration, unit is metres per second^2

    /** Calculates the temperature, in Kelvin, at [altitudeFt] above sea level at ISA conditions */
    fun calculateTempAtAlt(altitudeFt: Float): Float {
        return if (altitudeFt <= 36090) TEMPERATURE_K_SL_ISA + LAPSE_RATE_KPM_ISA * MathTools.ftToM(altitudeFt) else 216.65f
    }

    /** Calculates the pressure, in pascals, at [altitudeFt] above sea level at ISA conditions */
    fun calculatePressureAtAlt(altitudeFt: Float): Float {
        return if (altitudeFt < 36090) AIR_PRESSURE_PA_SL_ISA * (calculateTempAtAlt(altitudeFt) / TEMPERATURE_K_SL_ISA).pow(-GRAVITY_ACCELERATION_MPS2 / LAPSE_RATE_KPM_ISA / AIR_GAS_CONSTANT_JPKGPK)
        else calculatePressureAtAlt(36089.99f) * exp(-GRAVITY_ACCELERATION_MPS2 * MathTools.ftToM(altitudeFt - 36089.99f) / AIR_GAS_CONSTANT_JPKGPK / calculateTempAtAlt(36089.99f))
    }

    /** Calculates the density of air, in kilograms per metre^3, given the [pressurePa] and [temperatureK] */
    fun calculateDensityWithConditions(pressurePa: Float, temperatureK: Float): Float {
        return pressurePa / AIR_GAS_CONSTANT_JPKGPK / temperatureK
    }

    /** Calculates the maximum thrust, in newtons, of the aircraft given its [AircraftTypeData.AircraftPerfData]
     *
     * [tasKt] must be provided for turboprop/propeller aircraft in order for the max propeller thrust to be calculated
     * */
    fun calculateMaxThrustAtConditions(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float?): Float {
        val temp = calculateTempAtAlt(altitudeFt)
        val pressure = calculatePressureAtAlt(altitudeFt)
        val jetThrust = aircraftPerfData.thrustKnSLISA?.let { thrustKnSLISA ->
            calculateMaxJetThrustAtConditions(thrustKnSLISA, pressure, temp)
        } ?: 0f
        val propPowerKwSLISA = aircraftPerfData.propPowerKwSLISA
        val propArea = aircraftPerfData.propArea
        val propThrust = if (propPowerKwSLISA != null && propArea != null && tasKt != null) calculateMaxPropThrustAtConditions(propPowerKwSLISA, propArea, tasKt, pressure, temp)
        else 0f
        return jetThrust + propThrust
    }

    /** Calculates the maximum jet engine thrust, in newtons, at the specified [pressure] and [temperature],
     * given the max thrust at sea level in ISA conditions ([thrustKnSLISA])
     * */
    fun calculateMaxJetThrustAtConditions(thrustKnSLISA: Short, pressure: Float, temperature: Float): Float {
        return thrustKnSLISA * 1000f * pressure / AIR_PRESSURE_PA_SL_ISA * sqrt(TEMPERATURE_K_SL_ISA / temperature)
    }

    /** Calculates the maximum propeller thrust, in newtons, at the specified [tasKt], [pressure] and [temperature],
     * given the max power at sea level in ISA conditions ([propPowerKwSLISA])
     * */
    fun calculateMaxPropThrustAtConditions(propPowerKwSLISA: Short, propArea: Float, tasKt: Float, pressure: Float, temperature: Float): Float {
        val density = calculateDensityWithConditions(pressure, temperature)
        val tasMps = MathTools.ktToMps(tasKt)
        return sqrt(2 * propArea * density * tasMps * (2 * propArea * density * tasMps * tasMps * tasMps + propPowerKwSLISA * 1000)) - 2 * propArea * density * tasMps * tasMps
    }

    /** Calculates the maximum drag, in newtons, of the aircraft given its [AircraftTypeData.AircraftPerfData],
     * at the specified [altitude] and [tasKt] */
    fun calculateMaxDragAtConditions(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitude: Float, tasKt: Float): Float {
        return calculateDragAtConditions(aircraftPerfData.maxCdTimesRefArea, calculateDensityWithConditions(
            calculatePressureAtAlt(altitude), calculateTempAtAlt(altitude)
        ), tasKt)
    }

    /** Calculates the minimum drag, in newtons, of the aircraft given its [AircraftTypeData.AircraftPerfData],
     * at the specified [altitude] and [tasKt] */
    fun calculateMinDragAtConditions(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitude: Float, tasKt: Float): Float {
        return calculateDragAtConditions(aircraftPerfData.minCdTimesRefArea, calculateDensityWithConditions(
            calculatePressureAtAlt(altitude), calculateTempAtAlt(altitude)
        ), tasKt)
    }

    /** Calculates the drag, in newtons, at the specified [density] and [tasKt] */
    fun calculateDragAtConditions(cdTimesRefArea: Float, density: Float, tasKt: Float): Float {
        val tasMps = MathTools.ktToMps(tasKt)
        return cdTimesRefArea * density * tasMps * tasMps / 2
    }

    /** Calculates the IAS, in knots (more correctly the CAS, but we'll assume corrections between IAS and CAS are negligible),
     * from [tasKt] at [altitudeFt]
     * */
    fun calculateIASFromTASAtConditions(altitudeFt: Float, tasKt: Float): Float {
        val pressurePa = calculatePressureAtAlt(altitudeFt)
        val tasMps = MathTools.ktToMps(tasKt)
        val tempK = calculateTempAtAlt(altitudeFt)
        val impactPressurePa = pressurePa * ((1 + 0.2f * tasMps * tasMps / AIR_SPECIFIC_HEATS_RATIO / AIR_GAS_CONSTANT_JPKGPK / tempK).pow(3.5f) - 1)
        return MathTools.mpsToKt(SOUND_SPEED_MPS_SL_ISA * sqrt(5 * (impactPressurePa / AIR_PRESSURE_PA_SL_ISA + 1).pow(2f / 7) - 5))
    }
}