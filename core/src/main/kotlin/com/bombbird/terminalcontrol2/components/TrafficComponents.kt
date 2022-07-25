package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.entities.ApproachNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.DepartureNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import ktx.ashley.Mapper
import ktx.collections.GdxArray

/** Component for tagging runway that is active for landings */
class ActiveLanding: Component {
    companion object: Mapper<ActiveLanding>()
}

/** Component for tagging runway that is active for takeoffs */
class ActiveTakeoff: Component {
    companion object: Mapper<ActiveTakeoff>()
}

/** Component for tagging traffic distribution at an airport */
data class RandomAirlineData(val airlineDistribution: CumulativeDistribution<Triple<String, Boolean, GdxArray<String>>> = CumulativeDistribution()):
    Component {
    companion object: Mapper<RandomAirlineData>()
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

/** Component for tagging a closed airport for arrivals */
class ArrivalClosed: Component {
    companion object: Mapper<ArrivalClosed>()
}

/** Component for tagging a closed airport for departures */
data class DepartureInfo(var closed: Boolean = false, var backlog: Int = 0): Component {
    companion object: Mapper<DepartureInfo>()
}

/** Component for tagging the next departure aircraft entity of the airport */
data class AirportNextDeparture(var aircraft: Entity = Entity()): Component {
    companion object: Mapper<AirportNextDeparture>()
}

/** Component for tagging the information of a previous arrival aircraft of the runway */
data class RunwayPreviousArrival(var timeSinceTouchdownS: Float = 0f, var wakeCat: Char = AircraftTypeData.AircraftPerfData.WAKE_MEDIUM,
                                 var recat: Char = AircraftTypeData.AircraftPerfData.RECAT_D): Component {
    companion object: Mapper<RunwayPreviousArrival>()
}

/** Component for tagging the information of a previous departure aircraft of the runway */
data class RunwayPreviousDeparture(var timeSinceDepartureS: Float = 0f, var wakeCat: Char = AircraftTypeData.AircraftPerfData.WAKE_MEDIUM,
                                   var recat: Char = AircraftTypeData.AircraftPerfData.RECAT_D): Component {
    companion object: Mapper<RunwayPreviousDeparture>()
}

/** Component for tagging if a runway is occupied by an aircraft */
class RunwayOccupied: Component {
    companion object: Mapper<RunwayOccupied>()
}

/** Component for tagging the aircraft closest to landing on the runway */
data class RunwayNextArrival(var aircraft: Entity = Entity(), var distFromThrPx: Float = 0f): Component {
    companion object: Mapper<RunwayNextArrival>()
}

/**
 * Component for tagging a conflict-able entity, and the conflict sector (based on its altitude) that it belongs to; this
 * is updated once every second and used to reduce the number of comparisons required during the conflict check
 * */
data class ConflictAble(var conflictLevel: Int = Int.MAX_VALUE): Component {
    companion object: Mapper<ConflictAble>()
}

/**
 * Component for tagging the wake turbulence trails of an aircraft; the trail queue will be updated every 0.5nm travelled
 * by the aircraft
 * */
data class WakeTrail(val wakeZones: Queue<Pair<Position, WakeZone?>> = Queue(), var distNmCounter: Float = 0f): Component {
    companion object: Mapper<WakeTrail>()
}

/**
 * Component for tagging the wake turbulence zone strength, based on the leading aircraft wake/Recat category and the
 * distance from the leading aircraft
 * */
data class WakeStrength(var leadingWake: Char = 'M', var leadingRecat: Char = 'D', var distFromAircraft: Float = 0f): Component {
    companion object: Mapper<WakeStrength>()
}
