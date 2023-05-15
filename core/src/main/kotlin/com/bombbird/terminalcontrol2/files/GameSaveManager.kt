package com.bombbird.terminalcontrol2.files

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