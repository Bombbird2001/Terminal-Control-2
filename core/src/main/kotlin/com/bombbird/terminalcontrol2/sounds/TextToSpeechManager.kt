package com.bombbird.terminalcontrol2.sounds

import com.bombbird.terminalcontrol2.global.COMMS_PILOT_VOICES
import com.bombbird.terminalcontrol2.global.COMMUNICATIONS_SOUND

class TextToSpeechManager {
    private fun isVoiceDisabled(): Boolean {
        return COMMUNICATIONS_SOUND >= COMMS_PILOT_VOICES
    }
}