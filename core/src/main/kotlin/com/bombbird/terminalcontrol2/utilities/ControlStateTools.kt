package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.*
import ktx.ashley.get
import ktx.scene2d.Scene2DSkin
import kotlin.math.min
import kotlin.math.roundToInt

/** Helper file containing functions for dealing with aircraft control states */

/** Gets the appropriate aircraft blip icon for its [flightType] and [sectorID] */
fun getAircraftIcon(flightType: Byte, sectorID: Byte): TextureRegionDrawable {
    return TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion(
        when (sectorID) {
            SectorInfo.CENTRE -> "aircraftEnroute"
            SectorInfo.TOWER -> "aircraftTower"
            else -> when (flightType) {
                FlightType.DEPARTURE -> "aircraftDeparture"
                FlightType.ARRIVAL -> "aircraftArrival"
                FlightType.EN_ROUTE -> "aircraftEnroute"
                else -> {
                    Gdx.app.log("ControlState", "Unknown flight type $flightType")
                    "aircraftEnroute"
                }
        }
    }))
}

/**
 * Gets the minimum, maximum an optimal IAS that can be cleared depending on aircraft performance and navigation status
 *
 * The optimal IAS is the default IAS the aircraft will fly at without player intervention, hence it needs to be returned
 * to be added to the UI pane speed selectBox
 * @return a [Triple] that contains 3 shorts, the first being the minimum IAS, the second being the maximum IAS, and
 * the third being the optimal IAS for the phase of flight
 * */
fun getMinMaxOptimalIAS(entity: Entity): Triple<Short, Short, Short> {
    val perf = entity[AircraftInfo.mapper]?.aircraftPerf ?: return Triple(150, 250, 240)
    val altitude = entity[Altitude.mapper] ?: return Triple(150, 250, 240)
    val takingOff = entity[TakeoffClimb.mapper] != null || entity[TakeoffRoll.mapper] != null
    val crossOverAlt = calculateCrossoverAltitude(perf.tripIas, perf.maxMach)
    val below10000ft = altitude.altitudeFt < 10000
    val between10000ftAndCrossover = altitude.altitudeFt >= 10000 && altitude.altitudeFt < crossOverAlt
    val aboveCrossover = altitude.altitudeFt >= crossOverAlt
    val minSpd: Short = when {
        takingOff -> perf.climbOutSpeed
        below10000ft -> (perf.climbOutSpeed * 1.1f).roundToInt().toShort()
        between10000ftAndCrossover -> (perf.tripIas - 35).toShort()
        aboveCrossover -> (calculateIASFromTAS(altitude.altitudeFt, calculateSpeedOfSoundAtAlt(altitude.altitudeFt)) - 15).roundToInt().toShort()
        else -> 160
    }
    val maxSpd: Short = when {
        below10000ft -> min(250, perf.maxIas.toInt()).toShort()
        between10000ftAndCrossover -> perf.maxIas
        aboveCrossover -> calculateIASFromMach(altitude.altitudeFt, perf.maxMach).roundToInt().toShort()
        else -> 250
    }
    val optimalSpd: Short = MathUtils.clamp(when {
        takingOff -> perf.climbOutSpeed
        below10000ft -> min(250, perf.maxIas.toInt()).toShort()
        between10000ftAndCrossover -> perf.tripIas
        aboveCrossover -> calculateIASFromMach(altitude.altitudeFt, perf.tripMach).roundToInt().toShort()
        else -> 240
    }, minSpd, maxSpd)
    return Triple(minSpd, maxSpd, optimalSpd)
}
