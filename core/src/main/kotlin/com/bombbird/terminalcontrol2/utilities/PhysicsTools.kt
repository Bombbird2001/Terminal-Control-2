package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Polygon
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.navigation.compareLegEquality
import com.bombbird.terminalcontrol2.navigation.getFirstWaypointLegInSector
import ktx.ashley.get
import kotlin.math.*

/**
 * PHYSICSSSSSSSSSSSSSSSSSSSSSSS
 *
 * Note that for all calculations that involve atmospheric conditions, the atmosphere will be assumed to follow ISA
 * conditions; this is to prevent me from going crazy
 */

/** Constants */
const val AIR_PRESSURE_PA_SL_ISA = 101325 // Pressure of air at sea level in ISA conditions, unit is pascals
const val SOUND_SPEED_MPS_SL_ISA = 340.29f // Speed of sound at sea level in ISA conditions, unit is metres per second
const val AIR_SPECIFIC_HEATS_RATIO = 1.4f // Ratio of specific heats for air (no unit)
const val AIR_GAS_CONSTANT_JPKGPK = 287.05f // Gas constant of air, unit is joules per kilogram per Kelvin
const val LAPSE_RATE_KPM_ISA = -0.0065f // Rate at which temperature decreases as altitude increases in ISA conditions, unit is Kelvin per metre
const val TEMPERATURE_K_SL_ISA = 288.15f // Temperature at sea level in ISA conditions, unit is Kelvin
const val GRAVITY_ACCELERATION_MPS2 = 9.80665f // Gravitational acceleration, unit is metres per second^2
const val PROPELLER_MAX_EFFICIENCY = 0.85f // Maximum efficiency of propeller engines

/**
 * Calculates the temperature at an altitude above sea level at ISA conditions
 * @param altitudeFt the altitude, in feet
 * @return the temperature, in Kelvin, of the air
 */
fun calculateTempAtAlt(altitudeFt: Float): Float {
    return if (altitudeFt <= 36090) TEMPERATURE_K_SL_ISA + LAPSE_RATE_KPM_ISA * ftToM(altitudeFt) else 216.65f
}

/**
 * Calculates the pressure at an altitude above sea level at ISA conditions
 * @param altitudeFt the altitude, in feet
 * @return the pressure, in pascals, of the air
 */
fun calculatePressureAtAlt(altitudeFt: Float): Float {
    return if (altitudeFt < 36090) AIR_PRESSURE_PA_SL_ISA * (calculateTempAtAlt(altitudeFt) / TEMPERATURE_K_SL_ISA).pow(-GRAVITY_ACCELERATION_MPS2 / LAPSE_RATE_KPM_ISA / AIR_GAS_CONSTANT_JPKGPK)
    else calculatePressureAtAlt(36089.99f) * exp(-GRAVITY_ACCELERATION_MPS2 * ftToM(altitudeFt - 36089.99f) / AIR_GAS_CONSTANT_JPKGPK / calculateTempAtAlt(36089.99f))
}

/**
 * Calculates the altitude above sea level at which air is of a certain pressure at ISA conditions
 * @param pressurePa the air pressure at the altitude to be calculated
 * @return the altitude, in feet, at which air pressure is [pressurePa]
 */
fun calculateAltAtPressure(pressurePa: Float): Float {
    return if (pressurePa > 22630.9) mToFt(TEMPERATURE_K_SL_ISA / LAPSE_RATE_KPM_ISA * ((pressurePa / AIR_PRESSURE_PA_SL_ISA).pow(-AIR_GAS_CONSTANT_JPKGPK * LAPSE_RATE_KPM_ISA / GRAVITY_ACCELERATION_MPS2) - 1))
    else calculateAltAtPressure(22630.91f) + mToFt(AIR_GAS_CONSTANT_JPKGPK * calculateTempAtAlt(36089.99f) * ln(pressurePa / 22630.91f) / -GRAVITY_ACCELERATION_MPS2)
}

/**
 * Calculates the air density given the pressure and temperature
 * @param pressurePa the pressure, in pascals, of the air
 * @param temperatureK the temperature, in Kelvin, of the air
 * @return the density of air, in kilograms per metre^3
 */
fun calculateAirDensity(pressurePa: Float, temperatureK: Float): Float {
    return pressurePa / AIR_GAS_CONSTANT_JPKGPK / temperatureK
}

/**
 * Calculates the maximum thrust of the aircraft given its performance data, and TAS if applicable
 * @param aircraftPerfData the aircraft performance data
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at; must be provided for turboprop/propeller aircraft in order
 * for the max propeller thrust to be calculated
 * @return the maximum thrust, in newtons, that can be delivered by the aircraft's engines
 */
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

/**
 * Calculates the maximum jet engine thrust, in newtons, at the specified [pressure] and [temperature],
 * given the max thrust at sea level in ISA conditions ([thrustNSLISA])
 * @param thrustNSLISA the maximum achievable jst thrust, in newtons, at sea level at ISA conditions
 * @param pressure the pressure of the air, in pascals, the aircraft is flying at
 * @param temperature the temperature, in Kelvin, of the surrounding air
 * @return the maximum jet thrust, in newtons, that can be delivered by the jet engines
 */
