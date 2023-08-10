package com.bombbird.terminalcontrol2.android

import android.content.Intent
import android.os.Bundle

import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.bombbird.terminalcontrol2.TerminalControl2

/** Launches the Android application. */
class AndroidLauncher : AndroidApplication() {
    companion object {
        const val OPEN_SAVE_FILE = 45510
        const val CREATE_SAVE_FILE = 45511
    }

    private val fileHandler = AndroidFileHandler(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(TerminalControl2(fileHandler), AndroidApplicationConfiguration().apply {
            // Configure your application here.
            numSamples = 0
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = false
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            OPEN_SAVE_FILE -> {
                if (resultCode == RESULT_CANCELED) return
                fileHandler.handleOpenResult(data)
            }
            CREATE_SAVE_FILE -> {
                if (resultCode == RESULT_CANCELED) return
                fileHandler.handleSaveResult(data)
            }
        }
    }
}
