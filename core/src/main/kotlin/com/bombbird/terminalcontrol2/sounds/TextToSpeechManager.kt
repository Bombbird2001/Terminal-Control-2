package com.bombbird.terminalcontrol2.sounds

import com.badlogic.gdx.utils.Disposable
import com.bombbird.terminalcontrol2.global.COMMS_PILOT_VOICES
import com.bombbird.terminalcontrol2.global.COMMUNICATIONS_SOUND

class TextToSpeechManager(private val ttsHandler: TextToSpeechInterface): Disposable {
    /** Says the input text with the given voice */
    fun say(text: String, voice: String) {
        if (isVoiceDisabled()) return
        ttsHandler.sayText(text, voice)
    }

    /** Returns a random voice, or null if no voices available */
    fun getRandomVoice(): String? {
        return ttsHandler.getRandomVoice()
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