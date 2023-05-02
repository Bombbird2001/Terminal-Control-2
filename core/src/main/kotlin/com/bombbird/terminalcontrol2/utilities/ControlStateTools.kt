package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.HOLD_THRESHOLD_ALTITUDE
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.navigation.Route.*
import com.bombbird.terminalcontrol2.networking.dataclasses.AircraftControlStateUpdateData
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.ashley.remove
import ktx.scene2d.Scene2DSkin
import kotlin.math.max
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
                    Log.info("ControlState", "Unknown flight type $flightType")
                    "aircraftEnroute"
                }
            }
        }))
}

/**
 * Adds a new clearance sent by the client to the pending clearances for an aircraft
 * @param entity the aircraft entity to add the clearance to
 * @param clearance the [AircraftControlStateUpdateData] object that the clearance will be constructed from
 * @param returnTripTime the time taken for a ping to be sent and acknowledged, in ms, calculated by the server; half of
 * this value in seconds will be subtracted from the delay time (2s) to account for any ping time lag
 * */
fun addNewClearanceToPendingClearances(entity: Entity, clearance: AircraftControlStateUpdateData, returnTripTime: Int) {
    val pendingClearances = entity[PendingClearances.mapper]
    val newClearance = ClearanceState(clearance.primaryName, Route.fromSerialisedObject(clearance.route), Route.fromSerialisedObject(clearance.hiddenLegs),
        clearance.vectorHdg, clearance.vectorTurnDir, clearance.clearedAlt, clearance.expedite,
        clearance.clearedIas, clearance.minIas, clearance.maxIas, clearance.optimalIas,
        clearance.clearedApp, clearance.clearedTrans)
    if (pendingClearances == null) entity += PendingClearances(Queue<ClearanceState.PendingClearanceState>().apply {
        addLast(ClearanceState.PendingClearanceState(2f - returnTripTime / 2000f, newClearance))
    })
    else pendingClearances.clearanceQueue.apply {
        val lastTime = last().timeLeft
        addLast(ClearanceState.PendingClearanceState(max(2f - returnTripTime / 2000f - lastTime, 0.01f), newClearance))
    }
}

/**
 * Gets the latest clearance state for the aircraft, including pending clearances that are not acting yet
 * @param entity the aircraft entity to get the clearance state for
 * @return the latest clearance state, or null if none found
 */
fun getLatestClearanceState(entity: Entity): ClearanceState? {
    val pending = entity[PendingClearances.mapper]
    if (pending != null && pending.clearanceQueue.size > 0) return pending.clearanceQueue.last().clearanceState
    return entity[ClearanceAct.mapper]?.actingClearance?.clearanceState
}

/**
 * Compares input clearance states and checks if they are the same; routes must be strictly equal and route name,
 * cleared altitude and cleared IAS must be equal as well
 * @param clearanceState1 the first clearance state to compare
 * @param clearanceState2 the second clearance state to compare
 * @param checkVector whether to check for vector clearance differences
 * @return a boolean denoting whether the two clearance states are equal
 * */
fun checkClearanceEquality(clearanceState1: ClearanceState, clearanceState2: ClearanceState, checkVector: Boolean): Boolean {
    if (!checkRouteEqualityStrict(clearanceState1.route, clearanceState2.route)) return false
    if (!checkRouteEqualityStrict(clearanceState1.hiddenLegs, clearanceState2.hiddenLegs)) return false
    return clearanceState1.routePrimaryName == clearanceState2.routePrimaryName &&
            ((clearanceState1.vectorHdg == clearanceState2.vectorHdg &&
            clearanceState1.vectorTurnDir == clearanceState2.vectorTurnDir) || !checkVector) &&
            clearanceState1.clearedAlt == clearanceState2.clearedAlt &&
            clearanceState1.expedite == clearanceState2.expedite &&
            clearanceState1.clearedIas == clearanceState2.clearedIas &&
            clearanceState1.clearedApp == clearanceState2.clearedApp &&
            clearanceState1.clearedTrans == clearanceState2.clearedTrans
}