fun calculateMaxJetThrust(thrustNSLISA: Int, pressure: Float, temperature: Float): Float {
    return thrustNSLISA * pressure / AIR_PRESSURE_PA_SL_ISA * sqrt(TEMPERATURE_K_SL_ISA / temperature)
}

/**
 * Calculates the maximum propeller thrust, in newtons, at the specified TAS, pressure and temperature,
 * given the max power at sea level in ISA conditions and propeller area
 * @param propPowerWSLISA the maximum achievable propeller power, in watts, at sea level at ISA conditions
 * @param propArea the total area, in metres^2, of the propellers
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param pressure the pressure of the air, in pascals, the aircraft is flying at
 * @param temperature the temperature, in Kelvin, of the surrounding air
 * @return the maximum propeller thrust, in newtons, that can be delivered by the propeller engines
 */
fun calculateMaxPropThrust(propPowerWSLISA: Int, propArea: Float, tasKt: Float, pressure: Float, temperature: Float): Float {
    // val density = calculateAirDensity(pressure, temperature)
    // Minimum 10 knots TAS to simulate air movement due to pressure difference even when aircraft's TAS is 0
    val tasMps = ktToMps(max(tasKt, 10f))
    // return sqrt(2 * propArea * density * tasMps * (2 * propArea * density * tasMps * tasMps * tasMps + propPowerWSLISA)) - 2 * propArea * density * tasMps * tasMps
    return propPowerWSLISA / tasMps * PROPELLER_MAX_EFFICIENCY
}

/**
 * Calculates the maximum drag, in newtons, of the aircraft at the specified altitude and TAS
 * @param aircraftPerfData the aircraft performance data of the aircraft
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param takeoffGoAround whether the aircraft is still in the takeoff climb or go around phase
 * @return the maximum achievable drag, in newtons, of the aircraft
 */
fun calculateMaxDrag(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, takingOff: Boolean, takeoffGoAround: Boolean): Float {
    return calculateParasiticDrag(aircraftPerfData.maxCdTimesRefArea, calculateAirDensity(
        calculatePressureAtAlt(altitudeFt), calculateTempAtAlt(altitudeFt)
    ), tasKt) + if (takingOff) 0f else calculateInducedDrag(aircraftPerfData.massKg, aircraftPerfData.maxIas,
        calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt), altitudeFt, tasKt, takeoffGoAround)
}

/**
 * Calculates the minimum drag, in newtons, of the aircraft at the specified altitude and TAS
 * @param aircraftPerfData the aircraft performance data of the aircraft
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param takeoffGoAround whether the aircraft is still in the takeoff climb phase
 * @return the minimum achievable drag, in newtons, of the aircraft
 */
fun calculateMinDrag(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, takingOff: Boolean, takeoffGoAround: Boolean): Float {
    return calculateParasiticDrag(aircraftPerfData.minCd0TimesRefArea, calculateAirDensity(
        calculatePressureAtAlt(altitudeFt), calculateTempAtAlt(altitudeFt)
    ), tasKt) + if (takingOff) 0f else calculateInducedDrag(aircraftPerfData.massKg, aircraftPerfData.maxIas,
        calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt), altitudeFt, tasKt, takeoffGoAround)
}

/**
 * Calculates the parasitic drag, in newtons, with the specified drag coefficient * reference area, air density and TAS
 * @param cdTimesRefArea the product of the drag coefficient and the reference area of the aircraft, in metres^2
 * @param densityKgpm3 the density, in kilograms per metre^3, of the air the aircraft is flying through
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @return the drag, in newtons, the aircraft experiences when flying through air of density [densityKgpm3] at TAS of [tasKt]
 */
fun calculateParasiticDrag(cdTimesRefArea: Float, densityKgpm3: Float, tasKt: Float): Float {
    val tasMps = ktToMps(tasKt)
    return cdTimesRefArea * densityKgpm3 * tasMps * tasMps / 2
}

/**
 * Calculates the induced drag, in newtons, with the specified mass and IAS values
 *
 * This calculation assumes the plane is not experiencing any net vertical force (i.e. constant vertical speed)
 * @param massKg the mass, in kilograms, of the aircraft
 * @param maxIasKt the aircraft's max IAS, in knots, which will be assumed to be the speed at which the aircraft will
 * attain an angle of attack of 2 degrees
 * @param thrustN the thrust, in newtons, produced by the engine which will be taken into account for calculations of the
 * vertical component of forces acting on the aircraft
 * @param altitudeFt the altitude, in feet, of the aircraft
 * @param tasKt the true airspeed, in knots, of the aircraft
 * @param takeoffGoAround whether the aircraft is on its initial takeoff climb or go around phase; this is to prevent
 * induced drag from becoming too high due to the calculated angle of attack being higher than actuality due to low
 * takeoff/approach speed; this will cap the AOA at a max of 7 degrees during this phase
 * @return the induced drag, in newtons, an aircraft of mass of [massKg] experiences flying at TAS of [tasKt] an
 * altitude of [altitudeFt]
 */
fun calculateInducedDrag(massKg: Int, maxIasKt: Short, thrustN: Float, altitudeFt: Float, tasKt: Float, takeoffGoAround: Boolean): Float {
    val iasKt = calculateIASFromTAS(altitudeFt, tasKt)
    val minAoa = 2.0f
    val maxAoa = if (takeoffGoAround) 6.0f else 9.0f
    val aoaDeg = MathUtils.clamp(minAoa * (maxIasKt / iasKt).pow(2), minAoa, maxAoa).toDouble()
    return ((massKg * GRAVITY_ACCELERATION_MPS2 - thrustN * sin(Math.toRadians(aoaDeg))) * tan(Math.toRadians(aoaDeg))).toFloat()
}

