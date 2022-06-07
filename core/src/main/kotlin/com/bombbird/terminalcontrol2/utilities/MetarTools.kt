package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ktx.ashley.get
import ktx.ashley.has
import java.time.LocalTime
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Helper functions and classes for dealing with METAR shenanigans */

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
    val randomWeatherAirportList = ArrayList<Entity>()
    val randomUpdateNeeded = LocalTime.now().minute % 30 < 5 // || true // TODO check if is a new game that has not yet requested/generated METAR
    val metarRequest = MetarRequest(Secrets.GET_METAR_PW, ArrayList<MetarRequest.MetarMapper>().apply {
        (GAME.gameServer?.airports?.values() ?: return).forEach { arpt ->
            val realIcao = arpt.entity[MetarInfo.mapper]?.realLifeIcao ?: return@forEach
            val icao = arpt.entity[AirportInfo.mapper]?.arptId ?: return@forEach
            add(MetarRequest.MetarMapper(realIcao, icao))
            if (randomUpdateNeeded) randomWeatherAirportList.add(arpt.entity)
        }
    })
    HttpRequest.sendMetarRequest(Moshi.Builder().build().adapter(MetarRequest::class.java).toJson(metarRequest), true, randomWeatherAirportList)
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
                GAME.gameServer?.airports?.get(entry.key)?.entity?.get(MetarInfo.mapper)?.apply {
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
                    updateWindVector(windVectorPx, windHeadingDeg, windSpeedKt)
                }
            }
        }
        GAME.gameServer?.sendMetarTCPToAll()
    }
}

/**
 * Generates random weather for all airports
 *
 * If [basedOnCurrent] is true, will reduce the difference between the new weather direction and wind speed and that of
 * the current weather by 3/4, to prevent sudden wind direction or speed changes resulting in runway change
 * @param basedOnCurrent whether to take into account existing weather data when generating random weather
 * @param airports the list of airport entities to generate the random weather for
 * */
fun generateRandomWeather(basedOnCurrent: Boolean, airports: List<Entity>) {
    val worldTemp = MathUtils.random(20, 35)
    val worldDewDelta = -MathUtils.random(2, 8)
    val worldQnh = MathUtils.random(1005, 1019)

    for (arpt in airports) {
        val currMetar = arpt[MetarInfo.mapper] ?: continue
        val randomWeather = arpt[RandomMetarInfo.mapper] ?: continue
        val newWindDir = randomWeather.windDirDist.value() ?: 360
        val newWindSpd = randomWeather.windSpdDist.value() ?: 0

        val hundredFtDist = shortArrayOf(-1, 0, 1, 2, 5, 10, 20, 30, 50, 80, 120, 170, 230, 300, 380)
        val maxHundredFt = randomWeather.ceilingDist.value() ?: 320
        val index = hundredFtDist.indexOf(maxHundredFt)
        val newCeiling = if (maxHundredFt.toInt() == -1) null // -1 represents absence of ceiling
        else {
            val prevHundred = hundredFtDist[index - 1]
            // Check that prevHundred is at least 2 less than maxHundred to prevent exception in next line
            if (prevHundred + 1 == maxHundredFt.toInt()) maxHundredFt
            else MathUtils.random(prevHundred + 1, maxHundredFt.toInt()).toShort()
        }

        currMetar.visibilityM = randomWeather.visibilityDist.value() ?: 10000
        if (basedOnCurrent) {
            if (newWindDir.toInt() != 0) {
                // Change wind direction only if new wind direction is not variable
                val deltaWindDir = findDeltaHeading(currMetar.windHeadingDeg.toFloat(), newWindDir.toFloat(), CommandTarget.TURN_DEFAULT)
                val newDir = ((currMetar.windHeadingDeg + deltaWindDir / 4) / 10).roundToInt() * 10
                currMetar.windHeadingDeg = modulateHeading(newDir.toFloat()).roundToInt().toShort()
            }
            currMetar.windSpeedKt = ((currMetar.windSpeedKt * 3 + newWindSpd) / 4f).roundToInt().toShort()
            currMetar.ceilingHundredFtAGL = currMetar.ceilingHundredFtAGL.let {
                if (it == null) newCeiling // If no existing ceiling, set to new ceiling directly
                else {
                    // Cap change in ceiling at 2000 feet
                    val deltaCeiling = MathUtils.clamp((newCeiling ?: 999) - it, -20, 20).toShort()
                    (it + deltaCeiling).toShort()
                }
            }
        } else {
            currMetar.windHeadingDeg = newWindDir
            currMetar.windSpeedKt = newWindSpd
            currMetar.ceilingHundredFtAGL = newCeiling
        }

        // If wind is variable, cap wind speed at 7 knots
        if (currMetar.windHeadingDeg.toInt() == 0 && currMetar.windSpeedKt > 7) currMetar.windSpeedKt = 7

        currMetar.windGustKt = generateRandomGust(currMetar.windSpeedKt)
        generateRandomWsForAllRwy(arpt)

        currMetar.letterCode = currMetar.letterCode?.let {
            if (it + 1 <= 'Z') it + 1 else 'A'
        } ?: MathUtils.random(65, 90).toChar()

        updateWindVector(currMetar.windVectorPx, currMetar.windHeadingDeg, currMetar.windSpeedKt)
        val temp = (worldTemp + MathUtils.random(-2, 2)).toByte()
        val dewPoint = (temp + worldDewDelta).toByte()
        val qnh = (worldQnh + (if (MathUtils.randomBoolean()) 0 else -1)).toShort()
        currMetar.rawMetar = generateRawMetar(currMetar.windHeadingDeg, currMetar.windSpeedKt, currMetar.windGustKt,
            currMetar.visibilityM, currMetar.ceilingHundredFtAGL, temp, dewPoint, qnh, currMetar.windshear)
    }

    GAME.gameServer?.sendMetarTCPToAll()
}

