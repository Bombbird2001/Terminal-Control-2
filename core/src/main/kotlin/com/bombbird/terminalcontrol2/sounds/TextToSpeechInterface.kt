package com.bombbird.terminalcontrol2.sounds

/** Interface for calling Text-to-speech functionality on various platforms */
interface TextToSpeechInterface {
    /**
     * Says the input text with the given voice
     * @param text text to say
     * @param voice voice to use
      */
    fun sayText(text: String, voice: String)

    /** To be called when the player exits from a game */
    fun onQuitGame()

    /** Returns a random voice, or null if no voices available */
    fun getRandomVoice(): String?

    /** To be called when the app is quitting */
    fun onQuitApp()
}

/** Stub TTS interface for testing, not implemented or not required */
object StubTextToSpeech : TextToSpeechInterface {
    override fun sayText(text: String, voice: String) {}

    override fun onQuitGame() {}

    override fun getRandomVoice(): String { return "" }

    override fun onQuitApp() {}
}