/**
 * Calculates the speed of sound at an altitude above sea level at ISA conditions
 * @param altitudeFt the altitude, in feet, above sea level
 * @return the speed of sound, in knots, at [altitudeFt]
 */
fun calculateSpeedOfSoundAtAlt(altitudeFt: Float): Float {
    return mpsToKt(sqrt(AIR_SPECIFIC_HEATS_RATIO * AIR_GAS_CONSTANT_JPKGPK * calculateTempAtAlt(altitudeFt)))
}

/**
 * Calculates the IAS (more correctly the CAS, but we'll assume corrections between IAS and CAS are negligible)
 * from the TAS at an altitude
 * @param altitudeFt the altitude, in feet, of the aircraft
 * @param tasKt the TAS, in knots, of the aircraft
 * @return the IAS, in knots, that results in the aircraft flying with an TAS of [tasKt] at [altitudeFt]
 */
fun calculateIASFromTAS(altitudeFt: Float, tasKt: Float): Float {
    val pressurePa = calculatePressureAtAlt(altitudeFt)
    val tasMps = ktToMps(tasKt)
    val tempK = calculateTempAtAlt(altitudeFt)
    val impactPressurePa = pressurePa * ((1 + 0.2f * tasMps * tasMps / AIR_SPECIFIC_HEATS_RATIO / AIR_GAS_CONSTANT_JPKGPK / tempK).pow(3.5f) - 1)
    return mpsToKt(SOUND_SPEED_MPS_SL_ISA * sqrt(5 * (impactPressurePa / AIR_PRESSURE_PA_SL_ISA + 1).pow(2f / 7) - 5)) * (if (tasKt < 0) -1 else 1)
}

/**
 * Calculates the TAS from the IAS (more correctly the CAS, but we'll assume corrections between IAS
 * and CAS are negligible) at an altitude
 * @param altitudeFt the altitude, in feet, of the aircraft
 * @param iasKt the IAS, in knots, of the aircraft
 * @return the TAS, in knots, that the aircraft flies at with an IAS of [iasKt] at [altitudeFt]
 */
fun calculateTASFromIAS(altitudeFt: Float, iasKt: Float): Float {
    val pressurePa = calculatePressureAtAlt(altitudeFt)
    val iasMps = ktToMps(iasKt)
    val tempK = calculateTempAtAlt(altitudeFt)
    // Splitting up expressions to save my sanity
    val expr1 = (1 + iasMps * iasMps / 5 / SOUND_SPEED_MPS_SL_ISA / SOUND_SPEED_MPS_SL_ISA).pow(3.5f) - 1
    val expr2 = (1 + AIR_PRESSURE_PA_SL_ISA / pressurePa * expr1).pow(2f / 7) - 1
    return mpsToKt(sqrt(expr2 * 5 * AIR_SPECIFIC_HEATS_RATIO * AIR_GAS_CONSTANT_JPKGPK * tempK)) * (if (iasKt < 0) -1 else 1)
}

/**
 * Calculates the IAS from the mach number at an altitude
 * @param altitudeFt the altitude, in feet, of the aircraft
 * @param mach the mach number of the aircraft
 * @return the IAS, in knots, that results in a TAS equal to the [mach] number times the speed of sound at [altitudeFt]
 */
fun calculateIASFromMach(altitudeFt: Float, mach: Float): Float {
    return calculateIASFromTAS(altitudeFt, mach * calculateSpeedOfSoundAtAlt(altitudeFt))
}

/**
 * Calculates the maximum achievable acceleration, in metres per second^2, of an aircraft given its performance data,
 * altitude, TAS, minimum vertical speed if needed, and whether it is on approach or expediting
 * @param aircraftPerfData the aircraft performance data
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param vertSpdFpm the minimum vertical speed, in feet per minute, the aircraft is required to maintain during climb
 * @param onApproach whether the aircraft is on approach or expediting
 * @param takingOff whether the aircraft is in takeoff or landing roll
 * @param takeoffGoAround whether the aircraft is still in the takeoff climb or go around phase
 * @return the maximum acceleration, in metres per second^2, that is achievable by the aircraft
 */
fun calculateMaxAcceleration(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, vertSpdFpm: Float,
                             onApproach: Boolean, takingOff: Boolean, takeoffGoAround: Boolean): Float {
    val thrust = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt)
    val drag = if (onApproach) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    return calculateAcceleration(thrust, drag, aircraftPerfData.massKg) + (if (takingOff) 0f else calculateAccelerationDueToVertSpd(vertSpdFpm, tasKt))
}

/**
 * Calculates the minimum achievable acceleration (i.e. maximum deceleration), in metres per second^2, of an aircraft given
 * its performance data, altitude, TAS, minimum vertical speed if needed, and whether it is on approach or expediting
 * @param aircraftPerfData the aircraft performance data
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param vertSpdFpm the maximum vertical speed, in feet per minute, the aircraft is required to maintain during descent
 * @param approachExpedite whether the aircraft is on approach or expediting
 * @param takingOff whether the aircraft is in takeoff or landing roll
 * @param takeoffGoAround whether the aircraft is still in the takeoff climb or go around phase
 * @return the minimum acceleration, in metres per second^2, that is achievable by the aircraft
 */
