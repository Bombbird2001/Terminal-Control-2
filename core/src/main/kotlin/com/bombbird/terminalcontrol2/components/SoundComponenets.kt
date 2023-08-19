package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.Mapper

/** Component for tagging the TTS voice to be associated with this aircraft */
data class TTSVoice(var voice: String? = null): Component {
    companion object {
        val mapper = object: Mapper<TTSVoice>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising TTSVoice mapper")
        }
    }
}