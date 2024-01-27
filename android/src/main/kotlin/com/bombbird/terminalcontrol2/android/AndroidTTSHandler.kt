package com.bombbird.terminalcontrol2.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.Voice
import com.badlogic.gdx.backends.android.AndroidApplication
import com.bombbird.terminalcontrol2.sounds.TextToSpeechHandler
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.collections.GdxArray
import ktx.collections.GdxSet
import java.util.*

/** TTS handler for Android platform */
class AndroidTTSHandler(private val app: AndroidApplication): TextToSpeechHandler(), OnInitListener {
    private var tts: TextToSpeech? = null
    private val voiceArray = GdxArray<String>()
    private val voiceSet = GdxSet<String>()

    /** Loads all available voices for this device */
    private fun loadVoices() {
        voiceArray.clear()
        voiceSet.clear()
        tts?.let {
            try {
                if (it.voices.isEmpty()) return@let
            } catch (e: Exception) {
                return@let
            }
            it.voices?.let { voices ->
                for (available in voices) {
                    if (available.locale.language == Locale.ENGLISH.language ||
                        available.locale.language == Locale.ENGLISH.isO3Language) {
                        voiceArray.add(available.name)
                        voiceSet.add(available.name)
                    }
                }
            }
        }

        if (voiceArray.isEmpty) {
            try {
                FileLog.warn("Android TTS", "No English voices found; all voices available: " +
                        "${tts?.voices?.joinToString { "${it.name} (${it.locale.language})" }}")
            } catch (e: NullPointerException) {
                FileLog.warn("Android TTS", "No English voices found; could not get voices available")
            }
            onVoiceDataMissing?.invoke()
        }
    }

    override fun init() {
        val ttsIntent = Intent()
        ttsIntent.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
        try {
            app.startActivityForResult(ttsIntent, AndroidLauncher.ACT_CHECK_TTS_DATA)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            onInitFail?.invoke()
        }
    }

    override fun sayText(text: String, voice: String) {
        tts?.let {
            it.voice = Voice(voice, Locale.ENGLISH, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, null)
            it.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        }
    }

    override fun onQuitGame() {
        tts?.stop()
    }

    override fun getRandomVoice(): String? {
        return voiceArray.random()
    }

    override fun checkVoiceAvailable(voice: String): Boolean {
        val containsVoice = voiceSet.contains(voice)
        if (!containsVoice) {
            FileLog.warn("Android TTS", "Voice $voice not available; voices available: $voiceArray")
        }
        return containsVoice
    }

    override fun onQuitApp() {
        tts?.stop()
    }

    /**
     * Called when the Android application receives a result from the check voice data activity
     * @param resultCode result code from the check voice data activity
     */
    fun handleCheckTTSResult(resultCode: Int) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            // Data exists, so we instantiate the TTS engine
            tts = TextToSpeech(app, this)
            FileLog.info("AndroidTTSHandler", "TTS initialized")
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

    /** Sets initial properties after initialisation of TTS is complete */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts != null) {
                val result = tts?.setLanguage(Locale.ENGLISH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    onVoiceDataMissing?.invoke()
                } else {
                    FileLog.info("Android TTS", "TTS initialized successfully")
                    tts?.setSpeechRate(1.25f)
                    loadVoices()
                }
            }
        } else {
            onInitFail?.invoke()
            FileLog.warn("Android TTS", "TTS initialization failed")
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}