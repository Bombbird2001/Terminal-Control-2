package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.entities.ApproachNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.DepartureNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.json.DoNotOverwriteSavedJSON
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper
import ktx.collections.GdxArray

/** Component for tagging runway that is active for landings */
@JsonClass(generateAdapter = true)
class ActiveLanding: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ACTIVE_LANDING

    companion object {
        val mapper = object: Mapper<ActiveLanding>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging runway that is active for takeoffs */
@JsonClass(generateAdapter = true)
class ActiveTakeoff: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ACTIVE_TAKEOFF

    companion object {
        val mapper = object: Mapper<ActiveTakeoff>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging runway that is closed */
@JsonClass(generateAdapter = true)
class RunwayClosed: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RUNWAY_CLOSED

    companion object {
        val mapper = object: Mapper<RunwayClosed>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging traffic distribution at an airport */
data class RandomAirlineData(val airlineDistribution: CumulativeDistribution<Triple<String, Boolean, GdxArray<String>>> = CumulativeDistribution()):
    Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RANDOM_AIRLINE_DATA

    companion object {
        val mapper = object: Mapper<RandomAirlineData>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the approach NOZ groups for an airport */
data class ApproachNOZGroup(var appNoz: GdxArray<ApproachNormalOperatingZone> = GdxArray()): Component {
    companion object {
        val mapper = object: Mapper<ApproachNOZGroup>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the departure NOZ for a runway */
data class DepartureNOZ(var depNoz: DepartureNormalOperatingZone): Component {
    companion object {
        val mapper = object: Mapper<DepartureNOZ>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the active runway configuration of an airport */
@JsonClass(generateAdapter = true)
data class ActiveRunwayConfig(var configId: Byte = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ACTIVE_RUNWAY_CONFIG

    companion object {
        val mapper = object: Mapper<ActiveRunwayConfig>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging a pending runway configuration change for an airport */
data class PendingRunwayConfig(var pendingId: Byte = 0, var timeRemaining: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<PendingRunwayConfig>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging a closed airport for arrivals */
@JsonClass(generateAdapter = true)
class ArrivalClosed: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ARRIVAL_CLOSED

    companion object {
        val mapper = object: Mapper<ArrivalClosed>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for departure info for an airport, including closed status, backlog */
@JsonClass(generateAdapter = true)
data class DepartureInfo(var closed: Boolean = false, var backlog: Int = 0): Component, BaseComponentJSONInterface, DoNotOverwriteSavedJSON {
    override val componentType = BaseComponentJSONInterface.ComponentType.DEPARTURE_INFO

    companion object {
        val mapper = object: Mapper<DepartureInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the maximum number of departures an airport can depart in advance */
data class MaxAdvancedDepartures(var maxAdvanceDepartures: Int = 10): Component {
    companion object {
        val mapper = object: Mapper<MaxAdvancedDepartures>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

@JsonClass(generateAdapter = true)
/** Component for tagging the time since a plane departed from the airport */
data class TimeSinceLastDeparture(var time: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.TIME_SINCE_LAST_DEPARTURE

    companion object {
        val mapper = object: Mapper<TimeSinceLastDeparture>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the next departure aircraft entity of the airport */
data class AirportNextDeparture(var aircraft: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.AIRPORT_NEXT_DEPARTURE

    companion object {
        val mapper = object: Mapper<AirportNextDeparture>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the information of a previous arrival aircraft of the runway */
@JsonClass(generateAdapter = true)
data class RunwayPreviousArrival(var timeSinceTouchdownS: Float = 0f, var wakeCat: Char = AircraftTypeData.AircraftPerfData.WAKE_MEDIUM,
                                 var recat: Char = AircraftTypeData.AircraftPerfData.RECAT_D): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RUNWAY_PREVIOUS_ARRIVAL

    companion object {
        val mapper = object: Mapper<RunwayPreviousArrival>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the information of a previous departure aircraft of the runway */
@JsonClass(generateAdapter = true)
data class RunwayPreviousDeparture(var timeSinceDepartureS: Float = 0f, var wakeCat: Char = AircraftTypeData.AircraftPerfData.WAKE_MEDIUM,
                                   var recat: Char = AircraftTypeData.AircraftPerfData.RECAT_D, var isTurboprop: Boolean? = false): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RUNWAY_PREVIOUS_DEPARTURE
    companion object {
        val mapper = object: Mapper<RunwayPreviousDeparture>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging if a runway is occupied by an aircraft */
@JsonClass(generateAdapter = true)
class RunwayOccupied: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.RUNWAY_OCCUPIED

    companion object {
        val mapper = object: Mapper<RunwayOccupied>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the aircraft closest to landing on the runway */
data class RunwayNextArrival(var aircraft: Entity = Entity(), var distFromThrPx: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<RunwayNextArrival>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging the approach sequence to a runway for showing wake separation indicators
 *
 * Wake sequence is calculated independently on each client, and is not synced across clients
 */
data class ApproachWakeSequence(var aircraftDist: GdxArray<Pair<Entity, Float>> = GdxArray()): Component {
    companion object {
        val mapper = object: Mapper<ApproachWakeSequence>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging a conflict-able entity, and the conflict sector (based on its altitude) that it belongs to; this
 * is updated once every second and used to reduce the number of comparisons required during the conflict check
 */
data class ConflictAble(var conflictLevel: Int = Int.MAX_VALUE): Component {
    companion object {
        val mapper = object: Mapper<ConflictAble>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging the wake turbulence trails of an aircraft; the trail queue will be updated every 0.5nm travelled
 * by the aircraft
 */
data class WakeTrail(val wakeZones: Queue<Pair<Position, WakeZone?>> = Queue(), var distNmCounter: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.WAKE_TRAIL

    companion object {
        val mapper = object: Mapper<WakeTrail>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging the wake turbulence zone strength, based on the leading aircraft wake/Recat category and the
 * distance from the leading aircraft
 */
@JsonClass(generateAdapter = true)
data class WakeInfo(var aircraftCallsign: String = "", var leadingWake: Char = 'M', var leadingRecat: Char = 'D',
                    var distFromAircraft: Float = 0f, var approachAirportId: Byte? = null, var approachName: String? = null): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.WAKE_INFO

    companion object {
        val mapper = object: Mapper<WakeInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging the wake turbulence tolerance of an aircraft; this is updated every second to check if a go
 * around is required due to wake turbulence
 */
@JsonClass(generateAdapter = true)
data class WakeTolerance(var accumulation: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.WAKE_TOLERANCE

    companion object {
        val mapper = object: Mapper<WakeTolerance>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging information related to a trajectory point, including the aircraft it belongs to, and how many
 * seconds in advance the prediction is
 */
data class TrajectoryPointInfo(var aircraft: Entity = Entity(), var advanceTimingS: Int = 0): Component {
    companion object {
        val mapper = object: Mapper<TrajectoryPointInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component representing custom approach separation groups */
@JsonClass(generateAdapter = true)
class CustomApproachSeparation(var appGroup1: Array<String> = arrayOf(), var appGroup2: Array<String> = arrayOf(),
                               var sepNm: Float = 2f): Component {
    companion object {
        val mapper = object: Mapper<CustomApproachSeparation>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}
