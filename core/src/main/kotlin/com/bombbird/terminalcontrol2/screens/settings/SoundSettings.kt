package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.savePlayerSettings
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.scene2d.*

/** Settings screen for sound settings */
class SoundSettings: BaseSettings() {
    companion object {
        private const val SOUND_EFFECTS = "Sound effects only"
        private const val PILOT_VOICES = "Pilot voices"
    }

    private val commsSelectBox: KSelectBox<String>
    private val alertsSelectBox: KSelectBox<String>

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@ {
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Communications:")
                            commsSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, SOUND_EFFECTS, PILOT_VOICES)
                            }
                            defaultSettingsLabel("Alerts:")
                            alertsSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, ON)
                            }
                        }
                    }.cell(growY = true, padTop = 70f)
                    row().padTop(50f)
                    table {
                        textButton("Cancel", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, padRight = 100f, align = Align.bottom).addChangeListener { _, _ ->
                            GAME.setScreen<MainSettings>()
                        }
                        textButton("Confirm", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                            updateClientSettings()
                            GAME.setScreen<MainSettings>()
                        }
                    }
                }
            }
        }
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the relevant sound settings
     * and set the select box choices based on them
     */
    override fun setToCurrentClientSettings() {
        commsSelectBox.selected = when (COMMUNICATIONS_SOUND) {
            COMMS_OFF -> OFF
            COMMS_SOUND_EFFECTS -> SOUND_EFFECTS
            COMMS_PILOT_VOICES -> PILOT_VOICES
            else -> {
                FileLog.info("SoundSettings", "Unknown communication voice setting $COMMUNICATIONS_SOUND")
                PILOT_VOICES
            }
        }
        alertsSelectBox.selected = if (ALERT_SOUND_ON) ON else OFF
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the select box choices
     * and set the relevant sound settings based on them
     */
    override fun updateClientSettings() {
        COMMUNICATIONS_SOUND = when (commsSelectBox.selected) {
            OFF -> COMMS_OFF
            SOUND_EFFECTS -> COMMS_SOUND_EFFECTS
            PILOT_VOICES -> COMMS_PILOT_VOICES
            else -> {
                FileLog.info("SoundSettings", "Unknown communication voice selection ${commsSelectBox.selected}")
                COMMS_PILOT_VOICES
            }
        }
        ALERT_SOUND_ON = alertsSelectBox.selected != OFF
        savePlayerSettings()
    }
}