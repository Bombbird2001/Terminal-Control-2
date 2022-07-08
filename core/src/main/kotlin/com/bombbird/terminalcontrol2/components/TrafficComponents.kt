package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.bombbird.terminalcontrol2.entities.ApproachNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.DepartureNormalOperatingZone
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
data class DepartureInfo(var closed: Boolean = false, var backlog: Int = -10): Component {
    companion object: Mapper<DepartureInfo>()
}

/** Component for tagging the information of a previous departure aircraft */
data class PreviousDeparture(var timeSinceDepartureS: Float = 240f, var wakeCat: Char = AircraftTypeData.AircraftPerfData.WAKE_MEDIUM,
                             var recat: Char = AircraftTypeData.AircraftPerfData.RECAT_D): Component {
    companion object: Mapper<PreviousDeparture>()
}

/** Component for tagging if a runway is occupied by an aircraft */
class RunwayOccupied: Component {
    companion object: Mapper<RunwayOccupied>()
}

/** Component for tagging the aircraft closest to landing on the runway */
data class RunwayNextArrival(var aircraft: Entity? = null): Component {
    companion object: Mapper<RunwayNextArrival>()
}
