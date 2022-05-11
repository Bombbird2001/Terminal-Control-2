package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.*
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
                ias.iasKt = calculateIASFromTAS(alt.altitudeFt, spd.speedKts)
                if (ias.iasKt >= aircraftInfo.aircraftPerf.vR + calculateIASFromTAS(alt.altitudeFt, pxpsToKt(wind.windVectorPx.dot(dir.trackUnitVector)))) {
                    // Transition to takeoff climb mode
                    remove<TakeoffRoll>()
                    this += TakeoffClimb(alt.altitudeFt + MathUtils.random(1200, 1800))
                    // Transition to first leg on route if present, otherwise maintain runway heading
                    get(ClearanceAct.mapper)?.clearance?.route?.legs?.let {
                        if (it.size > 0) it.get(0).also { leg ->
                            this += when (leg) {
                                is Route.VectorLeg -> CommandVector(leg.heading)
                                is Route.InitClimbLeg -> CommandInitClimb(leg.heading, leg.minAltFt)
                                is Route.WaypointLeg -> CommandDirect(leg.wptId, leg.maxAltFt, leg.minAltFt, leg.maxSpdKt, leg.flyOver, leg.turnDir)
                                is Route.HoldLeg -> CommandHold(leg.wptId, leg.maxAltFt, leg.minAltFt, leg.maxSpdKtLower, leg.inboundHdg, leg.legDist, leg.turnDir)
                                else -> {
                                    Gdx.app.log("AISystem", "${leg::class} not allowed in departure")
                                    return@let
                                }
                            }
                            this += ClearanceChanged()
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
        val takeoffClimbFamily = allOf(Altitude::class, CommandTarget::class, TakeoffClimb::class, ClearanceAct::class).get()
        val takeoffClimb = engine.getEntitiesFor(takeoffClimbFamily)
        for (i in 0 until takeoffClimb.size()) {
            takeoffClimb[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val tkOff = get(TakeoffClimb.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper)?.clearance ?: return@apply
                if (alt.altitudeFt > tkOff.accelAltFt) {
                    // Climbed past acceleration altitude, set new target IAS and remove takeoff climb component
                    cmd.targetIasKt = clearanceAct.route.getMaxSpdAtCurrLegDep() ?: 250
                    cmd.targetAltFt = 10000f // TODO dynamically update based on aircraft route state
                    clearanceAct.clearedAlt = 10000
                    clearanceAct.clearedIas = cmd.targetIasKt
                    remove<TakeoffClimb>()
                    this += ClearanceChanged()
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
                this += ClearanceChanged()
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
        val waypointFamily = allOf(CommandDirect::class, CommandTarget::class, ClearanceAct::class, Position::class, Speed::class, Direction::class, IndicatedAirSpeed::class).get()
        val waypoint = engine.getEntitiesFor(waypointFamily)
        for (i in 0 until waypoint.size()) {
            waypoint[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdDir = get(CommandDirect.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper)?.clearance ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val wpt = GAME.gameServer?.waypoints?.get(cmdDir.wptId)?.entity?.get(Position.mapper) ?: run {
                    Gdx.app.log("AISystem", "Unknown command direct waypoint with ID ${cmdDir.wptId}")
                    return@apply
                }
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                // Calculate required track from aircraft position to waypoint
                val trackAndGS = getPointTargetTrackAndGS(pos.x, pos.y, wpt.x, wpt.y, spd.speedKts, dir, get(AffectedByWind.mapper))
                val targetTrack = trackAndGS.first
                val groundSpeed = trackAndGS.second
                // Set command target heading to target track + magnetic heading variation
                cmdTarget.targetHdgDeg = modulateHeading(targetTrack + MAG_HDG_DEV)

                // Calculate distance between aircraft and waypoint and check if aircraft should move to next leg
                val deltaX = wpt.x - pos.x
                val deltaY = wpt.y - pos.y
                val nextWptLegTrack = clearanceAct.route.findNextWptLegTrackAndDirection()
                val requiredDist = if (cmdDir.flyOver || nextWptLegTrack == null) 3f
                else findTurnDistance(findDeltaHeading(targetTrack, nextWptLegTrack.first, nextWptLegTrack.second), if (ias.iasKt > 250) 1.5f else 3f, ktToPxps(groundSpeed))
                if (requiredDist * requiredDist > deltaX * deltaX + deltaY * deltaY) {
                    remove<CommandDirect>()
                    setToNextRouteLeg(this)
                }
            }
        }

        // Update for holding leg
        val holdFamily = allOf(CommandHold::class, CommandTarget::class, Position::class, Speed::class, Direction::class).get()
        val hold = engine.getEntitiesFor(holdFamily)
        for (i in 0 until hold.size()) {
            hold[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdHold = get(CommandHold.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val holdWpt = GAME.gameServer?.waypoints?.get(cmdHold.wptId)?.entity?.get(Position.mapper) ?: return@apply
                val deltaX = holdWpt.x - pos.x
                val deltaY = holdWpt.y - pos.y
                val distPxFromWpt2 = deltaX * deltaX + deltaY * deltaY
                if (cmdHold.currentEntryProc == 0.byte) {
                    // Entry procedure has not been determined, calculate it
                    cmdHold.currentEntryProc = getEntryProc(cmdTarget.targetHdgDeg, cmdHold.inboundHdg, cmdHold.legDir)
                } else if (!cmdHold.entryDone) {
                    // Entry procedure has been determined, but entry is not complete
                        when (cmdHold.currentEntryProc) {
                        1.byte -> {
                            if (!cmdHold.oppositeTravelled) {
                                // Fly opposite to inbound leg
                                cmdTarget.targetHdgDeg = modulateHeading(cmdHold.inboundHdg + 180f)
                                val distToTurnPx = nmToPx(cmdHold.legDist - 1)
                                if (distPxFromWpt2 > distToTurnPx * distToTurnPx) cmdHold.oppositeTravelled = true // Opposite leg has been travelled
                            } else {
                                // Fly direct to waypoint
                                cmdTarget.targetHdgDeg = getPointTargetTrackAndGS(pos.x, pos.y, holdWpt.x, holdWpt.y, spd.speedKts, dir, get(AffectedByWind.mapper)).first + MAG_HDG_DEV
                                if (distPxFromWpt2 < 3 * 3) {
                                    // Waypoint reached, entry complete, fly outbound leg
                                    cmdHold.oppositeTravelled = false
                                    cmdHold.entryDone = true
                                    cmdHold.flyOutbound = true
                                }
                            }
                            // Turn direction is opposite to the hold direction
                            val reqTurnDir = (-cmdHold.legDir).toByte()
                            cmdTarget.turnDir = getAppropriateTurnDir(cmdTarget.targetHdgDeg, convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()), reqTurnDir)
                        }
                        2.byte -> {
                            // Fly 30 degrees off from the opposite of inbound leg, towards the outbound leg
                            cmdTarget.targetHdgDeg = modulateHeading(cmdHold.inboundHdg + cmdHold.legDir * 150f) // +150 degrees for right, -150 degrees for left
                            val distToTurnPx = nmToPx(cmdHold.legDist.toInt())
                            if (distPxFromWpt2 > distToTurnPx * distToTurnPx) {
                                // Entry complete, fly inbound leg
                                cmdHold.entryDone = true
                                cmdHold.flyOutbound = false
                            }
                        }
                        3.byte -> {
                            // Direct entry, fly outbound leg
                            cmdHold.entryDone = true
                            cmdHold.flyOutbound = true
                        }
                    }
                } else {
                    // Entry is complete, fly the inbound or outbound legs
                    if (cmdHold.flyOutbound) {
                        // Fly opposite heading of inbound leg
                        cmdTarget.targetHdgDeg = modulateHeading(cmdHold.inboundHdg + 180f)
                        cmdTarget.turnDir = getAppropriateTurnDir(cmdTarget.targetHdgDeg, convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()), cmdHold.legDir)
                        val distToTurnPx = nmToPx(cmdHold.legDist.toInt())
                        if (distPxFromWpt2 > distToTurnPx * distToTurnPx) cmdHold.flyOutbound = false // Outbound leg complete, fly inbound leg
                    } else {
                        // Fly an extended inbound track to the waypoint
                        val legTrackRad = Math.toRadians(convertWorldAndRenderDeg(cmdHold.inboundHdg.toFloat() - MAG_HDG_DEV) + 180.0)
                        val targetDistPx = sqrt(distPxFromWpt2) - nmToPx(0.75f)
                        val targetX = holdWpt.x + cos(legTrackRad) * targetDistPx
                        val targetY = holdWpt.y + sin(legTrackRad) * targetDistPx
                        cmdTarget.targetHdgDeg = getPointTargetTrackAndGS(pos.x, pos.y, targetX.toFloat(), targetY.toFloat(), spd.speedKts, dir, get(AffectedByWind.mapper)).first + MAG_HDG_DEV
                        cmdTarget.turnDir = CommandTarget.TURN_DEFAULT
                        if (distPxFromWpt2 < 3 * 3) cmdHold.flyOutbound = true // Waypoint reached, fly outbound leg
                    }
                }
            }
        }
    }

    /** Returns a pair of floats, which contains the track that the plane needs to fly as well as its ground speed (accounted for [wind] if any)
     *
     * [x1], [y1] is the present position and [x2], [y2] is the target destination
     *
     * [speedKts] is the true airspeed of the aircraft
     *
     * [dir] is the [Direction] component of the aircraft
     * */
    private fun getPointTargetTrackAndGS(x1: Float, y1: Float, x2: Float, y2: Float, speedKts: Float, dir: Direction, wind: AffectedByWind?): Pair<Float, Float> {
        var targetTrack = getRequiredTrack(x1, y1, x2, y2).toDouble()
        var groundSpeed = speedKts
        if (wind != null) {
            // Calculate angle difference required due to wind component
            val angle = 180.0 - convertWorldAndRenderDeg(wind.windVectorPx.angleDeg()) + convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg())
            val windSpdKts = pxpsToKt(wind.windVectorPx.len())
            groundSpeed = sqrt(speedKts.pow(2.0f) + windSpdKts.pow(2.0f) - 2 * speedKts * windSpdKts * cos(Math.toRadians(angle))).toFloat()
            val angleOffset = asin(windSpdKts * sin(Math.toRadians(angle)) / groundSpeed) * MathUtils.radiansToDegrees
            targetTrack -= angleOffset
        }
        return Pair(targetTrack.toFloat(), groundSpeed)
    }

    /** Removes the [entity]'s route's first leg, and adds the required component for the next leg */
    private fun setToNextRouteLeg(entity: Entity) {
        entity[ClearanceAct.mapper]?.clearance?.route?.legs?.apply {
            if (size > 0) removeIndex(0)
            entity += ClearanceChanged()
            while (size > 0) get(0)?.let {
                entity += when (it) {
                    is Route.VectorLeg -> {
                        entity[ClearanceAct.mapper]?.clearance?.vectorHdg = it.heading
                        CommandVector(it.heading)
                    }
                    is Route.InitClimbLeg -> CommandInitClimb(it.heading, it.minAltFt)
                    is Route.WaypointLeg -> CommandDirect(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKt, it.flyOver, it.turnDir)
                    is Route.HoldLeg -> CommandHold(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKtLower, it.inboundHdg, it.legDist, it.turnDir)
                    else -> {
                        removeIndex(0)
                        return@let
                    }
                }
                return@apply
            }
        }
    }

    /** Returns one of the 3 possible entry procedures for a holding pattern, with the holding pattern's [inboundHdg],
     * [legDir] and the aircraft's [targetHeading] to the waypoint
     * */
    private fun getEntryProc(targetHeading: Float, inboundHdg: Short, legDir: Byte): Byte {
        // Offset is relative to opposite of inbound heading
        var offset = targetHeading - inboundHdg + 180
        if (offset < -180) {
            offset += 360f
        } else if (offset > 180) {
            offset -= 360f
        }
        return when (legDir) {
            CommandTarget.TURN_RIGHT -> if (offset > -1 && offset < 129) 1 else if (offset < -1 && offset > -69) 2 else 3
            CommandTarget.TURN_LEFT -> if (offset < 1 && offset > -129) 1 else if (offset > 1 && offset < 69) 2 else 3
            else -> {
                Gdx.app.log("AISystem", "Invalid turn direction $legDir specified for holding pattern")
                if (offset > -1 && offset < 129) 1 else if (offset < -1 && offset > -69) 2 else 3
            }
        }
    }

    /** Gets the appropriate turn direction given the [targetHeading], [currHeading] and the instructed [cmdTurnDir]
     *
     * This is to ensure that after the aircraft turns though the commanded turn direction, it does not perform another
     * 360 degree loop after reaching the [targetHeading] by allowing a window of 3 degrees where the aircraft should
     * return to the default turn direction behaviour
     * */
    private fun getAppropriateTurnDir(targetHeading: Float, currHeading: Float, cmdTurnDir: Byte): Byte {
        // Maintain the turn direction until magnitude of deltaHeading is less than 3 degrees
        return if (withinRange(findDeltaHeading(currHeading,
                targetHeading - MAG_HDG_DEV, CommandTarget.TURN_DEFAULT), -3f, 3f)) CommandTarget.TURN_DEFAULT
        else cmdTurnDir
    }
}