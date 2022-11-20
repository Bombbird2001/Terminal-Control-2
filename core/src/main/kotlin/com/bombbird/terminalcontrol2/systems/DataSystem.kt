package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.RadarData
import com.bombbird.terminalcontrol2.components.TrailInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.TRAIL_DOT_UPDATE_INTERVAL_S
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.collections.GdxArray

/**
 * System that is responsible solely for transmission of data
 *
 * Used only in GameServer
 */
class DataSystem: EntitySystem() {
    var trailDotTimer = 0f

    private val trailInfoUpdateFamily: Family = allOf(AircraftInfo::class, RadarData::class, TrailInfo::class).get()

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Timer for updating trail info
        trailDotTimer += deltaTime
        if (trailDotTimer > TRAIL_DOT_UPDATE_INTERVAL_S) {
            val trailDotUpdates = engine.getEntitiesFor(trailInfoUpdateFamily)
            val trailUpdates = GdxArray<Pair<String, Position>>()
            for (i in 0 until trailDotUpdates.size()) {
                trailDotUpdates[i]?.apply {
                    val acInfo = get(AircraftInfo.mapper) ?: return@apply
                    val trailInfo = get(TrailInfo.mapper) ?: return@apply
                    val radarData = get(RadarData.mapper) ?: return@apply
                    trailInfo.positions.addFirst(radarData.position)
                    trailUpdates.add(Pair(acInfo.icaoCallsign, radarData.position))
                }
            }

            GAME.gameServer?.sendAircraftTrailDotUpdate(trailUpdates)
            trailDotTimer -= TRAIL_DOT_UPDATE_INTERVAL_S
        }
    }
}