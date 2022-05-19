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
 *
 * Used only in GameServer
 * */
class AISystem: EntitySystem() {

    /** Main update function */
    override fun update(deltaTime: Float) {
        updateTakeoffAcceleration()
        updateTakeoffClimb()
        updateCommandTarget()
        update10000ftSpeed()
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
                if (ias.iasKt >= aircraftInfo.aircraftPerf.vR + calculateIASFromTAS(alt.altitudeFt, pxpsToKt(wind.windVectorPxps.dot(dir.trackUnitVector)))) {
                    // Transition to takeoff climb mode
                    remove<TakeoffRoll>()
                    this += TakeoffClimb(alt.altitudeFt + MathUtils.random(1200, 1800))
                    this += AccelerateToAbove250kts()
                    // Transition to first leg on route if present, otherwise maintain runway heading
                    setToFirstRouteLeg(this)
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
        val takeoffClimbFamily = allOf(Altitude::class, CommandTarget::class, TakeoffClimb::class, ClearanceAct::class, AircraftInfo::class).get() // TODO occasional indexOutOfBoundsException here
        val takeoffClimb = engine.getEntitiesFor(takeoffClimbFamily)
        for (i in 0 until takeoffClimb.size()) {
            takeoffClimb[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val tkOff = get(TakeoffClimb.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.actingClearance ?: return@apply
                val perf = get(AircraftInfo.mapper)?.aircraftPerf ?: return@apply
                if (alt.altitudeFt > tkOff.accelAltFt) {
                    // Climbed past acceleration altitude, set new target IAS and remove takeoff climb component
                    cmd.targetIasKt = min(clearanceAct.route.getNextMaxSpd()?.toInt() ?: 250, min(250, perf.maxIas.toInt())).toShort()
                    clearanceAct.clearedIas = cmd.targetIasKt
                    remove<TakeoffClimb>()
                    this += LatestClearanceChanged()
                }
            }
        }
    }

    /** Update cleared IAS changes at 10000 feet */
    private fun update10000ftSpeed() {
        // Update for aircraft going faster than 250 knots above 10000 feet
        val above250Family = allOf(AccelerateToAbove250kts::class, Altitude::class, CommandTarget::class, ClearanceAct::class).get()
        val above250 = engine.getEntitiesFor(above250Family)
        for (i in 0 until above250.size()) {
            above250[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper) ?: return@apply
                if (alt.altitudeFt > 10000) {
                    val spds = getMinMaxOptimalIAS(this)
                    cmd.targetIasKt = spds.third // If aircraft is still constrained by SIDs, it will automatically accelerate to optimal speed later
                    clearanceAct.actingClearance.actingClearance.let {
                        it.clearedIas = spds.third
                        it.optimalIas = spds.third
                        it.maxIas = spds.second
                        it.minIas = spds.first
                    }
                    remove<AccelerateToAbove250kts>()
                    this += LatestClearanceChanged()
                }
            }
        }

        // Update aircraft to slow down before reaching 10000 feet
        val below240Family = allOf(AircraftInfo::class, DecelerateTo240kts::class, Altitude::class, CommandTarget::class, ClearanceAct::class).get()
        val below240 = engine.getEntitiesFor(below240Family)
        for (i in 0 until below240.size()) {
            below240[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper) ?: return@apply
                if (alt.altitudeFt < 11000 && cmd.targetAltFt < 10000) {
                    if (cmd.targetIasKt > 240) {
                        cmd.targetIasKt = 240
                        clearanceAct.actingClearance.actingClearance.clearedIas = 240
                    }
                    remove<DecelerateTo240kts>()
                    this += LatestClearanceChanged()
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
                this += LatestClearanceChanged()
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
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.actingClearance ?: return@apply
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
        val holdFamily = allOf(CommandHold::class, CommandTarget::class, Position::class, Speed::class, Direction::class).get() // TODO occasional indexOutOfBoundsException here
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

        // Update when the acting clearance has been changed by player action
        val actingClearanceChangedFamily = allOf(CommandTarget::class, ClearanceActChanged::class, ClearanceAct::class).get()
        val actingClearances = engine.getEntitiesFor(actingClearanceChangedFamily)
        for (i in 0 until actingClearances.size()) {
            actingClearances[i]?.apply {
                setCommandTargetToNewActingClearance(this)
                remove<ClearanceActChanged>()
            }
        }
    }

    /**
     * Adds the required component for the first leg
     *
     * Also updates the most recent restrictions if any are present (if the first leg is a waypoint leg)
     * @param entity the aircraft entity to apply the changes to
     */
    private fun setToFirstRouteLeg(entity: Entity) {
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.actingClearance ?: return
        actingClearance.route.legs.apply {
            removeAllAdvancedCommandModes(entity)
            entity += LatestClearanceChanged()
            while (size > 0) get(0).let {
                entity += when (it) {
                    is Route.VectorLeg -> {
                        actingClearance.vectorHdg = it.heading
                        CommandVector(it.heading)
                    }
                    is Route.InitClimbLeg -> {
                        (entity[LastRestrictions.mapper] ?: LastRestrictions().also { restr -> entity += restr }).minAltFt = it.minAltFt
                        CommandInitClimb(it.heading, it.minAltFt)
                    }
                    is Route.WaypointLeg -> {
                        if (!it.legActive) {
                            removeIndex(0)
                            return@let
                        }
                        (entity[LastRestrictions.mapper] ?: LastRestrictions().also { restr -> entity += restr }).let { restr ->
                            it.minAltFt?.let { minAltFt -> restr.minAltFt = minAltFt }
                            it.maxAltFt?.let { maxAltFt -> restr.maxAltFt = maxAltFt }
                            it.maxSpdKt?.let { maxSpdKt -> restr.maxSpdKt = maxSpdKt }
                        }
                        updateWaypointLegRestr(entity)
                        CommandDirect(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKt, it.flyOver, it.turnDir)
                    }
                    is Route.HoldLeg -> CommandHold(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKtLower, it.inboundHdg, it.legDist, it.turnDir)
                    else -> {
                        Gdx.app.log("AISystem", "Unknown leg type ${it::class}")
                        removeIndex(0)
                        return@let
                    }
                }
                return@apply
            }
        }
    }

    /**
     * Removes the aircraft's acting route's first leg, and adds the required component for the next leg
     * @param entity the aircraft entity
     * */
    private fun setToNextRouteLeg(entity: Entity) {
        entity[ClearanceAct.mapper]?.actingClearance?.actingClearance?.route?.legs?.apply { if (size > 0) removeIndex(0) }
        setToFirstRouteLeg(entity)
    }

    /**
     * Updates the new command target the aircraft should fly with its current clearance state's altitude clearance and
     * the altitude, speed restrictions along its route
     *
     * Call this function after the aircraft's next waypoint leg has changed
     * @param entity the aircraft entity
     * */
    private fun updateWaypointLegRestr(entity: Entity) {
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.actingClearance ?: return
        val commandTarget = entity[CommandTarget.mapper] ?: return
        val flightType = entity[FlightType.mapper]
        val lastRestriction = entity[LastRestrictions.mapper] ?: LastRestrictions().apply { entity += this }
        val nextRouteMinAlt = actingClearance.route.getNextMinAlt()
        val minAlt = lastRestriction.minAltFt.let { lastMinAlt ->
            when {
                lastMinAlt != null && nextRouteMinAlt != null -> min(lastMinAlt, nextRouteMinAlt)
                lastMinAlt != null -> when (flightType?.type) {
                    FlightType.DEPARTURE -> lastMinAlt// No further min alts, use the last min alt
                    FlightType.ARRIVAL -> null // No further min alts, but aircraft is an arrival so allow descent below previous min alt
                    else -> null
                }
                nextRouteMinAlt != null -> when (flightType?.type) {
                    FlightType.DEPARTURE -> null// No min alts before
                    FlightType.ARRIVAL -> nextRouteMinAlt // No min alts before, but aircraft is an arrival so must follow all subsequent min alts
                    else -> null
                }
                else -> null
            }
        }
        lastRestriction.minAltFt = nextRouteMinAlt

        val nextRouteMaxAlt = actingClearance.route.getNextMaxAlt()
        var maxAlt = lastRestriction.maxAltFt.let { lastMaxAlt ->
            when {
                lastMaxAlt != null && nextRouteMaxAlt != null -> max(lastMaxAlt, nextRouteMaxAlt)
                lastMaxAlt != null -> when (flightType?.type) {
                    FlightType.DEPARTURE -> null// No further max alts, but aircraft is a departure so allow climb above previous min alt
                    FlightType.ARRIVAL -> lastMaxAlt// No further max alts, use the last min alt
                    else -> null
                }
                nextRouteMaxAlt != null -> when (flightType?.type) {
                    FlightType.DEPARTURE -> nextRouteMaxAlt// No max alts before, but aircraft is a departure so must follow all subsequent max alts
                    FlightType.ARRIVAL -> null// No max alts before
                    else -> null
                }
                else -> null
            }
        }

        if (minAlt != null && maxAlt != null && minAlt > maxAlt) {
            Gdx.app.log("AISystem", "minAlt ($minAlt) should not > maxAlt ($maxAlt)")
            maxAlt = minAlt
        }

        entity[Altitude.mapper]?.apply {
            var targetAlt = actingClearance.clearedAlt
            minAlt?.let { if (targetAlt < it && altitudeFt > it) targetAlt = it }
            maxAlt?.let { if (targetAlt > it && altitudeFt < it) targetAlt = it }

            commandTarget.targetAltFt = targetAlt // Update command target to the new calculated target altitude
        }
        val spds = getMinMaxOptimalIAS(entity)
        val maxSpd = spds.second
        val optimalSpd = spds.third
        // If the cleared IAS is currently at the maximum possible and the new max speed is higher than (or equal to, as in
        // the event of player clearing speed restriction the acting max speed is already set to maxSpd) it, update it to the new optimal speed
        if (actingClearance.clearedIas == actingClearance.maxIas && maxSpd >= actingClearance.maxIas) commandTarget.targetIasKt = optimalSpd
        else if (actingClearance.clearedIas > maxSpd) commandTarget.targetIasKt = maxSpd // If currently cleared IAS exceeds max speed restriction
        val prevMaxIas = actingClearance.maxIas
        val prevClearedIas = actingClearance.clearedIas
        actingClearance.maxIas = maxSpd
        actingClearance.clearedIas = commandTarget.targetIasKt
        if (prevMaxIas != actingClearance.maxIas || prevClearedIas != actingClearance.clearedIas) {
            val pendingClearances = entity[PendingClearances.mapper]
            if (pendingClearances == null || pendingClearances.clearanceQueue.isEmpty) entity += LatestClearanceChanged()
        }
    }

    /**
     * Updates the command target parameters with the latest acting clearance; should be called only when the acting
     * clearance has been changed due to player clearance
     * @param entity the aircraft entity to apply the changes to
     * */
    private fun setCommandTargetToNewActingClearance(entity: Entity) {
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.actingClearance ?: return
        val commandTarget = entity[CommandTarget.mapper] ?: return
        actingClearance.vectorHdg?.let { hdg ->
            // Cleared vector heading
            removeAllAdvancedCommandModes(entity)
            entity += CommandVector(hdg)
            actingClearance.route.legs.apply {
                while (size > 0) {
                    // Remove any vector legs at the beginning of the route
                    if (get(0) is Route.VectorLeg) removeIndex(0)
                    else break
                }
            }
        } ?: setToFirstRouteLeg(entity) // Not cleared vector, set to the first cleared leg instead

        actingClearance.vectorHdg?.let { hdg ->
            commandTarget.targetHdgDeg = hdg.toFloat()
            commandTarget.targetAltFt = actingClearance.clearedAlt
            commandTarget.targetIasKt = actingClearance.clearedIas
        }

        if (entity[PendingClearances.mapper] == null) entity += LatestClearanceChanged()
    }

    /**
     * Returns one of the 3 possible entry procedures for a holding pattern, given the holding pattern's inbound leg heading,
     * its turn direction, and the aircraft's target heading to the waypoint
     * @param targetHeading the heading the aircraft is currently targeting to fly
     * @param inboundHdg the inbound leg heading of the hold leg
     * @param legDir the turn direction of the hold leg
     * @return a byte denoting one of 3 possible holding entry procedures
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

    /**
     * Gets the appropriate turn direction given the [targetHeading], [currHeading] and the instructed [cmdTurnDir]
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

    /** Removes all persistent advanced command modes from the entity */
    private fun removeAllAdvancedCommandModes(entity: Entity) {
        entity.remove<CommandHold>()
        entity.remove<CommandDirect>()
        entity.remove<CommandInitClimb>()
    }
}