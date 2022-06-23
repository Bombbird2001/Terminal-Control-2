package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.ui.newSettingsRow
import ktx.scene2d.*

/** Settings screen for display settings */
class DisplaySettingsScreen: BasicUIScreen() {
    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Trajectory line duration:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("Off", "30 sec", "60 sec", "90 sec", "120 sec")
                            }
                            defaultSettingsLabel("Radar sweep:")
                            defaultSettingsSelectBox<String>().apply {
                                setItems("0.5 sec", "1 sec", "2 sec", "4 sec", "10 sec") // TODO Incremental unlocks
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