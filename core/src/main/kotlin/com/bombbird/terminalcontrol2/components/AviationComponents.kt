package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import ktx.ashley.Mapper

/** Component for tagging airport related information */
class AirportInfo(var arptId: Int = 0, var icaoCode: String = "", var name: String = "", val rwys: RunwayChildren = RunwayChildren()): Component {
    // TODO Add other airport related components
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
class RunwayInfo(var rwyId: Int = 0, var rwyName: String = "", var lengthM: Int = 4000): Component {
    lateinit var airport: Airport
    // TODO Add other runway related components
    companion object: Mapper<RunwayInfo>()
}

/** Component for tagging airport METAR information */
class MetarInfo(var realLifeIcao: String = "", var rawMetar: String = "", windHeading: Int = 360, windSpeedKts: Int = 0, windGustKts: Int = 0, visibility: Int = 10000, ceilingAGL: Int = 32000, windshear: String = ""): Component {
    companion object: Mapper<MetarInfo>()
}

/** Component for tagging sector related information
 * [sectorId] = -1 -> tower control
 *
 * [sectorId] = -2 -> ACC control
 * */
class SectorInfo(var sectorId: Int = 0, var controllerName: String = "ChangeYourNameLol", var frequency: String = "121.5"): Component {
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
class Controllable(var sectorId: Int = 0): Component {
    companion object: Mapper<Controllable>()
}

/** Component for tagging aircraft specific information
 * Includes performance determining data - minimum approach speed, rotation speed, engine thrust (at sea level)
 * */
class AircraftInfo(var icaoCallsign: String = "XYZ123", var icaoType: String = "B77W", var appSpd: Int = 130, var vR: Int = 150, var weightTons: Float = 209f): Component {
    val wakeCat: Char
        get() = 'H'
    val recat: Char
        get() = 'B'
    val thrustKNMax: Int
        get() = 1026
    // TODO Map aircraft type to its above 3 parameters
    // TODO I love physics
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
class FlightType(var type: Int = 0): Component {
    companion object: Mapper<FlightType>() {
        val ARRIVAL = 0
        val DEPARTURE = 1
        val EN_ROUTE = 2
    }
}

class Emergency(): Component {

}

class Conflictable(): Component {

}
