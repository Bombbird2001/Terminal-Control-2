package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.badlogic.gdx.utils.Queue.QueueIterator
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.WakeZone
import com.bombbird.terminalcontrol2.global.GAME
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.get
import ktx.collections.GdxArray

/** Data class storing data for individual airline data for JSON serialization */
@JsonClass(generateAdapter = true)
data class AirlineDistributionValueJSON(val icaoCode: String, val private: Boolean, val aircraftTypes: List<String>, val interval: Float)

/** Data class for storing data used when choosing a random airline/private aircraft to generate */
@JsonClass(generateAdapter = true)
data class RandomAirlineDataJSON(val airlines: List<AirlineDistributionValueJSON>)

/** Adapter object for serialization between [RandomAirlineData] and [RandomAirlineDataJSON] */
object RandomAirlineDataAdapter {
    @ToJson
    fun toJson(randomAirlineData: RandomAirlineData): RandomAirlineDataJSON {
        val array = ArrayList<AirlineDistributionValueJSON>()
        for (i in 0 until randomAirlineData.airlineDistribution.size()) {
            val value = randomAirlineData.airlineDistribution.getValue(i)
            val interval = randomAirlineData.airlineDistribution.getInterval(i)
            val aircraftArray = ArrayList<String>()
            for (j in 0 until value.third.size) aircraftArray.add(value.third[j])
            array.add(AirlineDistributionValueJSON(value.first, value.second, aircraftArray, interval))
        }
        return RandomAirlineDataJSON(array)
    }

    @FromJson
    fun fromJson(randomAirlineDataJSON: RandomAirlineDataJSON): RandomAirlineData {
        val dist = CumulativeDistribution<Triple<String, Boolean, GdxArray<String>>>()
        randomAirlineDataJSON.airlines.forEach {
            val acArray = GdxArray<String>()
            it.aircraftTypes.forEach { type -> acArray.add(type) }
            dist.add(Triple(it.icaoCode, it.private, acArray), it.interval)
        }
        return RandomAirlineData(dist)
    }
}

/** Data class for storing the next departure aircraft callsign for JSON serialization */
@JsonClass(generateAdapter = true)
data class AirportNextDepartureJSON(val callsign: String)

/** Adapter object for serialization between [AirportNextDeparture] and [AirportNextDepartureJSON] */
object AirportNextDepartureAdapter {
    @ToJson
    fun toJson(airportNextDeparture: AirportNextDeparture): AirportNextDepartureJSON {
        return AirportNextDepartureJSON(airportNextDeparture.aircraft[AircraftInfo.mapper]?.icaoCallsign ?: "")
    }

    @FromJson
    fun fromJson(airportNextDepartureJSON: AirportNextDepartureJSON): AirportNextDeparture {
        return AirportNextDeparture(GAME.gameServer?.aircraft?.get(airportNextDepartureJSON.callsign)?.entity ?: Entity())
    }
}

/** Data class for storing each individual point in the wake trail queue */
@JsonClass(generateAdapter = true)
data class WakeTrailValue(val position: Position, val wakeZone: WakeZone?)

/** Data class for storing an aircraft's wake trail points */
@JsonClass(generateAdapter = true)
data class WakeTrailJSON(val points: List<WakeTrailValue>, val distNmCounter: Float)

/** Adapter object for serialization between [WakeTrail] and [WakeTrailJSON] */
object WakeTrailAdapter {
    @ToJson
    fun toJson(wakeTrail: WakeTrail): WakeTrailJSON {
        val array = ArrayList<WakeTrailValue>()
        for (point in QueueIterator(wakeTrail.wakeZones)) array.add(WakeTrailValue(point.first, point.second))
        return WakeTrailJSON(array, wakeTrail.distNmCounter)
    }

    @FromJson
    fun fromJson(wakeTrailJSON: WakeTrailJSON): WakeTrail {
        return WakeTrail(distNmCounter = wakeTrailJSON.distNmCounter).apply {
            wakeTrailJSON.points.forEach { wakeZones.addLast(Pair(it.position, it.wakeZone)) }
        }
    }
}
