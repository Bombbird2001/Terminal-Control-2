package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

    /** Requests METAR for all airports in the current gameServer instance */
    fun requestAllMetar() {
        val metarRequest = MetarRequest(Secrets.GET_METAR_PW, ArrayList<MetarRequest.MetarMapper>().apply {
            for (airport in Constants.GAME.gameServer?.airports?.values ?: return) add(
                MetarRequest.MetarMapper(
                    airport.entity[MetarInfo.mapper]!!.realLifeIcao,
                    airport.entity[AirportInfo.mapper]!!.icaoCode
                )
            )
        })
        HttpRequest.sendMetarRequest(Moshi.Builder().build().adapter(MetarRequest::class.java).toJson(metarRequest), true)
    }

    /** Helper class that specifies the JSON format of METAR responses from the server */
    @JsonClass(generateAdapter = true)
    class MetarResponse(
        var realLifeIcao: String,
        var rawMetar: String?,
        var windHeadingDeg: Short?,
        var windSpeedKt: Short?,
        var windGustKt: Short?,
        var visibilityM: Short?,
        var ceilingFtAGL: Short?,
        var windshear: String?
    )

    /** Updates the in-game airports' METAR with the supplied [metarJson] string */
    fun updateAirportMetar(metarJson: String) {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, MetarResponse::class.java)
        Moshi.Builder().build().adapter<Map<String, MetarResponse>>(type).fromJson(metarJson)?.apply {
            for (entry in entries) {
                entry.value.let {
                Constants.GAME.gameServer?.airports?.get(entry.key)?.entity?.get(MetarInfo.mapper)?.apply {
                        if (rawMetar != it.rawMetar && !(rawMetar == "" && it.rawMetar == null)) letterCode = letterCode?.let {
                            if (it + 1 <= 'Z') it + 1 else 'A'
                        } ?: MathUtils.random(65, 90).toChar()
                        realLifeIcao = it.realLifeIcao
                        rawMetar = it.rawMetar ?: ""
                        windHeadingDeg = it.windHeadingDeg ?: 360
                        windSpeedKt = it.windSpeedKt ?: 0
                        windGustKt = it.windGustKt ?: 0
                        visibilityM = it.visibilityM ?: 10000
                        ceilingHundredFtAGL = it.ceilingFtAGL
                        windshear = it.windshear ?: ""
                    }
                }
            }
            Constants.GAME.gameServer?.sendMetarTCPToAll()
        }
    }

    /** Generates random weather for all airports */
    fun generateRandomWeather() {
        // TODO generate random weather
    }
}