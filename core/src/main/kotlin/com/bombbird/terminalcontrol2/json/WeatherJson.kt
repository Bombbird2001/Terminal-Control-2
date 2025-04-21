package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.ThunderStormInfo
import com.bombbird.terminalcontrol2.entities.ThunderStorm
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.plusAssign

/**
 * Thunderstorm JSON data class which handles the serialization and
 * de-serialization of thunderstorm entities specifically
 */
@JsonClass(generateAdapter = true)
data class ThunderStormJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between thunderstorm entities */
object ThunderStormAdapter {
    @ToJson
    fun toJson(storm: ThunderStorm): ThunderStormJSON {
        return ThunderStormJSON(BaseEntityJson(storm.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(stormJSON: ThunderStormJSON): ThunderStorm {
        val components = stormJSON.entity.components
        val stormInfo = components.getComponent<ThunderStormInfo>() ?: return emptyStorm("ThunderStormInfo")
        val pos = components.getComponent<Position>() ?: return emptyStorm("Position")
        val alt = components.getComponent<Altitude>() ?: return emptyStorm("Altitude")
        return ThunderStorm(stormInfo.id, pos.x, pos.y, alt.altitudeFt, false).apply {
            components.forEach { if (it is Component) entity += it }
        }
    }

    /** Gets a default empty [ThunderStorm] due to a [missingComponent] */
    private fun emptyStorm(missingComponent: String): ThunderStorm {
        FileLog.info("WeatherJSON", "Empty storm returned due to missing $missingComponent")
        return ThunderStorm(-1, 0f, 0f, 0f, false)
    }
}