package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.*
import com.bombbird.terminalcontrol2.utilities.byte
import ktx.scene2d.*

/** Settings screen for game-specific settings */
// TODO Inherit BaseSettingsScreen
class GameSettingsScreen: BasicUIScreen() {
    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    label("These settings are specific to the current game", "SettingsHeader").cell(padTop = 70f, colspan = 2)
                    row().padTop(50f)
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Weather:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Live weather", "Random weather", "Custom weather")
                            }
                            defaultSettingsLabel("Emergencies:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "Low", "Medium", "High")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Storms:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "Low", "Medium", "High", "Nightmare")
                            }
                            if (GAME.gameServer?.playerNo == 1.byte) {
                                defaultSettingsLabel("Game speed:")
                                defaultSettingsSelectBox<String>().apply {
                                    setItems("1x", "2x", "4x", "8x")
                                }
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Night mode:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "On")
                            }
                            defaultSettingsLabel("Active from:")
                        }
                        setOverscroll(false, false)
                    }.cell(growY = true, padRight = 100f)
                    table {
                        textButton("Manage traffic", "SettingsSubpane").cell(width = BUTTON_WIDTH_BIG / 2f, height = BUTTON_HEIGHT_BIG )
                        row().padTop(50f)
                        textButton("Custom aircraft", "SettingsSubpane").cell(width = BUTTON_WIDTH_BIG / 2f, height = BUTTON_HEIGHT_BIG)
                    }
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom, colspan = 2).addChangeListener { _, _ ->
                        GAME.setScreen<MainSettingsScreen>()
                    }
                }
            }
        }
    }
}