/**
 * Gets the minimum, maximum an optimal IAS that can be cleared depending on aircraft performance and navigation status
 *
 * The optimal IAS is the default IAS the aircraft will fly at without player intervention, hence it needs to be returned
 * to be added to the UI pane speed selectBox
 *
 * This also takes into account any current or upcoming speed restrictions from SIDs/STARs
 * @param entity the aircraft entity to calculate the speed values for
 * @return a [Triple] that contains 3 shorts, the first being the minimum IAS, the second being the maximum IAS, and
 * the third being the optimal IAS for the phase of flight
 * */
fun getMinMaxOptimalIAS(entity: Entity): Triple<Short, Short, Short> {
    val perf = entity[AircraftInfo.mapper]?.aircraftPerf ?: return Triple(150, 250, 240)
    val altitude = entity[Altitude.mapper] ?: return Triple(150, 250, 240)
    val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return Triple(150, 250, 240)
    val flightType = entity[FlightType.mapper] ?: return Triple(150, 250, 240)
    val onApproach = entity.has(LocalizerCaptured.mapper) || entity.has(VisualCaptured.mapper) || entity.has(GlideSlopeCaptured.mapper)
    val takingOff = entity[TakeoffClimb.mapper] != null || entity[TakeoffRoll.mapper] != null
    val lastRestriction = entity[LastRestrictions.mapper]
    val crossOverAlt = calculateCrossoverAltitude(perf.tripIas, perf.tripMach)
    val below10000ft = altitude.altitudeFt < 10000
    val between10000ftAndCrossover = altitude.altitudeFt >= 10000 && altitude.altitudeFt < crossOverAlt
    val aboveCrossover = altitude.altitudeFt >= crossOverAlt
    val minSpd: Short = when {
        onApproach -> perf.appSpd
        takingOff || below10000ft -> perf.climbOutSpeed
        between10000ftAndCrossover || aboveCrossover -> (((altitude.altitudeFt - 10000) / (perf.maxAlt - 10000)) * (perf.climbOutSpeed * 2f / 9) + perf.climbOutSpeed * 10f / 9).roundToInt().toShort()
        else -> 160
    }
    // Aircraft enforced speeds - max 250 knots below 10000 ft, max IAS between 10000 ft and crossover, max mach above crossover
    val maxAircraftSpd: Short = when {
        below10000ft -> min(250, perf.maxIas.toInt()).toShort()
        between10000ftAndCrossover -> perf.maxIas
        aboveCrossover -> calculateIASFromMach(altitude.altitudeFt, perf.maxMach).roundToInt().toShort()
        else -> 250
    }
    val nextRouteMaxSpd = getNextMaxSpd(actingClearance.route)
    // SID/STAR enforced max speeds
    val maxSpd = if (actingClearance.vectorHdg != null) null // If being cleared on vectors, no speed restrictions
    else {
        var holdMaxSpd: Short? = null
        var holding = false
        // Check if aircraft is holding; if so use the max speed for the holding leg
        actingClearance.route.let {
            if (it.size > 0) holdMaxSpd = (it[0] as? HoldLeg)?.let { holdLeg ->
                holding = true
                if (altitude.altitudeFt > HOLD_THRESHOLD_ALTITUDE) holdLeg.maxSpdKtHigher else holdLeg.maxSpdKtLower
            }
        }
        // If aircraft is not holding, get restrictions for the SID/STAR
        if (!holding) holdMaxSpd = lastRestriction?.maxSpdKt.let { lastMaxSpd ->
            when {
                lastMaxSpd != null && nextRouteMaxSpd != null -> max(lastMaxSpd.toInt(), nextRouteMaxSpd.toInt()).toShort()
                lastMaxSpd != null -> when (flightType.type) {
                    FlightType.DEPARTURE -> null// No further max speeds, but aircraft is a departure so allow acceleration beyond previous max speed
                    FlightType.ARRIVAL -> lastMaxSpd// No further max speeds, use the last max speed
                    else -> null
                }
                nextRouteMaxSpd != null -> when (flightType.type) {
                    FlightType.DEPARTURE -> nextRouteMaxSpd// No max speeds before, but aircraft is a departure so must follow all subsequent max speeds
                    FlightType.ARRIVAL -> null// No max speeds before
                    else -> null
                }
                else -> null
            }
        }
        holdMaxSpd
    }
    val optimalSpd: Short = MathUtils.clamp(when {
        takingOff -> perf.climbOutSpeed
        below10000ft -> min(250, perf.maxIas.toInt()).toShort()
        between10000ftAndCrossover -> perf.tripIas
        aboveCrossover -> calculateIASFromMach(altitude.altitudeFt, perf.tripMach).roundToInt().toShort()
        else -> 240
    }, minSpd, maxAircraftSpd)
    return Triple(minSpd, min(maxAircraftSpd.toInt(), (maxSpd ?: maxAircraftSpd).toInt()).toShort(), min(optimalSpd.toInt(), (maxSpd ?: optimalSpd).toInt()).toShort())
}

