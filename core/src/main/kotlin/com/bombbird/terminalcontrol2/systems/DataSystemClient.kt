package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.calculateAllDistToGo
import com.bombbird.terminalcontrol2.ui.datatag.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagText
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.bombbird.terminalcontrol2.utilities.pxToNm
import com.bombbird.terminalcontrol2.utilities.pxpsToKt
import ktx.ashley.allOf
import ktx.ashley.get
import kotlin.math.roundToInt

/**
 * System that is responsible solely for transmission of data
 *
 * Used only in RadarScreen
 */
class DataSystemClient: EntitySystem() {
    companion object {
        private val radarDataUpdateFamily: Family = allOf(Position::class, Direction::class, Speed::class, Altitude::class, RadarData::class).get()
        private val datatagUpdateFamily: Family = allOf(AircraftInfo::class, RadarData::class, CommandTarget::class, Datatag::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private var radarDataTimer = 0f
    private var datatagTimer = 0f

    private val radarDataUpdateFamilyEntities = FamilyWithListener.newClientFamilyWithListener(radarDataUpdateFamily)
    private val datatagUpdateFamilyEntities = FamilyWithListener.newClientFamilyWithListener(datatagUpdateFamily)

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Timer for updating radar returns and datatags
        radarDataTimer += deltaTime
        if (radarDataTimer > RADAR_REFRESH_INTERVAL_S) {
            val radarDataUpdate = radarDataUpdateFamilyEntities.getEntities()
            for (i in 0 until radarDataUpdate.size()) {
                radarDataUpdate[i]?.apply { updateAircraftRadarData(this) }
            }

            // We also update the dist to go information here
            updateDistToGo()

            radarDataTimer -= RADAR_REFRESH_INTERVAL_S
        }

        datatagTimer += deltaTime
        if (datatagTimer > DATATAG_REFRESH_INTERVAL_S) {
            val datatagUpdates = datatagUpdateFamilyEntities.getEntities()
            for (i in 0 until datatagUpdates.size()) {
                datatagUpdates[i]?.apply { updateAircraftDatatagText(this) }
            }

            datatagTimer -= DATATAG_REFRESH_INTERVAL_S
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
        val groundTrack = get(GroundTrack.mapper) ?: return@apply
        val radarData = get(RadarData.mapper) ?: return@apply
        radarData.position.x = pos.x
        radarData.position.y = pos.y
        radarData.direction.trackUnitVector = Vector2(dir.trackUnitVector)
        radarData.speed.speedKts = spd.speedKts
        radarData.speed.vertSpdFpm = spd.vertSpdFpm
        radarData.speed.angularSpdDps = spd.angularSpdDps
        radarData.altitude.altitudeFt = alt.altitudeFt
        radarData.groundSpeed = groundTrack.trackVectorPxps.len().let { pxpsToKt(it) }
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

/**
 * Updates the dist to go information for the selected aircraft depending on settings, else hides dist to go information
 */
fun updateDistToGo() {
    // Clear all waypoint dist to go display
    val allWpts = GAME.gameClientScreen?.waypoints ?: return
    for (wpt in allWpts.values.toList()) {
        wpt.entity[GenericLabels.mapper]?.labels?.get(1)?.updateText("")
    }

    if (SHOW_DIST_TO_GO == SHOW_DIST_TO_GO_HIDE) return

    val selectedAircraft = GAME.gameClientScreen?.selectedAircraft?.entity ?: return
    val flightType = selectedAircraft[FlightType.mapper]?.type ?: return
    if (SHOW_DIST_TO_GO == SHOW_DIST_TO_GO_ARRIVALS && flightType != FlightType.ARRIVAL) return

    val radarData = selectedAircraft[RadarData.mapper] ?: return
    val route = selectedAircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route ?: return
    if (route.size == 0) return

    val distances = calculateAllDistToGo(radarData.position, route[0], route[route.size - 1], route)
    for (i in 0 until distances.size) {
        val dist = distances[i]
        val roundedDist = (pxToNm(dist.distToGoPx) * 10).roundToInt() / 10f
        allWpts[dist.wpt.wptId]?.entity?.get(GenericLabels.mapper)?.labels?.get(1)?.apply {
            updateText(roundedDist.toString())
            xOffset = -label.prefWidth / 2
        }
    }
}
