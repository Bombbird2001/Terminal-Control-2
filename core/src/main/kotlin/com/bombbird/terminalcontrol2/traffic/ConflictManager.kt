package com.bombbird.terminalcontrol2.traffic

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.SerialisableEntity
import com.bombbird.terminalcontrol2.global.CONFLICT_SIZE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MIN_SEP
import com.bombbird.terminalcontrol2.global.VERT_SEP
import com.bombbird.terminalcontrol2.navigation.establishedOnFinalApproachTrack
import com.bombbird.terminalcontrol2.utilities.calculateDistanceBetweenPoints
import com.bombbird.terminalcontrol2.utilities.nmToPx
import ktx.ashley.get
import ktx.ashley.has
import ktx.collections.GdxArray
import kotlin.math.abs

/** Helper class for managing conflicts between entities */
class ConflictManager {
    val conflicts = GdxArray<Conflict>(CONFLICT_SIZE)
    val potentialConflicts = GdxArray<PotentialConflict>(CONFLICT_SIZE)

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
        // Iterate through each layer to check for conflicts between the entities in the same level and 1 level above
        for (i in conflictLevels.indices) {
            val aircraft = conflictLevels[i]
            if (i + 1 < conflictLevels.size) aircraft.addAll(conflictLevels[i + 1])
            // Check all aircraft within these 2 layers with one another
            for (j in 0 until aircraft.size) {
                for (k in j + 1 until aircraft.size) checkAircraftConflict(aircraft[j], aircraft[k])
            }
        }

        // TODO Other forms of conflict
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
        val app1 = entity1[ClearanceAct.mapper]?.actingClearance?.actingClearance?.clearedApp?.let { arrival1?.get(ApproachChildren.mapper)?.approachMap?.get(it) }
        val app2 = entity2[ClearanceAct.mapper]?.actingClearance?.actingClearance?.clearedApp?.let { arrival2?.get(ApproachChildren.mapper)?.approachMap?.get(it) }
        val appNoz1 = app1?.entity?.get(ApproachInfo.mapper)?.rwyObj?.entity?.get(ApproachNOZ.mapper)?.appNoz
        val appNoz2 = app2?.entity?.get(ApproachInfo.mapper)?.rwyObj?.entity?.get(ApproachNOZ.mapper)?.appNoz
        // Since appNoz1 and appNoz2 are nullable, the .contains method must return a true (not false or null)
        if (arrival1 === arrival2 && appNoz1 !== appNoz2 &&
            appNoz1?.contains(pos1.x, pos1.y) == true && appNoz2?.contains(pos2.x, pos2.y) == true) return

        // Inhibit if both planes are departing from the same airport, and are both inside different NOZs of their departure runways
        val depRwy1 = entity1[DepartureAirport.mapper]?.let {
            GAME.gameServer?.airports?.get(it.arptId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(it.rwyId)
        }
        val depRwy2 = entity2[DepartureAirport.mapper]?.let {
            GAME.gameServer?.airports?.get(it.arptId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(it.rwyId)
        }
        val depNoz1 = depRwy1?.entity?.get(DepartureNOZ.mapper)?.depNoz
        val depNoz2 = depRwy2?.entity?.get(DepartureNOZ.mapper)?.depNoz
        // Since depNoz1 and depNoz2 are nullable, the .contains method must return a true (not false or null)
        if (depNoz1 !== depNoz2 && appNoz1?.contains(pos1.x, pos1.y) == true && appNoz2?.contains(pos2.x, pos2.y) == true) return

        // Inhibit if either plane just did a go around
        if (entity1.has(RecentGoAround.mapper) || entity2.has(RecentGoAround.mapper)) return

        var latMinima = MIN_SEP
        val altMinima = VERT_SEP
        var conflictReason = Conflict.NORMAL_CONFLICT
        // TODO Check for emergency -> Reduced minima by half

        val appRwy1 = app1?.entity?.get(ApproachInfo.mapper)?.rwyObj
        val appRwy2 = app2?.entity?.get(ApproachInfo.mapper)?.rwyObj
        val appRwy1Pos = appRwy1?.entity?.get(Position.mapper)

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
        if (calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y) < nmToPx(latMinima) &&
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

            // TODO Add conflict to list
        }

        if (calculateDistanceBetweenPoints(pos1.x, pos1.y, pos2.x, pos2.y) < nmToPx(latMinima + 2) &&
            abs(alt1.altitudeFt - alt2.altitudeFt) < altMinima) {
            // TODO Add potential conflict to list
        }
    }

    /** Nested class to store information related to an instance of a conflict */
    class Conflict(private val entity1: Entity, private val entity2: Entity?, private val latSepRequiredNm: Float, private val reason: Byte):
        SerialisableEntity<Conflict.SerialisedConflict> {

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
        }

        /** Object that contains [Conflict] data to be serialised by Kryo */
        data class SerialisedConflict(val name1: String = "", val name2: String? = null, val latSepRequiredNm: Float = 3f, val reason: Byte = NORMAL_CONFLICT)

        /**
         * Returns a default empty [SerialisedConflict] due to missing component, and logs a message to the console
         * @param missingComponent the missing aircraft component
         */
        override fun emptySerialisableObject(missingComponent: String): SerialisedConflict {
            Gdx.app.log("ConflictManager", "Empty serialised conflict returned due to missing $missingComponent component")
            return SerialisedConflict()
        }

        /** Gets a [SerialisedConflict] from current state */
        override fun getSerialisableObject(): SerialisedConflict {
            val acInfo1 = entity1[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
            val acInfo2 = entity2?.get(AircraftInfo.mapper)
            return SerialisedConflict(acInfo1.icaoCallsign, acInfo2?.icaoCallsign, latSepRequiredNm, reason)
        }
    }

    /** Nested class to store information related to an instance of a potential conflict between 2 entities */
    class PotentialConflict(private val entity1: Entity, private val entity2: Entity, private val latSepWarningNm: Float):
        SerialisableEntity<PotentialConflict.SerialisedPotentialConflict> {

        /** Object that contains [PotentialConflict] data to be serialised by Kryo */
        data class SerialisedPotentialConflict(val name1: String = "", val name2: String = "", val latSepRequiredNm: Float = 5f)

        /**
         * Returns a default empty [SerialisedPotentialConflict] due to missing component, and logs a message to the console
         * @param missingComponent the missing aircraft component
         */
        override fun emptySerialisableObject(missingComponent: String): SerialisedPotentialConflict {
            Gdx.app.log("ConflictManager", "Empty serialised potential conflict returned due to missing $missingComponent component")
            return SerialisedPotentialConflict()
        }

        /** Gets a [SerialisedPotentialConflict] from current state */
        override fun getSerialisableObject(): SerialisedPotentialConflict {
            val acInfo1 = entity1[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
            val acInfo2 = entity2[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
            return SerialisedPotentialConflict(acInfo1.icaoCallsign, acInfo2.icaoCallsign, latSepWarningNm)
        }
    }
}