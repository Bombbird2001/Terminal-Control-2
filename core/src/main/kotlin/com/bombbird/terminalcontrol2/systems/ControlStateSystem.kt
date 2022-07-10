package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.TRACK_EXTRAPOLATE_TIME_S
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.calculateDistToGo
import com.bombbird.terminalcontrol2.navigation.getNextWaypointWithSpdRestr
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*

/**
 * System that is responsible for aircraft control states
 *
 * Used only in GameServer
 * */
class ControlStateSystem(override val updateTimeS: Float = 0f): EntitySystem(), LowFreqUpdate {
    override var timer = 0f

    private val latestClearanceChangedFamily: Family = allOf(LatestClearanceChanged::class, AircraftInfo::class, ClearanceAct::class).get()
    private val contactFromTowerFamily: Family = allOf(Altitude::class, Position::class, ContactFromTower::class, GroundTrack::class, Controllable::class).get()
    private val contactToTowerFamily: Family = allOf(Altitude::class, ContactToTower::class, Controllable::class).get()
    private val pendingFamily: Family = allOf(PendingClearances::class, ClearanceAct::class).get()
    private val minMaxOptIasFamily: Family = allOf(AircraftInfo::class, Altitude::class, ClearanceAct::class, CommandTarget::class).get()
    private val spdRestrFamily: Family = allOf(ClearanceAct::class, LastRestrictions::class, Position::class, Direction::class, GroundTrack::class, AircraftInfo::class, CommandTarget::class).get()
    private val contactFromCentreFamily: Family = allOf(Altitude::class, Position::class, ContactFromCentre::class, GroundTrack::class, Controllable::class).get()
    private val contactToCentreFamily: Family = allOf(Altitude::class, Position::class, ContactToCentre::class).get()
    private val checkSectorFamily: Family = allOf(Position::class, GroundTrack::class, Controllable::class).get()

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Aircraft that have their clearance states changed
        val clearanceChanged = engine.getEntitiesFor(latestClearanceChangedFamily)
        for (i in 0 until clearanceChanged.size()) {
            clearanceChanged[i]?.let { entity ->
                val aircraftInfo = entity[AircraftInfo.mapper] ?: return@let
                // Try to get the last pending clearance; if no pending clearances exist, use the existing clearance
                entity[PendingClearances.mapper]?.clearanceQueue.also {
                    val clearanceToUse = if (it != null && it.size > 0) it.last()?.clearanceState ?: return@also
                    else {
                        entity.remove<PendingClearances>()
                        entity[ClearanceAct.mapper]?.actingClearance?.actingClearance ?: return@also
                    }
                    clearanceToUse.apply {
                        GAME.gameServer?.sendAircraftClearanceStateUpdateToAll(aircraftInfo.icaoCallsign, routePrimaryName, route, hiddenLegs,
                            vectorHdg, vectorTurnDir, clearedAlt, clearedIas, minIas, maxIas, optimalIas, clearedApp, clearedTrans)
                    }
                }
                entity.remove<LatestClearanceChanged>()
            }
        }

        // Aircraft that have pending clearances (due to 2s pilot response)
        val pendingClearances = engine.getEntitiesFor(pendingFamily)
        for (i in 0 until pendingClearances.size()) {
            pendingClearances[i]?.apply {
                get(PendingClearances.mapper)?.clearanceQueue?.let { queue ->
                    if (queue.notEmpty()) {
                        val firstEntry = queue.first()
                        firstEntry.timeLeft -= deltaTime
                        if (firstEntry.timeLeft < 0) {
                            get(ClearanceAct.mapper)?.actingClearance?.let { acting ->
                                acting.updateClearanceAct(firstEntry.clearanceState, this)
                                this += ClearanceActChanged()
                            } ?: return@apply
                            queue.removeFirst()
                        }
                    }
                    if (queue.isEmpty) remove<PendingClearances>()
                } ?: return@apply
            }
        }

