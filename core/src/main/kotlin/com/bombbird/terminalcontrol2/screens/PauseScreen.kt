package com.bombbird.terminalcontrol2.screens

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.settings.GameSettingsScreen
import com.bombbird.terminalcontrol2.screens.settings.MainSettingsScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.assets.disposeSafely
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
                            if (!GAME.containsScreen<MainSettingsScreen>()) GAME.addScreen(MainSettingsScreen(this@PauseScreen))
                            GAME.setScreen<MainSettingsScreen>()
                        }
                    textButton("Save & Quit", "PauseScreen").cell(width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            // Quit the client, and if this client is also hosting the server it will be automatically closed
                            // as part of the radarScreen's disposal process
                            GAME.setScreen<MainMenu>()
                            // Send the resume signal before quitting game, so the server doesn't remain paused and unable to quit
                            radarScreen?.resumeGame()
                            radarScreen?.disposeSafely()
                            GAME.removeScreen<RadarScreen>()
                            radarScreen = null
                            GAME.removeScreen<MainSettingsScreen>()
                            GAME.removeScreen<GameSettingsScreen>()
                        }
                }
            }
        }
    }
}