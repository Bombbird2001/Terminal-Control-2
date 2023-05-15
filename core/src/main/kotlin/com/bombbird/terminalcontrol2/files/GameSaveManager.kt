package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.GAME

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
fun exportSave(saveId: Int): Boolean {
    val saveFolderHandle = getExtDir("Saves") ?: return false
    if (!saveFolderHandle.exists()) return false

    val saveHandle = saveFolderHandle.child("${saveId}.json")
    if (!saveHandle.exists()) return false

    return GAME.externalFileHandler.selectAndSaveToFile(saveHandle.readString())
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