/**
 * Generates random gust values based on wind speed, and some curve fitting of a large amount of data
 * @param windSpeedKts the current wind speed, in knots
 * @return the gust speed in knots, or null if no gust is generated
 * */
private fun generateRandomGust(windSpeedKts: Short): Short {
    // Magic constants from some curve fitting on Excel of all places
    val prob = 0.00000005 * windSpeedKts.toDouble().pow(4.8615)
    if (!MathUtils.randomBoolean(prob.toFloat())) return 0

    return max(15.0, 1.0476 * windSpeedKts + 9.6722).roundToInt().toShort()
}

/**
 * Generates random presence of windshear for all the input airport's landing runways
 * @param airport the airport entity to generate random windshear for
 */
private fun generateRandomWsForAllRwy(airport: Entity): String {
    val speed = airport[MetarInfo.mapper]?.windSpeedKt ?: return ""
    val logCoefficients = airport[RandomMetarInfo.mapper]?.windshearLogCoefficients ?: return ""
    val b0 = logCoefficients.first
    val b1 = logCoefficients.second
    val prob = (1 / (1 + exp(-b0 - b1 * speed.toDouble()))).toFloat()

    var landingRwyCount = 0
    val stringBuilder = StringBuilder()
    (airport[RunwayChildren.mapper]?.rwyMap?.values() ?: return "").mapNotNull { it?.entity }.forEach { rwy ->
        if (!rwy.has(ActiveLanding.mapper)) return@forEach
        landingRwyCount++
        if (MathUtils.randomBoolean(prob)) {
            stringBuilder.append("R")
            stringBuilder.append(rwy[RunwayInfo.mapper]?.rwyName)
            stringBuilder.append(" ")
        }
    }
    return if (stringBuilder.length > 3 && stringBuilder.length == landingRwyCount * 3)"ALL RWY" else stringBuilder.toString()
}

