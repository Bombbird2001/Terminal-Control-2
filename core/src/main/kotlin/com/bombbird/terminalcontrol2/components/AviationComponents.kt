package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import ktx.ashley.Mapper

/** Component for tagging airport related information */
data class AirportInfo(var arptId: Byte = 0, var icaoCode: String = "", var name: String = "", var tfcRatio: Byte = 1): Component {
    // TODO Add other airport related components
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
data class RunwayInfo(var rwyId: Byte = 0, var rwyName: String = "", var lengthM: Short = 4000): Component {
    lateinit var airport: Airport
    // TODO Add other runway related components
    companion object: Mapper<RunwayInfo>()
}

/** Component for tagging airport METAR information */
data class MetarInfo(var realLifeIcao: String = "", var letterCode: Char? = null, var rawMetar: String? = null, var windHeadingDeg: Short = 360, var windSpeedKt: Short = 0, var windGustKt: Short = 0, var visibilityM: Short = 10000, var ceilingHundredFtAGL: Short? = null, var windshear: String = ""): Component {
    val windVectorPx = Vector2()
    companion object: Mapper<MetarInfo>()
}

/** Component for tagging sector related information
 * [sectorId] = -1 -> tower control
 *
 * [sectorId] = -2 -> ACC control
 * */
data class SectorInfo(var sectorId: Byte = 0, var controllerName: String = "ChangeYourNameLol", var frequency: String = "121.5", var sectorCallsign: String = "Approach"): Component {
    companion object: Mapper<SectorInfo>() {
        val TOWER = -1
        val CENTRE = -2
    }
}

/** Component for tagging MVA/Restricted area related information
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

/** Component for tagging sector control information */
data class Controllable(var sectorId: Byte = 0): Component {
    companion object: Mapper<Controllable>()
}

/** Component for tagging aircraft specific information
 *
 * Includes performance determining data - minimum approach speed, rotation speed, weight, others in [aircraftPerf]
 * */
data class AircraftInfo(var icaoCallsign: String = "SHIBA1", var icaoType: String = "B77W"): Component {
    val aircraftPerf = AircraftTypeData.getAircraftPerf(icaoType)
    var maxAcc: Float = 0f
    var minAcc: Float = 0f
    var maxVs: Float = 0f
    var minVs: Float = 0f
    companion object: Mapper<AircraftInfo>()
}

/** Component for tagging aircraft flight type
 *
 * [type] = 0 -> Arrival
 *
 * [type] = 1 -> Departure
 *
 * [type] = 2 -> En-route
 * */
data class FlightType(var type: Byte = 0): Component {
    companion object: Mapper<FlightType>() {
        val ARRIVAL: Byte = 0
        val DEPARTURE: Byte = 1
        val EN_ROUTE: Byte = 2
    }
}

/** Component for tagging basic approach information */
data class ApproachInfo(var approachName: String = "", var rwyId: Byte = 0, var towerName: String = "", var frequency: String = ""): Component {
    companion object: Mapper<ApproachInfo>()
}

/** Component for tagging localiser information */
data class Localiser(var maxDistNm: Byte = 0): Component {
    companion object: Mapper<Localiser>()
}

/** Component for tagging glide slope information */
data class GlideSlope(var glideAngle: Float = 0f, var offsetNm: Float = 0f, var maxInterceptAlt: Short = 0): Component {
    companion object: Mapper<GlideSlope>()
}

/** Component for tagging step down approach information */
class StepDown(var altAtDist: Array<Pair<Float, Short>> = arrayOf()): Component {
    companion object: Mapper<StepDown>()
}

/** Component for tagging approach minimums information */
data class Minimums(var baroAltFt: Short = 0, var rvrM: Short = 0): Component {
    companion object: Mapper<Minimums>()
}

/** Component for tagging offset approach information */
data class Offset(var lineUpDistNm: Float = 0f): Component {
    companion object: Mapper<Offset>()
}

/** Component for tagging visual approach */
class Visual: Component {
    companion object: Mapper<Visual>()
}

class Emergency: Component {

}

class Conflictable: Component {

}
