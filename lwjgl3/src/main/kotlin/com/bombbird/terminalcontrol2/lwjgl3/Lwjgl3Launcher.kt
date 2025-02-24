@file:JvmName("Lwjgl3Launcher")

package com.bombbird.terminalcontrol2.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.integrations.StubAchievementHandler

/** Launches the desktop (LWJGL3) application. */
fun main() {
    Lwjgl3Application(TerminalControl2(DesktopFileHandler(), DesktopTTSHandler(), DesktopDiscordHandler(), StubAchievementHandler),
        Lwjgl3ApplicationConfiguration().apply {
            setTitle("Terminal Control 2")
            if (!isLinuxWayland()) {
                setWindowedMode(1920, 1440)
                setMaximized(true)
                setWindowIcon(
                    "WindowIcon/Icon16.png",
                    "WindowIcon/Icon32.png",
                    "WindowIcon/Icon48.png",
                    "WindowIcon/Icon64.png"
                )
            }
            setForegroundFPS(60)
            setBackBufferConfig(8, 8, 8, 8, 16, 0, 0)
    })
}

fun isLinuxWayland(): Boolean {
    return System.getProperty("os.name").lowercase().contains("linux") && System.getenv("XDG_SESSION_TYPE") == "wayland"
}
