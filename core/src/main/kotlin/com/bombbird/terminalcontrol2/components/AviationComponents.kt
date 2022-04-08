package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import ktx.ashley.Mapper

/** Component for tagging airport related information */
class AirportInfo(var icaoCode: String = "", var name: String = "", val rwys: RunwayChildren = RunwayChildren()): Component {
    // TODO Add other airport related components
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
class RunwayInfo(var rwyName: String = ""): Component {
    lateinit var airport: Airport
    // TODO Add other runway related components
    companion object: Mapper<RunwayInfo>()
}

/** Component for tagging sector related information */
class SectorInfo(var sectorId: Int = 0, var frequency: String = "121.5"): Component {
    companion object: Mapper<SectorInfo>()
}

/** Component for tagging waypoint related information */
class WaypointInfo(var wptName: String = "-----"): Component {
    companion object: Mapper<WaypointInfo>()
}

/** Component for tagging sector control information
 * sectorId = -1 means tower control
 * sectorId = -2 means ACC control
 * */
class Controllable(var sector: Int = 0): Component {
    companion object: Mapper<Controllable>()
}

/** Component for tagging aircraft specific information
 * Includes performance determining data - minimum approach speed, rotation speed, engine thrust (at sea level)
 * */
class AircraftInfo(var icaoCallsign: String = "XYZ123", icaoType: String = "B77W", wakeCat: Char = 'H', recat: Char = 'B', appSpd: Int = 130, vR: Int = 150, weightTons: Float = 209f, thrustKNMax: Int = 1026): Component {
    // TODO I love physics
    companion object: Mapper<AirportInfo>()
}

class Arrival(): Component {
    companion object: Mapper<Arrival>()
}

class Departure(): Component {
    companion object: Mapper<Departure>()
}

class EnRoute(): Component {
    companion object: Mapper<EnRoute>()
}

class Emergency(): Component {

}

class Conflictable(): Component {

}
