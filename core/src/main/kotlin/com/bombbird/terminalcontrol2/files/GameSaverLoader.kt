package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.json.getMoshiWithAllAdapters
import com.bombbird.terminalcontrol2.networking.GameServer
import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapter

/** Base data class save for game server */
@JsonClass(generateAdapter = true)
data class GameServerSave(
    val arrivalSpawnTimerS: Float,
    val previousArrivalOffsetS: Float,
    val trafficValue: Float,
    val trafficMode: Byte,
    val score: Int,
    val highScore: Int,
    val weatherMode: Byte,
    val emergencyRate: Byte,
    val stormsDensity: Byte,
    val gameSpeed: Int,
    val nightModeStart: Int,
    val nightModeEnd: Int,
    val useRecat: Boolean,
    val aircraft: List<Aircraft>,
    val airports: List<Airport>,
    val waypoints: List<Waypoint>
)

/**
 * Saves the game state for the input GameServer
 * @param gs the [GameServer] to save
 */
@OptIn(ExperimentalStdlibApi::class)
fun saveGame(gs: GameServer) {
    val moshi = getMoshiWithAllAdapters()
    val saveObject = GameServerSave(gs.arrivalSpawnTimerS, gs.previousArrivalOffsetS, gs.trafficValue, gs.trafficMode, gs.score, gs.highScore,
        gs.weatherMode, gs.emergencyRate, gs.stormsDensity, gs.gameSpeed, gs.nightModeStart, gs.nightModeEnd, gs.useRecat,
        gs.aircraft.values().toList(), gs.airports.values().toList(), gs.waypoints.values.toList())
    val saveFolderHandle = getExtDir("Saves") ?: return
    if (!saveFolderHandle.exists()) saveFolderHandle.mkdirs()
    var saveIndex = 0
    saveFolderHandle.list().forEach {
        val fileIndex = it.nameWithoutExtension().toInt()
        if (saveIndex <= fileIndex) saveIndex = fileIndex + 1
    }
    val saveHandle = saveFolderHandle.child("${saveIndex}.json")
    saveHandle.writeString(moshi.adapter<GameServerSave>().toJson(saveObject), false)
}

/**
 * Loads the game state for the input GameServer
 * @param gs the [GameServer] to load to
 */
@OptIn(ExperimentalStdlibApi::class)
fun loadSave(gs: GameServer, saveId: Int) {
    val moshi = getMoshiWithAllAdapters()
    val saveFolderHandle = getExtDir("Saves") ?: return
    if (!saveFolderHandle.exists()) return
    val saveHandle = saveFolderHandle.child("${saveId}.json")
    if (!saveHandle.exists()) return
    val saveObject = moshi.adapter<GameServerSave>().fromJson(saveHandle.readString())
    println(saveObject)
    // TODO Load to game server
}