fun calculateMinAcceleration(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, vertSpdFpm: Float,
                             approachExpedite: Boolean, takingOff: Boolean, takeoffGoAround: Boolean): Float {
    val thrust = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt) * 0.05f // Assume idle power/thrust is 5% of max thrust
    val drag = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    return calculateAcceleration(thrust, drag, aircraftPerfData.massKg) + (if (takingOff) 0f else calculateAccelerationDueToVertSpd(vertSpdFpm, tasKt))
}

/**
 * Calculates the acceleration of an aircraft given the thrust, drag and mass of the aircraft
 * @param thrustN the total thrust, in newtons, produced by the engines
 * @param dragN the total drag, in newtons, experienced by the aircraft
 * @param massKg the mass, in kilograms, of the aircraft
 * @return the acceleration, in metres per second^2, that will be experienced by the aircraft
 */
fun calculateAcceleration(thrustN: Float, dragN: Float, massKg: Int): Float {
    return (thrustN - dragN) / massKg
}

/**
 * Calculates the acceleration due to the vertical speed of the aircraft (GPE -> KE)
 * @param vertSpdFpm the vertical speed, in feet per minute, of the aircraft
 * @param tasKt the TAS, in knots, of the aircraft
 */
fun calculateAccelerationDueToVertSpd(vertSpdFpm: Float, tasKt: Float): Float {
    // Clamp between -100 and 100 m/s^2 to prevent insanely large values at low TAS
    return MathUtils.clamp(- GRAVITY_ACCELERATION_MPS2 * vertSpdFpm / ktToFpm(tasKt), -100f, 100f)
}

/**
 * Calculates required acceleration of an aircraft to accelerate from an initial speed to a target speed, assuming
 * constant acceleration
 * @param initialSpdKt the initial speed, in knots
 * @param targetSpdKt the target speed to achieve, in knots
 * @param distanceM the distance, in metres, the aircraft has to accelerate from [initialSpdKt] to [targetSpdKt]
 * @return the required acceleration, in metres per second^2
 */
fun calculateRequiredAcceleration(initialSpdKt: Short, targetSpdKt: Short, distanceM: Float): Float {
    val targetMps = ktToMps(targetSpdKt.toInt())
    val initialMps = ktToMps(initialSpdKt.toInt())
    return (targetMps * targetMps - initialMps * initialMps) / 2 / distanceM
}

/**
 * Calculates the maximum achievable vertical speed (i.e. maximum climb rate), in feet per minute, given the
 * [aircraftPerfData], [altitudeFt], [tasKt] and [accMps2] of the plane and whether it is on approach or expediting ([onApproach])
 * @param aircraftPerfData the aircraft performance data of the aircraft
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param accMps2 the acceleration, in metres per second^2, the aircraft is experiencing
 * @param onApproach whether the aircraft is on approach
 * @param takingOff whether the aircraft is in takeoff or landing roll
 * @param takeoffGoAround whether the aircraft is still in the takeoff climb or go around phase
 * @return the maximum vertical speed, in feet per minute, that is achievable by the aircraft
 */
fun calculateMaxVerticalSpd(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float,
                            accMps2: Float, onApproach: Boolean, takingOff: Boolean, takeoffGoAround: Boolean): Float {
    val thrustN = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt)
    val dragN = calculateMinDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    val maxVertSpd = calculateVerticalSpd(thrustN - dragN, tasKt, accMps2, aircraftPerfData.massKg)
    // If aircraft is on approach, taking off or in takeoff climb, it should climb at 1000fpm minimum
    if (onApproach || takingOff || takeoffGoAround) return max(maxVertSpd, 1000f)
    return maxVertSpd
}

/**
 * Calculates the minimum achievable vertical speed (i.e. maximum descent rate), in feet per minute, given the
 * [aircraftPerfData], [altitudeFt], [tasKt] and [accMps2] of the plane and whether it is on approach or expediting ([approachExpedite])
 * @param aircraftPerfData the aircraft performance data of the aircraft
 * @param altitudeFt the altitude, in feet, the aircraft is flying at
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param accMps2 the acceleration, in metres per second^2, the aircraft is experiencing
 * @param approachExpedite whether the aircraft is on approach or expediting
 * @param takingOff whether the aircraft is in takeoff or landing roll
 * @param takeoffGoAround whether the aircraft is still in the takeoff climb or go around phase
 * @return the minimum vertical speed, i.e. maximum descent vertical speed, in feet per minute, that is achievable by the aircraft
 */
fun calculateMinVerticalSpd(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float,
                            accMps2: Float, approachExpedite: Boolean, takingOff: Boolean, takeoffGoAround: Boolean): Float {
    val thrustN = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt) * 0.05f
    val dragN = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt, takingOff, takeoffGoAround)
    return calculateVerticalSpd(thrustN - dragN, tasKt, accMps2, aircraftPerfData.massKg)
}

/**
 * Calculates the achievable vertical speed, in feet per minute, given the net force, TAS, acceleration and mass of the plane
 * @param netForceN the net force, in newtons, acting on the aircraft
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @param accMps2 the acceleration, in metres per second^2, the aircraft is experiencing
 * @param massKg the mass, in kilograms, of the aircraft
 * @return the vertical speed, in feet per minute, that is achieved by the aircraft
 */
