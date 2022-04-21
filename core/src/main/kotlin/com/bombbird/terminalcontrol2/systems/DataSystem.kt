package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Variables
import ktx.ashley.allOf
import ktx.ashley.get

/** System that is responsible solely for transmission of data */
class DataSystem: EntitySystem() {
    var radarDataTimer = 0f

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Timer for updating radar returns
        radarDataTimer += deltaTime
        if (radarDataTimer > Variables.RADAR_REFRESH_INTERVAL_S) {
            val radarDataUpdateFamily = allOf(Position::class, Direction::class, Speed::class, Altitude::class, RadarData::class).get()
            val radarDataUpdate = engine.getEntitiesFor(radarDataUpdateFamily)
            for (i in 0 until radarDataUpdate.size()) {
                radarDataUpdate[i]?.apply {
                    val pos = get(Position.mapper) ?: return@apply
                    val dir = get(Direction.mapper) ?: return@apply
                    val spd = get(Speed.mapper) ?: return@apply
                    val alt = get(Altitude.mapper) ?: return@apply
                    val radarData = get(RadarData.mapper) ?: return@apply
                    radarData.position.x = pos.x
                    radarData.position.y = pos.y
                    radarData.direction.trackUnitVector = Vector2(dir.trackUnitVector)
                    radarData.speed.speedKts = spd.speedKts
                    radarData.speed.vertSpdFpm = spd.vertSpdFpm
                    radarData.speed.angularSpdDps = spd.angularSpdDps
                    radarData.altitude.altitudeFt = alt.altitudeFt
                }
            }
            radarDataTimer -= Variables.RADAR_REFRESH_INTERVAL_S
        }
    }
}