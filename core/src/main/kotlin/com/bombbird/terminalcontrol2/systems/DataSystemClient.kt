package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.RADAR_REFRESH_INTERVAL_S
import com.bombbird.terminalcontrol2.ui.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.updateDatatagText
import ktx.ashley.allOf
import ktx.ashley.get

/**
 * System that is responsible solely for transmission of data
 *
 * Used only in RadarScreen
 */
class DataSystemClient: EntitySystem() {
    var radarDataTimer = 0f

    private val radarDataUpdateFamily: Family = allOf(Position::class, Direction::class, Speed::class, Altitude::class, RadarData::class).get()
    private val datatagUpdateFamily: Family = allOf(AircraftInfo::class, RadarData::class, CommandTarget::class, Datatag::class).get()

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Timer for updating radar returns and datatags
        radarDataTimer += deltaTime
        if (radarDataTimer > RADAR_REFRESH_INTERVAL_S) {
            val radarDataUpdate = engine.getEntitiesFor(radarDataUpdateFamily)
            for (i in 0 until radarDataUpdate.size()) {
                radarDataUpdate[i]?.apply { updateAircraftRadarData(this) }
            }

            val datatagUpdates = engine.getEntitiesFor(datatagUpdateFamily)
            for (i in 0 until datatagUpdates.size()) {
                datatagUpdates[i]?.apply { updateAircraftDatatagText(this) }
            }
            radarDataTimer -= RADAR_REFRESH_INTERVAL_S
        }
    }
}

/**
 * Updates the radar screen displayed data with the latest aircraft data
 * @param aircraft the aircraft to update
 */
fun updateAircraftRadarData(aircraft: Entity) {
    aircraft.apply {
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

/**
 * Updates the datatag text for the aircraft
 * @param aircraft the aircraft to update
 */
fun updateAircraftDatatagText(aircraft: Entity) {
    aircraft.apply {
        val datatag = get(Datatag.mapper) ?: return@apply
        updateDatatagText(datatag, getNewDatatagLabelText(this, datatag.minimised))
    }
}