/**
 * Generates a basic raw metar based on the parameters generated by randomised weather
 * @param windDir the heading of the wind, in degrees
 * @param windSpd the wind speed, in knots
 * @param windGust the gust speed, in knots
 * @param visibility the visibility, in metres
 * @param ceilingHundredFt the ceiling above ground level, in hundreds of feet
 * @param temp the temperature, in celsius
 * @param dewPoint the dew point, in celsius
 * @param qnh the QNH, in hectopascals
 * @param windshear the windshear string
 * @return the generated raw METAR
 * */
private fun generateRawMetar(windDir: Short, windSpd: Short, windGust: Short, visibility: Short, ceilingHundredFt: Short?,
                             temp: Byte, dewPoint: Byte, qnh: Short, windshear: String): String {
    val sb = StringBuilder()
    val windDirStr = when {
        windDir.toInt() == 0 -> "VRB"
        windDir < 100 -> "0$windDir"
        else -> windDir.toString()
    }
    sb.append(windDirStr)
    sb.append(if (windSpd < 10) "0$windSpd" else windSpd)
    if (windGust > 0) sb.append("G${windGust}")
    sb.append("KT ")
    if (visibility >= 10000 && (ceilingHundredFt == null || ceilingHundredFt >= 50)) {
        // Use CAVOK instead of visibility and clouds
        sb.append("CAVOK ")
    } else {
        val visText = if (visibility >= 10000) "9999" else visibility.toString()
        sb.append("$visText ")
        val randomLowCloud = if (MathUtils.randomBoolean()) "FEW" else "SCT"
        val randomHighCloud = if (MathUtils.randomBoolean()) "BKN" else "OVC"
        val cloudText = if (ceilingHundredFt == null) {
            val randomLowAlt = MathUtils.random(20, 380)
            randomLowCloud + (if (randomLowAlt < 100) "0$randomLowAlt" else randomLowAlt)
        } else if (ceilingHundredFt < 4) "VV00$ceilingHundredFt"
        else {
            val randomLowAlt = MathUtils.random(ceilingHundredFt / 2, ceilingHundredFt - 1)
            randomLowCloud + (if (randomLowAlt < 10) "00$randomLowAlt" else if (randomLowAlt < 100) "0$randomLowAlt" else randomLowAlt) + " " +
            randomHighCloud + (if (ceilingHundredFt < 10) "00$ceilingHundredFt" else if (ceilingHundredFt < 100) "0$ceilingHundredFt" else ceilingHundredFt)
        }
        sb.append("$cloudText ")
    }
    sb.append("$temp/$dewPoint ")
    sb.append("Q$qnh ")
    sb.append(if (windshear.isBlank()) "NOSIG" else "WS $windshear")
    return sb.toString()
}

/** Updates the given [vec] with the new [windDeg] and [windSpdKt], each dimension in px */
fun updateWindVector(vec: Vector2, windDeg: Short, windSpdKt: Short) {
    if (windDeg == 0.toShort()) {
        vec.setZero()
        return
    }
    vec.y = 1f
    vec.x = 0f
    vec.rotateDeg(-(windDeg + 180 - MAG_HDG_DEV))
    vec.scl(ktToPxps(windSpdKt.toInt()))
}

/** Given the position ([x], [y]), find the closest airport and returns the wind vector of it (each dimension in px) */
fun getClosestAirportWindVector(x: Float, y: Float): Vector2 {
    var closest = -1f
    var vectorToUse = Vector2()
    GAME.gameServer?.airports?.values()?.toArray()?.apply {
        for (airport in this) {
            airport.entity.let {
                val pos = it[Position.mapper] ?: return@let
                val metar = it[MetarInfo.mapper] ?: return@let
                val deltaX = pos.x - x
                val deltaY = pos.y - y
                val radiusSq = deltaX * deltaX + deltaY * deltaY
                if (closest < 0 || radiusSq < closest) {
                    vectorToUse = metar.windVectorPx
                    closest = radiusSq
                }
            }
        }
    }

    return vectorToUse
}