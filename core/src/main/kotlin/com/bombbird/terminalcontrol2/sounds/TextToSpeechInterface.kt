package com.bombbird.terminalcontrol2.sounds

/** Interface for calling Text-to-speech functionality on various platforms */
interface TextToSpeechInterface {
    fun sayText(text: String, voice: String)

    fun cancel()

    fun checkAndUpdateVoice(voice: String): String

    fun loadVoices()

    fun quit()
}