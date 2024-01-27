package com.bombbird.terminalcontrol2.integrations

/** Interface for handling Discord Game SDK (upgraded Discord Rich Presence */
interface DiscordHandler {
    fun initialize()

    fun updateInGame(mapIcao: String, planesInControl: Int, playersInGame: Int, maxPlayers: Int,
                     publicMultiplayer: Boolean)

    fun updateInMenu()

    fun quit()
}

/** Stub Discord handler object for testing, not implemented or not required */
object StubDiscordHandler: DiscordHandler {
    override fun initialize() {}

    override fun updateInGame(mapIcao: String, planesInControl: Int, playersInGame: Int, maxPlayers: Int,
                              publicMultiplayer: Boolean) {}

    override fun updateInMenu() {}

    override fun quit() {}
}