package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.ui.newSettingsRow
import ktx.scene2d.*

/** Settings screen for alert settings */
class AlertSettingsScreen: BasicUIScreen() {
    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@ {
                    scrollPane("SettingsPane") {
                        table {
                            // TODO Incremental unlocks
                            defaultSettingsLabel("Trajectory prediction:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "30 sec", "60 sec", "120 sec")
                            }
                            defaultSettingsLabel("Area penetration warning:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "30 sec", "60 sec", "120 sec")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Short-term conflict alert:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "30 sec", "60 sec", "90 sec")
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