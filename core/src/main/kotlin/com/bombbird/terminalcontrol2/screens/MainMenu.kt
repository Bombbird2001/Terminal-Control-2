package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/** The main menu screen which extends [BasicUIScreen] */
class MainMenu: BasicUIScreen() {
    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    val iconTexture = GAME.assetStorage.get<Texture>("Images/MainMenuIcon.png")
                    iconTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    image(iconTexture).cell(width = 360f, height = 119f, expandY = true, padTop = 105f)
                    row().padTop(100f)
                    table {
                        textButton("New Game", "Menu").cell(width = BUTTON_WIDTH_MAIN, height = BUTTON_HEIGHT_MAIN).addChangeListener { _, _ ->
                            GAME.setScreen<NewGame>()
                        }
                        textButton("Load Game", "Menu").cell(width = BUTTON_WIDTH_MAIN, height = BUTTON_HEIGHT_MAIN, padLeft = 20f).addChangeListener { _, _ ->
                            GAME.setScreen<LoadGame>()
                        }
                    }
                    row().padTop(15f)
                    table {
                        textButton("Multiplayer", "Menu").cell(width = BUTTON_WIDTH_MAIN, height = BUTTON_HEIGHT_MAIN).addChangeListener { _, _ ->
                            GAME.setScreen<JoinGame>()
                        }
                        imageButton("MenuSettings").cell(width = BUTTON_WIDTH_SMALL, height = BUTTON_HEIGHT_MAIN, padLeft = 20f).addChangeListener { _, _ ->
                            GAME.getScreen<MainSettings>().prevScreen = this@MainMenu
                            GAME.setScreen<MainSettings>()
                        }
                        imageButton("MenuInfo").cell(width = BUTTON_WIDTH_SMALL, height = BUTTON_HEIGHT_MAIN, padLeft = 20f).addChangeListener { _, _ ->
                            GAME.setScreen<AboutGame>()
                        }
                    }
                    row().padTop(150f)
                    textButton("Quit", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN).addChangeListener { _, _ ->
                        GAME.dispose()
                        Gdx.app.exit()
                    }
                }
            }
        }

        val prefs = Gdx.app.getPreferences(PREFS_FILE_NAME)
        if (!prefs.getBoolean("beta-welcome-msg-shown", false)) {
            // Show welcome message
            CustomDialog("Welcome to the beta!", "Thank you for joining the beta! This is a very early version" +
                    " of the game, so there are likely to be bugs and issues. Please report them by emailing" +
                    " bombbirddev@gmail.com, or by clicking the \"Report Bug\" button in the info menu. Please include" +
                    " as much information as possible, including the build version (in info menu), expected behaviour" +
                    " and steps to reproduce the bug. Screenshots and video recordings are very helpful too.\n\n" +
                    "Currently, there are only 2 default airports for testing, but rest assured that more will be" +
                    " added as testing goes on. We hope you will enjoy the new multiplayer functionality, have fun!",
                "", "Ok!", height = 800, width = 1800, fontAlign = Align.left).show(stage)
            prefs.putBoolean("beta-welcome-msg-shown", true)
            prefs.flush()
        }
    }
}