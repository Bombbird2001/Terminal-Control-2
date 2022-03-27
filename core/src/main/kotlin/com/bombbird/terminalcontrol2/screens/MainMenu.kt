package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.scene2d.*
import kotlin.math.max

/** The main menu screen which extends [BasicScreen] */
class MainMenu(game: TerminalControl2): BasicScreen(game) {
    init {
        stage.actors {
            // Background image
            if (Variables.BG_INDEX > 0) image(game.assetStorage.get<Texture>("Images/${Variables.BG_INDEX}.png")) {
                scaleBy(max(Constants.WORLD_WIDTH / width, Constants.WORLD_HEIGHT / height) - 1)
                x = Constants.WORLD_WIDTH / 2 - width * scaleX / 2
                y = Constants.WORLD_HEIGHT / 2 - height * scaleY / 2
            }
            // UI Container
            container = container {
                fill()
                setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
                table {
                    // debugAll()
                    val iconTexture = game.assetStorage.get<Texture>("Images/MainMenuIcon.png")
                    iconTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    image(iconTexture).cell(width = 270f, height = 89f, expandY = true, align = Align.top, padTop = 80f)
                    row().padTop(50f)
                    textButton("New Game", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            game.setScreen<NewGame>()
                        }
                    })
                    row().padTop(20f)
                    textButton("Load Game", "Menu").cell(width = 450f, height = 100f)
                    row().padTop(100f)
                    textButton("Quit", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT, padBottom = Constants.BOTTOM_BUTTON_MARGIN).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            game.dispose()
                            Gdx.app.exit()
                        }
                    })
                }
            }
        }
    }
}