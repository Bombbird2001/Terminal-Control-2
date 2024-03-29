package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.global.Secrets.DISCORD_INVITE_LINK
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
import com.bombbird.terminalcontrol2.ui.MenuNotificationManager
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/** The main menu screen which extends [BasicUIScreen] */
class MainMenu: BasicUIScreen() {
    private val menuNotificationManager: MenuNotificationManager

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    table {
                        textButton("Join Our\nDiscord!", "Menu").cell(width = BUTTON_WIDTH_MEDIUM,
                            height = BUTTON_HEIGHT_MAIN * 1.2f, align = Align.left, expandX = true).addChangeListener { _, _ ->
                            Gdx.net.openURI(DISCORD_INVITE_LINK)
                        }
                    }.cell(growX = true, uniformX = true)
                    table {
                        val iconTexture = GAME.assetStorage.get<Texture>("Images/MainMenuIcon.png")
                        iconTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                        image(iconTexture).cell(width = 405f, height = 134f, expandY = true, padTop = 75f)
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
                    table {  }.cell(growX = true, uniformX = true)
                }
            }
        }

        menuNotificationManager = MenuNotificationManager(stage)
        menuNotificationManager.showMessages()
    }
}