package com.bombbird.terminalcontrol2.json

import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.get
import kotlin.math.roundToInt

/** Data class for storing information of the waypoint for JSON serialization */
@JsonClass(generateAdapter = true)
data class WaypointJSON(val position: Position, val wptInfo: WaypointInfo)

/** Adapter object for serialization between [Waypoint] and [WaypointJSON] */
object WaypointAdapter {
    @ToJson
    fun toJson(waypoint: Waypoint): WaypointJSON {
        return WaypointJSON(waypoint.entity[Position.mapper] ?: Position(), waypoint.entity[WaypointInfo.mapper] ?: WaypointInfo())
    }

    @FromJson
    fun fromJson(waypointJSON: WaypointJSON): Waypoint {
        return Waypoint(waypointJSON.wptInfo.wptId, waypointJSON.wptInfo.wptName,
            waypointJSON.position.x.roundToInt().toShort(), waypointJSON.position.y.roundToInt().toShort(), false)
    }
}
