package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.*
import ktx.scene2d.*

class GameSettingsScreen: BasicUIScreen() {
    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    label("These settings are specific to the current game", "SettingsHeader").cell(padTop = 100f)
                    row().padTop(100f)
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Option 1:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Shiba1", "Shiba2", "Shiba3", "Shiba4")
                            }
                            defaultSettingsLabel("Option 2:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Lucas", "Claus", "Boney", "Flint", "Hinawa")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Option 3:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Ryuka", "Kurausu", "Boni", "Furinto", "Hinawa")
                            }
                        }
                    }.cell(growY = true)
                    row().padTop(100f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainSettingsScreen>()
                    }
                }
            }
        }
    }
}