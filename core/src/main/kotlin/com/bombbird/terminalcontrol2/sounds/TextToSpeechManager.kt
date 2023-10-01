package com.bombbird.terminalcontrol2.sounds

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Disposable
import com.bombbird.terminalcontrol2.global.COMMS_PILOT_VOICES
import com.bombbird.terminalcontrol2.global.COMMUNICATIONS_SOUND
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.screens.MainMenu
import com.bombbird.terminalcontrol2.ui.CustomDialog

class TextToSpeechManager(private val ttsHandler: TextToSpeechHandler): Disposable {
    private var ttsNotAvailable = false

    /** Initialises the TTS engine for the appropriate platform */
    fun init() {
        ttsHandler.setOnInitFailAction {
            ttsNotAvailable = true
            Gdx.app.postRunnable {
                CustomDialog("TTS error", "Text-to-Speech initialisation failed - " +
                        "pilot voices will not work. Your device may not have a TTS engine " +
                        "installed, please check your device TTS settings and try again.",
                    "", "Ok").show(GAME.getScreen<MainMenu>().stage)
            }
        }

        ttsHandler.setOnVoiceDataMissingAction {
            ttsNotAvailable = true
            Gdx.app.postRunnable {
                CustomDialog("TTS error", "No voices found for Text-to-Speech - " +
                        "pilot voices will not work. Please ensure that there are TTS voices " +
                        "installed on your device and try again.",
                    "", "Ok").show(GAME.getScreen<MainMenu>().stage)
            }
        }
        ttsHandler.init()
    }

    /** Says the input text with the given voice */
    fun say(text: String, voice: String) {
        if (ttsNotAvailable || isVoiceDisabled()) return
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

    /** Returns true if TTS is not available */
    fun isTTSNotAvailable(): Boolean {
        return ttsNotAvailable
    }

    /** Checks whether the game sound settings has disabled pilot voices */
    private fun isVoiceDisabled(): Boolean {
        return COMMUNICATIONS_SOUND < COMMS_PILOT_VOICES
    }
}