/**
 * Calculates the arrival aircraft's IAS that it should be set to fly at on aircraft creation
 * @param origStarRoute the original route of the STAR
 * @param aircraftRoute the current aircraft route
 * @param spawnAlt the altitude at which the aircraft will spawn at
 * @return the IAS the aircraft should spawn at
 * */
fun calculateArrivalSpawnIAS(origStarRoute: Route, aircraftRoute: Route, spawnAlt: Float, aircraftPerf: AircraftTypeData.AircraftPerfData): Short {
    var maxSpd: Short? = null
    // Try to find an existing speed restriction
    if (aircraftRoute.size > 0) { (aircraftRoute[0] as? WaypointLeg)?.apply {
        for (i in 0 until origStarRoute.size) { origStarRoute[i].let { wpt ->
            if (compareLegEquality(this, wpt)) return@apply // Stop searching for max speed once current direct is reached
            val currMaxSpd = maxSpd
            maxSpdKt?.let { wptMaxSpd -> if (currMaxSpd == null || wptMaxSpd < currMaxSpd) maxSpd = maxSpdKt }
        }}
    }}

    val crossOverAlt = calculateCrossoverAltitude(aircraftPerf.tripIas, aircraftPerf.tripMach)
    return min((maxSpd ?: aircraftPerf.maxIas).toInt(), (if (spawnAlt > crossOverAlt) max(250, calculateIASFromMach(spawnAlt, aircraftPerf.tripMach).roundToInt())
    else aircraftPerf.tripIas).toInt()).toShort()
}

/**
 * Removes all approach components from the aircraft
 * @param aircraft the aircraft entity
 */
fun removeAllApproachComponents(aircraft: Entity) {
    aircraft.apply {
        remove<GlideSlopeArmed>()
        remove<GlideSlopeCaptured>()
        remove<LocalizerArmed>()
        remove<LocalizerCaptured>()
        remove<StepDownApproach>()
        remove<VisualCaptured>()
        remove<CirclingApproach>()
        remove<AppDecelerateTo190kts>()
        remove<DecelerateToAppSpd>()
        remove<ContactToTower>()
    }
}

/**
 * Gets the appropriate sector the aircraft is in
 * @param posX the x coordinate of the aircraft position
 * @param posY the y coordinate of the aircraft position
 * @param useServerSectors whether to use the sector data from the server or the client
 * @return the sector ID of the sector the aircraft is in, or null if none found
 */
fun getSectorForPosition(posX: Float, posY: Float, useServerSectors: Boolean): Byte? {
    val allSectors = if (useServerSectors) GAME.gameServer?.let { it.sectors[it.playerNo.get().toByte()] } ?: return null
    else CLIENT_SCREEN?.sectors ?: return null
    for (j in 0 until allSectors.size) allSectors[j]?.let { sector ->
        if (sector.entity[GPolygon.mapper]?.polygonObj?.contains(posX, posY) == true) {
            return sector.entity[SectorInfo.mapper]?.sectorId ?: return@let
        }
    }

    return null
}

/**
 * Gets the appropriate sector for the extrapolated position based on current position and ground track, given the time
 * to extrapolate
 * @param posX the x coordinate of the aircraft position
 * @param posY the y coordinate of the aircraft position
 * @param track the ground track vector, in pixels per second in each dimension
 * @param extrapolateTime the time, in seconds, to extrapolate the current aircraft position
 * @param useServerSectors whether to use the sector data from the server or the client
 */
fun getSectorForExtrapolatedPosition(posX: Float, posY: Float, track: Vector2, extrapolateTime: Float, useServerSectors: Boolean): Byte? {
    val newX = posX + track.x * extrapolateTime
    val newY = posY + track.y * extrapolateTime
    return getSectorForPosition(newX, newY, useServerSectors)
}
