package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import ktx.ashley.*

/**
 * System that is responsible for aircraft control states
 *
 * Used only in GameServer
 * */
class ControlStateSystem: EntitySystem() {
    private val latestClearanceChangedFamily: Family = allOf(LatestClearanceChanged::class, AircraftInfo::class, ClearanceAct::class).get()
    private val pendingFamily: Family = allOf(PendingClearances::class, ClearanceAct::class).get()

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
    }
}