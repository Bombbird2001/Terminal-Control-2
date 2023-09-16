package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.RouteZone
import com.bombbird.terminalcontrol2.navigation.Approach
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.collections.set

/** Data class for storing all runways belonging to an airport */
@JsonClass(generateAdapter = true)
data class RunwayChildrenJSON(val rwys: List<Airport.Runway>)

/** Adapter object for serialization between [RunwayChildren] and [RunwayChildrenJSON] */
object RunwayChildrenAdapter {
    @ToJson
    fun toJson(rwys: RunwayChildren): RunwayChildrenJSON {
        val array = ArrayList<Airport.Runway>()
        Entries(rwys.rwyMap).forEach { array.add(it.value) }
        return RunwayChildrenJSON(array)
    }

    @FromJson
    fun fromJson(rwysJSON: RunwayChildrenJSON): RunwayChildren {
        return RunwayChildren().apply {
            rwysJSON.rwys.forEach {
                val rwyInfo = it.entity[RunwayInfo.mapper] ?: return@forEach
                rwyMap[rwyInfo.rwyId] = it
            }
        }
    }
}

/** Data class for storing all approaches belonging to an airport */
@JsonClass(generateAdapter = true)
data class ApproachChildrenJSON(val apps: List<Approach>)

/** Adapter object for serialization between [ApproachChildren] and [ApproachChildrenJSON] */
object ApproachChildrenAdapter {
    @ToJson
    fun toJson(apps: ApproachChildren): ApproachChildrenJSON {
        val array = ArrayList<Approach>()
        Entries(apps.approachMap).forEach { array.add(it.value) }
        return ApproachChildrenJSON(array)
    }

    @FromJson
    fun fromJson(appsJSON: ApproachChildrenJSON): ApproachChildren {
        return ApproachChildren().apply {
            appsJSON.apps.forEach {
                val appInfo = it.entity[ApproachInfo.mapper] ?: return@forEach
                approachMap[appInfo.approachName] = it
            }
        }
    }
}

/** Data class for storing the visual approach belonging to a runway */
@JsonClass(generateAdapter = true)
data class VisualApproachJSON(val visApp: BaseEntityJson)

