package com.bombbird.terminalcontrol2.files

import com.badlogic.gdx.utils.ArrayMap.Entries
import com.badlogic.gdx.utils.Queue.QueueIterator
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.WakeTrail
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.json.getMoshiWithAllAdapters
import com.bombbird.terminalcontrol2.json.runDelayedEntityRetrieval
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.systems.TrafficSystemInterval
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.minlog.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.adapter
import ktx.ashley.get
import ktx.ashley.getSystem
import ktx.collections.set

/** Base data class save for game server */
@JsonClass(generateAdapter = true)
data class GameServerSave(
    val mainName: String,
    val arrivalSpawnTimerS: Float,
    val previousArrivalOffsetS: Float,
    val trafficValue: Float,
    val trafficMode: Byte,
    val score: Int,
    val highScore: Int,
    val landed: Int,
    val departed: Int,
    val weatherMode: Byte,
    val emergencyRate: Byte,
    val stormsDensity: Byte,
    val gameSpeed: Int,
    val nightModeStart: Int,
    val nightModeEnd: Int,
    val useRecat: Boolean,
    val trailDotTimer: Float,
    val aircraft: List<Aircraft>,
    val airports: List<Airport>,
    val waypoints: List<Waypoint>
)

/**
 * Data class save for game meta information (displayed in the loading screen) so the full save object does not need to
 * be loaded
 */
@JsonClass(generateAdapter = true)
data class GameSaveMeta(val mainName: String, val score: Int, val highScore: Int, val landed: Int, val departed: Int)

/**
 * Saves the game state for the input GameServer
 * @param gs the [GameServer] to save
 */
@OptIn(ExperimentalStdlibApi::class)
fun saveGame(gs: GameServer) {
    val moshi = getMoshiWithAllAdapters()

    // Save main game information
    val saveObject = GameServerSave(gs.mainName, gs.arrivalSpawnTimerS, gs.previousArrivalOffsetS, gs.trafficValue,
        gs.trafficMode, gs.score, gs.highScore, gs.landed, gs.departed, gs.weatherMode, gs.emergencyRate, gs.stormsDensity,
        gs.gameSpeed, gs.nightModeStart, gs.nightModeEnd, gs.useRecat, gs.trailDotTimer,
        Entries(gs.aircraft).toList().map { it.value },
        Entries(gs.airports).toList().map { it.value },
        gs.waypoints.values.toList())
    val saveFolderHandle = getExtDir("Saves") ?: return
    if (!saveFolderHandle.exists()) saveFolderHandle.mkdirs()

    // Get a new save ID if gameServer's save ID is null
    val currSaveId = gs.saveID
    if (currSaveId != null) {
        // Try to shift existing save to back-up save (if save exists)
        val saveHandle = saveFolderHandle.child("${currSaveId}.json")
        val saveMetaHandle = saveFolderHandle.child("${currSaveId}.meta")
        if (saveHandle.exists() && saveMetaHandle.exists()) {
            val backupHandle = saveFolderHandle.child("${currSaveId}-backup.json")
            val backupMetaHandle = saveFolderHandle.child("${currSaveId}-backup.meta")
            saveHandle.moveTo(backupHandle)
            saveMetaHandle.moveTo(backupMetaHandle)
        }
    }

    val saveIndex = currSaveId ?: getNextAvailableSaveID() ?: return
    gs.saveID = saveIndex
    val saveHandle = saveFolderHandle.child("${saveIndex}.json")
    saveHandle.writeString(moshi.adapter<GameServerSave>().toJson(saveObject), false)

    // Save meta information
    val metaObject = GameSaveMeta(gs.mainName, gs.score, gs.highScore, gs.landed, gs.departed)
    val metaHandle = saveFolderHandle.child("${saveIndex}.meta")
    metaHandle.writeString(moshi.adapter<GameSaveMeta>().toJson(metaObject), false)
}

/**
 * Loads the game state for the input GameServer
 *
 * If the main save file is corrupted, tries to use the backup save if one exists. If it is missing or corrupted also,
 * exits to menu with error message
 * @param gs the [GameServer] to load to
 * @param saveId the ID of the save file to load
 */
