package com.bombbird.terminalcontrol2.integrations

interface CloudSaveHandler {
    fun showSavedGames()

    fun saveGame(uniqueId: String, saveData: String, description: String, timePlayedMs: Long)

    fun loadAllGames(onSuccess: (List<String>) -> Unit, onFailure: (String) -> Unit)
}

/** Stub Cloud Save interface object for testing, not implemented or not required */
object StubCloudSaveHandler: CloudSaveHandler {
    override fun showSavedGames() {}

    override fun saveGame(uniqueId: String, saveData: String, description: String, timePlayedMs: Long) {}

    override fun loadAllGames(onSuccess: (List<String>) -> Unit, onFailure: (String) -> Unit) {}
}