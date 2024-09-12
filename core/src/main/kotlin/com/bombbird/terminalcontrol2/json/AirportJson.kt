package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.plusAssign

/** Airport JSON data class which handles the serialization and de-serialization of airport entities specifically */
@JsonClass(generateAdapter = true)
data class AirportJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between [Airport] and [AirportJSON] */
object AirportAdapter {
    @ToJson
    fun toJson(airport: Airport): AirportJSON {
        return AirportJSON(BaseEntityJson(airport.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(airportJSON: AirportJSON): Airport {
        return Airport.newEmptyAirport().apply {
            airportJSON.entity.components.forEach { if (it is Component) entity += it }
        }
    }
}

/** Runway JSON data class which handles the serialization and de-serialization of runway entities specifically */
@JsonClass(generateAdapter = true)
data class RunwayJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between [Airport.Runway] and [RunwayJSON] */
object RunwayAdapter {
    @ToJson
    fun toJson(rwy: Airport.Runway): RunwayJSON {
        return RunwayJSON(BaseEntityJson(rwy.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(rwyJSON: RunwayJSON): Airport.Runway {
        return Airport.Runway().apply {
            rwyJSON.entity.components.forEach { if (it is Component) entity += it }
        }
    }
}
