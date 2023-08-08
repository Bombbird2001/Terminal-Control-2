package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAX_TRAIL_DOTS
import com.bombbird.terminalcontrol2.global.TRAIL_DOT_UPDATE_INTERVAL_S
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.collections.GdxArray

/**
 * System that is responsible solely for transmission of data
 *
 * Used only in GameServer
 */
class DataSystem: EntitySystem() {
    companion object {
        private val trailInfoUpdateFamily: Family = allOf(AircraftInfo::class, Position::class, TrailInfo::class)
            .exclude(WaitingTakeoff::class, TakeoffRoll::class, LandingRoll::class).get()
    }

    private val trailInfoUpdateFamilyEntities = FamilyWithListener.newServerFamilyWithListener(trailInfoUpdateFamily)

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Timer for updating trail info
        GAME.gameServer?.also { gs ->
            gs.trailDotTimer += deltaTime
            if (gs.trailDotTimer > TRAIL_DOT_UPDATE_INTERVAL_S) {
                val trailDotUpdates = trailInfoUpdateFamilyEntities.getEntities()
                val trailUpdates = GdxArray<Pair<String, Position>>()
                for (i in 0 until trailDotUpdates.size()) {
                    trailDotUpdates[i]?.apply {
                        val acInfo = get(AircraftInfo.mapper) ?: return@apply
                        val trailInfo = get(TrailInfo.mapper) ?: return@apply
                        val pos = get(Position.mapper) ?: return@apply
                        // Cap dots at MAX_TRAIL_DOTS
                        while (trailInfo.positions.size >= MAX_TRAIL_DOTS)
                            trailInfo.positions.removeLast()
                        trailInfo.positions.addFirst(pos.copy())
                        trailUpdates.add(Pair(acInfo.icaoCallsign, pos))
                    }
                }

                if (!trailUpdates.isEmpty) GAME.gameServer?.sendAircraftTrailDotUpdate(trailUpdates)
                gs.trailDotTimer -= TRAIL_DOT_UPDATE_INTERVAL_S
            }
        }
    }
}