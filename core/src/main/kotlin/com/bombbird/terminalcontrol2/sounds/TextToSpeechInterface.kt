package com.bombbird.terminalcontrol2.sounds

/** Interface for calling Text-to-speech functionality on various platforms */
interface TextToSpeechInterface {
    fun sayText(text: String, voice: String)

    fun cancel()

    fun checkAndUpdateVoice(voice: String): String

    fun loadVoices()

    fun quit()
}

/** Stub TTS interface for testing, not implemented or not required */
object StubTextToSpeech : TextToSpeechInterface {
    override fun sayText(text: String, voice: String) {}

    override fun cancel() {}

    override fun checkAndUpdateVoice(voice: String): String { return "" }

    override fun loadVoices() {}

    override fun quit() {}
}