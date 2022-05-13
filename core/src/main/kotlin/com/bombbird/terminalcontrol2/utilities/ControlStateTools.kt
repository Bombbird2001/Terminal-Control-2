package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.networking.SerialisationRegistering
import ktx.ashley.get
import ktx.ashley.plusAssign
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
                    Gdx.app.log("ControlState", "Unknown flight type $flightType")
                    "aircraftEnroute"
                }
            }
        }))
}

/**
 * Adds a new clearance sent by the client to the pending clearances for an aircraft
 * @param entity the aircraft entity to add the clearance to
 * @param clearance the [SerialisationRegistering.AircraftControlStateUpdateData] object that the clearance will be constructed from
 * @param returnTripTime the time taken for a ping to be sent and acknowledged, in ms, calculated by the server; half of
 * this value in seconds will be subtracted from the delay time (2s) to account for any ping time lag
 * */
fun addNewClearanceToPendingClearances(entity: Entity, clearance: SerialisationRegistering.AircraftControlStateUpdateData, returnTripTime: Int) {
    val pendingClearances = entity[PendingClearances.mapper]
    val newClearance = ClearanceState(clearance.primaryName, Route.fromSerialisedObject(clearance.route), Route.fromSerialisedObject(clearance.hiddenLegs),
        clearance.vectorHdg, clearance.clearedAlt, clearance.clearedIas, clearance.minIas, clearance.maxIas, clearance.optimalIas)
    if (pendingClearances == null) entity += PendingClearances(Queue<ClearanceState.PendingClearanceState>().apply {
        addLast(ClearanceState.PendingClearanceState(2f - returnTripTime / 2000f, newClearance))
    })
    else pendingClearances.clearanceQueue.apply {
        val lastTime = last().timeLeft
        addLast(ClearanceState.PendingClearanceState(max(2f - returnTripTime / 2000f - lastTime, 0.01f), newClearance))
    }
}

/**
 * Checks whether the provided legs are equal - leg class and all of their properties must be the same except for
 * skipped legs or cancelled restrictions (for waypoint legs)
 * @param leg1 the first leg to compare
 * @param leg2 the second leg to compare
 * @return a boolean denoting whether the two input legs are the same
 * */
fun compareLegEquality(leg1: Route.Leg, leg2: Route.Leg): Boolean {
    if (leg1.phase != leg2.phase) return false
    return when {
        leg1 is Route.WaypointLeg && leg2 is Route.WaypointLeg ->
            leg1.wptId == leg2.wptId && leg1.minAltFt == leg2.minAltFt && leg1.maxAltFt == leg2.maxAltFt &&
                    leg1.maxSpdKt == leg2.maxSpdKt && leg1.flyOver == leg2.flyOver && leg1.turnDir == leg2.turnDir
        leg1 is Route.HoldLeg && leg2 is Route.HoldLeg ->
            leg1.wptId == leg2.wptId && leg1.maxAltFt == leg2.maxAltFt && leg1.minAltFt == leg2.minAltFt &&
                    leg1.maxSpdKtLower == leg2.maxSpdKtLower && leg1.maxSpdKtHigher == leg2.maxSpdKtHigher &&
                    leg1.inboundHdg == leg2.inboundHdg && leg1.legDist == leg2.legDist && leg1.turnDir == leg2.turnDir
        leg1 is Route.VectorLeg && leg2 is Route.VectorLeg -> leg1.heading == leg2.heading
        leg1 is Route.InitClimbLeg && leg2 is Route.InitClimbLeg -> leg1.heading == leg2.heading && leg1.minAltFt == leg2.minAltFt
        leg1 is Route.DiscontinuityLeg && leg2 is Route.DiscontinuityLeg -> true
        else -> false
    }
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
        takingOff || below10000ft -> perf.climbOutSpeed
        between10000ftAndCrossover || aboveCrossover -> (((altitude.altitudeFt - 10000) / (perf.maxAlt - 10000)) * (perf.climbOutSpeed * 2f / 9) + perf.climbOutSpeed * 10f / 9).roundToInt().toShort()
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
