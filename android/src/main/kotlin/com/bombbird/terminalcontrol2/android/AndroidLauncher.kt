package com.bombbird.terminalcontrol2.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.multidex.MultiDex

import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.utilities.FileLog

/** Launches the Android application. */
class AndroidLauncher : AndroidApplication() {
    companion object {
        const val OPEN_SAVE_FILE = 45510
        const val CREATE_SAVE_FILE = 45511
        const val ACT_CHECK_TTS_DATA = 45512
        const val ACT_INSTALL_TTS_DATA = 45513
        const val ACT_IN_APP_UPDATE = 45514
    }

    private lateinit var terminalControl2: TerminalControl2
    private val fileHandler = AndroidFileHandler(this)
    private val ttsHandler = AndroidTTSHandler(this)
    private val inAppUpdate = InAppUpdateManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppUpdate.checkUpdateAvailable()
        terminalControl2 = TerminalControl2(fileHandler, ttsHandler)
        initialize(terminalControl2, AndroidApplicationConfiguration().apply {
            // Configure your application here.
            numSamples = 0
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = false
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_CANCELED) return
        when (requestCode) {
            OPEN_SAVE_FILE -> {
                fileHandler.handleOpenResult(data)
            }
            CREATE_SAVE_FILE -> {
                fileHandler.handleSaveResult(data)
            }
            ACT_CHECK_TTS_DATA -> {
                ttsHandler.handleCheckTTSResult(resultCode)
            }
            ACT_INSTALL_TTS_DATA -> {
                ttsHandler.handleInstallTTSResult()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        FileLog.info("AndroidLauncher", "onPause called")
        terminalControl2.androidLifeCycleHandler.onPause()
    }

    override fun onResume() {
        super.onResume()
        FileLog.info("AndroidLauncher", "onResume called")
        terminalControl2.androidLifeCycleHandler.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHandler.destroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        MultiDex.install(this)
    }
}
