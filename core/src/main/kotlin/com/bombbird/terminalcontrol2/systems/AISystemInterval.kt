package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.AircraftRequestChildren
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import ktx.ashley.allOf
import ktx.ashley.get

/**
 * System that is responsible for miscellaneous aircraft AI behaviour, updating at a lower frequency of 1hz
 *
 * Used only in GameServer
 */
class AISystemInterval: IntervalSystem(1f) {
    companion object {
        private val aircraftRequestFamily = allOf(AircraftRequestChildren::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val aircraftRequestEntities = FamilyWithListener.newServerFamilyWithListener(aircraftRequestFamily)

    override fun updateInterval() {
        // Aircraft request status update
        val requestEntities = aircraftRequestEntities.getEntities()
        for (i in 0 until requestEntities.size()) {
            requestEntities[i]?.apply {
                val acInfo = get(AircraftInfo.mapper) ?: return@apply
                val requests = get(AircraftRequestChildren.mapper)?.requests ?: return@apply
                for (j in requests.size - 1 downTo 0) {
                    val request = requests[j]
                    val activate = request.updateShouldSendRequest(1, this)
                    if (activate.isPresent) {
                        GAME.gameServer?.sendAircraftRequest(acInfo.icaoCallsign, request.requestType, activate.get())
                    }
                    if (request.isDone) requests.removeIndex(j)
                }
            }
        }
    }
}