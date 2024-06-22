package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.calculateAllDistToGo
import com.bombbird.terminalcontrol2.ui.datatag.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagText
import com.bombbird.terminalcontrol2.ui.isMobile
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.math.unaryMinus
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
        private val wakeSequenceFamily = allOf(ClearanceAct::class, ArrivalAirport::class).get()
        private val approachWakeSequenceFamily = allOf(ApproachWakeSequence::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private var radarDataTimer = 0f
    private var datatagTimer = 0f

    private val radarDataUpdateFamilyEntities = FamilyWithListener.newClientFamilyWithListener(radarDataUpdateFamily)
    private val datatagUpdateFamilyEntities = FamilyWithListener.newClientFamilyWithListener(datatagUpdateFamily)
    private val wakeSequenceFamilyEntities = FamilyWithListener.newClientFamilyWithListener(wakeSequenceFamily)
    private val approachWakeSequenceFamilyEntities = FamilyWithListener.newClientFamilyWithListener(approachWakeSequenceFamily)

    /** Main update function */
    override fun update(deltaTime: Float) {
        // Timer for updating radar returns and datatags
        radarDataTimer += deltaTime
        if (radarDataTimer > RADAR_REFRESH_INTERVAL_S) {
            val radarDataUpdate = radarDataUpdateFamilyEntities.getEntities()
            for (i in 0 until radarDataUpdate.size()) {
                radarDataUpdate[i]?.apply { updateAircraftRadarData(this) }
            }

            // We also update the dist to go, waypoint restriction information here
            updateDistToGo()
            updateWaypointRestr()

            // Also update wake separation lines
            // Clear all approach wake sequences
            val approachWakeSequence = approachWakeSequenceFamilyEntities.getEntities()
            for (i in 0 until approachWakeSequence.size()) {
                approachWakeSequence[i]?.apply {
                    get(ApproachWakeSequence.mapper)?.aircraftDist?.clear()
                }
            }

            // Wake sequencing calculation for aircraft established or about to be established on localizer
            val wakeSequence = wakeSequenceFamilyEntities.getEntities()
            for (i in 0 until wakeSequence.size()) {
                wakeSequence[i]?.apply {
                    val radar = get(RadarData.mapper) ?: return@apply
                    val acPos = radar.position
                    val locCap = get(LocalizerCaptured.mapper)
                    val arrAirport = get(ArrivalAirport.mapper)
                    val clearance = get(ClearanceAct.mapper)
                    val refAppAndDir = (if (arrAirport != null && clearance != null) {
                        val approaches = GAME.gameClientScreen?.airports?.get(arrAirport.arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap
                        val reqApp = approaches?.get(clearance.actingClearance.clearanceState.clearedApp)?.entity
                        // We will also check if the aircraft is heading towards the approach (i.e. within 90 degrees of the approach heading)
                        val acDir = radar.direction
                        val appDirUnitVector = reqApp?.get(Direction.mapper)?.trackUnitVector
                            ?: reqApp?.get(ApproachInfo.mapper)?.rwyObj?.entity?.getOrLogMissing(Direction.mapper)?.let { -it.trackUnitVector }
                        // Since the app's direction is the opposite of the track, we need to check if the dot product is <= 0 before adding
                        if (locCap == null && (appDirUnitVector == null || acDir.trackUnitVector.dot(appDirUnitVector) > 0)) null
                        else Pair(reqApp, appDirUnitVector)
                    } else null) ?: return@apply
                    val refApp = refAppAndDir.first ?: return@apply
                    val refAppDir = refAppAndDir.second ?: return@apply
                    val alt = get(Altitude.mapper)?.altitudeFt ?: return@apply
                    val rwyAlt = refApp[ApproachInfo.mapper]?.rwyObj?.entity?.get(Altitude.mapper)?.altitudeFt ?: return@apply
                    if (alt < rwyAlt + 10) return@apply // Aircraft has touched down
                    val refPos = refApp[Position.mapper] ?: return@apply
                    val distNm = pxToNm(calculateDistanceBetweenPoints(acPos.x, acPos.y, refPos.x, refPos.y))
                    val maxDistNm = refApp[Localizer.mapper]?.maxDistNm ?: 20
                    // Not within 25 degrees of localizer centerline
                    if (!checkInArc(refPos.x, refPos.y, convertWorldAndRenderDeg(refAppDir.angleDeg()), nmToPx(maxDistNm.toInt()),
                            25f, acPos.x, acPos.y)
                    ) return@apply
                    refApp[ApproachWakeSequence.mapper]?.aircraftDist?.add(ApproachWakeSequencePosition(this, distNm, false))
                    refApp[ParallelWakeAffects.mapper]?.let {
                        val parApp = GAME.gameClientScreen?.airports?.get(arrAirport?.arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(it.approachName)?.entity
                        parApp?.get(ApproachWakeSequence.mapper)?.aircraftDist
                            ?.add(ApproachWakeSequencePosition(this, distNm + it.offsetNm, true))
                    }
                }
            }

            // Sort all approach wake sequences
            for (i in 0 until approachWakeSequence.size()) {
                approachWakeSequence[i]?.apply {
                    val distances = get(ApproachWakeSequence.mapper) ?: return@apply
                    distances.aircraftDist.sort { o1, o2 ->
                        if (o1.distFromThrNm > o2.distFromThrNm) 1
                        else if (o1.distFromThrNm < o2.distFromThrNm) -1
                        else 0
                    }
                }
            }

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
    val shownWpts = GdxArray<Short>()
    for (i in 0 until distances.size) {
        val dist = distances[i]
        if (shownWpts.contains(dist.wpt.wptId, false)) continue
        val roundedDist = (pxToNm(dist.distToGoPx) * 10).roundToInt() / 10f
        allWpts[dist.wpt.wptId]?.entity?.get(GenericLabels.mapper)?.labels?.get(1)?.apply {
            updateText(roundedDist.toString())
            xOffset = -label.prefWidth / 2
        }
        shownWpts.add(dist.wpt.wptId)
    }
}

/** Updates the waypoint to display altitude and speed restrictions for the selected aircraft */
fun updateWaypointRestr() {
    // Clear all waypoint restriction display
    val allWpts = GAME.gameClientScreen?.waypoints ?: return
    for (wpt in allWpts.values.toList()) {
        wpt.entity[GenericLabels.mapper]?.labels?.let {
            // Clears both alt restr labels, and also speed restr label
            it[2].updateText("")
            it[3].updateText("")
            it[4].updateText("")
        }
    }

    if (!SHOW_WPT_RESTRICTIONS) return

    val selectedAircraft = GAME.gameClientScreen?.selectedAircraft?.entity ?: return
    val route = selectedAircraft[ClearanceAct.mapper]?.actingClearance?.clearanceState?.route ?: return
    if (route.size == 0) return

    val shownWpts = GdxArray<Short>()
    for (i in 0 until route.size) {
        val leg = route[i]
        if (leg !is Route.WaypointLeg) continue
        if (shownWpts.contains(leg.wptId, false)) continue
        shownWpts.add(leg.wptId)
        if (!leg.legActive) continue
        val topAlt = if (leg.altRestrActive) leg.maxAltFt else null
        val bottomAlt = if (leg.altRestrActive) leg.minAltFt else null
        val speed = if (leg.spdRestrActive) leg.maxSpdKt else null

        val genericLabels = GAME.gameClientScreen?.waypoints?.get(leg.wptId)?.entity?.get(GenericLabels.mapper)?.labels ?: continue
        val topY = if (genericLabels[1].label.text.isEmpty) -10f else genericLabels[1].yOffset - 4
        var count = 1
        if (topAlt != null) {
            genericLabels[2].updateText(topAlt.toString())
            genericLabels[2].yOffset = if (isMobile()) (topY - 22f * count) else (topY - 14f * count)
            if (bottomAlt == topAlt)  genericLabels[2].updateStyle(if (isMobile()) "WaypointBothAltRestrMobile" else "WaypointBothAltRestr")
            else genericLabels[2].updateStyle(if (isMobile()) "WaypointTopAltRestrMobile" else "WaypointTopAltRestr")
            count++
        } else {
            genericLabels[2].updateText("")
        }

        if (bottomAlt != null && topAlt != bottomAlt) {
            genericLabels[3].updateText(bottomAlt.toString())
            genericLabels[3].yOffset = if (isMobile()) (topY - 22f * count) else (topY - 14f * count)
            count++
        }
        else genericLabels[3].updateText("")

        if (speed != null) {
            genericLabels[4].updateText("${speed}K")
            genericLabels[4].yOffset = if (isMobile()) (topY - 22f * count) else (topY - 14f * count)
        }
        else genericLabels[4].updateText("")

        genericLabels[2].xOffset = -genericLabels[2].label.prefWidth / 2
        genericLabels[3].xOffset = -genericLabels[3].label.prefWidth / 2
        genericLabels[4].xOffset = -genericLabels[4].label.prefWidth / 2
    }
}
