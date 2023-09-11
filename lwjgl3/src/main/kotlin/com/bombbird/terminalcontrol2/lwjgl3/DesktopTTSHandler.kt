package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.sounds.TextToSpeechInterface
import io.github.jonelo.jAdapterForNativeTTS.engines.SpeechEngine
import io.github.jonelo.jAdapterForNativeTTS.engines.SpeechEngineNative
import io.github.jonelo.jAdapterForNativeTTS.engines.VoicePreferences
import ktx.collections.GdxArray
import ktx.collections.GdxSet
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class DesktopTTSHandler: TextToSpeechInterface {
    private lateinit var speechEngine: SpeechEngine
    private lateinit var voicePrefs: VoicePreferences
    private val voiceArray = GdxArray<String>()
    private val voiceSet = GdxSet<String>()
    private val speechQueue = LinkedBlockingQueue<SpeechQueueItem>()
    private val running = AtomicBoolean(true)

    private class SpeechQueueItem(val text: String, val voice: String)

    /** Initializes the Desktop TTS speech engine and gets available voices */
    fun initializeEngine() {
        speechEngine = SpeechEngineNative.getInstance()
        voicePrefs = VoicePreferences().apply {
            language = Locale.ENGLISH.language
        }
        voiceArray.clear()
        voiceSet.clear()
        for (voice in speechEngine.availableVoices) {
            if (voice.matches(voicePrefs)) {
                voiceArray.add(voice.name)
                voiceSet.add(voice.name)
            }
        }
        speechEngine.setRate(20)

        thread(name = "Desktop TTS") {
            while (running.get()) {
                // Timeout to prevent this thread from being blocked forever even when app closes
                val item = speechQueue.poll(10, TimeUnit.SECONDS) ?: continue
                saySpeechItem(item)
            }
        }
    }

    /**
     * Starts the speaking for the input [SpeechQueueItem]
     * @param item item to be spoken
     */
    private fun saySpeechItem(item: SpeechQueueItem) {
        speechEngine.setVoice(item.voice)
        // waitFor blocks till the process is complete, so we won't have multiple speeches at once
        speechEngine.say(item.text).waitFor()
    }

    override fun sayText(text: String, voice: String) {
        speechQueue.offer(SpeechQueueItem(text, voice))
    }

    override fun onQuitGame() {
        speechQueue.clear()
        speechEngine.stopTalking()
    }

    override fun getRandomVoice(): String? {
        return voiceArray.random()
    }

    override fun checkVoiceAvailable(voice: String): Boolean {
        return voiceSet.contains(voice)
    }

    override fun onQuitApp() {
        speechQueue.clear()
        running.set(false)
    }
}