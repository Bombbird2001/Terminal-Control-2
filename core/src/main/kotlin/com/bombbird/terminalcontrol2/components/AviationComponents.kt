package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.ApproachNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.DepartureNormalOperatingZone
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import ktx.ashley.Mapper
import ktx.ashley.get
import ktx.collections.GdxArray

/** Component for tagging airport related information */
data class AirportInfo(var arptId: Byte = 0, var icaoCode: String = "", var name: String = "", var tfcRatio: Byte = 1): Component {
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
data class RunwayInfo(var rwyId: Byte = 0, var rwyName: String = "", var lengthM: Short = 4000,
                      var displacedThresholdM: Short = 0, var intersectionTakeoffM: Short = 0,
                      var tower: String = "", var freq: String = ""): Component {
    lateinit var airport: Airport
    companion object: Mapper<RunwayInfo>()
}

/** Component for tagging runway that is active for landings */
class ActiveLanding: Component {
    companion object: Mapper<ActiveLanding>()
}

/** Component for tagging runway that is active for takeoffs */
class ActiveTakeoff: Component {
    companion object: Mapper<ActiveTakeoff>()
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
                           var windshearLogCoefficients: Pair<Float, Float>? = null): Component {
    companion object: Mapper<RandomMetarInfo>()
}

/**
 * Component for tagging sector related information
 *
 * [sectorId] = -1 -> tower control
 *
 * [sectorId] = -2 -> ACC control
 * */
data class SectorInfo(var sectorId: Byte = 0, var controllerName: String = "Shiba Inu", var frequency: String = "121.5", var sectorCallsign: String = "Approach"): Component {
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
data class WaypointInfo(var wptId: Short = 0, var wptName: String = "-----"): Component {
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
data class AircraftInfo(var icaoCallsign: String = "SHIBA1", var icaoType: String = "SHIB"): Component {
    var aircraftPerf = AircraftTypeData.AircraftPerfData()
    var maxAcc: Float = 0f
    var minAcc: Float = 0f
    var maxVs: Float = 0f
    var minVs: Float = 0f
    companion object: Mapper<AircraftInfo>()
}

/** Component for tagging the arrival airport for an aircraft */
data class ArrivalAirport(var arptId: Byte = 0): Component {
    companion object: Mapper<ArrivalAirport>()
}

/** Component for tagging a closed airport for arrivals */
class ArrivalClosed: Component {
    companion object: Mapper<ArrivalClosed>()
}

/** Component for tagging a closed airport for departures */
data class DepartureInfo(var closed: Boolean = false, var backlog: Int = -10): Component {
    companion object: Mapper<DepartureInfo>()
}

/** Component for tagging traffic distribution at an airport */
data class RandomAirlineData(val airlineDistribution: CumulativeDistribution<Triple<String, Boolean, GdxArray<String>>> = CumulativeDistribution()): Component {
    companion object: Mapper<RandomAirlineData>()
}

/** Component for tagging basic approach information */
data class ApproachInfo(var approachName: String = "", var airportId: Byte = 0, var rwyId: Byte = 0): Component {
    val rwyObj: Airport.Runway by lazy {
        (GAME.gameServer?.airports ?: GAME.gameClientScreen?.airports)?.get(airportId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(rwyId) ?:
        throw NullPointerException("No runway with ID $rwyId found in airport with ID $airportId")
    }

    companion object: Mapper<ApproachInfo>()
}

/** Component for tagging localizer information */
data class Localizer(var maxDistNm: Byte = 0): Component {
    companion object: Mapper<Localizer>()
}

/** Component for tagging the distance from the runway threshold to turn and line up (in an offset approach) */
data class LineUpDist(var lineUpDistNm: Float = 0f): Component {
    companion object: Mapper<LineUpDist>()
}

/** Component for tagging glide slope information */
data class GlideSlope(var glideAngle: Float = 0f, var offsetNm: Float = 0f, var maxInterceptAlt: Short = 0): Component {
    companion object: Mapper<GlideSlope>()
}

/** Component for tagging step down approach information */
class StepDown(var altAtDist: Array<Pair<Float, Short>> = arrayOf()): Component {
    companion object: Mapper<StepDown>()
}

/** Component for tagging circling approach information */
class Circling(var minBreakoutAlt: Int = 0, var maxBreakoutAlt: Int = 0, var breakoutDir: Byte = CommandTarget.TURN_LEFT): Component {
    companion object: Mapper<Circling>()
}

/** Component for tagging approach minimums information */
data class Minimums(var baroAltFt: Short = 0, var rvrM: Short = 0): Component {
    companion object: Mapper<Minimums>()
}

/**
 * Component for tagging visual approach (one will be created for every runway with their own extended centerline up to
 * 10nm and glide path of 3 degrees)
 * */
class Visual: Component {
    companion object: Mapper<Visual>()
}

/** Component for tagging the approach NOZ for a runway */
data class ApproachNOZ(var appNoz: ApproachNormalOperatingZone): Component {
    companion object: Mapper<ApproachNOZ>()
}

/** Component for tagging the departure NOZ for a runway */
data class DepartureNOZ(var depNoz: DepartureNormalOperatingZone): Component {
    companion object: Mapper<DepartureNOZ>()
}

/** Component for tagging the active runway configuration of an airport */
data class ActiveRunwayConfig(var configId: Byte = 0): Component {
    companion object: Mapper<ActiveRunwayConfig>()
}

/** Component for tagging a pending runway configuration change for an airport */
data class PendingRunwayConfig(var pendingId: Byte = 0, var timeRemaining: Float = 0f): Component {
    companion object: Mapper<PendingRunwayConfig>()
}

class Emergency: Component {

}

class Conflictable: Component {

}
