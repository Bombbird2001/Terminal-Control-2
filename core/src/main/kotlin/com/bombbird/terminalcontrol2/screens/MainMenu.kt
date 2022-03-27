package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
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
            image(game.assetStorage.get<Texture>("Images/${MathUtils.random(1, 8)}.png")) {
                scaleBy(max(Constants.WORLD_WIDTH / width, Constants.WORLD_HEIGHT / height) - 1)
                x = Constants.WORLD_WIDTH / 2 - width * scaleX / 2
                y = Constants.WORLD_HEIGHT / 2 - height * scaleY / 2
            }
            // UI Container
            container = container {
                fill()
                setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
                table {
                    debugAll()
                    val iconTexture = game.assetStorage.get<Texture>("Images/MainMenuIcon.png")
                    iconTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                    add(Image(iconTexture)).width(270f).height(89f).expandY().align(Align.top).padTop(80f)
                    row().padTop(50f)
                    add(TextButton("New Game", Scene2DSkin.defaultSkin, "Menu")).width(450f).height(100f)
                    row().padTop(20f)
                    add(TextButton("Load Game", Scene2DSkin.defaultSkin, "Menu")).width(450f).height(100f)
                    row().padTop(100f)
                    add(TextButton("Quit", Scene2DSkin.defaultSkin, "Menu").apply {
                        addListener(object: ChangeListener() {
                            override fun changed(event: ChangeEvent?, actor: Actor?) {
                                game.dispose()
                                Gdx.app.exit()
                            }
                        })
                    }).width(450f).height(100f).padBottom(70f)
                }
            }
        }
    }
}