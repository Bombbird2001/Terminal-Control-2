package com.bombbird.terminalcontrol2.android

import android.os.Bundle

import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.bombbird.terminalcontrol2.TerminalControl2

/** Launches the Android application. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(TerminalControl2(), AndroidApplicationConfiguration().apply {
            // Configure your application here.
        })
    }
}
