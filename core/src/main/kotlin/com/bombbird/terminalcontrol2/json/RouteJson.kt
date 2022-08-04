package com.bombbird.terminalcontrol2.json

import com.bombbird.terminalcontrol2.navigation.Route
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

/** Data class for storing route info for JSON serialization */
@JsonClass(generateAdapter = true)
data class RouteJSON(val legs: List<BaseLegJSONInterface>)

/** Adapter object for serialization between [Route] and [RouteJSON] */
object RouteAdapter {
    @ToJson
    fun toJson(route: Route): RouteJSON {
        val array = ArrayList<BaseLegJSONInterface>()
        for (i in 0 until route.size) route[i].let { if (it is BaseLegJSONInterface) array.add(it) }
        return RouteJSON(array)
    }

    @FromJson
    fun fromJson(routeJSON: RouteJSON): Route {
        return Route().apply {
            routeJSON.legs.forEach { if (it is Route.Leg) add(it) }
        }
    }
}
