package com.bombbird.terminalcontrol2.files

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.bombbird.terminalcontrol2.global.APP_TYPE
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.assets.toExternalFile
import ktx.assets.toLocalFile

/**
 * Gets the FileHandle corresponding to the user appdata directory where game data is saved, depending on platform
 * @param path the subfolder inside the app data directory of the game to retrieve
 * @return a [FileHandle] with the requested subfolder in the app data directory, or null if not found
 */
fun getExtDir(path: String): FileHandle? {
    var handle: FileHandle? = null
    if (APP_TYPE == Application.ApplicationType.Desktop) {
        // If desktop, save to external roaming appData
        handle = "AppData/Roaming/TerminalControl2/$path".toExternalFile()
    } else if (Gdx.app.type == Application.ApplicationType.Android) {
        // If Android, check first if local storage available
        if (Gdx.files.isLocalStorageAvailable) {
            handle = path.toLocalFile()
        } else FileLog.info("FileLoaderTools", "Local storage unavailable for Android")
    } else FileLog.info("FileLoaderTools", "Unknown platform")
    return handle
}
