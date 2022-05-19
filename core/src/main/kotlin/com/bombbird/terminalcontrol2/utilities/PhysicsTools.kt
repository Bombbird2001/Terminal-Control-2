package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.AffectedByWind
import com.bombbird.terminalcontrol2.components.Direction
import kotlin.math.*

/** PHYSICSSSSSSSSSSSSSSSSSSSSSSS
 *
 * Note that for all calculations that involve atmospheric conditions, the atmosphere will be assumed to follow ISA
 * conditions; this is to prevent me from going crazy
 * */

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
    return if (altitudeFt <= 36090) TEMPERATURE_K_SL_ISA + LAPSE_RATE_KPM_ISA * ftToM(altitudeFt) else 216.65f
}

/** Calculates the pressure, in pascals, at [altitudeFt] above sea level at ISA conditions */
fun calculatePressureAtAlt(altitudeFt: Float): Float {
    return if (altitudeFt < 36090) AIR_PRESSURE_PA_SL_ISA * (calculateTempAtAlt(altitudeFt) / TEMPERATURE_K_SL_ISA).pow(-GRAVITY_ACCELERATION_MPS2 / LAPSE_RATE_KPM_ISA / AIR_GAS_CONSTANT_JPKGPK)
    else calculatePressureAtAlt(36089.99f) * exp(-GRAVITY_ACCELERATION_MPS2 * ftToM(altitudeFt - 36089.99f) / AIR_GAS_CONSTANT_JPKGPK / calculateTempAtAlt(36089.99f))
}

/**
 * Calculates the altitude above sea level at which air is of a certain pressure at ISA conditions
 * @param pressurePa the air pressure at the altitude to be calculated
 * @return the altitude, in feet, at which air pressure is [pressurePa]
 * */
fun calculateAltAtPressure(pressurePa: Float): Float {
    return if (pressurePa > 22630.9) mToFt(TEMPERATURE_K_SL_ISA / LAPSE_RATE_KPM_ISA * ((pressurePa / AIR_PRESSURE_PA_SL_ISA).pow(-AIR_GAS_CONSTANT_JPKGPK * LAPSE_RATE_KPM_ISA / GRAVITY_ACCELERATION_MPS2) - 1))
    else calculateAltAtPressure(22630.91f) + mToFt(AIR_GAS_CONSTANT_JPKGPK * calculateTempAtAlt(36089.99f) * ln(pressurePa / 22630.91f) / -GRAVITY_ACCELERATION_MPS2)
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
    val tasMps = ktToMps(tasKt)
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

/**
 * Calculates the drag, in newtons, with the specified drag coefficient * reference area, air density and TAS
 * @param cdTimesRefArea the product of the drag coefficient and the reference area of the aircraft, in metres^2
 * @param densityKgpm3 the density, in kilograms per metre^3, of the air the aircraft is flying through
 * @param tasKt the TAS, in knots, the aircraft is flying at
 * @return the drag, in newtons, the aircraft experiences when flying through air of density [densityKgpm3] at TAS of [tasKt]
 * */
fun calculateDrag(cdTimesRefArea: Float, densityKgpm3: Float, tasKt: Float): Float {
    val tasMps = ktToMps(tasKt)
    return cdTimesRefArea * densityKgpm3 * tasMps * tasMps / 2
}

/**
 * Calculates the speed of sound at an altitude above sea level at ISA conditions
 * @param altitudeFt the altitude, in feet, above sea level
 * @return the speed of sound, in knots, at [altitudeFt]
 * */
fun calculateSpeedOfSoundAtAlt(altitudeFt: Float): Float {
    return mpsToKt(sqrt(AIR_SPECIFIC_HEATS_RATIO * AIR_GAS_CONSTANT_JPKGPK * calculateTempAtAlt(altitudeFt)))
}

/**
 * Calculates the IAS (more correctly the CAS, but we'll assume corrections between IAS and CAS are negligible)
 * from the TAS at an altitude
 * @param altitudeFt the altitude, in feet, of the aircraft
 * @param tasKt the TAS, in knots, of the aircraft
 * @return the IAS, in knots, that results in the aircraft flying with an TAS of [tasKt] at [altitudeFt]
 * */
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
 * */
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
 * */
fun calculateIASFromMach(altitudeFt: Float, mach: Float): Float {
    return calculateIASFromTAS(altitudeFt, mach * calculateSpeedOfSoundAtAlt(altitudeFt))
}

/** Calculates the maximum achievable acceleration, in metres per second^2, of an aircraft given its [aircraftPerfData], [altitudeFt], [tasKt]
 * and whether it is on approach or expediting ([approachExpedite])
 * */
fun calculateMaxAcceleration(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, approachExpedite: Boolean): Float {
    val thrust = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt)
    val drag = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt) else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt)
    return calculateAcceleration(thrust, drag, aircraftPerfData.massKg)
}

/** Calculates the minimum achievable acceleration (i.e. maximum deceleration), in metres per second^2, of an aircraft given its [aircraftPerfData], [altitudeFt], [tasKt]
 * and whether it is on approach or expediting ([approachExpedite])
 * */
