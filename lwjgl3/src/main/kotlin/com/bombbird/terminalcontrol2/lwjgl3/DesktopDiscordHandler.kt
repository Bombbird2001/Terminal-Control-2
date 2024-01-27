package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.integrations.DiscordHandler
import com.bombbird.terminalcontrol2.utilities.FileLog
import de.jcm.discordgamesdk.Core
import de.jcm.discordgamesdk.CreateParams
import de.jcm.discordgamesdk.activity.Activity
import ktx.assets.toInternalFile
import java.time.Instant

class DesktopDiscordHandler: DiscordHandler {
    private lateinit var discordThread: Thread
    private var running = false

    private lateinit var core: Core
    private lateinit var menuActivity: Activity
    private lateinit var gameActivity: Activity
    private var gameTimeSet = false

    override fun initialize() {
        discordThread = Thread {
            val discordLibrary = "Libs/windows_x86_64/discord_game_sdk.dll".toInternalFile()
            Core.init(discordLibrary.file())

            val params = CreateParams().apply {
                clientID = Secrets.DISCORD_GAME_SDK_APP_ID
                flags = CreateParams.getDefaultFlags()
            }
            core = Core(params)
            menuActivity = Activity().apply {
                assets().largeImage = "icon"
                details = "Chilling in menu"
            }
            gameActivity = Activity().apply {
                assets().largeImage = "icon"
            }
            updateInMenu()

            while (!Thread.currentThread().isInterrupted && running) {
                core.runCallbacks()
                Thread.sleep(16)
            }
        }
        running = true
        discordThread.start()
        FileLog.info("DesktopDiscordHandler", "Discord Game SDK initialized")
    }

    override fun updateInGame(mapIcao: String, planesInControl: Int, playersInGame: Int, maxPlayers: Int,
                              publicMultiplayer: Boolean) {
        gameActivity.details = "$mapIcao - $planesInControl aircraft in control"
        if (maxPlayers > 1) {
            gameActivity.state = if (publicMultiplayer) "Public multiplayer" else "LAN multiplayer"
            gameActivity.party().size().maxSize = maxPlayers
            gameActivity.party().size().currentSize = playersInGame
        } else {
            gameActivity.state = "Singleplayer"
            gameActivity.party().size().maxSize = 0
            gameActivity.party().size().currentSize = 0
        }
        if (!gameTimeSet) {
            gameActivity.timestamps().start = Instant.now()
            gameTimeSet = true
        }
        core.activityManager().updateActivity(gameActivity)
    }

    override fun updateInMenu() {
        core.activityManager().updateActivity(menuActivity)
        gameTimeSet = false
    }

    override fun quit() {
        running = false
    }
}