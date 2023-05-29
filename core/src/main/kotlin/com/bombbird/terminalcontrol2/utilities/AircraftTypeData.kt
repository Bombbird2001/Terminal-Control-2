package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.FlightType
import com.esotericsoftware.minlog.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.math.roundToInt

/** Helper object for aircraft performance data */
object AircraftTypeData {
    private val aircraftPerfMap = HashMap<String, AircraftPerfData>()

    /**
     * Adds aircraft performance data for the input ICAO type code; this instance will be used to generate new instances
     * with the randomised load factors and speed values
     * @param icaoType the ICAO type code of the aircraft
     * @param perf the [AircraftPerfData] object for the aircraft type
     */
    fun addAircraftPerf(icaoType: String, perf: AircraftPerfData) {
        aircraftPerfMap[icaoType] = perf
    }

    /**
     * Gets the aircraft performance data for the specified aircraft ICAO type; a new instance is created for the random values
     * @param icaoType the ICAO aircraft type
     * @param flightType the type of the flight
     * @return the new instance of [AircraftPerfData]
     */
    fun getAircraftPerf(icaoType: String, flightType: Byte): AircraftPerfData {
        return aircraftPerfMap[icaoType]?.newInstance(flightType) ?: run {
            Log.info("AircraftTypeData", "No performance data found for $icaoType, returning default data")
            return AircraftPerfData()
        }
    }

    /**
     * Aircraft performance data class
     *
     * [wakeCategory]: ICAO wake turbulence category: L, M, H, J
     *
     * [recat]: ICAO recat wake category: F, E, D, C, B, A
     *
     * [thrustNSLISA]: For turboprop/jet planes only: Max total thrust, in newtons, produced by the jet engines at sea level under ISA conditions
     *
     * [propPowerWSLISA]: For turboprop/propeller planes only: Max total power, in watts, produced by the propeller engines at sea level under ISA conditions
     *
     * [propArea]: For turboprop/propeller planes only: Total propeller blade area, in metres^2
     *
     * [minCd0TimesRefArea]: The lowest possible value of the product of the zero-lift drag coefficient and reference area in the
     * drag equation; will be used for most calculations
     *
     * [maxCdTimesRefArea]: The highest possible value of the product of the drag coefficient and reference area, in metres^2, in the
     * drag equation; used only when expediting descent or on approach
     *
     * [maxIas]: The maximum IAS that the aircraft can fly at (Vmo; below the crossover altitude)
     *
     * [maxMach]: The maximum mach number that the aircraft can fly at (Mmo; above the crossover altitude)
     *
     * [operatingEmptyWeightKg]: The operating empty weight of the aircraft
     *
     * [maxTakeoffWeightKg]: The maximum takeoff weight of the aircraft
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
     * [maxAlt]: The maximum altitude the climb can fly at given its performance data - the aircraft will fly at the highest
     * flight level below this altitude; this value will be calculated dynamically based on other performance data
     *
     * [massKg]: Mass of the plane
     *
     * @param flightType the type of the flight, used for load factor calculation purposes
     */
    @JsonClass(generateAdapter = true)
    class AircraftPerfData(val wakeCategory: Char = WAKE_HEAVY, val recat: Char = RECAT_B,
                           val thrustNSLISA: Int? = 1026000, val propPowerWSLISA: Int? = null, val propArea: Float? = null,
                           val minCd0TimesRefArea: Float = 6.552f, val maxCdTimesRefArea: Float = 41.496f,
                           val maxIas: Short = 340, val maxMach: Float = 0.89f,
                           @Json(ignore = true) private val typApp: Short = 149,
                           @Json(ignore = true) private val typVr: Short = 158,
                           @Json(ignore = true) private val operatingEmptyWeightKg: Int = 167829,
                           @Json(ignore = true) private val maxTakeoffWeightKg: Int = 351533,
                           flightType: Byte = FlightType.ARRIVAL) {

        companion object {
            const val WAKE_SUPER = 'J'
            const val WAKE_HEAVY = 'H'
            const val WAKE_MEDIUM = 'M'
            const val WAKE_LIGHT = 'L'
            const val RECAT_A = 'A'
            const val RECAT_B = 'B'
            const val RECAT_C = 'C'
            const val RECAT_D = 'D'
            const val RECAT_E = 'E'
            const val RECAT_F = 'F'
        }

        var appSpd: Short
        var vR: Short
        var climbOutSpeed: Short
        var tripIas: Short
        var tripMach: Float
        var maxAlt: Int
        var massKg: Int

        init {
            val loadFactor = MathUtils.random(0.1f, when (flightType) {
                FlightType.DEPARTURE -> 0.9f
                FlightType.EN_ROUTE -> 0.6f
                else -> 0.3f
            }) // Load factor between 10% and 90% (for departures), 60% (for en-route) or 30% (for arrivals)
            appSpd = (typApp * (1 + 0.19f * (loadFactor - 0.5f))).roundToInt().toShort()
            vR = (typVr * (1 + 0.19f * (loadFactor - 0.5f))).roundToInt().toShort()
            climbOutSpeed = (vR + MathUtils.random(5, 10)).toShort()
            tripIas = (maxIas * MathUtils.random(0.9f, 0.97f)).roundToInt().toShort()
            tripMach = maxMach * MathUtils.random(0.915f, 0.945f)
            massKg = (operatingEmptyWeightKg + (maxTakeoffWeightKg - operatingEmptyWeightKg) * loadFactor).roundToInt()
            maxAlt = calculateMaxAlt(this)
        }

        /**
         * Creates a new instance of the aircraft type data, where a new value of load factor will be generated and its
         * dependent values calculated
         * @param flightType the type of the flight to generate the parameters for
         * @return a new instance of [AircraftPerfData]
         */
        fun newInstance(flightType: Byte): AircraftPerfData {
            return AircraftPerfData(wakeCategory, recat,
                thrustNSLISA, propPowerWSLISA, propArea,
                minCd0TimesRefArea, maxCdTimesRefArea, maxIas, maxMach, typApp, typVr,
                operatingEmptyWeightKg, maxTakeoffWeightKg, flightType)
        }
    }
}