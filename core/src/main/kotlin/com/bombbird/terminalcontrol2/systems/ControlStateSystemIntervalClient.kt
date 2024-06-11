package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.bombbird.terminalcontrol2.utilities.getSectorForExtrapolatedPosition
import ktx.ashley.*

/**
 * System that is responsible for miscellaneous aircraft control state matters on the client, updating at lower frequency
 * of 1hz
 *
 * Used only in RadarScreen
 */
class ControlStateSystemIntervalClient: IntervalSystem(1f) {
    companion object {
        private val handoverUpdateFamily: Family = allOf(Controllable::class, GroundTrack::class, Position::class, Altitude::class).get()
        private val vectorFamily = allOf(ClearanceAct::class, Controllable::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val handoverUpdateFamilyEntities = FamilyWithListener.newClientFamilyWithListener(handoverUpdateFamily)
    private val vectorFamilyEntities = FamilyWithListener.newClientFamilyWithListener(vectorFamily)

    /** Main update function */
    override fun updateInterval() {
        val handoverUpdate = handoverUpdateFamilyEntities.getEntities()
        for (i in 0 until handoverUpdate.size()) {
            handoverUpdate[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val alt = get(Altitude.mapper) ?: return@apply
                val track = get(GroundTrack.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                CLIENT_SCREEN?.let {
                    if (controllable.sectorId == it.playerSector && controllable.controllerUUID == myUuid) {
                        if (has(LocalizerCaptured.mapper) || has(GlideSlopeCaptured.mapper) || has(VisualCaptured.mapper)) {
                            // Aircraft has captured localizer/glide slope/visual approach path, allow handover to tower
                            this += CanBeHandedOver(SectorInfo.TOWER)
                            updateUIPaneHandover(this)
                            return@apply
                        }
                        if (has(ContactToCentre.mapper) && alt.altitudeFt >= MAX_ALT - 1900) {
                            // Aircraft is expected to contact ACC, and is less than 1900 feet below the max TMA altitude,
                            // allow handover to ACC
                            this += CanBeHandedOver(SectorInfo.CENTRE)
                            updateUIPaneHandover(this)
                            return@apply
                        }
                        val extrapolatedSectorId = getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S, false)
                        if (extrapolatedSectorId != null && extrapolatedSectorId >= 0 && extrapolatedSectorId != it.playerSector) {
                            // Aircraft is entering another player controlled sector in 30 seconds, allow handover to them
                            this += CanBeHandedOver(extrapolatedSectorId)
                            updateUIPaneHandover(this)
                            return@apply
                        }
                    }

                    // No targets to hand over to, remove component
                    remove<CanBeHandedOver>()
                    updateUIPaneHandover(this)
                } ?: return@apply
            }
        }

        // Update timer for checking vector achievement
        // Host singleplayer speed up allowed; engine update rate already takes into account game speed up
        GAME.achievementManager.incrementVectorVictorCounter(
            (vectorFamilyEntities.getEntities().filter {
                val controllable = it[Controllable.mapper] ?: return@filter false
                it[ClearanceAct.mapper]?.actingClearance?.clearanceState?.vectorHdg != null
                        && controllable.sectorId == CLIENT_SCREEN?.playerSector
                        && controllable.controllerUUID == myUuid
            }.size * interval).toInt()
        )
    }

    /**
     * Updates the UI pane's handover/acknowledge button state
     * @param aircraft the aircraft entity to check for an update
     */
    private fun updateUIPaneHandover(aircraft: Entity) {
        CLIENT_SCREEN?.apply {
            val callsign = aircraft[AircraftInfo.mapper]?.icaoCallsign ?: return
            if (selectedAircraft?.entity?.get(AircraftInfo.mapper)?.icaoCallsign == callsign) {
                uiPane.updateHandoverAckButtonState(aircraft)
            }
        }
    }
}