package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/** Component for tagging airport related information */
@JsonClass(generateAdapter = true)
data class AirportInfo(var arptId: Byte = 0, var icaoCode: String = "", var name: String = "", var tfcRatio: Byte = 1): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.AIRPORT_INFO

    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
data class RunwayInfo(var rwyId: Byte = 0, var rwyName: String = "", var lengthM: Short = 4000,
                      var displacedThresholdM: Short = 0, var intersectionTakeoffM: Short = 0,
                      var tower: String = "", var freq: String = ""): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RUNWAY_INFO

    lateinit var airport: Airport
    companion object: Mapper<RunwayInfo>()
}

/** Component for tagging airport METAR information */
data class MetarInfo(var realLifeIcao: String = "", var letterCode: Char? = null, var rawMetar: String? = null,
                     var windHeadingDeg: Short = 360, var windSpeedKt: Short = 0, var windGustKt: Short = 0,
                     var visibilityM: Short = 10000, var ceilingHundredFtAGL: Short? = null, var windshear: String = ""): Component {
    val windVectorPx = Vector2()
    companion object: Mapper<MetarInfo>()
}

/** Component for tagging runway tailwind, crosswind component data */
data class RunwayWindComponents(var tailwindKt: Float = 0f, var crosswindKt: Float = 0f): Component {
    companion object: Mapper<RunwayWindComponents>()
}

/** Component for tagging random weather generation data */
data class RandomMetarInfo(var windDirDist: CumulativeDistribution<Short> = CumulativeDistribution(),
                           var windSpdDist: CumulativeDistribution<Short> = CumulativeDistribution(),
                           var visibilityDist: CumulativeDistribution<Short> = CumulativeDistribution(),
                           var ceilingDist: CumulativeDistribution<Short> = CumulativeDistribution(),
                           var windshearLogCoefficients: Pair<Float, Float>? = null): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RANDOM_METAR_INFO

    companion object: Mapper<RandomMetarInfo>()
}

/**
 * Component for tagging sector related information
 *
 * [sectorId] = -1 -> tower control
 *
 * [sectorId] = -2 -> ACC control
 * */
data class SectorInfo(var sectorId: Byte = 0, var frequency: String = "121.5",
                      var arrivalCallsign: String = "Approach", var departureCallsign: String = "Departure"): Component {
    companion object: Mapper<SectorInfo>() {
        const val TOWER: Byte = -1
        const val CENTRE: Byte = -2
    }
}

/**
 * Component for tagging MVA/Restricted area related information
 *
 * Additional tagging of [GPolygon] or [GCircle] is required for boundary information
 * */
data class MinAltSectorInfo(var minAltFt: Int? = null, var restricted: Boolean = false): Component {
    companion object: Mapper<MinAltSectorInfo>()
}

/** Component for tagging waypoint related information */
@JsonClass(generateAdapter = true)
data class WaypointInfo(var wptId: Short = 0, var wptName: String = "-----"): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.WAYPOINT_INFO

    companion object: Mapper<WaypointInfo>()
}

/** Component for tagging a published hold procedure */
data class PublishedHoldInfo(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null,
                             var maxSpdKtLower: Short = 230, var maxSpdKtHigher: Short = 240, var inboundHdgDeg: Short = 360, var legDistNm: Byte = 5,
                             var turnDir: Byte = CommandTarget.TURN_RIGHT): Component {
     companion object: Mapper<PublishedHoldInfo>()
}

/**
 * Component for tagging aircraft specific information
 *
 * Includes performance determining data - minimum approach speed, rotation speed, weight, others in [aircraftPerf]
 * */
@JsonClass(generateAdapter = true)
data class AircraftInfo(var icaoCallsign: String = "SHIBA1", var icaoType: String = "SHIB"): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.AIRCRAFT_INFO

    var aircraftPerf = AircraftTypeData.AircraftPerfData()
    var maxAcc: Float = 0f
    var minAcc: Float = 0f
    var maxVs: Float = 0f
    var minVs: Float = 0f
    companion object: Mapper<AircraftInfo>()
}

/** Component for tagging the arrival airport for an aircraft */
@JsonClass(generateAdapter = true)
data class ArrivalAirport(var arptId: Byte): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ARRIVAL_AIRPORT

    companion object: Mapper<ArrivalAirport>()
}

/** Component for tagging the departure airport (and runway after cleared for takeoff) for an aircraft */
@JsonClass(generateAdapter = true)
data class DepartureAirport(var arptId: Byte, var rwyId: Byte): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DEPARTURE_AIRPORT

    companion object: Mapper<DepartureAirport>()
}

/** Component for tagging aircraft trail positions */
data class TrailInfo(val positions: Queue<Position> = Queue()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.TRAIL_INFO

    companion object: Mapper<TrailInfo>()
}
