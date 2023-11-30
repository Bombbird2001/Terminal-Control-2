package com.bombbird.terminalcontrol2.traffic.conflict

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.SerialisableEntity
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get

/** Class to store information related to an instance of a conflict */
class Conflict(val entity1: Entity, val entity2: Entity?, val minAltSectorIndex: Int?, val latSepRequiredNm: Float,
               val reason: Byte):
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
                                  val latSepRequiredNm: Float = 3f, val reason: Byte = NORMAL_CONFLICT
    )

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

/** Class to store information related to an instance of a potential conflict between 2 entities */
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
    data class SerialisedPotentialConflict(val name1: String = "", val name2: String? = null, val latSepRequiredNm: Float = 5f)

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

/** Class to store information related to an instance of a predicted conflict between 2 entities */
class PredictedConflict(val aircraft1: Entity, val aircraft2: Entity?, val advanceTimeS: Short, val posX: Float,
                        val posY: Float): SerialisableEntity<PredictedConflict.SerialisedPredictedConflict> {
    /** Object that contains [PredictedConflict] data to be serialised by Kryo */
    data class SerialisedPredictedConflict(val name1: String = "", val name2: String? = null, val advanceTimeS: Short = 0,
                                           val posX: Float = 0f, val posY: Float = 0f)

    /**
     * Returns a default empty [SerialisedPredictedConflict] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedPredictedConflict {
        FileLog.info("ConflictManager", "Empty serialised predicted conflict returned due to missing $missingComponent component")
        return SerialisedPredictedConflict()
    }

    /** Gets a [SerialisedPredictedConflict] from current state */
    override fun getSerialisableObject(): SerialisedPredictedConflict {
        val acInfo1 = aircraft1[AircraftInfo.mapper] ?: return emptySerialisableObject("AircraftInfo")
        val acInfo2 = aircraft2?.get(AircraftInfo.mapper)
        return SerialisedPredictedConflict(acInfo1.icaoCallsign, acInfo2?.icaoCallsign, advanceTimeS, posX, posY)
    }
}

/** Data class for storing conflict separation minima information */
data class ConflictMinima(val latMinima: Float, val vertMinima: Int, val conflictReason: Byte)
val DEFAULT_CONFLICT_MINIMA = ConflictMinima(3f, 1000, Conflict.NORMAL_CONFLICT)

/** Data class for storing MVA/restricted area infringement information */
data class MVAConflictInfo(val reason: Byte, val minAltSectorIndex: Int)