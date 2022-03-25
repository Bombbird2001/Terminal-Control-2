@file:JvmName("Lwjgl3Launcher")

package com.bombbird.terminalcontrol2.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.bombbird.terminalcontrol2.TerminalControl2

/** Launches the desktop (LWJGL3) application. */
fun main() {
    Lwjgl3Application(TerminalControl2(), Lwjgl3ApplicationConfiguration().apply {
        setTitle("TerminalControl2")
        setWindowedMode(1920, 1440)
        setMaximized(true)
        setWindowIcon(*(arrayOf(128, 64, 32, 16).map { "libgdx$it.png" }.toTypedArray()))
    })
}
