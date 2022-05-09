package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.remove
import kotlin.math.roundToInt

/** System that is responsible for aircraft control states */
class ControlStateSystem: EntitySystem() {

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Aircraft that have their clearance states changed
        val clearanceChangedFamily = allOf(ClearanceChanged::class, AircraftInfo::class, ClearedRoute::class, ClearedAltitude::class, CommandTarget::class).get()
        val clearanceChanged = engine.getEntitiesFor(clearanceChangedFamily)
        for (i in 0 until clearanceChanged.size()) {
            clearanceChanged[i]?.apply {
                val aircraftInfo = get(AircraftInfo.mapper) ?: return@apply
                val clearedRoute = get(ClearedRoute.mapper) ?: return@apply
                val clearedAlt = get(ClearedAltitude.mapper) ?: return@apply
                val cmdTarget = get(CommandTarget.mapper) ?: return@apply
                Constants.GAME.gameServer?.sendAircraftClearanceStateUpdateToAll(
                    aircraftInfo.icaoCallsign, clearedRoute.primaryName, clearedRoute.route, clearedRoute.hiddenLegs,
                    if (get(CommandDirect.mapper) != null || get(CommandHold.mapper) != null) null else cmdTarget.targetHdgDeg.roundToInt().toShort(),
                    clearedAlt.altitudeFt)
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
                    get(AircraftInfo.mapper)?.icaoCallsign?.let { callsign -> Constants.GAME.gameServer?.sendAircraftSectorUpdateTCPToAll(callsign, controllable.sectorId) }
                    remove<ContactFromTower>()
                }
            }
        }
    }
}