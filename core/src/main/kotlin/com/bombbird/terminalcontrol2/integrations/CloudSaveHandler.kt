package com.bombbird.terminalcontrol2.integrations

import com.badlogic.gdx.graphics.Pixmap

interface CloudSaveHandler {
    fun showSavedGames()

    fun saveGame(uniqueId: String, saveData: String, description: String, pixmap: Pixmap)
}

/** Stub Cloud Save interface object for testing, not implemented or not required */
object StubCloudSaveHandler: CloudSaveHandler {
    override fun showSavedGames() {}

    override fun saveGame(uniqueId: String, saveData: String, description: String, pixmap: Pixmap) {}
}