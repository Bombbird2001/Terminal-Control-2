package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
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
                        imageButton("RunwayConfig").cell(width = BUTTON_WIDTH_SMALL, height = BUTTON_HEIGHT_MAIN, padLeft = 20f, padRight = 210f).addChangeListener { _, _ ->
                            GAME.getScreen<MainSettings>().prevScreen = this@MainMenu
                            GAME.setScreen<MainSettings>()
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
    }
}