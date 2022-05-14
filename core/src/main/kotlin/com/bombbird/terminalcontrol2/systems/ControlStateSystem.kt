package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.getMinMaxOptimalIAS
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.remove

/**
 * System that is responsible for aircraft control states
 *
 * Used only in GameServer
 * */
class ControlStateSystem: EntitySystem(), LowFreqUpdate {

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Aircraft that have their clearance states changed
        val latestClearanceChangedFamily = allOf(LatestClearanceChanged::class, AircraftInfo::class, ClearanceAct::class).get()
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
                        GAME.gameServer?.sendAircraftClearanceStateUpdateToAll(aircraftInfo.icaoCallsign, routePrimaryName, route, hiddenLegs, vectorHdg, clearedAlt, clearedIas, minIas, maxIas, optimalIas)
                    }
                }
                entity.remove<LatestClearanceChanged>()
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

        // Aircraft that have pending clearances (due to 2s pilot response)
        val pendingFamily = allOf(PendingClearances::class, ClearanceAct::class).get()
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
    }

    /**
     * Secondary update system, for operations that can be updated at a lower frequency and do not rely on deltaTime
     * (e.g. can be derived from other values without needing a time variable)
     *
     * Values that require constant updating or relies on deltaTime should be put in the main [update] function
     * */
    override fun lowFreqUpdate() {
        // Updating the minimum, maximum and optimal IAS for aircraft
        val minMaxOptIasFamily = allOf(AircraftInfo::class, Altitude::class, ClearanceAct::class).get()
        val minMaxOptIas = engine.getEntitiesFor(minMaxOptIasFamily)
        for (i in 0 until minMaxOptIas.size()) {
            minMaxOptIas[i]?.apply {
                val clearanceAct = get(ClearanceAct.mapper)?.actingClearance?.actingClearance ?: return@apply
                val prevSpds = Triple(clearanceAct.minIas, clearanceAct.maxIas, clearanceAct.optimalIas)
                val spds = getMinMaxOptimalIAS(this)
                clearanceAct.minIas = spds.first
                clearanceAct.maxIas = spds.second
                clearanceAct.optimalIas = spds.third
                // TODO update current and all pending cleared IAS with updated optimal IAS if the cleared speed is the previous optimal IAS
                if (spds != prevSpds) this += LatestClearanceChanged()
            }
        }
    }
}