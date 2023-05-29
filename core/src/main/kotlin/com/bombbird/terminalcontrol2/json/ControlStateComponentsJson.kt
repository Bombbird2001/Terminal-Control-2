package com.bombbird.terminalcontrol2.json

import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.ClearanceAct
import com.bombbird.terminalcontrol2.components.Controllable
import com.bombbird.terminalcontrol2.components.PendingClearances
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

/**
 * Data class for storing controllable information for JSON serialization
 *
 * The only sector IDs that can be stored are -1 (Tower), -2 (ACC) and 0 (player controlled), since when the game server
 * launches it starts with 1 player by default, and subsequent player connections will result in the aircraft automatically
 * being reassigned to the relevant controller
 */
@JsonClass(generateAdapter = true)
data class ControllableJSON(val sector: Byte)

/** Adapter object for serialization between [Controllable] and [ControllableJSON] */
object ControllableAdapter {
    @ToJson
    fun toJson(controllable: Controllable): ControllableJSON {
        return ControllableJSON(if (controllable.sectorId >= 0) 0 else controllable.sectorId)
    }

    @FromJson
    fun fromJson(controllableJSON: ControllableJSON): Controllable {
        return Controllable(controllableJSON.sector)
    }
}

/** Data class for storing pending clearance information for JSON serialization */
@JsonClass(generateAdapter = true)
data class PendingClearancesJSON(val pending: List<ClearanceState.PendingClearanceState>)

/** Adapter object for serialization between [PendingClearances] and [PendingClearancesJSON] */
object PendingClearanceAdapter {
    @ToJson
    fun toJson(pending: PendingClearances): PendingClearancesJSON {
        val array = ArrayList<ClearanceState.PendingClearanceState>()
        for (clearance in Queue.QueueIterator(pending.clearanceQueue)) array.add(clearance)
        return PendingClearancesJSON(array)
    }

    @FromJson
    fun fromJson(pendingJSON: PendingClearancesJSON): PendingClearances {
        val queue = Queue<ClearanceState.PendingClearanceState>()
        for (clearance in pendingJSON.pending) queue.addLast(clearance)
        return PendingClearances(queue)
    }
}

/**
 * Data class for storing acting clearance information for JSON serialization (adapter for ActingClearance cannot be
 * generated automatically due to it being an inner class)
 */
@JsonClass(generateAdapter = true)
data class ActingClearanceJSON(val actingClearance: ClearanceState)

/** Adapter object for serialization between [ClearanceAct] and [ActingClearanceJSON] */
object ClearanceActAdapter {
    @ToJson
    fun toJson(acting: ClearanceAct): ActingClearanceJSON {
        return ActingClearanceJSON(acting.actingClearance.clearanceState)
    }

    @FromJson
    fun fromJson(actingJSON: ActingClearanceJSON): ClearanceAct {
        return ClearanceAct(actingJSON.actingClearance.ActingClearance())
    }
}
