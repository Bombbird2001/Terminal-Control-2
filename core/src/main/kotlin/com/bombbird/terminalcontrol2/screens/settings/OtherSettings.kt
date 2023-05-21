package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.savePlayerSettings
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import ktx.scene2d.*

class OtherSettings: BaseSettings() {
    private val autosaveSelectBox: KSelectBox<String>

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@ {
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Autosave interval:")
                            autosaveSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, "1$MINUTES_SUFFIX", "2$MINUTES_SUFFIX", "3$MINUTES_SUFFIX", "5$MINUTES_SUFFIX")
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
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the relevant miscellaneous
     * settings and set the select box choices based on them
     */
    override fun setToCurrentClientSettings() {
        autosaveSelectBox.selected = if (AUTOSAVE_INTERVAL_MIN == 0) OFF else "$AUTOSAVE_INTERVAL_MIN$MINUTES_SUFFIX"
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the select box choices
     * and set the relevant miscellaneous settings based on them
     */
    override fun updateClientSettings() {
        AUTOSAVE_INTERVAL_MIN = autosaveSelectBox.selected.let { if (it == OFF) 0 else it.replace(MINUTES_SUFFIX, "").toInt() }
        savePlayerSettings()
    }
}