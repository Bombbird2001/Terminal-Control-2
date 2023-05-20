package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.json.getMoshiWithAllAdapters
import com.bombbird.terminalcontrol2.networking.GameServer
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.adapter

/**
 * Deletes the save file with the input ID
 * @param saveId ID of the save to delete
 */
fun deleteSave(saveId: Int) {
    val saveFolderHandle = getExtDir("Saves") ?: return
    if (!saveFolderHandle.exists()) return

    val saveHandle = saveFolderHandle.child("${saveId}.json")
    if (saveHandle.exists()) saveHandle.delete()

    val metaHandle = saveFolderHandle.child("${saveId}.meta")
    if (metaHandle.exists()) metaHandle.delete()
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
 * Imports an exported file into the saves folder of the game
 * @return a triple containing a boolean - true if the import was successful, else false; a string, containing the
 * reason for failure if any; a string, with the name of the airport is import was successful
 */
@OptIn(ExperimentalStdlibApi::class)
fun importSave(onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
    GAME.externalFileHandler.selectAndReadFromFile({
        val moshi = getMoshiWithAllAdapters()
        try {
            if (it == null) return@selectAndReadFromFile onFailure("Data is missing in file")
            GAME.gameServer = GameServer.testGameServer()
            loadAircraftData()
            val saveObject = moshi.adapter<GameServerSave>().fromJson(it) ?: return@selectAndReadFromFile onFailure("Error parsing file")
            GAME.gameServer = null
            // Save meta information
            val metaObject = GameSaveMeta(saveObject.mainName, saveObject.score, saveObject.highScore, saveObject.landed, saveObject.departed)
            val saveFolderHandle = getExtDir("Saves") ?: return@selectAndReadFromFile onFailure("Error opening game save folder")
            val saveId = getNextAvailableSaveID()
            val saveHandle = saveFolderHandle.child("${saveId}.json")
            saveHandle.writeString(it, false)
            val metaHandle = saveFolderHandle.child("${saveId}.meta")
            metaHandle.writeString(moshi.adapter<GameSaveMeta>().toJson(metaObject), false)
            onSuccess(saveObject.mainName)
        } catch (e: JsonDataException) {
            e.printStackTrace()
            GAME.gameServer = null
            onFailure("File may be corrupted - it does not contain valid game data")
        }
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
        val fileIndex = it.nameWithoutExtension().toInt()
        if (saveIndex <= fileIndex) saveIndex = fileIndex + 1
    }
    return saveIndex
}