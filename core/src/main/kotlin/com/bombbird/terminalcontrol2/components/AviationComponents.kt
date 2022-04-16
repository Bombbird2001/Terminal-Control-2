package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import ktx.ashley.Mapper

/** Component for tagging airport related information */
class AirportInfo(var arptId: Byte = 0, var icaoCode: String = "", var name: String = "", val rwys: RunwayChildren = RunwayChildren()): Component {
    // TODO Add other airport related components
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
class RunwayInfo(var rwyId: Byte = 0, var rwyName: String = "", var lengthM: Short = 4000): Component {
    lateinit var airport: Airport
    // TODO Add other runway related components
    companion object: Mapper<RunwayInfo>()
}

/** Component for tagging airport METAR information */
class MetarInfo(var realLifeIcao: String = "", var rawMetar: String = "", windHeading: Short = 360, windSpeedKts: Short = 0, windGustKts: Short = 0, visibility: Short = 10000, ceilingAGL: Short = 32000, windshear: String = ""): Component {
    companion object: Mapper<MetarInfo>()
}

/** Component for tagging sector related information
 * [sectorId] = -1 -> tower control
 *
 * [sectorId] = -2 -> ACC control
 * */
class SectorInfo(var sectorId: Byte = 0, var controllerName: String = "ChangeYourNameLol", var frequency: String = "121.5"): Component {
    companion object: Mapper<SectorInfo>() {
        val TOWER = -1
        val CENTRE = -2
    }
}

/** Component for tagging waypoint related information */
class WaypointInfo(var wptName: String = "-----"): Component {
    companion object: Mapper<WaypointInfo>()
}

/** Component for tagging sector control information */
class Controllable(var sectorId: Byte = 0): Component {
    companion object: Mapper<Controllable>()
}

/** Component for tagging aircraft specific information
 * Includes performance determining data - minimum approach speed, rotation speed, weight, others in [aircraftPerf]
 * */
class AircraftInfo(var icaoCallsign: String = "SHIBA1", var icaoType: String = "B77W", var appSpd: Short = 130, var vR: Short = 150, var weightTons: Float = 209f): Component {
    val aircraftPerf = AircraftTypeData.getAircraftPerf(icaoType)
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
class FlightType(var type: Byte = 0): Component {
    companion object: Mapper<FlightType>() {
        val ARRIVAL: Byte = 0
        val DEPARTURE: Byte = 1
        val EN_ROUTE: Byte = 2
    }
}

class Emergency(): Component {

}

class Conflictable(): Component {

}
