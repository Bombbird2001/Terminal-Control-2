package com.bombbird.terminalcontrol2.screens

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.actors
import ktx.scene2d.container
import ktx.scene2d.table
import ktx.scene2d.textButton

/** Pause screen which extends [BasicUIScreen] */
class PauseScreen: BasicUIScreen() {
    var radarScreen: RadarScreen? = null

    init {
        stage.actors {
            // UI container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    textButton("Resume", "PauseScreen").cell(padRight = 20f, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            radarScreen?.resumeGame()
                            GAME.setScreen<RadarScreen>()
                        }
                    textButton("Settings", "PauseScreen").cell(padRight = 20f, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            GAME.getScreen<MainSettings>().prevScreen = this@PauseScreen
                            GAME.setScreen<MainSettings>()
                        }
                    textButton("Save & Quit", "PauseScreen").cell(width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ -> GAME.quitCurrentGame() }
                }
            }
        }
    }
}