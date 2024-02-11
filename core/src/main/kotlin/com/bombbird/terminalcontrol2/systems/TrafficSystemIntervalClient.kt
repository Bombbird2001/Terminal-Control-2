package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.systems.IntervalSystem
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.get

/**
 * Client-side traffic interval update system, set at 1hz
 *
 * Used only in RadarScreen
 */
class TrafficSystemIntervalClient: IntervalSystem(1f) {
    companion object {
        private val wakeSequenceFamily = allOf(ClearanceAct::class, ArrivalAirport::class).get()
        private val approachWakeSequenceFamily = allOf(ApproachWakeSequence::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val wakeSequenceFamilyEntities = FamilyWithListener.newClientFamilyWithListener(wakeSequenceFamily)
    private val approachWakeSequenceFamilyEntities = FamilyWithListener.newClientFamilyWithListener(approachWakeSequenceFamily)

    override fun updateInterval() {
        val rs = GAME.gameClientScreen

        // Play the conflict sound every 1s
        if (rs != null && rs.conflicts.size > 0) GAME.soundManager.playWarning()

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
                val refApp = (if (arrAirport != null && clearance != null) {
                        val approaches = rs?.airports?.get(arrAirport.arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap
                        val reqApp = approaches?.get(clearance.actingClearance.clearanceState.clearedApp)?.entity
                        // We will also check if the aircraft is heading towards the approach (i.e. within 90 degrees of the approach heading)
                        val acDir = radar.direction
                        val appDir = reqApp?.get(Direction.mapper)
                        // Since the app's direction is the opposite of the track, we need to check if the dot product is <= 0 before adding
                        if (locCap == null && (appDir == null || acDir.trackUnitVector.dot(appDir.trackUnitVector) > 0)) null
                        else reqApp
                    } else null) ?: return@apply
                val alt = get(Altitude.mapper)?.altitudeFt ?: return@apply
                val rwyAlt = refApp[ApproachInfo.mapper]?.rwyObj?.entity?.get(Altitude.mapper)?.altitudeFt ?: return@apply
                if (alt < rwyAlt + 10) return@apply // Aircraft has touched down
                val refPos = refApp[Position.mapper] ?: return@apply
                val dist = calculateDistanceBetweenPoints(acPos.x, acPos.y, refPos.x, refPos.y)
                val maxDistNm = refApp[Localizer.mapper]?.maxDistNm ?: 15
                val appDir = refApp[Direction.mapper]?.trackUnitVector ?: return@apply
                // Not in 15 degree of localizer centerline
                if (!checkInArc(refPos.x, refPos.y, convertWorldAndRenderDeg(appDir.angleDeg()), nmToPx(maxDistNm.toInt()),
                        15f, acPos.x, acPos.y)) return@apply
                refApp[ApproachWakeSequence.mapper]?.aircraftDist?.add(Pair(this, dist))
            }
        }

        // Sort all approach wake sequences
        for (i in 0 until approachWakeSequence.size()) {
            approachWakeSequence[i]?.apply {
                val distances = get(ApproachWakeSequence.mapper) ?: return@apply
                distances.aircraftDist.sort { o1, o2 ->
                    if (o1.second > o2.second) 1
                    else if (o1.second < o2.second) -1
                    else 0
                }
            }
        }
    }
}