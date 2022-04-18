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
    fun calculateAirDensity(pressurePa: Float, temperatureK: Float): Float {
        return pressurePa / AIR_GAS_CONSTANT_JPKGPK / temperatureK
    }

    /** Calculates the maximum thrust, in newtons, of the aircraft given its [AircraftTypeData.AircraftPerfData]
     *
     * [tasKt] must be provided for turboprop/propeller aircraft in order for the max propeller thrust to be calculated
     * */
    fun calculateMaxThrust(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float?): Float {
        val temp = calculateTempAtAlt(altitudeFt)
        val pressure = calculatePressureAtAlt(altitudeFt)
        val jetThrust = aircraftPerfData.thrustNSLISA?.let { thrustNSLISA ->
            calculateMaxJetThrust(thrustNSLISA, pressure, temp)
        } ?: 0f
        val propPowerWSLISA = aircraftPerfData.propPowerWSLISA
        val propArea = aircraftPerfData.propArea
        val propThrust = if (propPowerWSLISA != null && propArea != null && tasKt != null) calculateMaxPropThrust(propPowerWSLISA, propArea, tasKt, pressure, temp)
        else 0f
        return jetThrust + propThrust
    }

    /** Calculates the maximum jet engine thrust, in newtons, at the specified [pressure] and [temperature],
     * given the max thrust at sea level in ISA conditions ([thrustNSLISA])
     * */
    fun calculateMaxJetThrust(thrustNSLISA: Int, pressure: Float, temperature: Float): Float {
        return thrustNSLISA * pressure / AIR_PRESSURE_PA_SL_ISA * sqrt(TEMPERATURE_K_SL_ISA / temperature)
    }

    /** Calculates the maximum propeller thrust, in newtons, at the specified [tasKt], [pressure] and [temperature],
     * given the max power at sea level in ISA conditions ([propPowerWSLISA])
     * */
    fun calculateMaxPropThrust(propPowerWSLISA: Int, propArea: Float, tasKt: Float, pressure: Float, temperature: Float): Float {
        val density = calculateAirDensity(pressure, temperature)
        val tasMps = MathTools.ktToMps(tasKt)
        return sqrt(2 * propArea * density * tasMps * (2 * propArea * density * tasMps * tasMps * tasMps + propPowerWSLISA)) - 2 * propArea * density * tasMps * tasMps
    }

    /** Calculates the maximum drag, in newtons, of the aircraft given its [AircraftTypeData.AircraftPerfData],
     * at the specified [altitude] and [tasKt] */
    fun calculateMaxDrag(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitude: Float, tasKt: Float): Float {
        return calculateDrag(aircraftPerfData.maxCdTimesRefArea, calculateAirDensity(
            calculatePressureAtAlt(altitude), calculateTempAtAlt(altitude)
        ), tasKt)
    }

    /** Calculates the minimum drag, in newtons, of the aircraft given its [AircraftTypeData.AircraftPerfData],
     * at the specified [altitudeFt] and [tasKt] */
    fun calculateMinDrag(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float): Float {
        return calculateDrag(aircraftPerfData.minCdTimesRefArea, calculateAirDensity(
            calculatePressureAtAlt(altitudeFt), calculateTempAtAlt(altitudeFt)
        ), tasKt)
    }

    /** Calculates the drag, in newtons, at the specified [densityKgpm3] and [tasKt] */
    fun calculateDrag(cdTimesRefArea: Float, densityKgpm3: Float, tasKt: Float): Float {
        val tasMps = MathTools.ktToMps(tasKt)
        return cdTimesRefArea * densityKgpm3 * tasMps * tasMps / 2
    }

    /** Calculates the IAS, in knots (more correctly the CAS, but we'll assume corrections between IAS and CAS are negligible),
     * from [tasKt] at [altitudeFt]
     * */
    fun calculateIASFromTAS(altitudeFt: Float, tasKt: Float): Float {
        val pressurePa = calculatePressureAtAlt(altitudeFt)
        val tasMps = MathTools.ktToMps(tasKt)
        val tempK = calculateTempAtAlt(altitudeFt)
        val impactPressurePa = pressurePa * ((1 + 0.2f * tasMps * tasMps / AIR_SPECIFIC_HEATS_RATIO / AIR_GAS_CONSTANT_JPKGPK / tempK).pow(3.5f) - 1)
        return MathTools.mpsToKt(SOUND_SPEED_MPS_SL_ISA * sqrt(5 * (impactPressurePa / AIR_PRESSURE_PA_SL_ISA + 1).pow(2f / 7) - 5))
    }

    /** Calculates the TAS, in knots, from [iasKt] (more correctly the CAS, but we'll assume corrections between IAS
     * and CAS are negligible) at [altitudeFt]
     * */
    fun calculateTASFromIAS(altitudeFt: Float, iasKt: Float): Float {
        val pressurePa = calculatePressureAtAlt(altitudeFt)
        val iasMps = MathTools.ktToMps(iasKt)
        val tempK = calculateTempAtAlt(altitudeFt)
        // Splitting up expressions to save my sanity
        val expr1 = (1 + iasMps * iasMps / 5 / SOUND_SPEED_MPS_SL_ISA / SOUND_SPEED_MPS_SL_ISA).pow(3.5f) - 1
        val expr2 = (1 + AIR_PRESSURE_PA_SL_ISA / pressurePa * expr1).pow(2f / 7) - 1
        return sqrt(expr2 * 5 * AIR_SPECIFIC_HEATS_RATIO * AIR_GAS_CONSTANT_JPKGPK * tempK)
    }

    /** Calculates the maximum achievable acceleration, in metres per second^2, of an aircraft given its [aircraftPerfData], [altitudeFt], [tasKt]
     * and whether it is on approach or expediting ([approachExpedite])
     * */
    fun calculateMaxAcceleration(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, approachExpedite: Boolean): Float {
        val thrust = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt)
        val drag = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt) else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt)
        return calculateAcceleration(thrust, drag, aircraftPerfData.weightKg)
    }

    /** Calculates the minimum achievable acceleration (i.e. maximum deceleration), in metres per second^2, of an aircraft given its [aircraftPerfData], [altitudeFt], [tasKt]
     * and whether it is on approach or expediting ([approachExpedite])
     * */
    fun calculateMinAcceleration(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, approachExpedite: Boolean): Float {
        val thrust = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt) * 0.1f // Assume idle power/thrust is 10% of max thrust
        val drag = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt) else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt)
        return calculateAcceleration(thrust, drag, aircraftPerfData.weightKg)
    }

    /** Calculates the acceleration, in metres per second^2, of an aircraft given the [thrustN], [dragN] and [massKg] */
    fun calculateAcceleration(thrustN: Float, dragN: Float, massKg: Int): Float {
        return (thrustN - dragN) / massKg
    }

    /** Calculates required acceleration, in metres per second^2, of an aircraft to accelerate from [initialSpdKt]
     * to [targetSpdKt] within [distanceM], assuming constant acceleration
     * */
    fun calculateRequiredAcceleration(initialSpdKt: Short, targetSpdKt: Short, distanceM: Float): Float {
        val targetMps = MathTools.ktToMps(targetSpdKt.toInt())
        val initialMps = MathTools.ktToMps(initialSpdKt.toInt())
        return (targetMps * targetMps - initialMps * initialMps) / 2 / distanceM
    }
}