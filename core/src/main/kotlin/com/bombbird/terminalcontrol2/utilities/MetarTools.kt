package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_LIVE
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_RANDOM
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_STATIC
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.traffic.RunwayConfiguration
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.ashley.remove
import java.time.LocalTime
import kotlin.math.*

/** Helper functions and classes for dealing with METAR shenanigans */
private val airportMetarMapType = Types.newParameterizedType(Map::class.java, Byte::class.javaObjectType, MetarResponse::class.java)
private val airportMetarMoshiAdapter = Moshi.Builder().build().adapter<Map<Byte, MetarResponse>>(airportMetarMapType)

/** Requests METAR for all airports in the current gameServer instance */
fun requestAllMetar() {
    // No need to update for static weather
    val weatherMode = GAME.gameServer?.weatherMode ?: return
    if (weatherMode == WEATHER_STATIC) {
        if (GAME.gameServer?.initialisingWeather?.get() == true) initialiseAirportMetarStatic()
        return notifyGameServerWeatherLoaded()
    }

    // Check if is a new game that has not yet requested/generated METAR, or the minute of requesting is from 0-4 or 30-34
    val randomUpdateNeeded = GAME.gameServer?.initialisingWeather?.get() == true || (LocalTime.now().minute % 30 < 5)
    val randomWeatherAirportList = ArrayList<Entity>()
    val metarRequestList = ArrayList<HttpRequest.MetarRequest.MetarMapper>().apply {
        val arptEntries = Entries(GAME.gameServer?.airports ?: return)
        arptEntries.forEach { arptEntry ->
            val arpt = arptEntry.value
            val realIcao = arpt.entity[RealLifeMetarIcao.mapper]?.realLifeIcao ?: return@forEach
            val icao = arpt.entity[AirportInfo.mapper]?.arptId ?: return@forEach
            add(HttpRequest.MetarRequest.MetarMapper(realIcao, icao))
            if (randomUpdateNeeded) randomWeatherAirportList.add(arpt.entity)
        }
    }
    if (weatherMode == WEATHER_LIVE) {
        HttpRequest.sendMetarRequest(metarRequestList) {
            // Called on failure
            generateRandomWeather(true, randomWeatherAirportList)
        }
    } else if (weatherMode == WEATHER_RANDOM) generateRandomWeather(true, randomWeatherAirportList)
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

/** Initialises static airport METAR on game start */
fun initialiseAirportMetarStatic() {
    val airports = GAME.gameServer?.airports ?: return
    for (i in 0 until airports.size) {
        airports.getValueAt(i)?.entity?.let { arpt ->
            arpt[MetarInfo.mapper]?.apply {
                updateWindVector(windVectorPx, windHeadingDeg, windSpeedKt)
                updateRunwayWindComponents(arpt)
                calculateRunwayConfigScores(arpt)
                checkRunwayConfigSelection(arpt)
            }
        }
    }
}

/** Updates the in-game airports' METAR with the supplied [metarJson] string */
fun updateAirportMetar(metarJson: String) {
    airportMetarMoshiAdapter.fromJson(metarJson)?.apply {
        for (entry in entries) {
            entry.value.let { GAME.gameServer?.airports?.get(entry.key)?.entity?.also { arpt ->
                arpt[MetarInfo.mapper]?.apply {
                    if (rawMetar != it.rawMetar) letterCode = letterCode?.let {
                        if (it + 1 <= 'Z') it + 1 else 'A'
                    } ?: MathUtils.random(65, 90).toChar()
                    rawMetar = it.rawMetar ?: ""
                    windHeadingDeg = it.windHeadingDeg ?: 0
                    windSpeedKt = it.windSpeedKt ?: 0
                    windGustKt = it.windGustKt ?: 0
                    visibilityM = it.visibilityM ?: 10000
                    ceilingHundredFtAGL = it.ceilingFtAGL
                    windshear = it.windshear ?: ""
                    updateWindVector(windVectorPx, windHeadingDeg, windSpeedKt)
                    updateRunwayWindComponents(arpt)
                    calculateRunwayConfigScores(arpt)
                    checkRunwayConfigSelection(arpt)
                }
            }}
        }

        notifyGameServerWeatherLoaded()
    }
}

/**
 * Generates random weather for all airports
 *
 * If [basedOnCurrent] is true, will reduce the difference between the new weather direction and wind speed and that of
 * the current weather by 3/4, to prevent sudden wind direction or speed changes resulting in runway change
 * @param basedOnCurrent whether to take into account existing weather data when generating random weather
 * @param airports the list of airport entities to generate the random weather for
 */
fun generateRandomWeather(basedOnCurrent: Boolean, airports: List<Entity>) {
    val worldTemp = MathUtils.random(20, 35)
    val worldDewDelta = -MathUtils.random(2, 8)
    val worldQnh = MathUtils.random(1005, 1019)
    // basedOnCurrent must be true, and game must have already initialized weather to be able to use it to generate weather
    val checkedBaseOnCurrent = basedOnCurrent && GAME.gameServer?.initialisingWeather?.get() == false

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
        if (checkedBaseOnCurrent) {
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
        updateRunwayWindComponents(arpt)
        calculateRunwayConfigScores(arpt)
        checkRunwayConfigSelection(arpt)
        val temp = (worldTemp + MathUtils.random(-2, 2)).toByte()
        val dewPoint = (temp + worldDewDelta).toByte()
        val qnh = (worldQnh + (if (MathUtils.randomBoolean()) 0 else -1)).toShort()
        currMetar.rawMetar = generateRawMetar(currMetar.windHeadingDeg, currMetar.windSpeedKt, currMetar.windGustKt,
            currMetar.visibilityM, currMetar.ceilingHundredFtAGL, temp, dewPoint, qnh, currMetar.windshear)
    }

    notifyGameServerWeatherLoaded()
}

/**
 * Sets static weather for the airport; note that the client TCP update must be sent manually
 * @param arpt the airport entity to set the weather for
 * @param windHdg the heading, in degrees, of the wind
 * @param windSpd the speed, in knots, of the wind
 * @param visibility the visibility, in metres
 * @param ceilingHundredFt the ceiling, in hundreds of feet
 * @param worldTemp the world temperature, in Celsius
 * @param worldDewDelta the world dew point in comparison to [worldTemp], in Celsius
 * @param worldQnh the world QNH, in hectopascals
 */
fun setAirportStaticWeather(arpt: Entity, windHdg: Short, windSpd: Short, visibility: Short, ceilingHundredFt: Short?, worldTemp: Int, worldDewDelta: Int, worldQnh: Int) {
    arpt[MetarInfo.mapper]?.apply {
        windHeadingDeg = windHdg
        windSpeedKt = windSpd
        windGustKt = 0
        visibilityM = visibility
        ceilingHundredFtAGL = ceilingHundredFt
        windshear = ""
        val temp = (worldTemp + MathUtils.random(-2, 2)).toByte()
        val dewPoint = (temp + worldDewDelta).toByte()
        val qnh = (worldQnh + (if (MathUtils.randomBoolean()) 0 else -1)).toShort()
        val prevMetar = rawMetar
        rawMetar = generateRawMetar(windHeadingDeg, windSpeedKt, windGustKt, visibilityM, ceilingHundredFtAGL, temp, dewPoint, qnh, windshear)
        if (rawMetar != prevMetar) letterCode = letterCode?.let {
            if (it + 1 <= 'Z') it + 1 else 'A'
        } ?: MathUtils.random(65, 90).toChar()
        updateWindVector(windVectorPx, windHeadingDeg, windSpeedKt)
        updateRunwayWindComponents(arpt)
        calculateRunwayConfigScores(arpt)
        checkRunwayConfigSelection(arpt)
    }
}

/**
 * Generates random gust values based on wind speed, and some curve fitting of a large amount of data
 * @param windSpeedKts the current wind speed, in knots
 * @return the gust speed in knots, or null if no gust is generated
 */
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
    val rwyEntries = Entries(airport[RunwayChildren.mapper]?.rwyMap ?: return "")
    rwyEntries.mapNotNull { it.value.entity }.forEach { rwy ->
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
 */
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

/**
 * Updates the given wind vector with the new wind heading and speed
 * @param vec the wind vector to update (in pixels per second)
 * @param windDeg the heading, in degrees, of the wind
 * @param windSpdKt the speed, in knots, of the wind
 */
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

/**
 * Updates the tailwind, crosswind components of all the runways of the airport
 *
 * Call after the airport's wind velocity vector has changed
 * @param airport the airport entity to update
 */
fun updateRunwayWindComponents(airport: Entity) {
    val windVectorPxps = airport[MetarInfo.mapper]?.windVectorPx ?: return
    val rwyEntries = Entries(airport[RunwayChildren.mapper]?.rwyMap ?: return)
    rwyEntries.forEach { rwyEntry ->
        val rwy = rwyEntry.value
        val dir = rwy.entity[Direction.mapper] ?: return@forEach
        rwy.entity[RunwayWindComponents.mapper]?.apply {
            tailwindKt = pxpsToKt(windVectorPxps.dot(dir.trackUnitVector))
            crosswindKt = pxpsToKt(abs(windVectorPxps.crs(dir.trackUnitVector)))
        }
    }
}

/**
 * Updates the scores for all the runway configurations of the airport
 *
 * Call after the airport's wind velocity vector has changed, and the runway wind components have been updated
 * @param airport the airport entity to update
 */
fun calculateRunwayConfigScores(airport: Entity) {
    airport[RunwayConfigurationChildren.mapper]?.rwyConfigs?.apply {
        for (config in values()) { config.calculateScores() }
    }
}

/**
 * Checks the selected runway configuration of the airport, and updates it if needed; call this only on server-side, as
 * client-side is not in charge of doing so and will receive a request from the server instead to reflect the changes
 *
 * Call after the airport's runway configuration scores have been updated
 * @param airport the airport entity to update
 */
fun checkRunwayConfigSelection(airport: Entity) {
    val arptId = airport[AirportInfo.mapper]?.arptId ?: return
    val configs = airport[RunwayConfigurationChildren.mapper]?.rwyConfigs ?: return
    val activeConfig: RunwayConfiguration? = configs[airport[ActiveRunwayConfig.mapper]?.configId]
    val pendingConfig: RunwayConfiguration? = configs[airport[PendingRunwayConfig.mapper]?.pendingId]
    val rwyConfigEntries = Entries(airport[RunwayConfigurationChildren.mapper]?.rwyConfigs ?: return)
    val idealConfig = rwyConfigEntries.mapNotNull { it.value }.toTypedArray().sortedArray().last()
    if ((activeConfig == null || activeConfig.rwyAvailabilityScore == 0) && idealConfig.id != activeConfig?.id) {
        if (activeConfig == null) {
            // If no active current config, set the most ideal choice directly
            GAME.gameServer?.airports?.get(arptId)?.activateRunwayConfig(idealConfig.id)
            GAME.gameServer?.sendActiveRunwayUpdateToAll(arptId, idealConfig.id)
        } else {
            // If another alternative that is better than the current no-runway available config is present, set pending
            // runway selection to that
            airport += PendingRunwayConfig(idealConfig.id, 300f)
            GAME.gameServer?.sendPendingRunwayUpdateToAll(arptId, idealConfig.id)
        }
    } else if ((activeConfig.rwyAvailabilityScore > 0) && pendingConfig != null) {
        // Active config is ok for current configuration, but there is a pending runway change - cancel it
        airport.remove<PendingRunwayConfig>()
        GAME.gameServer?.sendPendingRunwayUpdateToAll(arptId, null)
    }
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

/** Notifies the gameServer that initial weather has been loaded */
private fun notifyGameServerWeatherLoaded() {
    GAME.gameServer?.apply {
        notifyWeatherLoaded()
        sendMetarTCPToAll()
    }
}