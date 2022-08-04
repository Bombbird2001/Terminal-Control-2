package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.RouteZone
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.plusAssign

/** Data class for storing route zone information */
@JsonClass(generateAdapter = true)
data class RouteZoneJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between [RouteZone] and [RouteZoneJSON] */
object RouteZoneAdapter {
    @ToJson
    fun toJson(routeZone: RouteZone): RouteZoneJSON {
        return RouteZoneJSON(BaseEntityJson(routeZone.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(routeZoneJSON: RouteZoneJSON): RouteZone {
        return RouteZone().apply {
            routeZoneJSON.entity.components.forEach { if (it is Component) entity += it }
        }
    }
}

/** Data class for storing wake zone information */
@JsonClass(generateAdapter = true)
data class WakeZoneJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between [WakeZone] and [WakeZoneJSON] */
object WakeZoneAdapter {
    @ToJson
    fun toJson(wakeZone: WakeZone): WakeZoneJSON {
        return WakeZoneJSON(BaseEntityJson(wakeZone.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(wakeZoneJSON: WakeZoneJSON): WakeZone {
        return WakeZone().apply {
            wakeZoneJSON.entity.components.forEach { if (it is Component) entity += it }
        }
    }
}
