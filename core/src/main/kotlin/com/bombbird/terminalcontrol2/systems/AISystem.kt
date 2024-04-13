package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.traffic.despawnAircraft
import com.bombbird.terminalcontrol2.utilities.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.*
import ktx.math.plusAssign
import ktx.math.times
import kotlin.math.*

/**
 * Main AI system, which handles aircraft flight controls, implementing behaviour for various basic and advanced flight modes
 *
 * Flight modes will directly alter [CommandTarget], which will then interact with PhysicsSystem to execute the required behaviour
 *
 * Used only in GameServer
 */
class AISystem: EntitySystem() {
    companion object {
        private val takeoffAccFamily: Family = allOf(Acceleration::class, Altitude::class, AircraftInfo::class,
            TakeoffRoll::class, Speed::class, IndicatedAirSpeed::class, AffectedByWind::class, Direction::class)
            .exclude(WaitingTakeoff::class).get()
        private val takeoffClimbFamily: Family = allOf(Altitude::class, CommandTarget::class, TakeoffClimb::class, ClearanceAct::class, AircraftInfo::class).get()
        private val landingAccFamily: Family = allOf(Acceleration::class, LandingRoll::class, GroundTrack::class).get()
        private val above250Family: Family = allOf(AccelerateToAbove250kts::class, Altitude::class, CommandTarget::class, ClearanceAct::class).get()
        private val below240Family: Family = allOf(AircraftInfo::class, DecelerateTo240kts::class, Altitude::class, CommandTarget::class, ClearanceAct::class).get()
        private val app190Family: Family = allOf(Position::class, AppDecelerateTo190kts::class, CommandTarget::class, ClearanceAct::class)
            .oneOf(VisualCaptured::class, LocalizerCaptured::class, GlideSlopeCaptured::class).get()
        private val minAppSpdFamily: Family = allOf(Position::class, DecelerateToAppSpd::class, CommandTarget::class, ClearanceAct::class, AircraftInfo::class)
            .oneOf(VisualCaptured::class, LocalizerCaptured::class, GlideSlopeCaptured::class).exclude(CirclingApproach::class).get()
        private val initialArrivalFamily: Family = allOf(ClearanceAct::class, InitialArrivalSpawn::class).get()
        private val vectorFamily: Family = allOf(CommandVector::class, CommandTarget::class).get()
        private val initClimbFamily: Family = allOf(CommandInitClimb::class, CommandTarget::class, Altitude::class).get()
        private val waypointFamily: Family = allOf(CommandDirect::class, CommandTarget::class, ClearanceAct::class,
            Position::class, Speed::class, Direction::class, GroundTrack::class, IndicatedAirSpeed::class).get()
        private val holdFamily: Family = allOf(CommandHold::class, CommandTarget::class, Position::class, Speed::class, Direction::class).get()
        private val pureVectorFamily: Family = allOf(CommandTarget::class, Direction::class, ClearanceAct::class)
            .exclude(CommandInitClimb::class, CommandDirect::class, CommandHold::class, CommandVector::class).get()
        private val appTrackCapFamily: Family = allOf(CommandTarget::class, ClearanceAct::class, Position::class, Speed::class, Direction::class)
            .oneOf(LocalizerCaptured::class, VisualCaptured::class).get()
        private val visAppGlideFamily: Family = allOf(CommandTarget::class, Position::class, GroundTrack::class, VisualCaptured::class).get()
        private val checkGoAroundFamily: Family = allOf(Position::class, Altitude::class, IndicatedAirSpeed::class, AircraftInfo::class)
            .oneOf(GlideSlopeCaptured::class, LocalizerCaptured::class, VisualCaptured::class, CirclingApproach::class).get()
        private val visArmedFamily: Family = allOf(Position::class, ClearanceAct::class, VisualArmed::class).get()
        private val locArmedFamily: Family = allOf(Position::class, Direction::class, IndicatedAirSpeed::class, GroundTrack::class, LocalizerArmed::class).get()
        private val gsArmedFamily: Family = allOf(Position::class, Altitude::class, GlideSlopeArmed::class, LocalizerCaptured::class).get()
        private val stepDownAppFamily: Family = allOf(CommandTarget::class, Position::class, LocalizerCaptured::class, StepDownApproach::class).get()
        private val circlingAppFamily: Family = allOf(CommandTarget::class, Position::class, Altitude::class, Direction::class, CirclingApproach::class, AircraftInfo::class).get()
        private val checkTouchdownFamily: Family = allOf(Altitude::class, Speed::class, Acceleration::class, Direction::class)
            .oneOf(VisualCaptured::class, GlideSlopeCaptured::class).get()
        private val actingClearanceChangedFamily: Family = allOf(CommandTarget::class, ClearanceActChanged::class, ClearanceAct::class).get()
        private val expediteFamily: Family = allOf(CommandExpedite::class, CommandTarget::class, Altitude::class, ClearanceAct::class).get()
        private val pendingEmergencyFamily: Family = allOf(EmergencyPending::class, Altitude::class, AircraftInfo::class, FlightType::class, DepartureAirport::class).get()
        private val runningChecklistFamily: Family = allOf(RunningChecklists::class, AircraftInfo::class).get()
        private val dumpingFuelFamily: Family = allOf(RequiresFuelDump::class, AircraftInfo::class).get()
        private val stayOnRunwayFamily: Family = allOf(ImmobilizeOnLanding::class, LandingRoll::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val takeoffAccFamilyEntities = FamilyWithListener.newServerFamilyWithListener(takeoffAccFamily)
    private val takeoffClimbFamilyEntities = FamilyWithListener.newServerFamilyWithListener(takeoffClimbFamily)
    private val landingAccFamilyEntities = FamilyWithListener.newServerFamilyWithListener(landingAccFamily)
    private val above250FamilyEntities = FamilyWithListener.newServerFamilyWithListener(above250Family)
    private val below240FamilyEntities = FamilyWithListener.newServerFamilyWithListener(below240Family)
    private val app190FamilyEntities = FamilyWithListener.newServerFamilyWithListener(app190Family)
    private val minAppSpdFamilyEntities = FamilyWithListener.newServerFamilyWithListener(minAppSpdFamily)
    private val initialArrivalFamilyEntities = FamilyWithListener.newServerFamilyWithListener(initialArrivalFamily)
    private val vectorFamilyEntities = FamilyWithListener.newServerFamilyWithListener(vectorFamily)
    private val initClimbFamilyEntities = FamilyWithListener.newServerFamilyWithListener(initClimbFamily)
    private val waypointFamilyEntities = FamilyWithListener.newServerFamilyWithListener(waypointFamily)
    private val holdFamilyEntities = FamilyWithListener.newServerFamilyWithListener(holdFamily)
    private val pureVectorFamilyEntities = FamilyWithListener.newServerFamilyWithListener(pureVectorFamily)
    private val appTrackCapFamilyEntities = FamilyWithListener.newServerFamilyWithListener(appTrackCapFamily)
    private val visAppGlideFamilyEntities = FamilyWithListener.newServerFamilyWithListener(visAppGlideFamily)
    private val checkGoAroundFamilyEntities = FamilyWithListener.newServerFamilyWithListener(checkGoAroundFamily)
    private val visArmedFamilyEntities = FamilyWithListener.newServerFamilyWithListener(visArmedFamily)
    private val locArmedFamilyEntities = FamilyWithListener.newServerFamilyWithListener(locArmedFamily)
    private val gsArmedFamilyEntities = FamilyWithListener.newServerFamilyWithListener(gsArmedFamily)
    private val stepDownAppFamilyEntities = FamilyWithListener.newServerFamilyWithListener(stepDownAppFamily)
    private val circlingAppFamilyEntities = FamilyWithListener.newServerFamilyWithListener(circlingAppFamily)
    private val checkTouchdownFamilyEntities = FamilyWithListener.newServerFamilyWithListener(checkTouchdownFamily)
    private val actingClearanceChangedFamilyEntities = FamilyWithListener.newServerFamilyWithListener(actingClearanceChangedFamily)
    private val expediteFamilyEntities = FamilyWithListener.newServerFamilyWithListener(expediteFamily)
    private val pendingEmergencyFamilyEntities = FamilyWithListener.newServerFamilyWithListener(pendingEmergencyFamily)
    private val runningChecklistFamilyEntities = FamilyWithListener.newServerFamilyWithListener(runningChecklistFamily)
    private val dumpingFuelFamilyEntities = FamilyWithListener.newServerFamilyWithListener(dumpingFuelFamily)
    private val stayOnRunwayFamilyEntities = FamilyWithListener.newServerFamilyWithListener(stayOnRunwayFamily)

    /** Main update function */
    override fun update(deltaTime: Float) {
        updateTakeoffAcceleration()
        updateTakeoffClimb()
        updateLandingAcceleration()
        updateCommandTarget()
        updateApproaches(deltaTime)
        update10000ftSpeed()
        updateInitialArrival()
    }

    /** Set the acceleration for takeoff aircraft */
    private fun updateTakeoffAcceleration() {
        val takeoffAcc = takeoffAccFamilyEntities.getEntities()
        for (i in 0 until takeoffAcc.size()) {
            takeoffAcc[i]?.apply {
                val takeoffRoll = get(TakeoffRoll.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val tailwindPxps = get(AffectedByWind.mapper)?.windVectorPxps?.dot(dir.trackUnitVector) ?: 0f
                ias.iasKt = calculateIASFromTAS(alt.altitudeFt, spd.speedKts - pxpsToKt(tailwindPxps))
                if (ias.iasKt >= aircraftInfo.aircraftPerf.vR) {
                    // Transition to takeoff climb mode
                    remove<TakeoffRoll>()
                    // Remove runway occupied status
                    takeoffRoll.rwy.remove<RunwayOccupied>()
                    takeoffRoll.rwy[OppositeRunway.mapper]?.oppRwy?.remove<RunwayOccupied>()
                    val randomAGL = MathUtils.random(1200, 1800)
                    val accelAlt = alt.altitudeFt + randomAGL
                    this += TakeoffClimb(accelAlt)
                    this += AccelerateToAbove250kts()
                    this += DivergentDepartureAllowed()
                    // Transition to first leg on route if present, otherwise maintain runway heading
                    setToFirstRouteLeg(this)
                    return@apply
                }
                val acc = get(Acceleration.mapper) ?: return@apply
                acc.dSpeedMps2 = min(takeoffRoll.targetAccMps2, aircraftInfo.maxAcc)
            }
        }
    }

    /** Set initial takeoff climb, transition to acceleration for departing aircraft */
    private fun updateTakeoffClimb() {
        val takeoffClimb = takeoffClimbFamilyEntities.getEntities()
        for (i in 0 until takeoffClimb.size()) {
            takeoffClimb[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val tkOff = get(TakeoffClimb.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return@apply
                val perf = get(AircraftInfo.mapper)?.aircraftPerf ?: return@apply
                if (alt.altitudeFt > tkOff.accelAltFt) {
                    // Climbed past acceleration altitude, set new target IAS and remove takeoff climb component
                    cmd.targetIasKt = min(getNextMaxSpd(clearanceAct.route)?.toInt() ?: 250, min(250, perf.tripIas.toInt())).toShort()
                    clearanceAct.clearedIas = cmd.targetIasKt
                    remove<TakeoffClimb>()
                    this += LatestClearanceChanged()
                }
            }
        }
    }

    /** Set the acceleration for landing aircraft */
    private fun updateLandingAcceleration() {
        val landingAcc = landingAccFamilyEntities.getEntities()
        for (i in 0 until landingAcc.size()) {
            landingAcc[i]?.apply {
                val acc = get(Acceleration.mapper) ?: return@apply
                val gsKt = pxpsToKt(get(GroundTrack.mapper)?.trackVectorPxps?.len() ?: return@apply)
                acc.dSpeedMps2 = if (gsKt > 60) -1.5f else -1f
                if (gsKt < 35 && hasNot(ImmobilizeOnLanding.mapper)) {
                    GAME.gameServer?.let {
                        get(LandingRoll.mapper)?.rwy?.let { landingRwy ->
                            landingRwy[RunwayNextArrival.mapper]?.also { nextArr ->
                                // If the closest aircraft is this aircraft, remove it
                                val nextArrCallsign = nextArr.aircraft[AircraftInfo.mapper]?.icaoCallsign ?: return@also
                                if (nextArrCallsign == get(AircraftInfo.mapper)?.icaoCallsign) landingRwy.remove<RunwayNextArrival>()
                            }
                            // Remove runway occupied status
                            landingRwy.remove<RunwayOccupied>()
                            landingRwy[OppositeRunway.mapper]?.oppRwy?.remove<RunwayOccupied>()
                        }

                        if (it.trafficMode == TrafficMode.NORMAL) {
                            var points = 0.6f - it.trafficValue / 30
                            points = MathUtils.clamp(points, 0.15f, 0.5f)
                            it.trafficValue = MathUtils.clamp(it.trafficValue + points, 4f, MAX_ARRIVALS.toFloat())
                        }
                        it.incrementScoreBy(1, FlightType.ARRIVAL)
                        get(ArrivalAirport.mapper)?.arptId?.also { landingArptId ->
                            val depInfo = GAME.gameServer?.airports?.get(landingArptId)?.entity?.get(DepartureInfo.mapper) ?: return@also
                            depInfo.backlog++
                        }
                    }

                    despawnAircraft(this)
                }

                // For emergencies that will remain on runway
                if (gsKt < 1) {
                    get(Speed.mapper)?.speedKts = 0f
                    acc.dSpeedMps2 = 0f
                }
            }
        }
    }

    /** Update cleared IAS changes at 10000 feet */
    private fun update10000ftSpeed() {
        // Update for aircraft going faster than 250 knots above 10000 feet
        val above250 = above250FamilyEntities.getEntities()
        for (i in 0 until above250.size()) {
            above250[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper) ?: return@apply
                if (alt.altitudeFt > 10000) {
                    val spds = getMinMaxOptimalIAS(this)
                    cmd.targetIasKt = spds.third // If aircraft is still constrained by SIDs, it will automatically accelerate to optimal speed later
                    clearanceAct.actingClearance.clearanceState.let {
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
        val below240 = below240FamilyEntities.getEntities()
        for (i in 0 until below240.size()) {
            below240[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper) ?: return@apply
                if (alt.altitudeFt < 11000 && cmd.targetAltFt <= 10000) {
                    if (cmd.targetIasKt > 240) {
                        cmd.targetIasKt = 240
                        clearanceAct.actingClearance.clearanceState.clearedIas = 240
                    }
                    remove<DecelerateTo240kts>()
                    this += LatestClearanceChanged()
                }
            }
        }

        // Update aircraft to slow down to 190 knots at less than 16nm from runway threshold
        val app190 = app190FamilyEntities.getEntities()
        for (i in 0 until app190.size()) {
            app190[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper) ?: return@apply
                val appEntity = get(GlideSlopeCaptured.mapper)?.gsApp ?: get(LocalizerCaptured.mapper)?.locApp ?: get(VisualCaptured.mapper)?.visApp ?: return@apply
                val rwyThrPos = appEntity[ApproachInfo.mapper]?.rwyObj?.entity?.get(CustomPosition.mapper) ?: return@apply
                val distNm = pxToNm(calculateDistanceBetweenPoints(pos.x, pos.y, rwyThrPos.x, rwyThrPos.y))
                if (distNm < 16) {
                    if (cmd.targetIasKt > 190) {
                        cmd.targetIasKt = 190
                        clearanceAct.actingClearance.clearanceState.clearedIas = 190
                    }
                    remove<AppDecelerateTo190kts>()
                    this += LatestClearanceChanged()
                }
            }
        }

        // Update aircraft to slow down to minimum approach speed at less than 6.4nm from runway threshold, except if
        // aircraft is on circling approach
        val minApp = minAppSpdFamilyEntities.getEntities()
        for (i in 0 until minApp.size()) {
            minApp[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper) ?: return@apply
                val appEntity = get(GlideSlopeCaptured.mapper)?.gsApp ?: get(LocalizerCaptured.mapper)?.locApp ?: get(VisualCaptured.mapper)?.visApp ?: return@apply
                val rwyThrPos = appEntity[ApproachInfo.mapper]?.rwyObj?.entity?.get(CustomPosition.mapper) ?: return@apply
                val minAppSpd = get(AircraftInfo.mapper)?.aircraftPerf?.appSpd ?: return@apply
                val distNm = pxToNm(calculateDistanceBetweenPoints(pos.x, pos.y, rwyThrPos.x, rwyThrPos.y))
                if (distNm < 6.4) {
                    if (cmd.targetIasKt > minAppSpd) {
                        cmd.targetIasKt = minAppSpd
                        clearanceAct.actingClearance.clearanceState.clearedIas = minAppSpd
                    }
                    remove<DecelerateToAppSpd>()
                    this += LatestClearanceChanged()
                }
            }
        }
    }

    /** Updates parameters for initial arrival aircraft spawns */
    private fun updateInitialArrival() {
        val initialArrivals = initialArrivalFamilyEntities.getEntities()
        for (i in 0 until initialArrivals.size()) {
            initialArrivals[i]?.apply {
                setToFirstRouteLeg(this)
                remove<InitialArrivalSpawn>()
            }
        }
    }

    /** Update the [CommandTarget] parameters for aircraft based on the different modes, excluding approaches */
    private fun updateCommandTarget() {
        // Update for vector leg
        val vector = vectorFamilyEntities.getEntities()
        for (i in 0 until vector.size()) {
            vector[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdVec = get(CommandVector.mapper) ?: return@apply
                cmdTarget.targetHdgDeg = cmdVec.heading.toFloat()
                cmdTarget.turnDir = cmdVec.turnDir
                remove<CommandVector>()
                this += LatestClearanceChanged()
            }
        }

        // Update for initial climb leg
        val initClimb = initClimbFamilyEntities.getEntities()
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
        val waypoint = waypointFamilyEntities.getEntities()
        for (i in 0 until waypoint.size()) {
            waypoint[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdDir = get(CommandDirect.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val wpt = getServerWaypointMap()?.get(cmdDir.wptId)?.entity?.get(Position.mapper) ?: run {
                    FileLog.info("AISystem", "Unknown command direct waypoint with ID ${cmdDir.wptId}")
                    return@apply
                }
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val groundTrack = get(GroundTrack.mapper) ?: return@apply
                // Calculate required track from aircraft position to waypoint
                val trackAndGS = getPointTargetTrackAndGS(pos.x, pos.y, wpt.x, wpt.y, spd.speedKts, dir, get(AffectedByWind.mapper))
                val targetTrack = trackAndGS.first
                val groundSpeed = trackAndGS.second
                // Set command target heading to target track + magnetic heading variation
                cmdTarget.targetHdgDeg = modulateHeading(targetTrack + MAG_HDG_DEV)
                cmdTarget.turnDir = getAppropriateTurnDir(cmdTarget.targetHdgDeg, convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV, cmdDir.turnDir)

                // Calculate distance between aircraft and waypoint and check if aircraft should move to next leg
                val deltaX = wpt.x - pos.x
                val deltaY = wpt.y - pos.y
                val nextWptLegTrack = findNextWptLegTrackAndDirection(clearanceAct.route) ?: run {
                    // If aircraft is departure, and the next leg is a vector leg and the last leg in the route, turn early as well
                    if (get(FlightType.mapper)?.type == FlightType.DEPARTURE && clearanceAct.route.size == 2)
                            (clearanceAct.route[1] as? Route.VectorLeg)?.let { Pair(it.heading.toFloat(), it.turnDir) }
                    else null
                }
                val requiredDist = if (cmdDir.flyOver || nextWptLegTrack == null) 3f
                else findTurnDistance(findDeltaHeading(targetTrack, nextWptLegTrack.first, nextWptLegTrack.second),
                    if (ias.iasKt > HALF_TURN_RATE_THRESHOLD_IAS) MAX_HIGH_SPD_ANGULAR_SPD else MAX_LOW_SPD_ANGULAR_SPD, ktToPxps(groundSpeed))
                // If aircraft is within the required distance, or aircraft is within 1nm of the waypoint but is travelling away from it
                if (requiredDist * requiredDist > deltaX * deltaX + deltaY * deltaY ||
                    (deltaX * deltaX + deltaY * deltaY < 625 && groundTrack.trackVectorPxps.dot(Vector2(deltaX, deltaY)) < 0)) {
                    remove<CommandDirect>()
                    setToNextRouteLeg(this)
                }
            }
        }

        // Update for holding leg
        val hold = holdFamilyEntities.getEntities()
        for (i in 0 until hold.size()) {
            hold[i]?.apply {
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                val cmdHold = get(CommandHold.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val holdWpt = getServerWaypointMap()?.get(cmdHold.wptId)?.entity?.get(Position.mapper) ?: return@apply
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
                            cmdTarget.turnDir = getAppropriateTurnDir(cmdTarget.targetHdgDeg,
                                convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV, reqTurnDir)
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
                        cmdTarget.turnDir = getAppropriateTurnDir(cmdTarget.targetHdgDeg,
                            convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV, cmdHold.legDir)
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

        // Update for pure vector (i.e. no legs)
        val pureVector = pureVectorFamilyEntities.getEntities()
        for (i in 0 until pureVector.size()) {
            pureVector[i]?.apply {
                val cmd = get(CommandTarget.mapper) ?: return@apply
                if (cmd.turnDir == CommandTarget.TURN_DEFAULT) return@apply // Return if turn direction not specified
                val dir = get(Direction.mapper) ?: return@apply
                val actingClearance = get(ClearanceAct.mapper)?.actingClearance ?: return@apply
                val appropriateTurnDir = getAppropriateTurnDir(cmd.targetHdgDeg,
                    convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV, cmd.turnDir)
                if (appropriateTurnDir != cmd.turnDir) {
                    // Aircraft has reached the end of turn
                    cmd.turnDir = appropriateTurnDir
                    actingClearance.clearanceState.vectorTurnDir = appropriateTurnDir
                    val pendingClearances = get(PendingClearances.mapper)
                    if (pendingClearances == null || pendingClearances.clearanceQueue.isEmpty)
                        this += LatestClearanceChanged()
                }
            }
        }

        // Update when the acting clearance has been changed by player action
        val actingClearances = actingClearanceChangedFamilyEntities.getEntities()
        for (i in 0 until actingClearances.size()) {
            actingClearances[i]?.apply {
                setCommandTargetToNewActingClearance(this)
                remove<ClearanceActChanged>()
            }
        }
    }

    /**
     * Updates aircraft AI behaviour for approaches, which will in most cases override behaviour stipulated by the modes
     * in the [updateCommandTarget] function
     * @param deltaTime time passed, in seconds, since the last update
     */
    private fun updateApproaches(deltaTime: Float) {
        // Update for localizer/extended centreline captured (this will override waypoint direct behaviour)
        val appTrackCaptured = appTrackCapFamilyEntities.getEntities()
        for (i in 0 until appTrackCaptured.size()) {
            appTrackCaptured[i]?.apply {
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val actingClearance = get(ClearanceAct.mapper)?.actingClearance ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val appEntity = get(LocalizerCaptured.mapper)?.locApp ?: get(VisualCaptured.mapper)?.visApp ?: return@apply
                val targetPos = getTargetPos(appEntity, pos.x, pos.y) ?: return@apply
                cmd.turnDir = CommandTarget.TURN_DEFAULT
                actingClearance.clearanceState.vectorTurnDir = CommandTarget.TURN_DEFAULT
                val targetTrackAndGs = getPointTargetTrackAndGS(getRequiredTrack(pos.x, pos.y, targetPos.x, targetPos.y).toDouble(), spd.speedKts, dir, get(AffectedByWind.mapper))
                cmd.targetHdgDeg = targetTrackAndGs.first + MAG_HDG_DEV
                actingClearance.clearanceState.vectorHdg = null

                appEntity[LineUpDist.mapper]?.let { if (checkLineUpDistReached(appEntity, pos.x, pos.y)) {
                    // If line up distance exists and is reached, remove all other approach components and add visual approach component
                    remove<LocalizerArmed>()
                    remove<LocalizerCaptured>()
                    remove<GlideSlopeArmed>()
                    remove<GlideSlopeCaptured>()
                    remove<StepDownApproach>()
                    remove<CirclingApproach>()
                    this += VisualCaptured(appEntity[ApproachInfo.mapper]?.rwyObj?.entity?.get(VisualApproach.mapper)?.visual ?: return@let, appEntity)
                }}
            }
        }

        // Update for visual approach armed
        val visArmed = visArmedFamilyEntities.getEntities()
        for (i in 0 until visArmed.size()) {
            visArmed[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val clearanceState = get(ClearanceAct.mapper) ?: return@apply
                val vis = get(VisualArmed.mapper) ?: return@apply
                val appPos = vis.visApp[Position.mapper] ?: return@apply

                // Return if current clearance state is not vector and route's first leg is not a missed approach leg
                val state = clearanceState.actingClearance.clearanceState
                val route = state.route
                if (state.vectorHdg == null && route.size > 0 && route[0].phase != Route.Leg.MISSED_APP) return@apply

                // Check aircraft position is less than visual approach range
                if (calculateDistanceBetweenPoints(pos.x, pos.y, appPos.x, appPos.y) > nmToPx(VIS_MAX_DIST_NM.toFloat())) return@apply

                // Change from armed to captured
                this += VisualCaptured(vis.visApp, vis.parentApp)
                remove<VisualArmed>()
                remove<OnGoAroundRoute>()
            }
        }

        // Update for visual glide path captured
        val visGlideCaptured = visAppGlideFamilyEntities.getEntities()
        for (i in 0 until visGlideCaptured.size()) {
            visGlideCaptured[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val groundTrack = get(GroundTrack.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val visApp = get(VisualCaptured.mapper)?.visApp ?: return@apply
                val targetAlt = getAppAltAtPos(visApp, pos.x, pos.y, pxpsToKt(groundTrack.trackVectorPxps.len())) ?: return@apply
                cmd.targetAltFt = targetAlt.roundToInt()
            }
        }

        // Update for localizer armed
        val locArmed = locArmedFamilyEntities.getEntities()
        for (i in 0 until locArmed.size()) {
            locArmed[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val locApp = get(LocalizerArmed.mapper)?.locApp ?: return@apply
                val groundTrack = get(GroundTrack.mapper) ?: return@apply
                val locPos = locApp[Position.mapper] ?: return@apply
                val locTrack = locApp[Direction.mapper]?.trackUnitVector ?: return@apply
                val locCourseHdg = convertWorldAndRenderDeg(locTrack.angleDeg()) + 180 + MAG_HDG_DEV

                // Check whether aircraft is heading away from the LOC
                if (dir.trackUnitVector.dot(locTrack) > 0) return@apply

                // Check whether aircraft is in the LOC arc - 35 deg at <= 10 nm and 10 deg at > 10nm
                if (!isInsideLocArc(locApp, pos.x, pos.y, LOC_INNER_ARC_ANGLE_DEG, LOC_INNER_ARC_DIST_NM) &&
                    !isInsideLocArc(locApp, pos.x, pos.y, LOC_OUTER_ARC_ANGLE_DEG, locApp[Localizer.mapper]?.maxDistNm ?: return@apply)) return@apply

                // Find point of intersection between aircraft ground track and localizer course
                val intersectionPoint = Vector2(locPos.x, locPos.y)
                val distFromAppOrigin = Intersector.intersectRayRay(intersectionPoint, locTrack, Vector2(pos.x, pos.y), groundTrack.trackVectorPxps)
                intersectionPoint.plusAssign(locTrack * distFromAppOrigin)
                // Calculate distance between aircraft and waypoint and check if aircraft should move to next leg
                val deltaX = intersectionPoint.x - pos.x
                val deltaY = intersectionPoint.y - pos.y
                val requiredDist = max(5f, findTurnDistance(findDeltaHeading(convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()),
                    locCourseHdg, CommandTarget.TURN_DEFAULT),
                    if (ias.iasKt > HALF_TURN_RATE_THRESHOLD_IAS) MAX_HIGH_SPD_ANGULAR_SPD else MAX_LOW_SPD_ANGULAR_SPD, groundTrack.trackVectorPxps.len()))
                if (requiredDist * requiredDist > deltaX * deltaX + deltaY * deltaY) {
                    remove<LocalizerArmed>()
                    this += LocalizerCaptured(locApp)
                    remove<OnGoAroundRoute>()
                    return@apply
                }

                // Alternatively, if aircraft is within 2 degrees of LOC track, capture
                val trackToLoc = Vector2(locPos.x - pos.x, locPos.y - pos.y)
                val targetHdg = convertWorldAndRenderDeg(trackToLoc.angleDeg()) + MAG_HDG_DEV
                if (abs(findDeltaHeading(targetHdg, locCourseHdg, CommandTarget.TURN_DEFAULT)) < 2) {
                    remove<LocalizerArmed>()
                    this += LocalizerCaptured(locApp)
                    remove<OnGoAroundRoute>()
                }
            }
        }

        // Update for glide slope armed
        val gsArmed = gsArmedFamilyEntities.getEntities()
        for (i in 0 until gsArmed.size()) {
            gsArmed[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val gsApp = get(GlideSlopeArmed.mapper)?.gsApp ?: return@apply
                // 25 feet leeway max capture altitude
                if (alt.altitudeFt < ((gsApp[GlideSlope.mapper]?.maxInterceptAlt ?: return@apply)) + 25) {
                    val gsAltAtPos = getAppAltAtPos(gsApp, pos.x, pos.y, 0f) ?: return@apply
                    // If below the GS capture altitude and more than 100 feet above command target altitude,
                    // fly at half the descent angle of the glide slope
                    if (alt.altitudeFt < gsAltAtPos && alt.altitudeFt > cmd.targetAltFt + 100) {
                        val groundSpeedPxps = get(GroundTrack.mapper)?.trackVectorPxps?.len() ?: ktToPxps(140)
                        val descentAngle = (gsApp[GlideSlope.mapper]?.glideAngle ?: 3f) / 2
                        val descentRatePxps = -groundSpeedPxps * tan(Math.toRadians(descentAngle.toDouble()))
                        this += CommandTargetVertSpd(pxToFt(descentRatePxps.toFloat() * 60))
                    }
                    // Capture glide slope when within 20 feet and below the max intercept altitude (and localizer is captured)
                    if (abs(alt.altitudeFt - gsAltAtPos) < 20) {
                        remove<GlideSlopeArmed>()
                        this += GlideSlopeCaptured(gsApp)
                    }
                }
            }
        }

        // Update for step down approach
        val stepDown = stepDownAppFamilyEntities.getEntities()
        for (i in 0 until stepDown.size()) {
            stepDown[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val stepDownApp = get(StepDownApproach.mapper)?.stepDownApp?: return@apply
                val stepDownAltAtPos = getAppAltAtPos(stepDownApp, pos.x, pos.y, 0f) ?: return@apply // No advanced altitude prediction needed, so ground speed is set at 0 knots
                cmd.targetAltFt = stepDownAltAtPos.roundToInt()
            }
        }

        // Update for circling approach
        val circling = circlingAppFamilyEntities.getEntities()
        for (i in 0 until circling.size()) {
            circling[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val circleApp = get(CirclingApproach.mapper) ?: return@apply
                val appDir = circleApp.circlingApp[Direction.mapper] ?: return@apply
                val circleInfo = circleApp.circlingApp[Circling.mapper] ?: return@apply
                val appInfo = circleApp.circlingApp[ApproachInfo.mapper] ?: return@apply
                val rwy = appInfo.rwyObj.entity
                val rwyDir = rwy[Direction.mapper] ?: return@apply
                val rwyPos = rwy[CustomPosition.mapper] ?: return@apply // Use threshold position for reference
                val oppRwyHdg = modulateHeading(convertWorldAndRenderDeg(rwyDir.trackUnitVector.angleDeg()) + MAG_HDG_DEV + 180)
                when (circleApp.phase.toInt()) {
                    0 -> if (alt.altitudeFt < circleApp.breakoutAlt &&
                        (has(LocalizerCaptured.mapper) || has(GlideSlopeCaptured.mapper) || has(StepDownApproach.mapper))) {
                        // Check for minimums at breakout altitude
                        val mins = circleApp.circlingApp[Minimums.mapper]
                        val arptMetar = GAME.gameServer?.airports?.get(appInfo.airportId)?.entity?.get(MetarInfo.mapper)
                        if (arptMetar != null && mins != null &&
                            ((arptMetar.visibilityM < mins.rvrM) ||
                            (arptMetar.ceilingHundredFtAGL ?: Short.MAX_VALUE) * 100 < mins.baroAltFt))
                            return@apply initiateGoAround(this, RecentGoAround.RWY_NOT_IN_SIGHT)
                        circleApp.phase = 1
                    }
                    1 -> {
                        cmd.targetHdgDeg = (modulateHeading(convertWorldAndRenderDeg(appDir.trackUnitVector.angleDeg()) + MAG_HDG_DEV + 180)
                                + when (circleInfo.breakoutDir) {
                            CommandTarget.TURN_LEFT -> -45
                            CommandTarget.TURN_RIGHT -> 45
                            else -> {
                                FileLog.info("AISystem", "Unknown circling breakout direction ${circleInfo.breakoutDir}")
                                -45
                            }
                        })
                        cmd.targetAltFt = circleApp.breakoutAlt - 100
                        remove<LocalizerArmed>()
                        remove<LocalizerCaptured>()
                        remove<GlideSlopeArmed>()
                        remove<GlideSlopeCaptured>()
                        remove<StepDownApproach>()
                        circleApp.phase1Timer -= deltaTime
                        if (circleApp.phase1Timer < 0) circleApp.phase = 2
                    }
                    2 -> {
                        cmd.targetHdgDeg = oppRwyHdg
                        cmd.targetAltFt = circleApp.breakoutAlt - 100
                        val toRwy = Vector2(rwyPos.x - pos.x, rwyPos.y - pos.y)
                        val dotProduct = dir.trackUnitVector.dot(toRwy)
                        // If aircraft has passed abeam runway, start 50 seconds timer in phase 3
                        if (dotProduct < 0 && findDeltaHeading(dir.trackUnitVector.angleDeg(),
                                rwyDir.trackUnitVector.angleDeg() + 180, CommandTarget.TURN_DEFAULT) < 10f) circleApp.phase = 3
                    }
                    3 -> {
                        cmd.targetHdgDeg = oppRwyHdg
                        val visApp = rwy[VisualApproach.mapper]?.visual ?: return@apply
                        cmd.targetAltFt = getAppAltAtPos(visApp, pos.x, pos.y, -pxpsToKt(get(GroundTrack.mapper)?.trackVectorPxps?.len() ?: 0f))?.toInt() ?:
                        (circleApp.breakoutAlt - 100)
                        cmd.targetIasKt = get(AircraftInfo.mapper)?.aircraftPerf?.appSpd ?: 135
                        circleApp.phase3Timer -= deltaTime
                        // Once 50 seconds has passed, transition to visual captured mode
                        if (circleApp.phase3Timer < 0) {
                            remove<CirclingApproach>()
                            this += VisualCaptured(visApp, circleApp.circlingApp)
                        }
                    }
                }
            }
        }

        // Update stabilized approach status, and checks for minimums
        val checkGoAround = checkGoAroundFamilyEntities.getEntities()
        for (i in 0 until checkGoAround.size()) {
            checkGoAround[i]?.apply {
                val gsApp = get(GlideSlopeCaptured.mapper)?.gsApp ?: get(GlideSlopeArmed.mapper)?.gsApp
                val stepDownApp = get(StepDownApproach.mapper)?.stepDownApp
                val visApp = get(VisualCaptured.mapper)?.visApp
                val visParentApp = get(VisualCaptured.mapper)?.parentApp
                val locApp = get(LocalizerCaptured.mapper)?.locApp
                val appVert = gsApp ?: stepDownApp ?: visApp ?: return@apply
                val appLat = locApp ?: visApp ?: return@apply
                val rwyObj = appVert[ApproachInfo.mapper]?.rwyObj?.entity ?: return@apply
                val rwyPos = rwyObj[CustomPosition.mapper] ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val ias = get(IndicatedAirSpeed.mapper) ?: return@apply
                val perf = get(AircraftInfo.mapper)?.aircraftPerf ?: return@apply

                val distFromRwyPx = calculateDistanceBetweenPoints(pos.x, pos.y, rwyPos.x, rwyPos.y)
                val arptMetar = GAME.gameServer?.airports?.get(appLat[ApproachInfo.mapper]?.airportId)?.entity?.get(MetarInfo.mapper) ?: return@apply

                // Check RVR requirement - 100m leeway
                val rvrRequired = ((visParentApp ?: appLat)[Minimums.mapper]?.rvrM ?: 100) - 100
                if (pxToM(distFromRwyPx) < rvrRequired) {
                    if (arptMetar.visibilityM < rvrRequired) return@apply initiateGoAround(this, RecentGoAround.RWY_NOT_IN_SIGHT)
                }

                // Check decision altitude/height requirement
                val decisionAlt = (visParentApp ?: appVert)[Minimums.mapper]?.baroAltFt ?: 0
                if (alt.altitudeFt < decisionAlt && (arptMetar.ceilingHundredFtAGL ?: Short.MAX_VALUE) * 100 < decisionAlt)
                    return@apply initiateGoAround(this, RecentGoAround.RWY_NOT_IN_SIGHT)

                // Check opposite runway aircraft departure
                // For all approaches, go around if aircraft is less than 7nm from runway and a departure has taken off
                // less than 135s ago from the opposite (including dependent) runway
                if (pxToNm(distFromRwyPx) < 7) {
                    rwyObj[OppositeRunway.mapper]?.oppRwy?.get(RunwayPreviousDeparture.mapper)?.let {
                        if (it.timeSinceDepartureS < 135) {
                            return@apply initiateGoAround(this, RecentGoAround.TRAFFIC_TOO_CLOSE)
                        }
                    }
                    rwyObj[DependentOppositeRunway.mapper]?.depOppRwys?.let { depOppRwys ->
                        for (j in 0 until depOppRwys.size) {
                            depOppRwys[j][RunwayPreviousDeparture.mapper]?.let {
                                if (it.timeSinceDepartureS < 135) {
                                    return@apply initiateGoAround(this, RecentGoAround.TRAFFIC_TOO_CLOSE)
                                }
                            }
                        }
                    }
                }

                // Check wake turbulence tolerance at less than 7nm from runway
                val wakeToleranceValue = get(WakeTolerance.mapper)?.accumulation ?: 0f
                if (wakeToleranceValue > 30f && pxToNm(distFromRwyPx) < 7) {
                    return@apply initiateGoAround(this, RecentGoAround.WAKE_TURBULENCE)
                }

                // Check distance
                // For visual approach, check for stabilized approach by 1.2nm from threshold
                // For approach with localizer and/or glide slope, check for stabilized approach by 3.2nm from threshold
                val stabDistNm = if (gsApp != null || locApp != null) 3.2f else 1.2f
                if (pxToNm(distFromRwyPx) > stabDistNm) return@apply // No need to check if aircraft is not yet close enough to the runway

                // Check airspeed
                // For visual approach, check speed not more than 10 knots above approach speed
                // For approach with glide slope, check speed not more than 20 knots above approach speed
                val maxAllowableIas = perf.appSpd + (if (gsApp != null) 20 else 10)
                if (ias.iasKt > maxAllowableIas) {
                    return@apply initiateGoAround(this, RecentGoAround.TOO_FAST)
                }

                // Check altitude
                // For visual approach, check altitude not more than 200 feet above 3 degree glide path altitude
                // For approach with glide slope, check altitude not more than (0.12 * glide angle) degrees (but will be
                // capped at min 50 feet difference) above the glide path
                val maxAllowableAlt = if (gsApp != null) {
                    val gs = appVert[GlideSlope.mapper] ?: return@apply
                    val appPos = appVert[Position.mapper] ?: return@apply
                    val appDistPx = calculateDistanceBetweenPoints(pos.x, pos.y, appPos.x, appPos.y)
                    val gsDistPx = appDistPx + nmToPx(gs.offsetNm)
                    val maxGsAngleDeg = 1.12 * gs.glideAngle
                    max(pxToFt(gsDistPx * tan(Math.toRadians(maxGsAngleDeg)).toFloat()), (getAppAltAtPos(appVert, pos.x, pos.y, 0f) ?: return@apply) + 50)
                } else {
                    (getAppAltAtPos(appVert, pos.x, pos.y, 0f) ?: return@apply) + 200
                }
                if (alt.altitudeFt > maxAllowableAlt) {
                    return@apply initiateGoAround(this, RecentGoAround.TOO_HIGH)
                }

                // Check position; only when aircraft is still more than 0.5nm from runway threshold
                // For visual approach, aircraft position should be within 10 degrees of track to runway
                // For approach with localizer, aircraft position should be within 1 degree of track to localizer
                val maxAllowableDeviation = if (locApp != null) 1 else 20
                val locPos = locApp?.get(Position.mapper)
                // We ignore deviation if a final line up is required for localizer
                val isLineUp = locApp?.get(LineUpDist.mapper) != null
                val trackToRwy = getRequiredTrack(pos.x, pos.y,  locPos?.x ?: rwyPos.x, locPos?.y ?: rwyPos.y)
                val appTrack = convertWorldAndRenderDeg(appLat[Direction.mapper]?.trackUnitVector?.angleDeg() ?: return@apply) + 180
                val deviation = abs(findDeltaHeading(trackToRwy, appTrack, CommandTarget.TURN_DEFAULT))
                if (!isLineUp && pxToNm(distFromRwyPx) > 0.5f && deviation > maxAllowableDeviation) {
                    return@apply initiateGoAround(this, RecentGoAround.UNSTABLE)
                }

                // Check wind
                // For all approaches, runway tailwind should be maximum 15 knots, crosswind should be maximum 25 knots
                rwyObj[RunwayWindComponents.mapper]?.let {
                    if (it.tailwindKt > 15) return@apply initiateGoAround(this, RecentGoAround.STRONG_TAILWIND)
                    if (it.crosswindKt > 25) return@apply initiateGoAround(this, RecentGoAround.STRONG_CROSSWIND)
                }

                // Check windshear go around
                if (hasNot(WindshearGoAround.mapper)) {
                    this += WindshearGoAround(generateRandomWindshearGoAround(rwyObj, arptMetar))
                }
                if (get(WindshearGoAround.mapper)?.goAround == true) {
                    return@apply initiateGoAround(this, RecentGoAround.WINDSHEAR)
                }

                // Check runway occupancy or runway closed
                // For all approaches, go around if runway is still occupied or closed by the time aircraft reaches 150 feet AGL
                val rwyAlt = rwyObj[Altitude.mapper]?.altitudeFt
                if (rwyAlt != null && (rwyObj.has(RunwayOccupied.mapper) || rwyObj.has(RunwayClosed.mapper)) && alt.altitudeFt < rwyAlt + 150) {
                    return@apply initiateGoAround(this, RecentGoAround.RWY_NOT_CLEAR)
                }
            }
        }

        // Update touchdown status for aircraft on approach
        val touchDown = checkTouchdownFamilyEntities.getEntities()
        for (i in 0 until touchDown.size()) {
            touchDown[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val spd = get(Speed.mapper) ?: return@apply
                val acc = get(Acceleration.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val appEntity = get(GlideSlopeCaptured.mapper)?.gsApp ?: get(VisualCaptured.mapper)?.visApp ?: return@apply
                val rwyEntity = appEntity[ApproachInfo.mapper]?.rwyObj?.entity ?: return@apply
                val rwyElevation = rwyEntity[Altitude.mapper]?.altitudeFt ?: return@apply
                if (alt.altitudeFt < rwyElevation + 25) {
                    removeAllApproachComponents(this)
                    this += LandingRoll(rwyEntity)
                    // Set runway as occupied
                    rwyEntity += RunwayOccupied()
                    rwyEntity[OppositeRunway.mapper]?.oppRwy?.plusAssign(RunwayOccupied())
                    // Set aircraft physics properties
                    alt.altitudeFt = rwyElevation
                    spd.vertSpdFpm = 0f
                    spd.angularSpdDps = 0f
                    acc.dVertSpdMps2 = 0f
                    acc.dAngularSpdDps2 = 0f
                    dir.trackUnitVector = rwyEntity[Direction.mapper]?.trackUnitVector ?: return@apply
                    // Update runway's previous arrival component to this aircraft
                    (rwyEntity[RunwayPreviousArrival.mapper] ?: RunwayPreviousArrival().apply { rwyEntity += this }).also { prevArr ->
                        val aircraftPerf = get(AircraftInfo.mapper)?.aircraftPerf ?: return@also
                        prevArr.wakeCat = aircraftPerf.wakeCategory
                        prevArr.recat = aircraftPerf.recat
                        prevArr.timeSinceTouchdownS = 0f
                    }
                    // If is emergency that will stay on runway, close runway
                    if (has(ImmobilizeOnLanding.mapper)) {
                        val airport = rwyEntity[RunwayInfo.mapper]?.airport ?: return@apply
                        airport.setRunwayClosed(rwyEntity[RunwayInfo.mapper]?.rwyId ?: return@apply, true)
                    }
                    // Remove wake zones
                    engine.getSystem<TrafficSystemInterval>().removeAircraftWakeZones(this)
                }
            }
        }

        // Clear any existing expedite flags if the aircraft is within 500 feet of its target altitude
        val expediteClearFamily = expediteFamilyEntities.getEntities()
        for (i in 0 until expediteClearFamily.size()) {
            expediteClearFamily[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return@apply
                if (abs(alt.altitudeFt - cmd.targetAltFt) < 500) {
                    clearanceAct.expedite = false
                    remove<CommandExpedite>()
                    this += LatestClearanceChanged()
                }
            }
        }

        // Update aircraft with pending emergency
        val pendingEmergencyFamily = pendingEmergencyFamilyEntities.getEntities()
        for (i in 0 until pendingEmergencyFamily.size()) {
            pendingEmergencyFamily[i]?.apply {
                val acInfo = get(AircraftInfo.mapper) ?: return@apply
                val pendingEmer = get(EmergencyPending.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val departureAirport = get(DepartureAirport.mapper) ?: return@apply

                if (alt.altitudeFt < pendingEmer.activationAlt || pendingEmer.active) return@apply

                val arptElevation = GAME.gameServer?.airports?.get(departureAirport.arptId)?.entity?.get(Altitude.mapper)?.altitudeFt ?: 0f
                get(FlightType.mapper)?.type = FlightType.ARRIVAL
                this += ArrivalAirport(departureAirport.arptId)
                this += ArrivalRouteZone()
                remove<DepartureAirport>()
                remove<ContactToCentre>()
                setCurrentAndPendingClearanceToAltitude(this,
                    getClearedAltitudeAfterEmergency(alt.altitudeFt, pendingEmer.type, arptElevation))
                GAME.gameServer?.sendAircraftDeclareEmergency(acInfo.icaoCallsign, pendingEmer.type)
                this += RunningChecklists(MathUtils.random(300f, 600f), MathUtils.random(150f, 210f), false)
                pendingEmer.active = true
            }
        }

        // Update aircraft running checklists
        val checklistFamily = runningChecklistFamilyEntities.getEntities()
        for (i in 0 until checklistFamily.size()) {
            checklistFamily[i]?.apply {
                val acInfo = get(AircraftInfo.mapper) ?: return@apply
                val runningChecklists = get(RunningChecklists.mapper) ?: return@apply

                runningChecklists.timeLeft -= deltaTime
                if (!runningChecklists.informedNearingDone &&
                    runningChecklists.timeLeft < runningChecklists.timeLeftToInformNearDone) {
                    val requiresFuelDump = RequiresFuelDump.canDumpFuel.contains(acInfo.icaoType) && MathUtils.randomBoolean()
                    if (requiresFuelDump) {
                        val fuelDumpTime = MathUtils.random(600f, 900f)
                        this += RequiresFuelDump(false, fuelDumpTime, fuelDumpTime - MathUtils.random(45f, 60f),
                            MathUtils.random(120f, 180f), informedDumpStarted = false,  informedNearingDone = false)
                    }
                    GAME.gameServer?.sendAircraftChecklistsNearingDone(acInfo.icaoCallsign, requiresFuelDump)
                    runningChecklists.informedNearingDone = true
                }
                if (runningChecklists.timeLeft < 0) {
                    remove<RunningChecklists>()
                    if (!has(RequiresFuelDump.mapper)) setReadyForApproachStatus(this)
                    else get(RequiresFuelDump.mapper)?.active = true
                }
            }
        }

        // Update aircraft dumping fuel
        val fuelDumpFamily = dumpingFuelFamilyEntities.getEntities()
        for (i in 0 until fuelDumpFamily.size()) {
            fuelDumpFamily[i]?.apply {
                val acInfo = get(AircraftInfo.mapper) ?: return@apply
                val fuelDump = get(RequiresFuelDump.mapper) ?: return@apply

                if (!fuelDump.active) return@apply

                fuelDump.timeLeft -= deltaTime
                if (!fuelDump.informedDumpStarted && fuelDump.timeLeft < fuelDump.timeLeftToInformStart) {
                    GAME.gameServer?.sendAircraftFuelDumpStatus(acInfo.icaoCallsign, false)
                    fuelDump.informedDumpStarted = true
                }
                if (!fuelDump.informedNearingDone && fuelDump.timeLeft < fuelDump.timeLeftToInformNearDone) {
                    GAME.gameServer?.sendAircraftFuelDumpStatus(acInfo.icaoCallsign, true)
                    fuelDump.informedNearingDone = true
                }
                if (fuelDump.timeLeft < 0) {
                    remove<RequiresFuelDump>()
                    setReadyForApproachStatus(this)
                }
            }
        }

        // Update aircraft immobilized on runway
        val immobilizedFamily = stayOnRunwayFamilyEntities.getEntities()
        for (i in 0 until immobilizedFamily.size()) {
            immobilizedFamily[i]?.apply {
                val immobilized = get(ImmobilizeOnLanding.mapper) ?: return@apply
                immobilized.timeLeft -= deltaTime

                if (immobilized.timeLeft < 0) {
                    val landingRwy = get(LandingRoll.mapper)?.rwy ?: return@apply
                    val airport = landingRwy[RunwayInfo.mapper]?.airport ?: return@apply
                    airport.setRunwayClosed(landingRwy[RunwayInfo.mapper]?.rwyId ?: return@apply, false)
                    remove<ImmobilizeOnLanding>()
                }
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
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val lastRestrictions = entity[LastRestrictions.mapper] ?: LastRestrictions().apply { entity += this }
        val cmd = entity[CommandTarget.mapper] ?: return
        actingClearance.route.apply {
            val currHold = entity[CommandHold.mapper]
            removeAllAdvancedCommandModes(entity)
            unsetTurnDirection(entity)
            entity += LatestClearanceChanged()
            if (size == 0) {
                actingClearance.vectorHdg = cmd.targetHdgDeg.roundToInt().toShort()
                actingClearance.vectorTurnDir = CommandTarget.TURN_DEFAULT
                cmd.targetAltFt = actingClearance.clearedAlt
                cmd.targetIasKt = actingClearance.clearedIas
            } else while (size > 0) get(0).let {
                if (it.phase == Route.Leg.APP || it.phase == Route.Leg.APP_TRANS) {
                    // Remove on go around route component
                    entity.remove<OnGoAroundRoute>()
                }
                entity += when (it) {
                    is Route.VectorLeg -> {
                        actingClearance.vectorHdg = it.heading
                        actingClearance.vectorTurnDir = it.turnDir
                        cmd.targetAltFt = actingClearance.clearedAlt
                        cmd.targetIasKt = actingClearance.clearedIas
                        CommandVector(it.heading, it.turnDir)
                    }
                    is Route.InitClimbLeg -> {
                        lastRestrictions.minAltFt = it.minAltFt
                        updateLegRestr(entity)
                        CommandInitClimb(it.heading, it.minAltFt)
                    }
                    is Route.WaypointLeg -> {
                        if (!it.legActive) {
                            removeIndex(0)
                            return@let
                        }
                        updateLegRestr(entity)
                        CommandDirect(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKt, it.flyOver, it.turnDir)
                    }
                    is Route.HoldLeg -> {
                        var alt = actingClearance.clearedAlt
                        it.minAltFt?.let { minAlt -> alt = max(minAlt, alt) }
                        it.maxAltFt?.let { maxAlt -> alt = min(maxAlt, alt) }
                        cmd.targetAltFt = alt
                        actingClearance.clearedAlt = alt
                        // If an existing hold mode exists, and new hold leg does not differ from it in the waypoint,
                        // inbound heading and direction, re-add the hold mode removed earlier
                        if (currHold?.wptId == it.wptId && currHold.inboundHdg == it.inboundHdg && currHold.legDir == it.turnDir) currHold
                        else CommandHold(it.wptId, it.maxAltFt, it.minAltFt, it.maxSpdKtLower, it.inboundHdg, it.legDist, it.turnDir)
                    }
                    is Route.DiscontinuityLeg -> {
                        cmd.targetAltFt = actingClearance.clearedAlt
                        cmd.targetIasKt = actingClearance.clearedIas
                        return@apply
                    }
                    else -> {
                        FileLog.info("AISystem", "Unknown leg type ${it::class}")
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
     */
    private fun setToNextRouteLeg(entity: Entity) {
        var lastWpt: Route.WaypointLeg? = null
        val route = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route?.apply { if (size > 0) {
           (get(0) as? Route.WaypointLeg)?.let { prevWpt ->
                lastWpt = prevWpt
                entity[LastRestrictions.mapper]?.let { restr ->
                    prevWpt.maxSpdKt?.let { maxSpd -> restr.maxSpdKt = maxSpd }
                    prevWpt.minAltFt?.let { minAltFt -> restr.minAltFt = minAltFt }
                    prevWpt.maxAltFt?.let { maxAltFt -> restr.maxAltFt = maxAltFt }
                }
            }
            removeIndex(0)
        }}
        setToFirstRouteLeg(entity)

        val finalLastWpt = lastWpt
        if (finalLastWpt != null && route != null) {
            addFlyoverExclusionZones(entity, finalLastWpt, route, entity[LastRestrictions.mapper]?.minAltFt)
        }
    }

    /**
     * Updates the new command target the aircraft should fly with its current clearance state's altitude clearance and
     * the altitude, speed restrictions along its route
     *
     * Call this function after the aircraft's next leg has changed (the last restriction component values will need to
     * be updated accordingly prior to calling this function to ensure valid results)
     * @param entity the aircraft entity
     */
    private fun updateLegRestr(entity: Entity) {
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val commandTarget = entity[CommandTarget.mapper] ?: return
        val flightType = entity[FlightType.mapper] ?: return
        val lastRestriction = entity[LastRestrictions.mapper] ?: LastRestrictions().apply { entity += this }
        val highestMinAlt = getHighestMinAlt(actingClearance.route)
        val minAlt = lastRestriction.minAltFt.let { lastMinAlt ->
            when (flightType.type) {
                FlightType.DEPARTURE -> lastMinAlt // If aircraft is a departure, use the last min alt
                FlightType.ARRIVAL -> highestMinAlt // If aircraft is an arrival, use the subsequent highest min alt
                else -> null
            }
        }
        lastRestriction.minAltFt = minAlt

        val lowestMaxAlt = getLowestMaxAlt(actingClearance.route)
        var maxAlt = lastRestriction.maxAltFt.let { lastMaxAlt ->
            when (flightType.type) {
                FlightType.DEPARTURE -> lowestMaxAlt // If aircraft is a departure, use the subsequent lowest max alt
                FlightType.ARRIVAL -> lastMaxAlt // If aircraft is an arrival, use the last max alt
                else -> null
            }
        }

        // If aircraft is on go around route, use the subsequent lowest max alt instead
        if (entity.has(OnGoAroundRoute.mapper) && flightType.type == FlightType.ARRIVAL) maxAlt = lowestMaxAlt

        if (minAlt != null && maxAlt != null && minAlt > maxAlt) {
            FileLog.info("AISystem", "minAlt ($minAlt) should not > maxAlt ($maxAlt)")
            maxAlt = minAlt
        }

        entity[Altitude.mapper]?.apply {
            var targetAlt = actingClearance.clearedAlt
            minAlt?.let {
                if (targetAlt < it && altitudeFt >= it) targetAlt = it
                else if (targetAlt < it && altitudeFt < it) {
                    // We set the target altitude to the current altitude if the aircraft is not on the missed approach track,
                    // and the arrival has already descended below the min alt for some reason
                    if (entity.hasNot(OnGoAroundRoute.mapper) && flightType.type == FlightType.ARRIVAL) targetAlt = altitudeFt.toInt()
                }
            }
            maxAlt?.let {
                if (targetAlt > it && altitudeFt <= it) targetAlt = it
                else if (targetAlt > it && altitudeFt > it && flightType.type == FlightType.DEPARTURE) targetAlt = altitudeFt.toInt()
            }

            commandTarget.targetAltFt = targetAlt // Update command target to the new calculated target altitude
        }
        val spds = getMinMaxOptimalIAS(entity)
        val maxSpd = spds.second
        val optimalSpd = spds.third
        // If the cleared IAS is currently at the maximum possible and the new max speed is higher than (or equal to, as in
        // the event of player clearing speed restriction the acting max speed is already set to maxSpd) it, and the
        // aircraft is a departure, update it to the new optimal speed automatically
        if (actingClearance.clearedIas == actingClearance.maxIas && maxSpd >= actingClearance.maxIas &&
            flightType.type == FlightType.DEPARTURE)
            commandTarget.targetIasKt = optimalSpd
        else if (actingClearance.clearedIas > maxSpd) commandTarget.targetIasKt = maxSpd // If currently cleared IAS exceeds max speed restriction
        else commandTarget.targetIasKt = actingClearance.clearedIas // Else just set to the cleared IAS
        val prevMaxIas = actingClearance.maxIas
        val prevClearedIas = actingClearance.clearedIas
        actingClearance.maxIas = maxSpd
        actingClearance.clearedIas = commandTarget.targetIasKt
        if (prevMaxIas != actingClearance.maxIas || prevClearedIas != actingClearance.clearedIas) {
            val pendingClearances = entity[PendingClearances.mapper]
            if (pendingClearances == null || pendingClearances.clearanceQueue.isEmpty)
                entity += LatestClearanceChanged()
        }
    }

    /**
     * Updates the command target parameters with the latest acting clearance;
     * should be called only when the acting clearance has been changed due to
     * player clearance, or in a way that requires a change in player clearance
     * @param entity the aircraft entity to apply the changes to
     */
    private fun setCommandTargetToNewActingClearance(entity: Entity) {
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val commandTarget = entity[CommandTarget.mapper] ?: return
        actingClearance.vectorHdg?.let { hdg ->
            // Cleared vector heading
            removeAllAdvancedCommandModes(entity)
            unsetTurnDirection(entity)
            entity += CommandVector(hdg, actingClearance.vectorTurnDir ?: CommandTarget.TURN_DEFAULT)
            actingClearance.route.apply {
                while (size > 0) {
                    // Remove any vector legs at the beginning of the route
                    if (get(0) is Route.VectorLeg) removeIndex(0)
                    else break
                }
            }
        } ?: setToFirstRouteLeg(entity) // Not cleared vector, set to the first cleared leg instead

        actingClearance.vectorHdg?.let { hdg ->
            commandTarget.targetHdgDeg = hdg.toFloat()
            commandTarget.turnDir = actingClearance.vectorTurnDir ?: CommandTarget.TURN_DEFAULT
            commandTarget.targetAltFt = actingClearance.clearedAlt
            commandTarget.targetIasKt = actingClearance.clearedIas
        }

        // Update expedite state
        if (actingClearance.expedite) entity += CommandExpedite()
        else entity.remove<CommandExpedite>()

        if (entity[PendingClearances.mapper] == null) entity += LatestClearanceChanged()
    }

    /**
     * Returns one of the 3 possible entry procedures for a holding pattern, given the holding pattern's inbound leg heading,
     * its turn direction, and the aircraft's target heading to the waypoint
     * @param targetHeading the heading the aircraft is currently targeting to fly
     * @param inboundHdg the inbound leg heading of the hold leg
     * @param legDir the turn direction of the hold leg
     * @return a byte denoting one of 3 possible holding entry procedures
     */
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
                FileLog.info("AISystem", "Invalid turn direction $legDir specified for holding pattern")
                if (offset > -1 && offset < 129) 1 else if (offset < -1 && offset > -69) 2 else 3
            }
        }
    }

    /**
     * Gets the appropriate turn direction given the target heading, aircraft heading and the instructed turn direction
     *
     * This is to ensure that after the aircraft turns though the commanded turn direction, it does not perform another
     * 360 degree loop after reaching the [targetHeading] by allowing a window of 3 degrees where the aircraft should
     * return to the default turn direction behaviour
     * @param targetHeading the heading that the aircraft is targeting to fly
     * @param currHeading the heading that the aircraft is flying
     * @param cmdTurnDir the current turn direction
     * @return the appropriate turn direction after taking into account the difference between th target and actual heading
     */
    private fun getAppropriateTurnDir(targetHeading: Float, currHeading: Float, cmdTurnDir: Byte): Byte {
        // Maintain the turn direction until magnitude of deltaHeading is less than 3 degrees
        return if (withinRange(findDeltaHeading(currHeading, targetHeading, CommandTarget.TURN_DEFAULT),
                -3f, 3f)) CommandTarget.TURN_DEFAULT
        else cmdTurnDir
    }

    /**
     * Removes all persistent advanced command modes from the entity
     * @param entity the aircraft entity to remove the modes from
     */
    private fun removeAllAdvancedCommandModes(entity: Entity) {
        entity.remove<CommandHold>()
        entity.remove<CommandDirect>()
        entity.remove<CommandInitClimb>()
    }

    /**
     * Sets the turn direction back to default
     * @param entity the aircraft entity to update the command target turn direction
     */
    private fun unsetTurnDirection(entity: Entity) {
        entity[CommandTarget.mapper]?.turnDir = CommandTarget.TURN_DEFAULT
    }

    /**
     * Sets the missed approach altitude for the input aircraft entity
     *
     * Should be called on initial glide slope/visual capture
     * @param entity the aircraft entity
     */
    private fun setMissedApproachAltitude(entity: Entity) {
        val actingClearance = entity[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return
        val prevClearedAlt = actingClearance.clearedAlt
        actingClearance.clearedAlt = findMissedApproachAlt(actingClearance.route) ?:
                ((((GAME.gameServer?.airports?.get(entity[ArrivalAirport.mapper]?.arptId)?.entity?.get(Altitude.mapper)?.altitudeFt ?: 0f) / 1000).roundToInt() + 3) * 1000)
        if (prevClearedAlt != actingClearance.clearedAlt)
            entity += LatestClearanceChanged()
    }

    /**
     * Initiates a go around for the input aircraft entity
     * @param entity the aircraft entity
     * @param reason the reason for the go around
     */
    private fun initiateGoAround(entity: Entity, reason: Byte) {
        entity.apply {
            // Remove all approach components
            removeAllApproachComponents(entity)

            get(ClearanceAct.mapper)?.actingClearance?.clearanceState?.let {
                // Set route to first missed approach leg
                removeAllLegsTillMissed(it.route)

                // Un-clear approach and approach transition, set speed and altitude
                setMissedApproachAltitude(this)
                it.clearedApp = null
                it.clearedTrans = null
                it.clearedIas = 220
                get(CommandTarget.mapper)?.targetIasKt = 220

                setAllMissedLegsToNormal(it.route)
                // Clear any preceding altitude and speed restrictions since approach route is no longer being used
                // Departure behaviour is simulated by setting the OnGoAroundRoute component
                (get(LastRestrictions.mapper) ?: LastRestrictions().also { restr -> entity += restr }).let { restr ->
                    restr.minAltFt = null
                    restr.maxAltFt = null
                    restr.maxSpdKt = null
                }
            }

            // Update clearance states
            entity += ClearanceActChanged()
            entity += LatestClearanceChanged()
            val airport = GAME.gameServer?.airports?.get(get(ArrivalAirport.mapper)?.arptId)?.entity
            val arptAltitude = airport?.get(Altitude.mapper)?.altitudeFt?.roundToInt() ?: 0
            entity += ContactFromTower(arptAltitude + MathUtils.random(600, 1100))

            // Add the go around flag with reason
            entity += RecentGoAround(reason = reason)
            // Mark the aircraft as being on the go around route
            // This component will be removed the next time the aircraft heads towards the first waypoint
            // of the approach/transition route, or once the aircraft captures the approach track
            entity += OnGoAroundRoute()
            // Also add to the airport to pause departures till required time has passed
            airport?.add(RecentGoAround(reason = reason))
        }
    }

    /**
     * Returns the altitude the aircraft is targeting after emergency starts depending on emergency type
     * @param currentAlt the current altitude of the aircraft
     * @param type the type of emergency
     * @param arptElevation the elevation of the airport the aircraft is flying from
     */
    private fun getClearedAltitudeAfterEmergency(currentAlt: Float, type: Byte, arptElevation: Float): Int {
        // Minimum altitude to maintain is 2000 feet AGL
        val minLevelOffAlt = arptElevation + 2000

        // Pressure loss means emergency descent to 10000 feet
        if (type == EmergencyPending.PRESSURE_LOSS) return 10000
        return ceil(max(currentAlt, minLevelOffAlt) / 1000).toInt() * 1000
    }

    /**
     * Sets the current clearance and any pending clearances' cleared altitude
     * to the input altitude, clearing any altitude restrictions along the
     * route and updating the aircraft command target altitude
     * @param entity the aircraft entity
     * @param newAlt the new altitude to set to
     */
    private fun setCurrentAndPendingClearanceToAltitude(entity: Entity, newAlt: Int) {
        entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.let {
            it.clearedAlt = newAlt
            it.deactivateAllRouteRestrictions()
        } ?: return

        // Do the same for any pending clearances
        entity[PendingClearances.mapper]?.clearanceQueue?.let { queue ->
            for (i in 0 until queue.size) {
                queue[i].clearanceState.let {
                    it.clearedAlt = newAlt
                    it.deactivateAllRouteRestrictions()
                }
            }
        }

        entity += ClearanceActChanged()
        entity += LatestClearanceChanged()
    }

    /**
     * Sets the entity to be ready for approach after performing checklists (and
     * possibly dumping fuel), and may require the aircraft to remain the runway
     * after landing depending on emergency type and RNG
     */
    private fun setReadyForApproachStatus(entity: Entity) {
        val acInfo = entity[AircraftInfo.mapper] ?: return
        val emergency = entity[EmergencyPending.mapper] ?: return
        val immobilizeOnLanding = when (emergency.type) {
            EmergencyPending.BIRD_STRIKE, EmergencyPending.ENGINE_FAIL -> MathUtils.randomBoolean(0.7f)
            EmergencyPending.HYDRAULIC_FAIL, EmergencyPending.FUEL_LEAK -> true
            EmergencyPending.MEDICAL -> false
            EmergencyPending.PRESSURE_LOSS -> MathUtils.randomBoolean(0.3f)
            else -> false
        }
        // Stay on runway for 5-10 mins if needed
        if (immobilizeOnLanding) entity += ImmobilizeOnLanding(MathUtils.random(300f, 600f))
        GAME.gameServer?.sendAircraftReadyForApproach(acInfo.icaoCallsign, immobilizeOnLanding)
    }

    /**
     * Adds the MVA exclusion zones to an [aircraft]'s [route] for a flyover waypoint segment based on the [prevWpt]
     * (the flyover point) and the required [minAlt] (if any)
     */
    private fun addFlyoverExclusionZones(aircraft: Entity, prevWpt: Route.WaypointLeg, route: Route, minAlt: Int?) {
        if (route.size == 0) return
        val nextWpt = route[0] as? Route.WaypointLeg ?: return
        if (prevWpt.flyOver && nextWpt.legActive) {
            val routeZone = aircraft[DepartureRouteZone.mapper]?.sidZone ?: aircraft[ArrivalRouteZone.mapper]?.starZone
            routeZone?.addAll(getFlyoverMVAExclusionZones(aircraft, prevWpt, nextWpt, minAlt))
        }
    }
}