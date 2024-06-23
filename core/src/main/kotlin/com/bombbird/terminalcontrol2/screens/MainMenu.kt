package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.Image
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
    private val radarBgImage: Image

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    table {
                        val radarBgTexture = GAME.assetStorage.get<Texture>("Images/RadarBg.png")
                        val textFgTexture = GAME.assetStorage.get<Texture>("Images/TextFg.png")
                        radarBgTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                        textFgTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                        val iconSize = 400f
                        radarBgImage = image(radarBgTexture)
                        radarBgImage.cell(width = iconSize, height = iconSize, expandY = true)
                        radarBgImage.setOrigin(iconSize / 2, iconSize / 2)
                        row()
                        image(textFgTexture).cell(width = iconSize, height = iconSize, expandY = true, padTop = -iconSize)
                        row().padTop(100f)
                        textButton("Join Our\nDiscord!", "Menu").cell(width = BUTTON_WIDTH_MEDIUM,
                            height = BUTTON_HEIGHT_MAIN, align = Align.center, expandX = true).addChangeListener { _, _ ->
                            Gdx.net.openURI(DISCORD_INVITE_LINK)
                        }
                    }.cell(padRight = 200f, padLeft = 200f)
                    table {
                        table {  }.cell(growY = true, uniformY = true)
                        row()
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
                        row().padTop(15f)
                        table {

                        }
                        row()
                        table {  }.cell(growY = true, uniformY = true)
                        if (APP_TYPE == Application.ApplicationType.Desktop) {
                            row()
                            textButton("Quit", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG,
                                padBottom = BOTTOM_BUTTON_MARGIN).addChangeListener { _, _ ->
                                    GAME.dispose()
                                    Gdx.app.exit()
                                }
                        }
                    }.cell(growY = true)
                    table {  }.cell(growX = true)
                }
            }
        }

        menuNotificationManager = MenuNotificationManager(stage)
        menuNotificationManager.showMessages()
    }

    override fun render(delta: Float) {
        radarBgImage.rotateBy(-delta * 90)
        super.render(delta)
    }
}