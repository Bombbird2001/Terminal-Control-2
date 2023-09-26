package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/** Component for tagging the TTS voice to be associated with this aircraft */
@JsonClass(generateAdapter = true)
data class TTSVoice(var voice: String? = null): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.TTS_VOICE

    companion object {
        val mapper = object: Mapper<TTSVoice>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}