package com.bombbird.terminalcontrol2.json

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.FlightType
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.plusAssign

/** Aircraft JSON data class which handles the serialization and de-serialization of aircraft entities specifically */
@JsonClass(generateAdapter = true)
data class AircraftJSON(val entity: BaseEntityJson)

/** Adapter object for serialization between [Aircraft] and [AircraftJSON] */
object AircraftAdapter {
    @ToJson
    fun toJson(aircraft: Aircraft): AircraftJSON {
        return AircraftJSON(BaseEntityJson(aircraft.entity.getComponentArrayList()))
    }

    @FromJson
    fun fromJson(aircraftJSON: AircraftJSON): Aircraft {
        val components = aircraftJSON.entity.components
        val acInfo = components.getComponent<AircraftInfo>() ?: return emptyAircraft("AircraftInfo")
        val alt = components.getComponent<Altitude>() ?: return emptyAircraft("Altitude")
        val pos = components.getComponent<Position>() ?: return emptyAircraft("Position")
        val flightType = components.getComponent<FlightType>() ?: return emptyAircraft("FlightType")
        return Aircraft(acInfo.icaoCallsign, pos.x, pos.y, alt.altitudeFt, acInfo.icaoType, flightType.type).apply {
            components.forEach { entity += it }
        }
    }

    /**
     * Gets a default empty [Aircraft] due to a missing component
     * @param missingComponent the name of the missing component
     * @return the empty default Aircraft
     */
    private fun emptyAircraft(missingComponent: String): Aircraft {
        Gdx.app.log("AircraftJSON", "Empty aircraft returned due to missing $missingComponent")
        return Aircraft()
    }
}