        checkLowFreqUpdate(deltaTime)
    }

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     * */
    override fun lowFreqUpdate() {
        // Updating the minimum, maximum and optimal IAS for aircraft
        val minMaxOptIas = engine.getEntitiesFor(minMaxOptIasFamily)
        for (i in 0 until minMaxOptIas.size()) {
            minMaxOptIas[i]?.apply {
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.actingClearance ?: return@apply
                val cmd = get(CommandTarget.mapper) ?: return@apply
                val prevSpds = Triple(clearanceAct.minIas, clearanceAct.maxIas, clearanceAct.optimalIas)
                val spds = getMinMaxOptimalIAS(this)
                clearanceAct.minIas = spds.first
                clearanceAct.maxIas = spds.second
                clearanceAct.optimalIas = spds.third
                val prevClearedIas = clearanceAct.clearedIas
                if (clearanceAct.clearedIas == prevSpds.third && spds.third != prevSpds.third) {
                    clearanceAct.clearedIas = spds.third
                    cmd.targetIasKt = clearanceAct.clearedIas
                }
                if (Triple(clearanceAct.minIas, clearanceAct.maxIas, clearanceAct.optimalIas) != prevSpds || prevClearedIas != clearanceAct.clearedIas) this += LatestClearanceChanged()
            }
        }

        // Check whether the aircraft is approaching a lower speed restriction, and set the reduced speed before
        // reaching the waypoint, so it crosses at the speed restriction
        val spdRestr = engine.getEntitiesFor(spdRestrFamily)
        for (i in 0 until spdRestr.size()) {
            spdRestr[i]?.apply {
                val actingClearance = get(ClearanceAct.mapper)?.actingClearance?.actingClearance ?: return@apply
                val lastRestriction = get(LastRestrictions.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val dir = get(Direction.mapper) ?: return@apply
                val gs = get(GroundTrack.mapper) ?: return@apply
                val acInfo = get(AircraftInfo.mapper) ?: return@apply
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                if (actingClearance.vectorHdg != null) return@apply // If aircraft is being vectored, this check is not needed

                // If no waypoints in route, this check is not needed
                val nextLeg = if (actingClearance.route.size > 0) actingClearance.route[0] else return@apply
                // Get the first upcoming waypoint with a speed restriction; if no waypoints with speed restriction, this check is not needed
                val nextRestrWpt = getNextWaypointWithSpdRestr(actingClearance.route) ?: return@apply
                val nextMaxSpd = nextRestrWpt.maxSpdKt ?: return@apply // This value shouldn't be null in the first place, but just in case
                val currMaxSpd = lastRestriction.maxSpdKt
                if (currMaxSpd != null && currMaxSpd <= nextMaxSpd) return@apply // Not required if next max speed is not lower than current max speed
                if (actingClearance.clearedIas <= nextMaxSpd) return@apply // Not required if next max speed is not lower than current cleared IAS
                // Physics - check distance needed to slow down from current speed to speed restriction
                val targetWptId = (actingClearance.route[0] as? Route.WaypointLeg)?.wptId ?: return@apply // Skip if next leg is not waypoint
                val targetPos = GAME.gameServer?.waypoints?.get(targetWptId)?.entity?.get(Position.mapper) ?: return@apply // Skip if waypoint not found or position not present
                val newGs = getPointTargetTrackAndGS(pos.x, pos.y, targetPos.x, targetPos.y, nextMaxSpd.toFloat(), dir, get(AffectedByWind.mapper)).second
                val approach = has(LocalizerCaptured.mapper) || has(GlideSlopeCaptured.mapper) || has(VisualCaptured.mapper) || (get(CirclingApproach.mapper)?.phase ?: 0) >= 1
                // Calculate distance needed to decelerate to the speed restriction, plus a 2km leeway
                val distReqPx = mToPx(calculateAccelerationDistanceRequired(pxpsToKt(gs.trackVectorPxps.len()), newGs,
                    calculateMinAcceleration(acInfo.aircraftPerf, alt.altitudeFt, calculateTASFromIAS(alt.altitudeFt, nextMaxSpd.toFloat()), -500f, approach, takingOff = false, takeoffClimb = false)) + 2000)
                // Calculate distance remaining on route from the waypoint with speed restriction
                val distToGoPx = calculateDistToGo(pos, nextLeg, nextRestrWpt, actingClearance.route)
                if (distToGoPx < distReqPx) {
                    // lastRestriction.maxSpdKt = nextMaxSpd
                    if (actingClearance.clearedIas > nextMaxSpd) cmdTarget.targetIasKt = nextMaxSpd
                    val prevMaxIas = actingClearance.maxIas
                    val prevClearedIas = actingClearance.clearedIas
                    if (actingClearance.maxIas > nextMaxSpd) actingClearance.maxIas = nextMaxSpd
                    actingClearance.clearedIas = nextMaxSpd
                    if (prevMaxIas != actingClearance.maxIas || prevClearedIas != actingClearance.clearedIas) {
                        val pendingClearances = get(PendingClearances.mapper)
                        // Send clearance change to clients if no pending clearance exists
                        if (pendingClearances == null || pendingClearances.clearanceQueue.isEmpty) this += LatestClearanceChanged()
                    }
                }
            }
        }

        // Aircraft that are expected to switch from tower to approach/departure
        val contactFromTower = engine.getEntitiesFor(contactFromTowerFamily)
        for (i in 0 until contactFromTower.size()) {
            contactFromTower[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val contact = get(ContactFromTower.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                if (alt.altitudeFt > contact.altitudeFt) {
                    controllable.sectorId = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S) ?: return@apply
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId,
                            GAME.gameServer?.sectorUUIDMap?.get(controllable.sectorId)?.toString())
                    }}
                    remove<ContactFromTower>()
                }
            }
        }

        // Aircraft that are expected to switch from approach to tower
        val contactToTower = engine.getEntitiesFor(contactToTowerFamily)
        for (i in 0 until contactToTower.size()) {
            contactToTower[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val contact = get(ContactToTower.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                if (alt.altitudeFt < contact.altitudeFt) {
                    controllable.sectorId = SectorInfo.TOWER
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, null)
                    }}
                    remove<ContactToTower>()
                }
            }
        }

        // Aircraft that are expected to switch from centre to approach/departure
        val contactFromCentre = engine.getEntitiesFor(contactFromCentreFamily)
        for (i in 0 until contactFromCentre.size()) {
            contactFromCentre[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val contact = get(ContactFromCentre.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                if (alt.altitudeFt < contact.altitudeFt) {
                    controllable.sectorId = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S) ?: return@apply
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId,
                            GAME.gameServer?.sectorUUIDMap?.get(controllable.sectorId)?.toString())
                    }}
                    remove<ContactFromCentre>()
                }
            }
        }

        // Aircraft that are expected to switch from approach/departure to centre
        val contactToCentre = engine.getEntitiesFor(contactToCentreFamily)
        for (i in 0 until contactToCentre.size()) {
            contactToCentre[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val contact = get(ContactFromCentre.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                if (alt.altitudeFt > contact.altitudeFt || getSectorForPosition(pos.x, pos.y) == null) {
                    controllable.sectorId = SectorInfo.CENTRE
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, null)
                    }}
                    remove<ContactFromCentre>()
                }
            }
        }

        // Checking of whether aircraft is still in player's sector; if not switch it other player's sector
        val checkSector = engine.getEntitiesFor(checkSectorFamily)
        for (i in 0 until checkSector.size()) {
            checkSector[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                if (controllable.sectorId == SectorInfo.TOWER || controllable.sectorId == SectorInfo.CENTRE) return@apply
                val expectedSector = getSectorForPosition(pos.x, pos.y)
                val expectedController = GAME.gameServer?.sectorUUIDMap?.get(expectedSector)
                if (expectedSector != null && expectedSector != controllable.sectorId) {
                    // If the sector has changed for the aircraft
                    controllable.sectorId = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S) ?: expectedSector
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, controllable.controllerUUID?.toString())
                    }}
                } else if (expectedSector != null && expectedController != controllable.controllerUUID) {
                    // If sector has not changed, but the controller has changed (i.e. during connections/disconnections)
                    controllable.controllerUUID = expectedController
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, controllable.controllerUUID?.toString())
                    }}
                }
            }
        }
    }
}