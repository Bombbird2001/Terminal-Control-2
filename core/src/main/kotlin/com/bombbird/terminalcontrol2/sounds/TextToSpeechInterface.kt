package com.bombbird.terminalcontrol2.sounds

/** Interface for calling Text-to-speech functionality on various platforms */
interface TextToSpeechInterface {
    /**
     * Says the input text with the given voice
     * @param text text to say
     * @param voice voice to use
      */
    fun sayText(text: String, voice: String)

    /** Cancels all current and queued speech */
    fun cancel()

    /** Returns a random voice, or null if no voices available */
    fun getRandomVoice(): String?

    /**
     * Quits and stops the TTS engine
     * // TODO May not be needed, or may only be called on Desktop
     */
    fun quit()
}

/** Stub TTS interface for testing, not implemented or not required */
object StubTextToSpeech : TextToSpeechInterface {
    override fun sayText(text: String, voice: String) {}

    override fun cancel() {}

    override fun getRandomVoice(): String { return "" }

    override fun quit() {}
}