fun calculateVerticalSpd(netForceN: Float, tasKt: Float, accMps2: Float, massKg: Int): Float {
    return mpsToFpm((netForceN - massKg * accMps2) * ktToMps(tasKt) / massKg / GRAVITY_ACCELERATION_MPS2)
}

/**
 * Calculate the maximum attainable flight level altitude for the input aircraft performance data
 * @param aircraftPerfData the aircraft performance data
 * @return the maximum attainable altitude, in feet rounded down to the closest flight level, that the aircraft can sustain
 * at least 1000 (500 for turboprops/propellers) feet per minute of climb
 */
fun calculateMaxAlt(aircraftPerfData: AircraftTypeData.AircraftPerfData): Int {
    val crossOverAlt = calculateCrossoverAltitude(aircraftPerfData.tripIas, aircraftPerfData.tripMach)
    val minClimbRateAtCruiseAlt = if (aircraftPerfData.propPowerWSLISA == null) 1000 else 500
    for (i in aircraftPerfData.serviceCeiling / 1000 downTo 8) {
        val alt = i * 1000f
        val iasToUse = if (alt > crossOverAlt) calculateIASFromMach(alt, aircraftPerfData.tripMach) else aircraftPerfData.tripIas.toFloat()
        if (calculateMaxVerticalSpd(aircraftPerfData, alt, calculateTASFromIAS(alt, iasToUse), 0f,
                onApproach = false, takingOff = false, takeoffGoAround = false) > minClimbRateAtCruiseAlt)
            return alt.roundToInt()
    }
    return 8000
}

/**
 * Calculates the crossover altitude (the altitude where the IAS results in a true airspeed equal to that of the mach number)
 * @param iasKt the IAS to calculate the crossover altitude for
 * @param mach the mach number to calculate the crossover altitude for
 * @return the crossover altitude, in feet
 */
fun calculateCrossoverAltitude(iasKt: Short, mach: Float): Float {
    val iasMps = ktToMps(iasKt.toInt())
    val impactPressurePa = ((iasMps * iasMps / SOUND_SPEED_MPS_SL_ISA / SOUND_SPEED_MPS_SL_ISA / 5 + 1).pow(3.5f) - 1) * AIR_PRESSURE_PA_SL_ISA
    val pressureAtAlt = impactPressurePa / ((mach * mach / 5 + 1).pow(3.5f) - 1)
    return calculateAltAtPressure(pressureAtAlt)
}

/**
 * Calculates the track that the plane needs to fly as well as its ground speed (accounted for [wind] if any)
 * @param x1 the x coordinate of the present position
 * @param y1 the y coordinate of the present position
 * @param x2 the x coordinate of the target position
 * @param y2 the y coordinate of the target position
 * @param speedKts the true airspeed of the aircraft
 * @param dir the [Direction] component of the aircraft
 * @return a [Pair] of floats, the first being the required target track the aircraft should fly in order to reach
 * the target position, the second being the resulting ground speed of the aircraft if this track is followed
 */
fun getPointTargetTrackAndGS(x1: Float, y1: Float, x2: Float, y2: Float, speedKts: Float, dir: Direction, wind: AffectedByWind?): Pair<Float, Float> {
    return getPointTargetTrackAndGS(getRequiredTrack(x1, y1, x2, y2).toDouble(), speedKts, dir, wind)
}

/**
 * Calculates the track that the plane needs to fly as well as its ground speed (accounted for [wind] if any)
 * @param targetTrack the track the aircraft wants to fly (taking into account any wind)
 * @param speedKts the true airspeed of the aircraft
 * @param dir the [Direction] component of the aircraft
 * @return a [Pair] of floats, the first being the required target track the aircraft should fly in order to reach
 * the target position, the second being the resulting ground speed of the aircraft if this track is followed
 */
fun getPointTargetTrackAndGS(targetTrack: Double, speedKts: Float, dir: Direction, wind: AffectedByWind?): Pair<Float, Float> {
    var groundSpeed = speedKts
    var aircraftTrack = targetTrack
    if (wind != null) {
        // Calculate angle difference required due to wind component
        val angle = 180.0 - convertWorldAndRenderDeg(wind.windVectorPxps.angleDeg()) + convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg())
        val windSpdKts = pxpsToKt(wind.windVectorPxps.len())
        groundSpeed = sqrt(speedKts.pow(2.0f) + windSpdKts.pow(2.0f) - 2 * speedKts * windSpdKts * cos(Math.toRadians(angle))).toFloat()
        if (groundSpeed.isNaN() || groundSpeed == 0f) return Pair(aircraftTrack.toFloat(), 0f)
        val angleOffset = asin(windSpdKts * sin(Math.toRadians(angle)) / groundSpeed) * MathUtils.radiansToDegrees
        aircraftTrack -= angleOffset
    }
    return Pair(aircraftTrack.toFloat(), groundSpeed)
}

/**
 * Calculates the distance required for an entity with an initial speed to accelerate to a target speed, assuming a constant
 * acceleration value
 * @param initSpeedKt the initial speed, in knots
 * @param finalSpeedKt the target speed, in knots
 * @param acc the acceleration, in metres per second^2
 * @return the acceleration distance required, in metres
 */
