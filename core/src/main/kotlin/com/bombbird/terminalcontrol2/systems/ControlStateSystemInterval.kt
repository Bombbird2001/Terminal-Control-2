package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IntervalSystem
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.TRACK_EXTRAPOLATE_TIME_S
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*

/**
 * System that is responsible for aircraft control states, updating at a lower frequency of 1hz
 *
 * Used only in GameServer
 */
class ControlStateSystemInterval: IntervalSystem(1f) {
    companion object {
        private val contactFromTowerFamily: Family = allOf(Altitude::class, Position::class, ContactFromTower::class, GroundTrack::class, Controllable::class).get()
        private val contactToTowerFamily: Family = allOf(Altitude::class, ContactToTower::class, Controllable::class).get()
        private val minMaxOptIasFamily: Family = allOf(AircraftInfo::class, Altitude::class, ClearanceAct::class, CommandTarget::class).get()
        private val spdRestrFamily: Family = allOf(ClearanceAct::class, LastRestrictions::class, Position::class, Direction::class, GroundTrack::class, AircraftInfo::class, CommandTarget::class).get()
        private val contactFromCentreFamily: Family = allOf(Altitude::class, Position::class, ContactFromCentre::class, GroundTrack::class, Controllable::class).get()
        private val contactToCentreFamily: Family = allOf(Altitude::class, Position::class, ContactToCentre::class).get()
        private val checkSectorFamily: Family = allOf(Position::class, GroundTrack::class, Controllable::class).get()
        private val goAroundFamily: Family = allOf(RecentGoAround::class).get()
        private val pendingCruiseFamily: Family = allOf(PendingCruiseAltitude::class, ClearanceAct::class, AircraftInfo::class).get()
        private val divergentDepFamily: Family = allOf(DivergentDepartureAllowed::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val contactFromTowerFamilyEntities = FamilyWithListener.newServerFamilyWithListener(contactFromTowerFamily)
    private val contactToTowerFamilyEntities = FamilyWithListener.newServerFamilyWithListener(contactToTowerFamily)
    private val minMaxOptIasFamilyEntities = FamilyWithListener.newServerFamilyWithListener(minMaxOptIasFamily)
    private val spdRestrFamilyEntities = FamilyWithListener.newServerFamilyWithListener(spdRestrFamily)
    private val contactFromCentreFamilyEntities = FamilyWithListener.newServerFamilyWithListener(contactFromCentreFamily)
    private val contactToCentreFamilyEntities = FamilyWithListener.newServerFamilyWithListener(contactToCentreFamily)
    private val checkSectorFamilyEntities = FamilyWithListener.newServerFamilyWithListener(checkSectorFamily)
    private val goAroundFamilyEntities = FamilyWithListener.newServerFamilyWithListener(goAroundFamily)
    private val pendingCruiseFamilyEntities = FamilyWithListener.newServerFamilyWithListener(pendingCruiseFamily)
    private val divergentDepFamilyEntities = FamilyWithListener.newServerFamilyWithListener(divergentDepFamily)

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     */
    override fun updateInterval() {
        // Updating the minimum, maximum and optimal IAS for aircraft
        val minMaxOptIas = minMaxOptIasFamilyEntities.getEntities()
        for (i in 0 until minMaxOptIas.size()) {
            minMaxOptIas[i]?.apply {
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return@apply
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
        val spdRestr = spdRestrFamilyEntities.getEntities()
        for (i in 0 until spdRestr.size()) {
            spdRestr[i]?.apply {
                val actingClearance = get(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return@apply
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
                val nextRestr = getNextWaypointWithSpdRestr(actingClearance.route, alt.altitudeFt) ?: return@apply
                val nextMaxSpd = nextRestr.second
                val currMaxSpd = if (actingClearance.cancelLastMaxSpd) null else lastRestriction.maxSpdKt
                if (currMaxSpd != null && currMaxSpd <= nextMaxSpd) return@apply // Not required if next max speed is not lower than current max speed
                if (actingClearance.clearedIas <= nextMaxSpd) return@apply // Not required if next max speed is not lower than current cleared IAS
                // Physics - check distance needed to slow down from current speed to speed restriction
                val targetWptId = (actingClearance.route[0] as? Route.WaypointLeg)?.wptId ?: return@apply // Skip if next leg is not waypoint
                val targetPos = getServerWaypointMap()?.get(targetWptId)?.entity?.get(Position.mapper) ?: return@apply // Skip if waypoint not found or position not present
                val newGs = getPointTargetTrackAndGS(pos.x, pos.y, targetPos.x, targetPos.y, nextMaxSpd.toFloat(), dir, get(
                    AffectedByWind.mapper)).second
                // Calculate distance needed to decelerate to the speed restriction, plus a 2km leeway
                val distReqPx = mToPx(
                    calculateAccelerationDistanceRequired(
                        pxpsToKt(gs.trackVectorPxps.len()), newGs,
                        calculateMinAcceleration(
                            acInfo.aircraftPerf,
                            alt.altitudeFt,
                            calculateTASFromIAS(alt.altitudeFt, nextMaxSpd.toFloat()),
                            -500f, isApproachCaptured(this),
                            takingOff = false, takeoffGoAround = false)
                    ) + 2000)
                // Calculate distance remaining on route from the waypoint with speed restriction
                val distToGoPx = calculateDistToGo(pos, nextLeg, nextRestr.first, actingClearance.route)
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
        val contactFromTower = contactFromTowerFamilyEntities.getEntities()
        for (i in 0 until contactFromTower.size()) {
            contactFromTower[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val contact = get(ContactFromTower.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                if (alt.altitudeFt > contact.altitudeFt) {
                    controllable.sectorId = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S, true) ?: return@apply
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId,
                            server.sectorUUIDMap.get(controllable.sectorId)?.toString(), true, has(NeedsToInformOfGoAround.mapper))
                    }}
                    remove<NeedsToInformOfGoAround>()
                    remove<ContactFromTower>()
                }
            }
        }

        // Aircraft that are expected to switch from approach to tower
        val contactToTower = contactToTowerFamilyEntities.getEntities()
        for (i in 0 until contactToTower.size()) {
            contactToTower[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val contact = get(ContactToTower.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                if (alt.altitudeFt < contact.altitudeFt) {
                    controllable.sectorId = SectorInfo.TOWER
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, null,
                             needsContact = true, needsSayMissedApproach = false)
                    }}
                    remove<ContactToTower>()
                }
            }
        }

        // Aircraft that are expected to switch from centre to approach/departure
        val contactFromCentre = contactFromCentreFamilyEntities.getEntities()
        for (i in 0 until contactFromCentre.size()) {
            contactFromCentre[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val contact = get(ContactFromCentre.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                if (alt.altitudeFt < contact.altitudeFt) {
                    controllable.sectorId = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S, true) ?: return@apply
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId,
                            server.sectorUUIDMap.get(controllable.sectorId)?.toString(),
                            needsContact = true, needsSayMissedApproach = false)
                    }}
                    remove<ContactFromCentre>()
                }
            }
        }

