package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.global.Variables
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
            val arptId: Byte
        )
    }

    /** Requests METAR for all airports in the current gameServer instance */
    fun requestAllMetar() {
        val metarRequest = MetarRequest(Secrets.GET_METAR_PW, ArrayList<MetarRequest.MetarMapper>().apply {
            for (airport in Constants.GAME.gameServer?.airports?.values() ?: return) {
                val realIcao = airport.entity[MetarInfo.mapper]?.realLifeIcao ?: continue
                val icao = airport.entity[AirportInfo.mapper]?.arptId ?: continue
                add(MetarRequest.MetarMapper(realIcao, icao))
            }
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
        val type = Types.newParameterizedType(Map::class.java, Byte::class.javaObjectType, MetarResponse::class.java)
        Moshi.Builder().build().adapter<Map<Byte, MetarResponse>>(type).fromJson(metarJson)?.apply {
            for (entry in entries) {
                entry.value.let {
                Constants.GAME.gameServer?.airports?.get(entry.key)?.entity?.get(MetarInfo.mapper)?.apply {
                        if (rawMetar != it.rawMetar && !(rawMetar == "" && it.rawMetar == null)) letterCode = letterCode?.let {
                            if (it + 1 <= 'Z') it + 1 else 'A'
                        } ?: MathUtils.random(65, 90).toChar()
                        realLifeIcao = it.realLifeIcao
                        rawMetar = it.rawMetar ?: ""
                        windHeadingDeg = it.windHeadingDeg ?: 0
                        windSpeedKt = it.windSpeedKt ?: 0
                        windGustKt = it.windGustKt ?: 0
                        visibilityM = it.visibilityM ?: 10000
                        ceilingHundredFtAGL = it.ceilingFtAGL
                        windshear = it.windshear ?: ""
                        updateWindVector(windVector, windHeadingDeg, windSpeedKt)
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

    /** Updates the given [vec] with the new [windDeg] and [windSpdKt], each dimension in px */
    fun updateWindVector(vec: Vector2, windDeg: Short, windSpdKt: Short) {
        if (windDeg == 0.toShort()) {
            vec.setZero()
            return
        }
        vec.y = 1f
        vec.x = 0f
        vec.rotateDeg(-(windDeg + 180 - Variables.MAG_HDG_DEV))
        vec.scl(MathTools.ktToPxps(windSpdKt.toInt()))
    }

    /** Given the position ([x], [y]), find the closest airport and returns the wind vector of it (each dimension in px) */
    fun getClosestAirportWindVector(x: Float, y: Float): Vector2 {
        var closest = -1f
        var vectorToUse = Vector2()
        Constants.GAME.gameServer?.airports?.values()?.apply {
            for (airport in this) {
                airport.entity.let {
                    val pos = it[Position.mapper] ?: return@let
                    val metar = it[MetarInfo.mapper] ?: return@let
                    val deltaX = pos.x - x
                    val deltaY = pos.y - y
                    val radiusSq = deltaX * deltaX + deltaY * deltaY
                    if (closest < 0 || radiusSq < closest) {
                        vectorToUse = metar.windVector
                        closest = radiusSq
                    }
                }
            }
        }

        return vectorToUse
    }
}