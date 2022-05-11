package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.remove

/** System that is responsible for aircraft control states */
class ControlStateSystem: EntitySystem() {

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Aircraft that have their clearance states changed
        val clearanceChangedFamily = allOf(ClearanceChanged::class, AircraftInfo::class, ClearanceAct::class).get()
        val clearanceChanged = engine.getEntitiesFor(clearanceChangedFamily)
        for (i in 0 until clearanceChanged.size()) {
            clearanceChanged[i]?.apply {
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                // Try to get the last pending clearance; if no pending clearances exist, use the existing clearance
                (get(PendingClearances.mapper)?.clearanceArray?.last()?.second ?: get(ClearanceAct.mapper)?.clearance ?: return@apply).apply {
                    GAME.gameServer?.sendAircraftClearanceStateUpdateToAll(aircraftInfo.icaoCallsign, routePrimaryName, route, hiddenLegs, vectorHdg, clearedAlt, clearedIas)
                }
                remove<ClearanceChanged>()
            }
        }

        // Aircraft that are expected to switch from tower to approach/departure
        val contactFromTowerFamily = allOf(Altitude::class, ContactFromTower::class, Controllable::class).get()
        val contactFromTower = engine.getEntitiesFor(contactFromTowerFamily)
        for (i in 0 until contactFromTower.size()) {
            contactFromTower[i]?.apply {
                val alt = get(Altitude.mapper) ?: return@apply
                val contact = get(ContactFromTower.mapper) ?: return@apply
                val controllable = get(Controllable.mapper) ?: return@apply
                if (alt.altitudeFt > contact.altitudeFt) {
                    // TODO Set to sector of the correct player
                    controllable.sectorId = 0
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> GAME.gameServer?.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId) }
                    remove<ContactFromTower>()
                }
            }
        }
    }
}