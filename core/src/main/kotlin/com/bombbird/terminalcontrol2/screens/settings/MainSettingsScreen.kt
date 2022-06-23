package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.app.KtxScreen
import ktx.scene2d.actors
import ktx.scene2d.container
import ktx.scene2d.table
import ktx.scene2d.textButton

/** The parent settings screen that allows the user to select the subcategory of settings they wish to modify */
class MainSettingsScreen(prevScreen: KtxScreen): BasicUIScreen() {
    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    if (GAME.gameServer != null) {
                        textButton("Game settings", "MainSettings").apply {
                            addChangeListener { _, _ ->
                                if (!GAME.containsScreen<GameSettingsScreen>()) GAME.addScreen(GameSettingsScreen())
                                GAME.setScreen<GameSettingsScreen>()
                            }
                        }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, colspan = 2, padTop = 150f)
                        row().padTop(30f)
                    }
                    textButton("Display", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<DisplaySettingsScreen>()) GAME.addScreen(DisplaySettingsScreen())
                            GAME.getScreen<DisplaySettingsScreen>().setToCurrentClientSettings()
                            GAME.setScreen<DisplaySettingsScreen>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padRight = 40f)
                    textButton("Datatag", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            // TODO Go to datatag settings screen
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row().padTop(30f)
                    textButton("Alerts", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<AlertSettingsScreen>()) GAME.addScreen(AlertSettingsScreen())
                            GAME.getScreen<AlertSettingsScreen>().setToCurrentClientSettings()
                            GAME.setScreen<AlertSettingsScreen>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padRight = 40f)
                    textButton("Sounds", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<SoundSettingsScreen>()) GAME.addScreen(SoundSettingsScreen())
                            GAME.getScreen<SoundSettingsScreen>().setToCurrentClientSettings()
                            GAME.setScreen<SoundSettingsScreen>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row()
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, colspan = 2, padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen(prevScreen::class.java)
                    }
                }
            }
        }
    }
}