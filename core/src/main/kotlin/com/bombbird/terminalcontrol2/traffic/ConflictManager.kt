package com.bombbird.terminalcontrol2.traffic

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Circle
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.RouteZone
import com.bombbird.terminalcontrol2.entities.SerialisableEntity
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.establishedOnFinalApproachTrack
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.ashley.has
import ktx.collections.GdxArray
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Helper class for managing conflicts between entities */
class ConflictManager {
    companion object {
        const val PENALTY_DURATION_S = 3 // 1 point is deducted for every conflict every 3 seconds
    }

    private var timer = PENALTY_DURATION_S
    private var prevConflictNoWakeCount = 0
    private val conflicts = GdxArray<Conflict>(CONFLICT_SIZE)
    private val potentialConflicts = GdxArray<PotentialConflict>(CONFLICT_SIZE)

    val wakeManager = WakeManager()

    /**
     * Main function to check for all types of conflicts, given the conflict sector distribution for each entity, as well
     * as the full array of conflict-able entities themselves
     *
     * The subtraction of score, sending of updates to clients will also be done in this function
     * @param conflictLevels the conflict level sectors where entities should have already been distributed into, for
     * checking of separation between each entity
     * @param conflictAbles the array of entities that can come into conflict with other entities, for checking of other
     * conflicts such as MVA, restricted areas
     */
    fun checkAllConflicts(conflictLevels: Array<GdxArray<Entity>>, conflictAbles: ImmutableArray<Entity>) {
        // Clear the existing list of conflicts/potential conflicts first
        prevConflictNoWakeCount = getCurrentNonWakeConflicts()
        conflicts.clear()
        potentialConflicts.clear()

        // Check aircraft separation with one another
        checkAircraftSeparationMinimaConflict(conflictLevels)

        // Check MVA, restricted areas
        checkMVARestrictedConflict(conflictAbles)

        // Check wake separation
        wakeManager.checkWakeConflicts(conflictAbles, conflicts)

        // Send all conflicts to clients using TCP
        GAME.gameServer?.sendConflicts(conflicts, potentialConflicts)

        // Subtract score
        updateScore()
    }

    /** Subtracts the game score corresponding to the number of conflicts and new conflicts, and updates the clients if needed */
    private fun updateScore() {
        GAME.gameServer?.apply {
            // For every new conflict (excluding wake conflicts), subtract 5% of score
            val currScore = score
            val newConflicts = getCurrentNonWakeConflicts() - prevConflictNoWakeCount
            if (newConflicts > 0) score = floor(score * 0.95f.pow(newConflicts)).roundToInt()
            // When the 3s timer is up, subtract 1 from score for every conflict
            timer--
            if (timer <= 0) {
                score -= conflicts.size
                timer += PENALTY_DURATION_S
            }
            // Score cannot go below 0
            if (score < 0) score = 0
            if (score != currScore) sendScoreUpdate()
        }
    }

    /**
     * Checks for conflicts between aircraft, using the level distribution to reduce the comparisons required; any conflicts
     * found will be added to the list of conflicts/potential conflicts for later use
     * @param conflictLevels the conflict level sectors where entities should have already been distributed into, for
     * checking of separation between each entity
     */
    private fun checkAircraftSeparationMinimaConflict(conflictLevels: Array<GdxArray<Entity>>) {
        // Iterate through each layer to check for conflicts between the entities in the same level and 1 level above
        for (i in conflictLevels.indices) {
            val aircraft = conflictLevels[i]
            // Check all aircraft within this layer with one another
            for (j in 0 until aircraft.size) {
                for (k in j + 1 until aircraft.size) checkAircraftConflict(aircraft[j], aircraft[k])
                // If a layer exists above, check with each aircraft in the above layer
                if (i + 1 < conflictLevels.size) {
                    val aboveAircraft = conflictLevels[i + 1]
                    for (k in 0 until aboveAircraft.size) checkAircraftConflict(aircraft[j], aboveAircraft[k])
                }
            }
        }
    }

