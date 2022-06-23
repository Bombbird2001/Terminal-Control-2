package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import ktx.scene2d.*

/** Settings screen for sound settings */
class SoundSettingsScreen: BasicUIScreen() {
    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@ {
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Communications:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "Sound effects only", "Pilot voices")
                            }
                            defaultSettingsLabel("Alerts:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "On")
                            }
                        }
                    }.cell(growY = true, padTop = 70f)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainSettingsScreen>()
                    }
                }
            }
        }
    }
}