fun calculateAccelerationDistanceRequired(initSpeedKt: Float, finalSpeedKt: Float, acc: Float): Float {
    val initSpdMps = ktToMps(initSpeedKt)
    val finalSpdMps = ktToMps(finalSpeedKt)
    return (finalSpdMps * finalSpdMps - initSpdMps * initSpdMps) / 2f / acc
}

/**
 * Calculates the descent gradient that is achievable with the net force acting against the aircraft - this uses a simple
 * energy approach that assumes the power loss due to the net force (drag - thrust) translates fully into the aircraft
 * descending hence losing potential energy instead of kinetic energy
 *
 * Assumes the slight reduction in true airspeed (due to loss in altitude) is negligible
 * @param netForceN the net force acting against the aircraft
 * @param massKg mass of the aircraft
 * @return a float denoting the gradient of descent (rise / run)
 */
fun calculateDescentGradient(netForceN: Float, massKg: Int): Float {
    return netForceN / massKg / GRAVITY_ACCELERATION_MPS2
}

/**
 * Calculates the spawn coordinates, track for an arrival aircraft route, just outside the primary sector's borders
 * @param route the route of the arrival aircraft
 * @param primarySector the polygon encompassing primary sector
 * @return a Triple of floats, the first being the x coordinate, the second being the y coordinate of the spawn position
 * and the third being the track of the opposite direction of the track the aircraft should spawn facing
 */
fun calculateArrivalSpawnPoint(route: Route, primarySector: Polygon): Triple<Float, Float, Float> {
    val originX: Float
    val originY: Float
    val endX: Float
    val endY: Float
    val oppSpawnTrack: Float
    val firstWptLegIndex = getFirstWaypointLegInSector(primarySector, route)
    val firstWptLeg = firstWptLegIndex?.let { route[it] as? Route.WaypointLeg }
    if (firstWptLeg != null) {
        // Waypoint inside sector found
        val pos = getServerOrClientWaypointMap()?.get(firstWptLeg.wptId)?.entity?.get(Position.mapper) ?: Position()
        originX = pos.x
        originY = pos.y
        if (firstWptLegIndex == 0) {
            var nextWptLeg: Route.WaypointLeg? = null
            route.apply {
                for (i in 1 until size) get(i).let { nextLeg ->
                        if (nextLeg is Route.WaypointLeg) {
                            nextWptLeg = nextLeg
                            return@apply
                        } else if (nextLeg !is Route.HoldLeg) return@apply // If a non-hold waypoint is reached, exit the loop
                    }
            }
            if (nextWptLeg != null) {
                // If this is the first leg, but subsequent leg is waypoint leg, use the same track as that from first to second leg
                val destPos = (route[1] as? Route.WaypointLeg)?.let {
                    getServerOrClientWaypointMap()?.get(it.wptId)?.entity?.get(Position.mapper)
                } ?: Position()
                oppSpawnTrack = getRequiredTrack(originX, originY, destPos.x, destPos.y)
                val trackToUse = Math.toRadians(convertWorldAndRenderDeg(oppSpawnTrack).toDouble())
                endX = (nmToPx(75) * cos(trackToUse)).toFloat() + pos.x
                endY = (nmToPx(75) * sin(trackToUse)).toFloat() + pos.y
            } else {
                // If this is the first leg with no subsequent waypoint leg, choose a random track towards the first leg
                oppSpawnTrack = MathUtils.random(1, 360).toFloat()
                val randomTrackConvertedRadians = Math.toRadians(convertWorldAndRenderDeg(oppSpawnTrack).toDouble())
                endX = (nmToPx(75) * cos(randomTrackConvertedRadians)).toFloat() + pos.x
                endY = (nmToPx(75) * sin(randomTrackConvertedRadians)).toFloat() + pos.y
            }
        } else {
            // Use the leg prior to this waypoint
            var prevWptLeg: Route.WaypointLeg? = null
            var prevVectorLeg: Route.VectorLeg? = null
            route.apply {
                for (i in firstWptLegIndex - 1 downTo 0) get(i).let { prevLeg ->
                    when (prevLeg) {
                        is Route.WaypointLeg -> {
                            prevWptLeg = prevLeg
                            return@apply
                        }
                        is Route.VectorLeg -> {
                            prevVectorLeg = prevLeg
                            return@apply
                        }
                        !is Route.HoldLeg -> return@apply // If a non-hold waypoint is reached, exit the loop
                        else -> return@let
                    }
                }
            }
            val finalPrevWptLeg = prevWptLeg
            val finalPrevVectorLeg = prevVectorLeg
            if (finalPrevWptLeg != null) {
                // If a previous waypoint exists
                val prevWptPos = getServerOrClientWaypointMap()?.get(finalPrevWptLeg.wptId)?.entity?.get(Position.mapper) ?: Position()
                endX = prevWptPos.x
                endY = prevWptPos.y
                oppSpawnTrack = getRequiredTrack(originX, originY, endX, endY)
            } else if (finalPrevVectorLeg != null) {
                // If a previous vector leg exists
                oppSpawnTrack = finalPrevVectorLeg.heading.toFloat() + 180 - MAG_HDG_DEV
                val trackToUse = Math.toRadians(convertWorldAndRenderDeg(oppSpawnTrack).toDouble())
                endX = (nmToPx(75) * cos(trackToUse)).toFloat() + pos.x
                endY = (nmToPx(75) * sin(trackToUse)).toFloat() + pos.y
            } else {
                // Shouldn't happen, but meh
                oppSpawnTrack = MathUtils.random(1, 360).toFloat()
                val randomTrackConvertedRadians = Math.toRadians(convertWorldAndRenderDeg(oppSpawnTrack).toDouble())
                endX = (nmToPx(75) * cos(randomTrackConvertedRadians)).toFloat()
                endY = (nmToPx(75) * sin(randomTrackConvertedRadians)).toFloat()
            }

            // Remove all previous legs and set the first leg to the first waypoint leg inside the sector
            route.removeRange(0, firstWptLegIndex - 1)
        }
    } else {
        // If no waypoint leg in sector, choose a random track towards the center (0, 0)
        originX = 0f
        originY = 0f
        oppSpawnTrack = MathUtils.random(1, 360).toFloat()
        val randomTrackConvertedRadians = Math.toRadians(convertWorldAndRenderDeg(oppSpawnTrack).toDouble())
        endX = (nmToPx(75) * cos(randomTrackConvertedRadians)).toFloat()
        endY = (nmToPx(75) * sin(randomTrackConvertedRadians)).toFloat()
    }

    return findClosestIntersectionBetweenSegmentAndPolygon(originX, originY, endX, endY, primarySector.vertices, nmToPx(ARRIVAL_SPAWN_EXTEND_DIST_NM))?.let {
        Triple(it.x, it.y, oppSpawnTrack)
    } ?: Triple(endX, endY, oppSpawnTrack)
}

