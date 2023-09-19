package com.bombbird.terminalcontrol2.sounds

import com.badlogic.gdx.utils.Disposable
import com.bombbird.terminalcontrol2.global.COMMS_PILOT_VOICES
import com.bombbird.terminalcontrol2.global.COMMUNICATIONS_SOUND

class TextToSpeechManager(private val ttsHandler: TextToSpeechHandler): Disposable {
    /** Initialises the TTS engine for the appropriate platform */
    fun init() {
        ttsHandler.init()

    }

    /** Says the input text with the given voice */
    fun say(text: String, voice: String) {
        if (isVoiceDisabled()) return
        ttsHandler.sayText(text, voice)
    }

    /** Returns a random voice, or null if no voices available */
    fun getRandomVoice(): String? {
        return ttsHandler.getRandomVoice()
    }

    /**
     * Checks whether the input voice is available on the device, if so returns the original input voice, else, returns
     * a random available voice
     * @param voice the voice to check
     */
    fun checkVoiceElseRandomVoice(voice: String): String? {
        return if (ttsHandler.checkVoiceAvailable(voice)) voice else getRandomVoice()
    }

    /** Called when player quits a game */
    fun quitGame() {
        ttsHandler.onQuitGame()
    }

    /** Performs clean up actions for TTS handler when being disposed */
    override fun dispose() {
        ttsHandler.onQuitApp()
    }

    /** Checks whether the game sound settings has disabled pilot voices */
    private fun isVoiceDisabled(): Boolean {
        return COMMUNICATIONS_SOUND < COMMS_PILOT_VOICES
    }
}