package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.MathTools
import com.bombbird.terminalcontrol2.utilities.PhysicsTools
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.remove
import kotlin.math.*

/** Main AI system, which handles aircraft flight controls, implementing behaviour for various basic and advanced flight modes
 *
 * Flight modes will directly alter [CommandTarget], which will then interact with PhysicsSystem to execute the required behaviour
 * */
class AISystem: EntitySystem() {

    /** Main update function */
    override fun update(deltaTime: Float) {
        updateTakeoffAcceleration()
        updateTakeoffClimb()
        updateCommandTarget()
    }

    /** Set the acceleration for takeoff aircraft */
    private fun updateTakeoffAcceleration() {
        val takeoffAccFamily = allOf(Acceleration::class, AircraftInfo::class, TakeoffRoll::class, Speed::class, Direction::class, AffectedByWind::class).get()
        val takeoffAcc = engine.getEntitiesFor(takeoffAccFamily)
        for (i in 0 until takeoffAcc.size()) {
            takeoffAcc[i]?.apply {
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper) ?: return@apply
                ias.iasKt = PhysicsTools.calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
                if (ias.iasKt >= aircraftInfo.aircraftPerf.vR + PhysicsTools.calculateIASFromTAS(alt.altitudeFt, MathTools.pxpsToKt(wind.windVectorPx.dot(dir.trackUnitVector)))) {
                    // Transition to takeoff climb mode
                    remove<TakeoffRoll>()
                    this += TakeoffClimb(alt.altitudeFt + MathUtils.random(1500, 2000))
                    // Transition to first leg on route if present, otherwise maintain runway heading
                    get(CommandRoute.mapper)?.route?.legs?.let {
                        if (it.size > 0) it.get(0).also { leg ->
                            this += when (leg) {
                                is Route.VectorLeg -> CommandVector(leg.heading)
                                is Route.InitClimbLeg -> CommandInitClimb(leg.heading, leg.minAltFt)
                                is Route.WaypointLeg -> CommandDirect(leg.wptId, leg.maxAltFt, leg.minAltFt, leg.maxSpdKt, leg.flyOver, leg.turnDir)
                                is Route.HoldLeg -> CommandHold(leg.wptId, leg.maxAltFt, leg.minAltFt, leg.maxSpdKt, leg.inboundHdg, leg.legDist, leg.turnDir)
                                else -> {
                                    Gdx.app.log("AISystem", "${leg::class} not allowed in departure")
                                    return@let
                                }
                            }
                        }
                    }
                    return@apply
                }
                val acc = get(Acceleration.mapper) ?: return@apply
                val takeoffRoll = get(TakeoffRoll.mapper) ?: return@apply
                acc.dSpeedMps2 = min(takeoffRoll.targetAccMps2, aircraftInfo.maxAcc)
            }
        }
    }

    /** Set initial takeoff climb, transition to acceleration for departing aircraft */
    private fun updateTakeoffClimb() {
        val takeoffClimbFamily = allOf(Altitude::class, CommandTarget::class, TakeoffClimb::class, CommandRoute::class).get()
        val takeoffClimb = engine.getEntitiesFor(takeoffClimbFamily)
        for (i in 0 until takeoffClimb.size()) {
            takeoffClimb[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val tkOff = get(TakeoffClimb.mapper) ?: return@apply
                val cmdRoute = get(CommandRoute.mapper) ?: return@apply
                if (alt.altitudeFt > tkOff.accelAltFt) {
                    // Climbed past acceleration altitude, set new target IAS and remove takeoff climb component
                    cmd.targetIasKt = cmdRoute.route.getMaxSpdAtCurrLegDep() ?: 250
                    cmd.targetAltFt = 10000f
                    remove<TakeoffClimb>()
                }
            }
        }
    }

    /** Update the [CommandTarget] parameters for aircraft */
    private fun updateCommandTarget() {
        // Update for vector leg
        val vectorFamily = allOf(CommandVector::class, CommandTarget::class).get()
        val vector = engine.getEntitiesFor(vectorFamily)
        for (i in 0 until vector.size()) {
            vector[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdVec = get(CommandVector.mapper) ?: return@apply
                cmdTarget.targetHdgDeg = cmdVec.heading.toFloat()
                remove<CommandVector>()
            }
        }

        // Update for initial climb leg
        val initClimbFamily = allOf(CommandInitClimb::class, CommandTarget::class, Altitude::class).get()
        val initClimb = engine.getEntitiesFor(initClimbFamily)
        for (i in 0 until initClimb.size()) {
            initClimb[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdInitClimb = get(CommandInitClimb.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                cmdTarget.targetHdgDeg = cmdInitClimb.heading.toFloat()
                if (alt.altitudeFt > cmdInitClimb.minAltFt) {
                    // Climbed past initial climb altitude, set to next leg
                    remove<CommandInitClimb>()
                    setToNextRouteLeg(this)
                }
            }
        }

        // Update for waypoint direct leg
        val waypointFamily = allOf(CommandDirect::class, CommandTarget::class, CommandRoute::class, Position::class, Speed::class, Direction::class, IndicatedAirSpeed::class).get()
        val waypoint = engine.getEntitiesFor(waypointFamily)
        for (i in 0 until waypoint.size()) {
            waypoint[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdDir = get(CommandDirect.mapper) ?: return@apply
                val cmdRoute = get(CommandRoute.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val wpt = com.bombbird.terminalcontrol2.global.Constants.GAME.gameServer?.waypoints?.get(cmdDir.wptId)?.entity?.get(Position.mapper) ?: run {
                    Gdx.app.log("AISystem", "Unknown command direct waypoint with ID ${cmdDir.wptId}")
                    return@apply
                }
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                // Calculate required track from aircraft position to waypoint
                var targetTrack = MathTools.getRequiredTrack(pos.x, pos.y, wpt.x, wpt.y).toDouble()
                var groundSpeed = spd.speedKts // When winds are not taken into account
                get(AffectedByWind.mapper)?.let { wind ->
                    // Calculate angle difference required due to wind component
                    val angle = 180.0 + MathTools.convertWorldAndRenderDeg(wind.windVectorPx.angleDeg()) - MathTools.convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg())
                    val windSpdKts = MathTools.pxpsToKt(wind.windVectorPx.len())
                    groundSpeed = sqrt(spd.speedKts.pow(2.0f) + windSpdKts.pow(2.0f) - 2 * spd.speedKts * windSpdKts * cos(Math.toRadians(angle))).toFloat()
                    targetTrack -= asin(windSpdKts * sin(Math.toRadians(angle)) / groundSpeed) * MathUtils.radiansToDegrees
                }
                // Set command target heading to target track + magnetic heading variation
                cmdTarget.targetHdgDeg = MathTools.modulateHeading(targetTrack.toFloat() + Variables.MAG_HDG_DEV)

                // Calculate distance between aircraft and waypoint and check if aircraft should move to next leg
                val deltaX = wpt.x - pos.x
                val deltaY = wpt.y - pos.y
                val nextWptLegTrack = cmdRoute.route.findNextWptLegTrackAndDirection()
                val requiredDist = if (cmdDir.flyOver || nextWptLegTrack == null) 3f
                else MathTools.findTurnDistance(MathTools.findDeltaHeading(targetTrack.toFloat(), nextWptLegTrack.first, nextWptLegTrack.second), if (ias.iasKt > 250) 1.5f else 3f, MathTools.ktToPxps(groundSpeed))
                if (requiredDist * requiredDist > deltaX * deltaX + deltaY * deltaY) {
                    remove<CommandDirect>()
                    setToNextRouteLeg(this)
                }
            }
        }
    }

    /** Removes the [entity]'s route's first leg, and adds the required component for the next leg */
    private fun setToNextRouteLeg(entity: Entity) {
        entity[CommandRoute.mapper]?.route?.legs?.apply {
            if (size > 0) removeIndex(0)
            while (size > 0) get(0)?.let {
                entity += when (it) {
                    is Route.VectorLeg -> CommandVector(it.heading)
                    is Route.InitClimbLeg -> CommandInitClimb(it.heading, it.minAltFt)
                    is Route.WaypointLeg -> CommandDirect(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKt, it.flyOver, it.turnDir)
                    is Route.HoldLeg -> CommandHold(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKt, it.inboundHdg, it.legDist, it.turnDir)
                    else -> {
                        removeIndex(0)
                        return@let
                    }
                }
                return@apply
            }
        }
    }
}