/**
 * Calculates the spawn altitude of an arrival aircraft on the STAR
 * @param origRoute the original [SidStar.STAR] route that the aircraft is using
 * @param aircraftRoute the current aircraft route
 * @return the altitude, in feet, to spawn the aircraft at
 */
fun calculateArrivalSpawnAltitude(aircraft: Entity, airport: Entity, origRoute: Route, posX: Float, posY: Float, aircraftRoute: Route): Float {
    // Find the distance between the first point with an upper altitude restriction if any, else select airport elevation as this point
    val distPxToAlt: Float
    val firstMaxAlt: Float
    val minStarAlt: Int?

    val airportAlt = airport[Altitude.mapper]?.altitudeFt ?: 0f
    val distPxToAirport: Float
    if (aircraftRoute.size == 0) {
        // No legs, select airport position and use airport elevation as closest point with max altitude restriction
        firstMaxAlt = airportAlt
        val airportPos = airport[Position.mapper] ?: Position()
        distPxToAirport = calculateDistanceBetweenPoints(posX, posY, airportPos.x, airportPos.y)
        distPxToAlt = distPxToAirport
        minStarAlt = null
    } else {
        // Find the closest point with a max altitude restriction
        var cumulativeDistPx = 0f
        var prevPos = Position(posX, posY)
        aircraftRoute.also { legs ->
            // Try to find the first waypoint after or equal to the current direct that has a min, max alt restriction
            var starMaxAltFound = false
            var firstStarMaxAlt: Float? = null
            var distToFirstStarMaxAlt: Float? = null
            var starMinAltFound = false
            var firstStarMinAlt: Int? = null
            for (i in 0 until legs.size) { (legs[i] as? Route.WaypointLeg)?.apply {
                val thisWptPos = getServerOrClientWaypointMap()?.get(wptId)?.entity?.get(Position.mapper) ?: Position()
                cumulativeDistPx += calculateDistanceBetweenPoints(prevPos.x, prevPos.y, thisWptPos.x, thisWptPos.y)
                if (maxAltFt != null && !starMaxAltFound) {
                    firstStarMaxAlt = maxAltFt.toFloat()
                    distToFirstStarMaxAlt = cumulativeDistPx
                    starMaxAltFound = true
                }
                if (minAltFt != null && !starMinAltFound) {
                    firstStarMinAlt = minAltFt
                    starMinAltFound = true
                }
                prevPos = thisWptPos
            }}

            // End of loop reached - add distance between last position and airport
            val airportPos = airport[Position.mapper] ?: Position()
            cumulativeDistPx += calculateDistanceBetweenPoints(prevPos.x, prevPos.y, airportPos.x, airportPos.y)
            distPxToAirport = cumulativeDistPx

            val finalFirstStarMaxAlt = firstStarMaxAlt
            val finalDistToFirstStarMaxAlt = distToFirstStarMaxAlt
            if (finalFirstStarMaxAlt != null && finalDistToFirstStarMaxAlt != null) {
                // Found a max alt restriction in the STAR
                distPxToAlt = finalDistToFirstStarMaxAlt
                firstMaxAlt = finalFirstStarMaxAlt
            } else {
                // Could not find max alt in STAR, use cumulative distance to airport and airport elevation
                distPxToAlt = distPxToAirport
                firstMaxAlt = airportAlt
            }

            minStarAlt = firstStarMinAlt
        }
    }

    // 1. Calculate a direct descent from spawn position to airport with 12 - 16nm leeway
    val aircraftPerf = aircraft[AircraftInfo.mapper]?.aircraftPerf ?: run {
        FileLog.info("PhysicsTools", "No aircraft performance data found")
        AircraftTypeData.AircraftPerfData()
    }
    val airportToPositionTrackPx = max(distPxToAirport - nmToPx(MathUtils.random(12, 16)), 0f)
    val directNoRestrictionPathAlt = calculateTopAltitudeAtDistanceFromAlt(airport[Altitude.mapper]?.altitudeFt ?: 0f, airportToPositionTrackPx, aircraftPerf)

    // 2. Calculate a direct descent from spawn position to first max alt restriction with 4 - 6nm leeway
    val positionToMaxAltRestrictionTrackPx = max(distPxToAlt - nmToPx(MathUtils.random(4, 6)), 0f)
    val maxAltRestrictionPathAlt = calculateTopAltitudeAtDistanceFromAlt(firstMaxAlt, positionToMaxAltRestrictionTrackPx, aircraftPerf)

    // Take into account any STAR max altitude restrictions
    var prevMaxStarAlt: Int? = null
    (aircraftRoute[0] as? Route.WaypointLeg)?.apply {
        for (i in 0 until origRoute.size) (origRoute[i] as? Route.WaypointLeg)?.let { wpt ->
            if (compareLegEquality(this, wpt)) return@apply // Once the current direct is reached, stop searching for max altitude restrictions
            wpt.maxAltFt?.let { maxAlt ->
                val prevMaxAlt = prevMaxStarAlt
                if (prevMaxAlt == null || prevMaxAlt > maxAlt) prevMaxStarAlt = maxAlt
            }
        }
    }
    val finalPrevMaxStarAlt = prevMaxStarAlt

    // Spawn alt is the lower of the two, must be lower than any previous max alt restrictions, and must be at least the min star alt if any
    var spawnAlt = min(directNoRestrictionPathAlt, maxAltRestrictionPathAlt)
    if (finalPrevMaxStarAlt != null) spawnAlt = min(spawnAlt, finalPrevMaxStarAlt.toFloat())
    // Cap spawn altitude at maximum of 6000 ft above MAX_ALT, with a random variation of -1500 to 1500 ft
    spawnAlt = min(spawnAlt, MAX_ALT + 6000f + MathUtils.random(-1500, 1500))
    if (minStarAlt != null) spawnAlt = max(spawnAlt, minStarAlt.toFloat())
    return if (spawnAlt > TRANS_ALT && spawnAlt < TRANS_LVL * 100) TRANS_ALT.toFloat() else spawnAlt
}