        // Aircraft that are expected to switch from approach/departure to centre
        val contactToCentre = contactToCentreFamilyEntities.getEntities()
        for (i in 0 until contactToCentre.size()) {
            contactToCentre[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                val contact = get(ContactToCentre.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                // Hand over to center if aircraft is above the contact altitude and is proceeding on the route,
                // or if the aircraft is outside the sector boundaries
                if ((alt.altitudeFt > contact.altitudeFt && has(CommandDirect.mapper))
                    || getSectorForPosition(pos.x, pos.y, true) == null) {
                    controllable.sectorId = SectorInfo.CENTRE
                    GAME.gameServer?.also { server ->
                        get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign ->
                            server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, null,
                                 needsContact = true, needsSayMissedApproach = false)
                        }
                        server.incrementScoreBy(1, FlightType.DEPARTURE)
                        this += PendingCruiseAltitude(MathUtils.random(6f, 12f))
                    }
                    remove<ContactToCentre>()
                }
            }
        }

        // Checking of whether aircraft is still in player's sector; if not switch it to other player's sector
        val sectorWasSwapped = GAME.gameServer?.sectorJustSwapped ?: false
        val checkSector = checkSectorFamilyEntities.getEntities()
        for (i in 0 until checkSector.size()) {
            checkSector[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                if (controllable.sectorId == SectorInfo.TOWER || controllable.sectorId == SectorInfo.CENTRE) return@apply
                val expectedSector = getSectorForPosition(pos.x, pos.y, true)
                val expectedController = GAME.gameServer?.sectorUUIDMap?.get(expectedSector)
                if (expectedSector != null && expectedSector != controllable.sectorId) {
                    // If the aircraft is not in expected sector, get the extrapolated sector and check if it is currently
                    // under that sector's control; if it is then do nothing, otherwise set it to the extrapolated sector
                    val extrapolatedSector = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S, true)
                    if (controllable.sectorId != extrapolatedSector) {
                        controllable.sectorId = extrapolatedSector ?: expectedSector
                        val extrapolatedController = GAME.gameServer?.sectorUUIDMap?.get(extrapolatedSector)
                        controllable.controllerUUID = extrapolatedController
                        get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                            server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, controllable.controllerUUID?.toString(), !sectorWasSwapped, false)
                        }}
                    }
                } else if (expectedSector != null && expectedController != controllable.controllerUUID) {
                    // If sector has not changed, but the controller has changed (i.e. during connections/disconnections)
                    controllable.controllerUUID = expectedController
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.also { server ->
                        server.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId, controllable.controllerUUID?.toString(), !sectorWasSwapped, false)
                    }}
                }
            }
        }

        // Reset flag to stop suppressing sector change notification
        if (GAME.gameServer?.sectorJustSwapped == true) GAME.gameServer?.sectorJustSwapped = false

        // Check recent go-around and decrement the timer counter
        val goAround = goAroundFamilyEntities.getEntities()
        for (i in 0 until goAround.size()) {
            goAround[i]?.apply {
                val recentGA = get(RecentGoAround.mapper) ?: return@apply
                recentGA.timeLeft -= interval
                if (recentGA.timeLeft < 0) remove<RecentGoAround>()
            }
        }

        // ACC clears departures up to their cruising altitude, and to their next waypoint if not already set
        val pendingCruise = pendingCruiseFamilyEntities.getEntities()
        for (i in 0 until pendingCruise.size()) {
            pendingCruise[i]?.apply {
                val pendingCruiseAltitude = get(PendingCruiseAltitude.mapper) ?: return@apply
                val acInfo = get(AircraftInfo.mapper) ?: return@apply
                pendingCruiseAltitude.timeLeft -= interval
                if (pendingCruiseAltitude.timeLeft < 0) {
                    val currClearance = getLatestClearanceState(this) ?: return@apply
                    val newClearance = ClearanceState(currClearance.routePrimaryName,
                        Route.fromSerialisedObject(currClearance.route.getSerialisedObject()),
                        Route.fromSerialisedObject(currClearance.hiddenLegs.getSerialisedObject()),
                        null, currClearance.vectorTurnDir, calculateFinalCruiseAlt(this), false,
                        acInfo.aircraftPerf.tripIas, currClearance.minIas, currClearance.maxIas, currClearance.optimalIas,
                        currClearance.clearedApp, currClearance.clearedTrans, currClearance.cancelLastMaxSpd)
                    addNewClearanceToPendingClearances(this, newClearance, 0)
                    remove<PendingCruiseAltitude>()
                }
            }
        }

        // Check divergent departures and decrement the timer counter
        val divergentDepartures = divergentDepFamilyEntities.getEntities()
        for (i in 0 until divergentDepartures.size()) {
            divergentDepartures[i]?.apply {
                val divDep = get(DivergentDepartureAllowed.mapper) ?: return@apply
                divDep.timeLeft -= interval
                if (divDep.timeLeft < 0) remove<DivergentDepartureAllowed>()
            }
        }
    }

    /**
     * Calculates the final cruising altitude for the aircraft based on its route and max alt
     * @param aircraft the aircraft to calculate the final cruising altitude for
     */
    private fun calculateFinalCruiseAlt(aircraft: Entity): Int {
        val perfData = aircraft[AircraftInfo.mapper]?.aircraftPerf ?: return 0
        val clearanceAct = aircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return perfData.maxAlt
        val route = clearanceAct.route
        if (route.size == 0) return perfData.maxAlt

        // Calculate heading from current aircraft position to last waypoint on route
        val pos = aircraft[Position.mapper] ?: return perfData.maxAlt
        val lastWptId = (route[route.size - 1] as? Route.WaypointLeg)?.wptId ?: return perfData.maxAlt
        val lastWptPos = getServerWaypointMap()?.get(lastWptId)?.entity?.get(Position.mapper) ?: return perfData.maxAlt
        val requiredHdg = getRequiredTrack(pos.x, pos.y, lastWptPos.x, lastWptPos.y) + MAG_HDG_DEV
        return getCruiseAltForHeading(requiredHdg, perfData.maxAlt)
    }
}