package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.integrations.DiscordHandler
import com.bombbird.terminalcontrol2.utilities.FileLog
import de.jcm.discordgamesdk.Core
import de.jcm.discordgamesdk.CreateParams
import de.jcm.discordgamesdk.GameSDKException
import de.jcm.discordgamesdk.activity.Activity
import ktx.assets.toInternalFile
import java.io.File
import java.time.Instant

class DesktopDiscordHandler: DiscordHandler {
    private lateinit var discordThread: Thread
    private var running = false

    private lateinit var core: Core
    private lateinit var menuActivity: Activity
    private lateinit var gameActivity: Activity
    private var gameTimeSet = false

    private fun getNativeLibrary(): File? {
        val name = "discord_game_sdk"
        var osName = System.getProperty("os.name").lowercase()
        var arch = System.getProperty("os.arch").lowercase()

        if (arch == "x86_64") arch = "amd64"

        val objectName: String

        if (osName.contains("windows")) {
            osName = "windows_$arch"
            objectName = "$name.dll"
        } else if (osName.contains("linux")) {
            osName = "linux"
            objectName = "$name.so"
        } else if (osName.contains("mac os")) {
            osName = "macos"
            objectName = "$name.dylib"
        } else {
            FileLog.warn("DesktopDiscordHandler", "Unknown OS: $osName")
            return null
        }

        return "libs/$osName/$objectName".toInternalFile().file()
    }

    override fun initialize() {
        discordThread = Thread {
            val nativeLibrary = getNativeLibrary()
            if (nativeLibrary == null) {
                FileLog.warn("DesktopDiscordHandler", "Failed to initialize Discord Game SDK: Native library not found")
                return@Thread
            }

            try {
                Core.init(nativeLibrary, "tc2-java-discord-game-sdk")
                FileLog.info("DesktopDiscordHandler", "Discord Game SDK initialized")
            } catch (e: UnsatisfiedLinkError) {
                FileLog.warn("DesktopDiscordHandler", "Failed to load Discord Game SDK native library:\n$e")
                return@Thread
            } catch (e: Exception) {
                FileLog.warn("DesktopDiscordHandler", "Failed to initialize Discord Game SDK:\n$e")
                return@Thread
            }

            val params = CreateParams().apply {
                clientID = Secrets.DISCORD_GAME_SDK_APP_ID
                setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD)
            }
            try {
                core = Core(params)
            } catch (e: GameSDKException) {
                FileLog.warn("DesktopDiscordHandler", "Discord is not installed and open, Game SDK will not work")
                return@Thread
            }
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