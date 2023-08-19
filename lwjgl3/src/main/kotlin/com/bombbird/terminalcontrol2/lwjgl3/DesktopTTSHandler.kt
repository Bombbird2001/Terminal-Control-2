package com.bombbird.terminalcontrol2.lwjgl3

import com.bombbird.terminalcontrol2.sounds.TextToSpeechInterface

class DesktopTTSHandler: TextToSpeechInterface {
    override fun sayText(text: String, voice: String) {

    }

    override fun cancel() {

    }

    override fun getRandomVoice(): String? {
        return ""
    }

    override fun quit() {

    }
}