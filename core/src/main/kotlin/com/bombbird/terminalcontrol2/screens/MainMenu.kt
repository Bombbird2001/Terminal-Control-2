package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.utilities.addChangeListener
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
                    image(iconTexture).cell(width = 360f, height = 119f, expandY = true, align = Align.top, padTop = 105f)
                    row().padTop(65f)
                    textButton("New Game", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT).addChangeListener { _, _ ->
                        Constants.GAME.setScreen<NewGame>()
                    }
                    row().padTop(25f)
                    textButton("Load Game", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT)
                    row().padTop(130f)
                    textButton("Quit", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT, padBottom = Constants.BOTTOM_BUTTON_MARGIN).addChangeListener { _, _ ->
                        Constants.GAME.dispose()
                        Gdx.app.exit()
                    }
                }
            }
        }
    }
}