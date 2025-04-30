package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.json.getMoshiWithAllAdapters
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.io.IOException
import java.lang.NumberFormatException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Deletes the main save file with the input ID (does not delete the backup)
 * @param saveId ID of the main save to delete
 */
fun deleteMainSave(saveId: Int) {
    val saveFolderHandle = getExtDir("Saves") ?: return
    if (!saveFolderHandle.exists()) return

    val saveHandle = saveFolderHandle.child("${saveId}.json")
    if (saveHandle.exists()) saveHandle.delete()

    val metaHandle = saveFolderHandle.child("${saveId}.meta")
    if (metaHandle.exists()) metaHandle.delete()
}

/**
 * Deletes the backup save files with the input ID (does not delete the main save)
 * @param saveId ID of the backup save to delete
 */
fun deleteBackupSave(saveId: Int) {
    val saveFolderHandle = getExtDir("Saves") ?: return
    if (!saveFolderHandle.exists()) return

    val backupHandle = saveFolderHandle.child("${saveId}-backup.json")
    if (backupHandle.exists()) backupHandle.delete()

    val backupMetaHandle = saveFolderHandle.child("${saveId}-backup.meta")
    if (backupMetaHandle.exists()) backupMetaHandle.delete()
}

/**
 * Exports a copy of the saved game to location of user's choice using the file API
 * @param saveId ID of the save to export
 * @return true if saved successfully, else false
 */
fun exportSave(saveId: Int, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    val saveFolderHandle = getExtDir("Saves") ?: return onFailure("Game save folder not found")
    if (!saveFolderHandle.exists()) return onFailure("Game save folder not found")

    val saveHandle = saveFolderHandle.child("${saveId}.json")
    if (!saveHandle.exists()) return onFailure("Save file not found")

    GAME.externalFileHandler.selectAndSaveToFile(saveHandle.readString(), onSuccess, onFailure)
}

/**
 * Imports a save from the provided [saveString] which should be in JSON format;
 * the save will be assigned a new unique ID if [uniqueIdToUse] is null, else
 * will use the provided ID in the save metadata; calls [onSuccess] with the
 * name of the airport if import was successful, or [onFailure] with the reason
 * for failure if import failed
 */
@OptIn(ExperimentalStdlibApi::class)
fun importSaveFromString(saveString: String, uniqueIdToUse: String?, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
    val moshi = getMoshiWithAllAdapters()
    try {
        val testGs = GameServer.testGameServer()
        GAME.gameServer = testGs
        loadAircraftData()
        val saveObject = moshi.adapter<GameServerSave>().fromJson(saveString) ?: return onFailure("Error parsing file")
        testGs.mainName = saveObject.mainName
        val configDetails = GAME.gameServer?.getSaveMetaRunwayConfigString()
        GAME.gameServer = null
        // Save meta information
        val metaObject = GameSaveMeta(
            saveObject.mainName, saveObject.score, saveObject.highScore, saveObject.landed,
            saveObject.departed, configDetails,
            ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
            testGs.getRandomUniqueID()
        )
        val saveFolderHandle = getExtDir("Saves") ?: return onFailure("Error opening game save folder")
        val saveId = getNextAvailableSaveID()
        val saveHandle = saveFolderHandle.child("${saveId}.json")
        saveHandle.writeString(saveString, false)
        val metaHandle = saveFolderHandle.child("${saveId}.meta")
        metaHandle.writeString(moshi.adapter<GameSaveMeta>().toJson(metaObject), false)
        onSuccess(saveObject.mainName)
    } catch (e: JsonDataException) {
        e.printStackTrace()
        GAME.gameServer = null
        onFailure("File may be corrupted - it does not contain valid game data")
    }
}

/**
 * Imports an exported file into the saves folder of the game with the file
 * selection UI, calling [onSuccess] with the name of the airport if import was
 * successful, or [onFailure] with the reason for failure if import failed
 */
fun importSaveWithFileDialog(onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
    GAME.externalFileHandler.selectAndReadFromFile({
        if (it == null) return@selectAndReadFromFile onFailure("Data is missing in file")
        importSaveFromString(it, null,  onSuccess, onFailure)
    }, onFailure)
}

/**
 * Gets the next ID that is larger than all current saves (so that saves created in order are listed in order)
 * @return new save ID, or null if no slots found
 */
fun getNextAvailableSaveID(): Int? {
    val saveFolderHandle = getExtDir("Saves") ?: return null
    var saveIndex = 0
    saveFolderHandle.list().forEach {
        try {
            val fileIndex = it.nameWithoutExtension().toInt()
            if (saveIndex <= fileIndex) saveIndex = fileIndex + 1
        } catch (_: NumberFormatException) {
            return@forEach
        }
    }
    return saveIndex
}

/** Gets a [GdxArrayMap] of save IDs mapped to their respective [GameSaveMeta] */
fun getAvailableSaveGames(): GdxArrayMap<Int, GameSaveMeta> {
    val gamesFound = GdxArrayMap<Int, GameSaveMeta>()
    val saveFolderHandle = getExtDir("Saves") ?: return gamesFound
    if (saveFolderHandle.exists()) {
        val allSaveHandles = saveFolderHandle.list()
        val backupsFound = GdxArray<Pair<Int, GameSaveMeta>>(allSaveHandles.size)
        allSaveHandles.forEach {
            if (it.extension() != "meta") return@forEach
            var name = it.nameWithoutExtension()
            val isBackup = if (name.contains("-backup")) {
                name = name.replace("-backup", "")
                true
            } else false
            val id: Int
            try {
                id = name.toInt()
            } catch (_: NumberFormatException) {
                return@forEach
            }
            @OptIn(ExperimentalStdlibApi::class)
            try {
                val metaInfo = Moshi.Builder().build().adapter<GameSaveMeta>().fromJson(it.readString()) ?: return@forEach
                if (!isBackup)
                    gamesFound.put(id, metaInfo)
                else
                    backupsFound.add(Pair(id, metaInfo))
            } catch (e: IOException) {
                FileLog.warn("GameSaveManager", "Error reading save meta file: $e")
                return@forEach
            }
        }
        for (i in 0 until backupsFound.size) {
            val backupEntry = backupsFound[i]
            if (!gamesFound.containsKey(backupEntry.first)) gamesFound.put(backupEntry.first, backupEntry.second)
        }
    }

    return gamesFound
}