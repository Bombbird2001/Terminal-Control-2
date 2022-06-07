package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.utilities.addChangeListener
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
                    image(iconTexture).cell(width = 360f, height = 119f, expandY = true, align = Align.top, padTop = 105f)
                    row().padTop(65f)
                    textButton("New Game", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG).addChangeListener { _, _ ->
                        GAME.setScreen<NewGame>()
                    }
                    row().padTop(25f)
                    textButton("Load Game", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row().padTop(130f)
                    textButton("Quit", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN).addChangeListener { _, _ ->
                        GAME.dispose()
                        Gdx.app.exit()
                    }
                }
            }
        }
    }
}