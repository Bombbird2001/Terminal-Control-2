package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import com.esotericsoftware.minlog.Log
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
        return Airport().apply {
            airportJSON.entity.components.forEach { if (it is Component) entity += it }
        }
    }

    /**
     * Gets a default empty [Airport] due to a missing component
     * @param missingComponent the name of the missing component
     * @return the empty default Airport
     */
    private fun emptyAirport(missingComponent: String): Airport {
        Log.info("AirportJSON", "Empty airport returned due to missing $missingComponent")
        return Airport()
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

    /**
     * Gets a default empty [Airport.Runway] due to a missing component
     * @param missingComponent the name of the missing component
     * @return the empty default Runway
     */
    private fun emptyRunway(missingComponent: String): Airport.Runway {
        Log.info("AirportJSON", "Empty runway returned due to missing $missingComponent")
        return Airport.Runway()
    }
}