/** Adapter object for serialization between [VisualApproach] and [VisualApproachJSON] */
object VisualApproachAdapter {
    @ToJson
    fun toJson(visApp: VisualApproach): VisualApproachJSON {
        return VisualApproachJSON(BaseEntityJson(visApp.visual.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(visAppJSON: VisualApproachJSON): VisualApproach {
        return VisualApproach().apply {
            visAppJSON.visApp.components.forEach { if (it is Component) visual += it }
        }
    }
}

/** Data class for storing the dependent opposite runways belonging to a runway */
@JsonClass(generateAdapter = true)
data class DependentOppositeRunwayJSON(val depOppRwys: List<RunwayRefJSON>)

/** Adapter object for serialization between [DependentOppositeRunway] and [DependentOppositeRunwayJSON] */
object DependentOppositeRunwayAdapter {
    @ToJson
    fun toJson(depOppRwy: DependentOppositeRunway): DependentOppositeRunwayJSON {
        val array = ArrayList<RunwayRefJSON>()
        for (i in 0 until depOppRwy.depOppRwys.size) depOppRwy.depOppRwys[i]?.let { array.add(toRunwayRefJSON(it)) }
        return DependentOppositeRunwayJSON(array)
    }

    @FromJson
    fun fromJson(depOppRwysJSON: DependentOppositeRunwayJSON): DependentOppositeRunway {
        return DependentOppositeRunway().apply {
            depOppRwysJSON.depOppRwys.forEach {
                delayedEntityRetrieval.add { depOppRwys.add(it.getRunwayEntity()) }
            }
        }
    }
}

/** Data class for storing the dependent parallel runways belonging to a runway */
@JsonClass(generateAdapter = true)
data class DependentParallelRunwayJSON(val depParallelRwys: List<RunwayRefJSON>)

/** Adapter object for serialization between [DependentParallelRunway] and [DependentParallelRunwayJSON] */
object DependentParallelRunwayAdapter {
    @ToJson
    fun toJson(depParallelRwy: DependentParallelRunway): DependentParallelRunwayJSON {
        val array = ArrayList<RunwayRefJSON>()
        for (i in 0 until depParallelRwy.depParRwys.size) depParallelRwy.depParRwys[i]?.let { array.add(toRunwayRefJSON(it)) }
        return DependentParallelRunwayJSON(array)
    }

    @FromJson
    fun fromJson(depParallelRwysJSON: DependentParallelRunwayJSON): DependentParallelRunway {
        return DependentParallelRunway().apply {
            depParallelRwysJSON.depParallelRwys.forEach {
                delayedEntityRetrieval.add { depParRwys.add(it.getRunwayEntity()) }
            }
        }
    }
}

/** Data class for storing the crossing runways belonging to a runway */
@JsonClass(generateAdapter = true)
data class CrossingRunwayJSON(val crossingRwys: List<RunwayRefJSON>)

/** Adapter object for serialization between [CrossingRunway] and [CrossingRunwayJSON] */
object CrossingRunwayAdapter {
    @ToJson
    fun toJson(crossingRwy: CrossingRunway): CrossingRunwayJSON {
        val array = ArrayList<RunwayRefJSON>()
        for (i in 0 until crossingRwy.crossRwys.size) crossingRwy.crossRwys[i]?.let { array.add(toRunwayRefJSON(it)) }
        return CrossingRunwayJSON(array)
    }

    @FromJson
    fun fromJson(crossingRwyJSON: CrossingRunwayJSON): CrossingRunway {
        return CrossingRunway().apply {
            crossingRwyJSON.crossingRwys.forEach {
                delayedEntityRetrieval.add { crossRwys.add(it.getRunwayEntity()) }
            }
        }
    }
}

/** Data class for storing each dependency rule */
@JsonClass(generateAdapter = true)
data class DependencyRuleJSON(val dependeeRwy: RunwayRefJSON, val arrivalSep: Int?, val departureSep: Int?)

/** Data class for storing the departure dependency rules belonging to a runway */
@JsonClass(generateAdapter = true)
data class DepartureDependencyJSON(val dependencies: List<DependencyRuleJSON>)

/** Adapter object for serialization between [DepartureDependency] and [DepartureDependencyJSON] */
object DepartureDependencyAdapter {
    @ToJson
    fun toJson(deptDep: DepartureDependency): DepartureDependencyJSON {
        val array = ArrayList<DependencyRuleJSON>()
        for (i in 0 until deptDep.dependencies.size) deptDep.dependencies[i]?.let {
            array.add(DependencyRuleJSON(toRunwayRefJSON(it.dependeeRwy), it.arrivalSep, it.departureSep))
        }
        return DepartureDependencyJSON(array)
    }

    @FromJson
    fun fromJson(deptDepJSON: DepartureDependencyJSON): DepartureDependency {
        return DepartureDependency().apply {
            deptDepJSON.dependencies.forEach {
                delayedEntityRetrieval.add { DepartureDependency.DependencyRule(it.dependeeRwy.getRunwayEntity(), it.arrivalSep, it.departureSep) }
            }
        }
    }
}

/** Data class for storing arrival route zone information */
@JsonClass(generateAdapter = true)
data class ArrivalRouteZoneJSON(val starZones: List<RouteZone>, val appZones: List<RouteZone>)

/** Adapter object for serialization between [ArrivalRouteZone] and [ArrivalRouteZoneJSON] */
object ArrivalRouteZoneAdapter {
    @ToJson
    fun toJson(arrivalRouteZone: ArrivalRouteZone): ArrivalRouteZoneJSON {
        val starArray = ArrayList<RouteZone>()
        for (i in 0 until arrivalRouteZone.starZone.size) starArray.add(arrivalRouteZone.starZone[i])
        val appArray  = ArrayList<RouteZone>()
        for (i in 0 until arrivalRouteZone.appZone.size) appArray.add(arrivalRouteZone.appZone[i])
        return ArrivalRouteZoneJSON(starArray, appArray)
    }

    @FromJson
    fun fromJson(arrivalRouteZoneJSON: ArrivalRouteZoneJSON): ArrivalRouteZone {
        return ArrivalRouteZone().apply {
            arrivalRouteZoneJSON.starZones.forEach { starZone.add(it) }
            arrivalRouteZoneJSON.appZones.forEach { appZone.add(it) }
        }
    }
}

/** Data class for storing departure route zone information */
@JsonClass(generateAdapter = true)
data class DepartureRouteZoneJSON(val sidZones: List<RouteZone>)

/** Adapter object for serialization between [DepartureRouteZone] and [DepartureRouteZoneJSON] */
object DepartureRouteZoneAdapter {
    @ToJson
    fun toJson(depRouteZone: DepartureRouteZone): DepartureRouteZoneJSON {
        val starArray = ArrayList<RouteZone>()
        for (i in 0 until depRouteZone.sidZone.size) starArray.add(depRouteZone.sidZone[i])
        return DepartureRouteZoneJSON(starArray)
    }

    @FromJson
    fun fromJson(depRouteZoneJSON: DepartureRouteZoneJSON): DepartureRouteZone {
        return DepartureRouteZone().apply {
            depRouteZoneJSON.sidZones.forEach { sidZone.add(it) }
        }
    }
}
