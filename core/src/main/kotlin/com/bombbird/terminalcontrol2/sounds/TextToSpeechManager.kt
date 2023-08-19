package com.bombbird.terminalcontrol2.sounds

import com.bombbird.terminalcontrol2.global.COMMS_PILOT_VOICES
import com.bombbird.terminalcontrol2.global.COMMUNICATIONS_SOUND
import com.bombbird.terminalcontrol2.global.GAME

class TextToSpeechManager {
    /** Says the input text with the given voice */
    fun say(text: String, voice: String) {
        if (isVoiceDisabled()) return
        GAME.ttsHandler.sayText(text, voice)
    }

    /** Returns a random voice, or null if no voices available */
    fun getRandomVoice(): String? {
        return GAME.ttsHandler.getRandomVoice()
    }

    /** Checks whether the game sound settings has disabled pilot voices */
    private fun isVoiceDisabled(): Boolean {
        return COMMUNICATIONS_SOUND < COMMS_PILOT_VOICES
    }
}