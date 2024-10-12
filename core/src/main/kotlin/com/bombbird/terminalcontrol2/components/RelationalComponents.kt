package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.RouteZone
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.json.DoNotOverwriteSavedJSON
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.traffic.RunwayConfiguration
import com.bombbird.terminalcontrol2.utilities.AircraftRequest
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import ktx.ashley.Mapper
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap

/** Component to keep track of an airport's runways as well as the mapping runway names to the most updated ID (for backwards compatibility) */
data class RunwayChildren(val rwyMap: GdxArrayMap<Byte, Airport.Runway> = GdxArrayMap(),
                          val updatedRwyMapping: GdxArrayMap<String, Byte> = GdxArrayMap()): Component, BaseComponentJSONInterface, DoNotOverwriteSavedJSON {
    override val componentType = BaseComponentJSONInterface.ComponentType.RUNWAY_CHILDREN

    companion object {
        val mapper = object: Mapper<RunwayChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of an airport's SIDs */
data class SIDChildren(val sidMap: GdxArrayMap<String, SidStar.SID> = GdxArrayMap()): Component {
    companion object {
        val mapper = object: Mapper<SIDChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of an airport's STARs */
data class STARChildren(val starMap: GdxArrayMap<String, SidStar.STAR> = GdxArrayMap()): Component {
    companion object {
        val mapper = object: Mapper<STARChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of an airport's approaches */
data class ApproachChildren(val approachMap: GdxArrayMap<String, Approach> = GdxArrayMap()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.APPROACH_CHILDREN

    companion object {
        val mapper = object: Mapper<ApproachChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of list of approach names only */
class ApproachList(var approachList: Array<String> = arrayOf()): Component {
    companion object {
        val mapper = object: Mapper<ApproachList>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of an airport's available runway configurations */
data class RunwayConfigurationChildren(val rwyConfigs: GdxArrayMap<Byte, RunwayConfiguration> = GdxArrayMap()): Component {
    companion object {
        val mapper = object: Mapper<RunwayConfigurationChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of list of available runway configuration IDs only */
data class RunwayConfigurationList(val rwyConfigs: GdxArray<Byte> = GdxArray()): Component {
    companion object {
        val mapper = object: Mapper<RunwayConfigurationList>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of a runway's default generated visual approach */
data class VisualApproach(val visual: Entity = Entity()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.VISUAL_APPROACH

    companion object {
        val mapper = object: Mapper<VisualApproach>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of a runway's opposite runway */
data class OppositeRunway(val oppRwy: Entity = Entity()): Component {
    companion object {
        val mapper = object: Mapper<OppositeRunway>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of the list of opposite runways that a runway is dependent on */
data class DependentOppositeRunway(val depOppRwys: GdxArray<Entity> = GdxArray(4)): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DEPENDENT_OPPOSITE_RUNWAY

    companion object {
        val mapper = object: Mapper<DependentOppositeRunway>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of the list of non-opposite runways that a runway is dependent on */
data class DependentParallelRunway(val depParRwys: GdxArray<Entity> = GdxArray(4)): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DEPENDENT_PARALLEL_RUNWAY

    companion object {
        val mapper = object: Mapper<DependentParallelRunway>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of the list of runways that a runway has crossings with */
data class CrossingRunway(val crossRwys: GdxArray<Entity> = GdxArray(3)): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CROSSING_RUNWAY

    companion object {
        val mapper = object: Mapper<CrossingRunway>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to keep track of all dependencies for departure from a runway */
data class DepartureDependency(val dependencies: GdxArray<DependencyRule> = GdxArray(3)): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DEPARTURE_DEPENDENCY

    companion object {
        val mapper = object: Mapper<DepartureDependency>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    /** Inner class for defining a departure dependency */
    class DependencyRule(val dependeeRwy: Entity, val arrivalSep: Int?, val departureSep: Int?)
}

/** Component to store an arrival route's MVA exclusion zones */
data class ArrivalRouteZone(val starZone: GdxArray<RouteZone> = GdxArray(), val appZone: GdxArray<RouteZone> = GdxArray()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.ARRIVAL_ROUTE_ZONE

    companion object {
        val mapper = object: Mapper<ArrivalRouteZone>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to store a departure route's MVA exclusion zones */
data class DepartureRouteZone(val sidZone: GdxArray<RouteZone> = GdxArray()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.DEPARTURE_ROUTE_ZONE

    companion object {
        val mapper = object: Mapper<DepartureRouteZone>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to store an airport's NOZ groups */
data class ApproachNOZChildren(val nozGroups: GdxArray<ApproachNOZGroup> = GdxArray()): Component {
    companion object {
        val mapper = object: Mapper<ApproachNOZChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component to mark an object or procedure as deprecated, and should not be
 * used in new clearances and maintained only for backwards compatibility
 */
class DeprecatedEntity: Component {
    companion object {
        val mapper = object: Mapper<DeprecatedEntity>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to store an airport's custom approach separation groups */
class CustomApproachSeparationChildren(val customAppGroups: GdxArray<CustomApproachSeparation> = GdxArray()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CUSTOM_APPROACH_SEPARATION_CHILDREN

    companion object {
        val mapper = object: Mapper<CustomApproachSeparationChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to store an aircraft's requests */
class AircraftRequestChildren(val requests: GdxArray<AircraftRequest> = GdxArray()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.AIRCRAFT_REQUEST_CHILDREN

    companion object {
        val mapper = object: Mapper<AircraftRequestChildren>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}