private fun calculateTopAltitudeAtDistanceFromAlt(startAlt: Float, distPx: Float, aircraftPerf: AircraftTypeData.AircraftPerfData): Float {
    val effectiveDistPxToAlt = if (distPx < 0) 0f else distPx

    var currStepAlt = startAlt
    var currStepDist = 0f
    val stepSize = nmToPx(1)
    while (currStepDist < effectiveDistPxToAlt) {
        val effectiveStepSize = min(stepSize, effectiveDistPxToAlt - currStepDist)
        // Estimate the altitude on top of each altitude step
        val estTas = calculateTASFromIAS(currStepAlt, aircraftPerf.tripIas.toFloat())
        val estMinDrag = calculateMinDrag(aircraftPerf, currStepAlt, estTas, takingOff = false, takeoffGoAround = false)
        val estMinThrust = calculateMaxThrust(aircraftPerf, currStepAlt, estTas) * 0.05f
        val estGrad = calculateDescentGradient(estMinDrag - estMinThrust, aircraftPerf.massKg)
        val topOfStepAlt = estGrad * pxToFt(effectiveStepSize) + currStepAlt
        // Use the estimated top altitude step to calculate the gradient
        val actlTas = calculateTASFromIAS(topOfStepAlt, aircraftPerf.tripIas.toFloat())
        val actlMinDrag = calculateMinDrag(aircraftPerf, topOfStepAlt, actlTas, takingOff = false, takeoffGoAround = false)
        val actlMinThrust = calculateMaxThrust(aircraftPerf, topOfStepAlt, actlTas) * 0.05f
        val actlGrad = calculateDescentGradient(actlMinDrag - actlMinThrust, aircraftPerf.massKg)
        currStepAlt += actlGrad * pxToFt(effectiveStepSize)
        currStepDist += effectiveStepSize
    }
    val crossOverAlt = calculateCrossoverAltitude(aircraftPerf.tripIas, aircraftPerf.tripMach)
    val estTasMpsAtTop = ktToMps(calculateTASFromIAS(currStepAlt, if (currStepAlt > crossOverAlt) calculateIASFromMach(currStepAlt, aircraftPerf.tripMach)
    else aircraftPerf.tripIas.toFloat()))
    val estFinalAlt = currStepAlt - mToFt(estTasMpsAtTop * estTasMpsAtTop / 2 / GRAVITY_ACCELERATION_MPS2)
    val tasMpsAtTop = ktToMps(calculateTASFromIAS(estFinalAlt, if (estFinalAlt > crossOverAlt) calculateIASFromMach(estFinalAlt, aircraftPerf.tripMach)
    else aircraftPerf.tripIas.toFloat()))
    currStepAlt -= mToFt(tasMpsAtTop * tasMpsAtTop / 2 / GRAVITY_ACCELERATION_MPS2)
    return min(currStepAlt, aircraftPerf.maxAlt.toFloat())
}