fun calculateMinAcceleration(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, approachExpedite: Boolean): Float {
    val thrust = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt) * 0.05f // Assume idle power/thrust is 5% of max thrust
    val drag = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt) else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt)
    return calculateAcceleration(thrust, drag, aircraftPerfData.massKg)
}

/** Calculates the acceleration, in metres per second^2, of an aircraft given the [thrustN], [dragN] and [massKg] */
fun calculateAcceleration(thrustN: Float, dragN: Float, massKg: Int): Float {
    return (thrustN - dragN) / massKg
}

/** Calculates required acceleration, in metres per second^2, of an aircraft to accelerate from [initialSpdKt]
 * to [targetSpdKt] within [distanceM], assuming constant acceleration
 * */
fun calculateRequiredAcceleration(initialSpdKt: Short, targetSpdKt: Short, distanceM: Float): Float {
    val targetMps = ktToMps(targetSpdKt.toInt())
    val initialMps = ktToMps(initialSpdKt.toInt())
    return (targetMps * targetMps - initialMps * initialMps) / 2 / distanceM
}

/** Calculates the maximum achievable vertical speed, in feet per minute, given the [aircraftPerfData], [altitudeFt], [tasKt] and [accMps2] of the plane */
fun calculateMaxVerticalSpd(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, accMps2: Float, approachExpedite: Boolean): Float {
    val thrustN = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt)
    val dragN = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt) else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt)
    return calculateVerticalSpd(thrustN - dragN, tasKt, accMps2, aircraftPerfData.massKg)
}

/** Calculates the minimum achievable vertical speed (i.e. maximum descent rate), in feet per minute, given the
 * [aircraftPerfData], [altitudeFt], [tasKt] and [accMps2] of the plane and whether it is on approach or expediting ([approachExpedite]) */
fun calculateMinVerticalSpd(aircraftPerfData: AircraftTypeData.AircraftPerfData, altitudeFt: Float, tasKt: Float, accMps2: Float, approachExpedite: Boolean): Float {
    val thrustN = calculateMaxThrust(aircraftPerfData, altitudeFt, tasKt) * 0.05f
    val dragN = if (approachExpedite) calculateMaxDrag(aircraftPerfData, altitudeFt, tasKt) else calculateMinDrag(aircraftPerfData, altitudeFt, tasKt)
    return calculateVerticalSpd(thrustN - dragN, tasKt, accMps2, aircraftPerfData.massKg)
}

/** Calculates the achievable vertical speed, in feet per minute, given the [netForceN], [tasKt], [accMps2] and [massKg] of the plane */
fun calculateVerticalSpd(netForceN: Float, tasKt: Float, accMps2: Float, massKg: Int): Float {
    return mpsToFpm((netForceN - massKg * accMps2) * ktToMps(tasKt) / massKg / GRAVITY_ACCELERATION_MPS2)
}

/**
 * Calculates the crossover altitude (the altitude where the IAS results in a true airspeed equal to that of the mach number)
 * @param iasKt the IAS to calculate the crossover altitude for
 * @param mach the mach number to calculate the crossover altitude for
 * @return the crossover altitude
 * */
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
 * */
fun getPointTargetTrackAndGS(x1: Float, y1: Float, x2: Float, y2: Float, speedKts: Float, dir: Direction, wind: AffectedByWind?): Pair<Float, Float> {
    var targetTrack = getRequiredTrack(x1, y1, x2, y2).toDouble()
    var groundSpeed = speedKts
    if (wind != null) {
        // Calculate angle difference required due to wind component
        val angle = 180.0 - convertWorldAndRenderDeg(wind.windVectorPxps.angleDeg()) + convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg())
        val windSpdKts = pxpsToKt(wind.windVectorPxps.len())
        groundSpeed = sqrt(speedKts.pow(2.0f) + windSpdKts.pow(2.0f) - 2 * speedKts * windSpdKts * cos(Math.toRadians(angle))).toFloat()
        val angleOffset = asin(windSpdKts * sin(Math.toRadians(angle)) / groundSpeed) * MathUtils.radiansToDegrees
        targetTrack -= angleOffset
    }
    return Pair(targetTrack.toFloat(), groundSpeed)
}

/**
 * Calculates the distance required for an entity with an initial speed to accelerate to a target speed, assuming a constant
 * acceleration value
 * @param initSpeedKt the initial speed, in knots
 * @param finalSpeedKt the target speed, in knots
 * @param acc the acceleration, in metres per second^2
 * @return the acceleration distance required, in metres
 * */
fun calculateAccelerationDistanceRequired(initSpeedKt: Float, finalSpeedKt: Float, acc: Float): Float {
    val initSpdMps = ktToMps(initSpeedKt)
    val finalSpdMps = ktToMps(finalSpeedKt)
    return (finalSpdMps * finalSpdMps - initSpdMps * initSpdMps) / 2f / acc
}
