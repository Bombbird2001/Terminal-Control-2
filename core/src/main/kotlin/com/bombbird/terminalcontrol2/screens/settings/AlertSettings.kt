package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.savePlayerSettings
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.ui.newSettingsRow
import ktx.scene2d.*

/** Settings screen for alert settings */
class AlertSettings: BaseSettings() {
    private val apwSelectBox: KSelectBox<String>
    private val stcaSelectBox: KSelectBox<String>

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@ {
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Area penetration warning:")
                            apwSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, "30$SECONDS_SUFFIX", "60$SECONDS_SUFFIX", "90$SECONDS_SUFFIX")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Short-term conflict alert:")
                            stcaSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, "30$SECONDS_SUFFIX", "60$SECONDS_SUFFIX", "90$SECONDS_SUFFIX")
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
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the relevant alert settings
     * and set the select box choices based on them
     */
    override fun setToCurrentClientSettings() {
        apwSelectBox.selected = if (APW_DURATION_S == 0) OFF else "$APW_DURATION_S$SECONDS_SUFFIX"
        stcaSelectBox.selected = if (STCA_DURATION_S == 0) OFF else "$STCA_DURATION_S$SECONDS_SUFFIX"
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the select box choices
     * and set the relevant alert settings based on them
     */
    override fun updateClientSettings() {
        APW_DURATION_S = apwSelectBox.selected.let { if (it == OFF) 0 else it.replace(SECONDS_SUFFIX, "").toInt() }
        STCA_DURATION_S = stcaSelectBox.selected.let { if (it == OFF) 0 else it.replace(SECONDS_SUFFIX, "").toInt() }
        savePlayerSettings()
    }
}