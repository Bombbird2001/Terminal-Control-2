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
                    label("These settings are specific to the current game", "SettingsHeader").cell(padTop = 70f)
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
                            defaultSettingsLabel("Aircraft trail:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "60 sec", "90 sec", "All")
                            }
                            defaultSettingsLabel("Show trail for uncontrolled aircraft:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Never", "When selected", "Always")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Range rings interval:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "5 nm", "10 nm", "15 nm", "20 nm")
                            }
                            defaultSettingsLabel("MVA sector altitude:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Hide", "Show")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("ILS display:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Realistic", "Simple")
                            }
                            defaultSettingsLabel("Colour style:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("More colourful", "More standardised")
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Show distance to go:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Never", "Arrivals only", "All aircraft")
                            }
                        }
                        setOverscroll(false, false)
                    }.cell(growY = true)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainSettingsScreen>()
                    }
                }
            }
        }
    }
}