    /**
     * Checks for conflicts between 2 entities, depending on their current state; if conflict is found, a new conflict
     * instance will be added to the conflict array
     * @param entity1 the first entity
     * @param entity2 the second entity
     */
    private fun checkAircraftConflict(entity1: Entity, entity2: Entity) {
        val alt1 = entity1[Altitude.mapper] ?: return
        val alt2 = entity2[Altitude.mapper] ?: return
        val pos1 = entity1[Position.mapper] ?: return
        val pos2 = entity2[Position.mapper] ?: return

        val arrival1 = entity1[ArrivalAirport.mapper]?.arptId?.let {
            GAME.gameServer?.airports?.get(it)?.entity
        }
        val arrival2 = entity2[ArrivalAirport.mapper]?.arptId?.let {
            GAME.gameServer?.airports?.get(it)?.entity
        }
        val dep1 = entity1[DepartureAirport.mapper]?.arptId?.let {
            GAME.gameServer?.airports?.get(it)?.entity
        }
        val dep2 = entity2[DepartureAirport.mapper]?.arptId?.let {
            GAME.gameServer?.airports?.get(it)?.entity
        }

        // Inhibit if either plane is less than 1000 ft AGL (to their respective arrival/departure airports)
        val arptElevation1 = (arrival1 ?: dep1)?.get(Altitude.mapper)?.altitudeFt ?: 0f
        val arptElevation2 = (arrival2 ?: dep2)?.get(Altitude.mapper)?.altitudeFt ?: 0f
        if (alt1.altitudeFt < arptElevation1 + 1000 || alt2.altitudeFt < arptElevation2 + 1000) return

        // Inhibit if both planes are arriving at same airport, and are both inside different NOZs of their cleared approaches
        val app1 = entity1[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { arrival1?.get(ApproachChildren.mapper)?.approachMap?.get(it) }
        val app2 = entity2[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { arrival2?.get(ApproachChildren.mapper)?.approachMap?.get(it) }
        val appNoz1 = app1?.entity?.get(ApproachInfo.mapper)?.rwyObj?.entity?.get(ApproachNOZ.mapper)?.appNoz
        val appNoz2 = app2?.entity?.get(ApproachInfo.mapper)?.rwyObj?.entity?.get(ApproachNOZ.mapper)?.appNoz
        // Since appNoz1 and appNoz2 are nullable, the .contains method must return a true (not false or null)
        if (arrival1 === arrival2 && appNoz1 !== appNoz2 &&
            appNoz1?.contains(pos1.x, pos1.y) == true && appNoz2?.contains(pos2.x, pos2.y) == true) return

        // Inhibit if both planes are departing from the same airport, and are both inside different NOZs of their departure runways
        val depArpt1 = entity1[DepartureAirport.mapper]
        val depArpt2 = entity2[DepartureAirport.mapper]
        val depArptEntity1 = GAME.gameServer?.airports?.get(depArpt1?.arptId)
        val depArptEntity2 = GAME.gameServer?.airports?.get(depArpt2?.arptId)
        if (depArpt1 != null && depArpt2 != null && depArptEntity1 != null && depArptEntity2 != null) {
            val depRwy1 = depArptEntity1.entity[RunwayChildren.mapper]?.rwyMap?.get(depArpt1.rwyId)
            val depRwy2 = depArptEntity2.entity[RunwayChildren.mapper]?.rwyMap?.get(depArpt2.rwyId)
            val depNoz1 = depRwy1?.entity?.get(DepartureNOZ.mapper)?.depNoz
            val depNoz2 = depRwy2?.entity?.get(DepartureNOZ.mapper)?.depNoz
            // Since depNoz1 and depNoz2 are nullable, the .contains method must return a true (not false or null)
            if (dep1 === dep2 && depNoz1 !== depNoz2 &&
                depNoz1?.contains(pos1.x, pos1.y) == true && depNoz2?.contains(pos2.x, pos2.y) == true) return

            // Allow simultaneous departures on divergent headings of at least 15 degrees
            if (entity1.has(DivergentDepartureAllowed.mapper) && entity2.has(DivergentDepartureAllowed.mapper) && depArptEntity1 == depArptEntity2) {
                val track1 = convertWorldAndRenderDeg(entity1[Direction.mapper]?.trackUnitVector?.angleDeg() ?: 0f)
                val track2 = convertWorldAndRenderDeg(entity2[Direction.mapper]?.trackUnitVector?.angleDeg() ?: 0f)

                // Identify which plane is on the left, right
                depArptEntity1.entity[Position.mapper]?.let { arptPos ->
                    val plane1Offset = Vector2(pos1.x - arptPos.x, pos1.y - arptPos.y)
                    val plane2Offset = Vector2(pos2.x - arptPos.x, pos2.y - arptPos.y)

                    // Check which angle is more left
                    val plane1PosTrack = convertWorldAndRenderDeg(plane1Offset.angleDeg())
                    val plane2PosTrack = convertWorldAndRenderDeg(plane2Offset.angleDeg())
                    val plane2PosTrackDiff = findDeltaHeading(plane1PosTrack, plane2PosTrack, CommandTarget.TURN_DEFAULT)
                    if (plane2PosTrackDiff > 0) {
                        // Plane 1 on left, 2 on right
                        if (findDeltaHeading(track1, track2, CommandTarget.TURN_DEFAULT) >= 15) return
                    } else {
                        // Plane 1 on right, 2 on left
                        if (findDeltaHeading(track2, track1, CommandTarget.TURN_DEFAULT) >= 15) return
                    }
                }
            }
        }

        // Inhibit if either plane just did a go around
        if (entity1.has(RecentGoAround.mapper) || entity2.has(RecentGoAround.mapper)) return

        // Inhibit if either plane is flying visual approach
        if (entity1.has(VisualCaptured.mapper) || entity2.has(VisualCaptured.mapper)) return

        var latMinima = MIN_SEP
        var altMinima = VERT_SEP
        var conflictReason = Conflict.NORMAL_CONFLICT

        val appRwy1 = app1?.entity?.get(ApproachInfo.mapper)?.rwyObj
        val appRwy2 = app2?.entity?.get(ApproachInfo.mapper)?.rwyObj
        val appRwy1Pos = appRwy1?.entity?.get(Position.mapper)

        // Reduce vertical separation to half if aircraft is an emergency
        if (entity1[EmergencyPending.mapper]?.active == true || entity2[EmergencyPending.mapper]?.active == true) {
            altMinima = VERT_SEP / 2
            conflictReason = Conflict.EMERGENCY_SEPARATION_CONFLICT
        }

        // Reduce lateral separation to 2nm (staggered) if both aircraft are established on different final approach tracks
        if (appRwy1 !== appRwy2 && app1 != null && establishedOnFinalApproachTrack(app1.entity, pos1.x, pos1.y) &&
            app2 != null && establishedOnFinalApproachTrack(app2.entity, pos2.x, pos2.y)) {
            latMinima = 2f
            conflictReason = Conflict.PARALLEL_DEP_APP
        }

        // Reduced lateral separation to 2.5nm if both aircraft are established on same final approach track and both are
        // less than 10nm away from runway (should we take into account weather e.g. visibility?)
        else if (appRwy1 === appRwy2 && app1 != null && establishedOnFinalApproachTrack(app1.entity, pos1.x, pos1.y) &&
            app2 != null && establishedOnFinalApproachTrack(app2.entity, pos2.x, pos2.y) &&
            appRwy1Pos != null &&
            calculateDistanceBetweenPoints(pos1.x, pos1.y, appRwy1Pos.x, appRwy1Pos.y) < nmToPx(10) &&
            calculateDistanceBetweenPoints(pos2.x, pos2.y, appRwy1Pos.x, appRwy1Pos.y) < nmToPx(10)) {
            latMinima = 2.5f
            conflictReason = Conflict.SAME_APP_LESS_THAN_10NM
        }

        // If lateral separation is less than minima, and vertical separation less than minima, conflict exists
        val distPx = calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y)
        if (distPx < nmToPx(latMinima) &&
            abs(alt1.altitudeFt - alt2.altitudeFt) < altMinima - 25) {
            // Additional check for NTZ infringement (so reason can be specified)
            if (arrival1 === arrival2 && appNoz1 != null && appNoz2 != null && appNoz1 !== appNoz2) {
                // Find matching runway config that contains both runways for landing, and check its NTZ(s)
                run { arrival1?.get(RunwayConfigurationChildren.mapper)?.rwyConfigs?.values()?.forEach {
                    if (!it.depRwys.contains(appRwy1, true) || !it.depRwys.contains(appRwy2, true)) return@run
                    // Suitable runway configuration found
                    for (i in 0 until it.ntzs.size) {
                        if (it.ntzs[i].contains(pos1.x, pos1.y) || it.ntzs[i].contains(pos2.x, pos2.y)) {
                            conflictReason = Conflict.PARALLEL_INDEP_APP_NTZ
                            return@run
                        }
                    }
                }}
            }

            conflicts.add(Conflict(entity1, entity2, null, latMinima, conflictReason))
        }

        // If no conflict, but separation is less than minima + 2nm, add to potential conflicts
        else if (distPx < nmToPx(latMinima + 2) && abs(alt1.altitudeFt - alt2.altitudeFt) < altMinima)
            potentialConflicts.add(PotentialConflict(entity1, entity2, latMinima))
    }

    /**
     * Checks for conflicts between an entity and MVA sectors, restricted areas; if conflict is found, a new conflict
     * instance will be added to the conflict array
     * @param conflictAbles the list of entities to check for MVA, restricted area conflict
     */
    private fun checkMVARestrictedConflict(conflictAbles: ImmutableArray<Entity>) {
        val allMinAltSectors = GAME.gameServer?.minAltSectors ?: return
        conflictAbles.forEach {
            val alt = it[Altitude.mapper] ?: return@forEach
            val pos = it[Position.mapper] ?: return@forEach

            // Whether the aircraft has already captured the vertical component of the approach (or phase >= 1 for circling)
            val approachCaptured = it.has(GlideSlopeCaptured.mapper) || it.has(StepDownApproach.mapper) ||
                    (it[CirclingApproach.mapper]?.phase ?: 0) >= 1 || it.has(VisualCaptured.mapper)

            // Whether the aircraft has already captured localizer but not the glide slope yet, but is above the ROC
            // slope
            val aboveGsRoc = !approachCaptured && it.has(LocalizerCaptured.mapper) && (it[GlideSlopeArmed.mapper]?.let { gsArmed ->
                val gs = gsArmed.gsApp[GlideSlope.mapper] ?: return@let false
                val appPos = gsArmed.gsApp[Position.mapper] ?: return@let false
                val gradientRatio = 102 / gs.glideAngle
                val distFromTouchdownPx = calculateDistanceBetweenPoints(pos.x, pos.y, appPos.x, appPos.y) + nmToPx(gs.offsetNm)
                val rwyAlt = gsArmed.gsApp[ApproachInfo.mapper]?.rwyObj?.entity?.get(Altitude.mapper)?.altitudeFt ?: return@let false
                alt.altitudeFt > rwyAlt + pxToFt(distFromTouchdownPx) / gradientRatio
            } ?: false)

            // Whether the aircraft is following vectors (if false, it is following the route)
            val underVector = getLatestClearanceState(it)?.vectorHdg != null

            // Whether the aircraft is within its route zones
            val routeZones = GdxArray<RouteZone>()
            it[ArrivalRouteZone.mapper]?.let { arrZone ->
                routeZones.addAll(arrZone.appZone)
                routeZones.addAll(arrZone.starZone)
            }
            it[DepartureRouteZone.mapper]?.let { depZone -> routeZones.addAll(depZone.sidZone) }
            var deviatedFromRoute = true
            run { for (i in 0 until routeZones.size) routeZones[i]?.also { zone ->
                val minAlt = zone.entity[Altitude.mapper]?.altitudeFt
                if ((minAlt == null || alt.altitudeFt > minAlt - 25) && zone.contains(pos.x, pos.y)) {
                    deviatedFromRoute = false
                    return@run
                }
            }}

            for (i in 0 until allMinAltSectors.size) {
                allMinAltSectors[i]?.apply {
                    val sectorInfo = entity[MinAltSectorInfo.mapper] ?: return@apply
                    val minAlt = sectorInfo.minAltFt
                    // If altitude is higher than min alt, break from the whole loop since the array is already sorted in
                    // descending order, and all subsequent sectors will not trigger a conflict
                    if (minAlt != null && alt.altitudeFt > minAlt - 25) return@forEach

                    // If aircraft is already on glide slope, step down, circling (phase 1 or later) or visual approach,
                    // or is above the ROC slope of the glide slope, and sector is not restricted, skip checking
                    if (!sectorInfo.restricted && (approachCaptured || aboveGsRoc))
                        return@apply

                    // If aircraft is not being vectored (i.e. SID/STAR/approach), is within the route's MVA exclusion
                    // zone, or instead of all the above, the aircraft just did a go around, and sector is not restricted,
                    // skip checking
                    if (!sectorInfo.restricted && ((!underVector && !deviatedFromRoute) || it.has(RecentGoAround.mapper))) return@apply

                    var insideShape = false
                    // Try to get either the polygon or the circle
                    val polygon = entity[GPolygon.mapper]
                    val circle = entity[GCircle.mapper]
                    if (polygon != null) {
                        insideShape = polygon.polygonObj.contains(pos.x, pos.y)
                    } else if (circle != null) {
                        insideShape = entity[Position.mapper]?.let { circlePos ->
                            Circle(circlePos.x, circlePos.y, circle.radius).contains(pos.x, pos.y)
                        } ?: false
                    }

                    if (!insideShape) return@apply
                    val reason = if (sectorInfo.restricted) Conflict.RESTRICTED
                    else if (deviatedFromRoute) Conflict.SID_STAR_MVA
                    else Conflict.MVA

                    conflicts.add(Conflict(it, entity, i, 3f, reason))
                }
            }
        }
    }

    /**
     * Gets the number of ongoing conflicts excluding wake conflicts
     * @return the number of conflicts that are not wake conflicts
     */
    private fun getCurrentNonWakeConflicts(): Int {
        var count = 0
        for (i in 0 until conflicts.size)
            if (conflicts[i].reason != Conflict.WAKE_INFRINGE) count++
        return count
    }

    /** Nested class to store information related to an instance of a conflict */
    class Conflict(val entity1: Entity, val entity2: Entity?, val minAltSectorIndex: Int?, val latSepRequiredNm: Float,
                   val reason: Byte): SerialisableEntity<Conflict.SerialisedConflict> {

        companion object {
            const val NORMAL_CONFLICT: Byte = 0
            const val SAME_APP_LESS_THAN_10NM: Byte = 1
            const val PARALLEL_DEP_APP: Byte = 2
            const val PARALLEL_INDEP_APP_NTZ: Byte = 3
            const val MVA: Byte = 4
            const val SID_STAR_MVA: Byte = 5
            const val RESTRICTED: Byte = 6
            const val WAKE_INFRINGE: Byte = 7
            const val STORM: Byte = 8
            const val EMERGENCY_SEPARATION_CONFLICT: Byte = 9

            /** Returns a default empty conflict object when a proper conflict object cannot be de-serialised */
            private fun getEmptyConflict(): Conflict {
                return Conflict(Entity(), null, null, 3f, NORMAL_CONFLICT)
            }

            /** De-serialises a [SerialisedConflict] and creates a new [Conflict] object from it */
            fun fromSerialisedObject(serialisedConflict: SerialisedConflict): Conflict {
                val entity1 = CLIENT_SCREEN?.aircraft?.get(serialisedConflict.name1)?.entity ?: return getEmptyConflict()
                val entity2 = CLIENT_SCREEN?.aircraft?.get(serialisedConflict.name2)?.entity
                return Conflict(entity1, entity2, serialisedConflict.minAltSectorIndex, serialisedConflict.latSepRequiredNm, serialisedConflict.reason)
            }
        }

        /** Object that contains [Conflict] data to be serialised by Kryo */
        data class SerialisedConflict(val name1: String = "", val name2: String? = null, val minAltSectorIndex: Int? = null,
                                      val latSepRequiredNm: Float = 3f, val reason: Byte = NORMAL_CONFLICT)

        /**
         * Returns a default empty [SerialisedConflict] due to missing component, and logs a message to the console
         * @param missingComponent the missing aircraft component
         */
        override fun emptySerialisableObject(missingComponent: String): SerialisedConflict {
            FileLog.info("ConflictManager", "Empty serialised conflict returned due to missing $missingComponent component")
            return SerialisedConflict()
        }

        /** Gets a [SerialisedConflict] from current state */
        override fun getSerialisableObject(): SerialisedConflict {
            val acInfo1 = entity1[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
            val acInfo2 = entity2?.get(AircraftInfo.mapper)
            return SerialisedConflict(acInfo1.icaoCallsign, acInfo2?.icaoCallsign, minAltSectorIndex, latSepRequiredNm, reason)
        }
    }

    /** Nested class to store information related to an instance of a potential conflict between 2 entities */
    class PotentialConflict(val entity1: Entity, val entity2: Entity, val latSepRequiredNm: Float):
        SerialisableEntity<PotentialConflict.SerialisedPotentialConflict> {

        companion object {
            /** Returns a default empty potential conflict object when a proper conflict object cannot be de-serialised */
            private fun getEmptyConflict(): PotentialConflict {
                return PotentialConflict(Entity(), Entity(), 3f)
            }

            /** De-serialises a [SerialisedPotentialConflict] and creates a new [PotentialConflict] object from it */
            fun fromSerialisedObject(serialisedPotentialConflict: SerialisedPotentialConflict): PotentialConflict {
                val entity1 = CLIENT_SCREEN?.aircraft?.get(serialisedPotentialConflict.name1)?.entity ?: return getEmptyConflict()
                val entity2 = CLIENT_SCREEN?.aircraft?.get(serialisedPotentialConflict.name2)?.entity ?: return getEmptyConflict()
                return PotentialConflict(entity1, entity2, serialisedPotentialConflict.latSepRequiredNm)
            }
        }

        /** Object that contains [PotentialConflict] data to be serialised by Kryo */
        data class SerialisedPotentialConflict(val name1: String = "", val name2: String = "", val latSepRequiredNm: Float = 5f)

        /**
         * Returns a default empty [SerialisedPotentialConflict] due to missing component, and logs a message to the console
         * @param missingComponent the missing aircraft component
         */
        override fun emptySerialisableObject(missingComponent: String): SerialisedPotentialConflict {
            FileLog.info("ConflictManager", "Empty serialised potential conflict returned due to missing $missingComponent component")
            return SerialisedPotentialConflict()
        }

        /** Gets a [SerialisedPotentialConflict] from current state */
        override fun getSerialisableObject(): SerialisedPotentialConflict {
            val acInfo1 = entity1[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
            val acInfo2 = entity2[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
            return SerialisedPotentialConflict(acInfo1.icaoCallsign, acInfo2.icaoCallsign, latSepRequiredNm)
        }
    }
}