fun loadSave(gs: GameServer, saveId: Int) {
    loadSave(gs, saveId, false)
}

/**
 * Loads the game state for the input GameServer
 * @param gs the [GameServer] to load to
 * @param saveId the ID of the save file to load
 * @param useBackup whether to use the backup save file (e.g. 1-backup.json)
 */
@OptIn(ExperimentalStdlibApi::class)
private fun loadSave(gs: GameServer, saveId: Int, useBackup: Boolean) {
        val moshi = getMoshiWithAllAdapters()
        val saveFolderHandle = getExtDir("Saves") ?: return
        if (!saveFolderHandle.exists()) return
        val saveHandle = saveFolderHandle.child("${saveId}${if (useBackup) "-backup" else ""}.json")
        if (!saveHandle.exists()) {
            Log.warn("GameSaverLoader", "${saveId}.json missing, attempting to use backup")
            loadSave(gs, saveId, true)
            return
        }
    try {
        val saveObject = moshi.adapter<GameServerSave>().fromJson(saveHandle.readString()) ?: return
        setGameServerFields(gs, saveObject)
        saveObject.aircraft.forEach {
            val callsign = it.entity[AircraftInfo.mapper]?.icaoCallsign ?: return@forEach
            gs.aircraft[callsign] = it
        }
        saveObject.airports.forEach {
            val arptId = it.entity[AirportInfo.mapper]?.arptId ?: return@forEach
            gs.airports[arptId] = it
        }
        saveObject.waypoints.forEach {
            val wptId = it.entity[WaypointInfo.mapper]?.wptId ?: return@forEach
            gs.waypoints[wptId] = it
        }
        val trafficSystem = getEngine(false).getSystem<TrafficSystemInterval>()
        saveObject.aircraft.forEach {
            val wakeTrail = it.entity[WakeTrail.mapper] ?: return@forEach
            for (point in QueueIterator(wakeTrail.wakeZones)) point.second?.let { zone -> trafficSystem.addWakeZone(zone) }
        }
        runDelayedEntityRetrieval()
    } catch (e: Exception) {
        if (e is JsonDataException || e is JsonEncodingException) {
            if (!useBackup) {
                Log.warn("GameSaverLoader", "Encountered JSON parsing error when loading ${saveId}.json, attempting to use backup, deleting corrupted save")
                deleteMainSave(saveId)
                loadSave(gs, saveId, true)
            } else {
                Log.warn("GameSaverLoader", "Encountered JSON parsing error when loading ${saveId}-backup.json, exiting")
                deleteBackupSave(saveId)
                GAME.quitCurrentGameWithDialog(CustomDialog("Failed to load game", "Save files are corrupted", "", "Ok"))
            }
            return
        }

        Log.warn("GameSaverLoader", "Encountered ${e.javaClass.name} when loading ${saveId}${if (useBackup) "-backup" else ""}.json, exiting")
        GAME.quitCurrentGameWithDialog(CustomDialog("Error loading game", "Please try again.", "", "Ok"))
    }
}

/**
 * Sets the individual fields in the gameServer from the save
 * @param gs the [GameServer] to set
 * @param save the [GameServerSave] object to load from
 */
private fun setGameServerFields(gs: GameServer, save: GameServerSave) {
    gs.arrivalSpawnTimerS = save.arrivalSpawnTimerS
    gs.previousArrivalOffsetS = save.previousArrivalOffsetS
    gs.trafficValue = save.trafficValue
    gs.trafficMode = save.trafficMode
    gs.score = save.score
    gs.highScore = save.highScore
    gs.landed = save.landed
    gs.departed = save.departed
    gs.weatherMode = save.weatherMode
    gs.emergencyRate = save.emergencyRate
    gs.stormsDensity = save.stormsDensity
    gs.gameSpeed = save.gameSpeed
    gs.nightModeStart = save.nightModeStart
    gs.nightModeEnd = save.nightModeEnd
    gs.useRecat = save.useRecat
    gs.trailDotTimer = save.trailDotTimer
}
