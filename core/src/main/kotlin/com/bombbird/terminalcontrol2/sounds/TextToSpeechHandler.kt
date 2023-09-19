package com.bombbird.terminalcontrol2.sounds

/** Abstract class for calling Text-to-speech functionality on various platforms */
abstract class TextToSpeechHandler {
    protected var onInitFail: (() -> Unit)? = null
    protected var onVoiceDataMissing: (() -> Unit)? = null

    fun setOnInitFailAction(onInitFail: () -> Unit) {
        this.onInitFail = onInitFail
    }

    fun setOnVoiceDataMissingAction(onVoiceDataMissing: () -> Unit) {
        this.onVoiceDataMissing = onVoiceDataMissing
    }

    /** Initializes the TTS handler */
    abstract fun init()

    /**
     * Says the input text with the given voice
     * @param text text to say
     * @param voice voice to use
      */
    abstract fun sayText(text: String, voice: String)

    /** To be called when the player exits from a game */
    abstract fun onQuitGame()

    /** Returns a random voice, or null if no voices available */
    abstract fun getRandomVoice(): String?

    /**
     * Checks whether the input voice is available on the device
     * @param voice the voice to check
     */
    abstract fun checkVoiceAvailable(voice: String): Boolean

    /** To be called when the app is quitting */
    abstract fun onQuitApp()
}

/** Stub TTS object for testing, not implemented or not required */
object StubTextToSpeech : TextToSpeechHandler() {
    override fun init() {}

    override fun sayText(text: String, voice: String) {}

    override fun onQuitGame() {}

    override fun getRandomVoice(): String { return "" }

    override fun checkVoiceAvailable(voice: String): Boolean { return false }

    override fun onQuitApp() {}
}