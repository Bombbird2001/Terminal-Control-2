package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import ktx.ashley.Mapper

/** Component for tagging airport related information */
data class AirportInfo(var arptId: Byte = 0, var icaoCode: String = "", var name: String = "", var tfcRatio: Byte = 1): Component {
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
data class RunwayInfo(var rwyId: Byte = 0, var rwyName: String = "", var lengthM: Short = 4000): Component {
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
data class MetarInfo(var realLifeIcao: String = "", var letterCode: Char? = null, var rawMetar: String? = null, var windHeadingDeg: Short = 360, var windSpeedKt: Short = 0, var windGustKt: Short = 0, var visibilityM: Short = 10000, var ceilingHundredFtAGL: Short? = null, var windshear: String = ""): Component {
    val windVectorPx = Vector2()
    companion object: Mapper<MetarInfo>()
}

/** Component for tagging sector related information
 *
 * [sectorId] = -1 -> tower control
 *
 * [sectorId] = -2 -> ACC control
 * */
data class SectorInfo(var sectorId: Byte = 0, var controllerName: String = "ChangeYourNameLol", var frequency: String = "121.5", var sectorCallsign: String = "Approach"): Component {
    companion object: Mapper<SectorInfo>() {
        const val TOWER: Byte = -1
        const val CENTRE: Byte = -2
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

/** Component for tagging a published hold procedure */
data class PublishedHoldInfo(var wptId: Short = 0, var maxAltFt: Int? = null, var minAltFt: Int? = null,
                             var maxSpdKtLower: Short = 230, var maxSpdKtHigher: Short = 240, var inboundHdgDeg: Short = 360, var legDistNm: Byte = 5,
                             var turnDir: Byte = CommandTarget.TURN_RIGHT): Component {
     companion object: Mapper<PublishedHoldInfo>()
 }

/** Component for tagging aircraft specific information
 *
 * Includes performance determining data - minimum approach speed, rotation speed, weight, others in [aircraftPerf]
 * */
data class AircraftInfo(var icaoCallsign: String = "SHIBA1", var icaoType: String = "B77W"): Component {
    var aircraftPerf = AircraftTypeData.AircraftPerfData()
    var maxAcc: Float = 0f
    var minAcc: Float = 0f
    var maxVs: Float = 0f
    var minVs: Float = 0f
    companion object: Mapper<AircraftInfo>()
}

/** Component for tagging basic approach information */
data class ApproachInfo(var approachName: String = "", var rwyId: Byte = 0, var towerName: String = "", var frequency: String = ""): Component {
    companion object: Mapper<ApproachInfo>()
}

/** Component for tagging localizer information */
data class Localizer(var maxDistNm: Byte = 0): Component {
    companion object: Mapper<Localizer>()
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

/** Component for tagging visual approach (one will be created for every runway with their own extended centerline and glide path of 3 degrees) */
class Visual: Component {
    companion object: Mapper<Visual>()
}

class Emergency: Component {

}

class Conflictable: Component {

}
