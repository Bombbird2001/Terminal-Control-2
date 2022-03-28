package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.scene2d.*

/** The main menu screen which extends [BasicUIScreen] */
class MainMenu: BasicUIScreen() {
    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
                table {
                    // debugAll()
                    val iconTexture = Constants.GAME.assetStorage.get<Texture>("Images/MainMenuIcon.png")
                    iconTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    image(iconTexture).cell(width = 270f, height = 89f, expandY = true, align = Align.top, padTop = 80f)
                    row().padTop(50f)
                    textButton("New Game", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            Constants.GAME.setScreen<NewGame>()
                        }
                    })
                    row().padTop(20f)
                    textButton("Load Game", "Menu").cell(width = 450f, height = 100f)
                    row().padTop(100f)
                    textButton("Quit", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT, padBottom = Constants.BOTTOM_BUTTON_MARGIN).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            Constants.GAME.dispose()
                            Gdx.app.exit()
                        }
                    })
                }
            }
        }
    }
}