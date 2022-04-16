package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import ktx.ashley.get

/** Helper object for dealing with METAR shenanigans */
object MetarTools {
    /** Helper class that specifies the JSON format to send METAR requests to the server */
    @JsonClass(generateAdapter = true)
    data class MetarRequest(
        val password: String,
        val airports: List<MetarMapper>
    ) {

        /** METAR request data format for an airport */
        @JsonClass(generateAdapter = true)
        data class MetarMapper(
            val realIcaoCode: String,
            val icaoCode: String
        )
    }

    /** Requests METAR for all airports in the supplied [airports] hashMap */
    fun requestAllMetar(airports: HashMap<String, Airport>) {
        val metarRequest = MetarRequest(Secrets.GET_METAR_PW, ArrayList<MetarRequest.MetarMapper>().apply {
            for (airport in airports.values) add(
                MetarRequest.MetarMapper(
                    airport.entity[MetarInfo.mapper]!!.realLifeIcao,
                    airport.entity[AirportInfo.mapper]!!.icaoCode
                )
            )
        })
        HttpRequest.sendMetarRequest(Moshi.Builder().build().adapter(MetarRequest::class.java).toJson(metarRequest), true)
    }
}