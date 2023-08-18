package com.bombbird.terminalcontrol2.android

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.Voice
import com.badlogic.gdx.backends.android.AndroidApplication
import com.bombbird.terminalcontrol2.sounds.TextToSpeechInterface
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.collections.GdxArray
import java.util.*

/** TTS handler for Android platform */
class AndroidTTSHandler(private val app: AndroidApplication): TextToSpeechInterface, OnInitListener {
    private var tts: TextToSpeech? = null
    private val voiceArray = GdxArray<String>()

    override fun sayText(text: String, voice: String) {
        tts?.let {
            it.voice = Voice(voice, Locale.ENGLISH, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, null)
            it.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    override fun cancel() {
        tts?.stop()
    }

    override fun checkAndUpdateVoice(voice: String): String {
        if (voiceArray.contains(voice)) return voice
        return voiceArray.random() ?: voice
    }

    override fun loadVoices() {
        tts?.let {
            try {
                if (it.voices.isEmpty()) return
            } catch (e: Exception) {
                return
            }
            it.voices?.let { voices ->
                for (available in voices) {
                    if ("en" == available.name.substring(0, 2)) {
                        voiceArray.add(available.name)
                    }
                }
            }
        } ?: return
    }

    override fun quit() {
        TODO("Not yet implemented")
    }

    /**
     * Called when the Android application receives a result from the check voice data activity
     * @param resultCode result code from the check voice data activity
     */
    fun handleCheckTTSResult(resultCode: Int) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            // Data exists, so we instantiate the TTS engine
            tts = TextToSpeech(app, this)
        } else {
            // Data is missing, so we start the TTS installation process
            val installIntent = Intent()
            installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            app.startActivityForResult(installIntent, AndroidLauncher.ACT_INSTALL_TTS_DATA)
        }
    }

    /** Called after the Android application receives a result from the install voice data activity */
    fun handleInstallTTSResult() {
        val ttsIntent = Intent()
        ttsIntent.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        app.startActivityForResult(ttsIntent, AndroidLauncher.ACT_CHECK_TTS_DATA)
    }

    /** Sets initial properties after initialisation of TTS is complete  */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts != null) {
                val result = tts?.setLanguage(Locale.ENGLISH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // toastManager.ttsLangNotSupported()
                } else {
                    FileLog.info("Android TTS", "TTS initialized successfully")
                    tts?.setSpeechRate(1.5f)
                    loadVoices()
                }
            }
        } else {
            // toastManager.initTTSFail()
            FileLog.warn("Android TTS", "TTS initialization failed")
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}