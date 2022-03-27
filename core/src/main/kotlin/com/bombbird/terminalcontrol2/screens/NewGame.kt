package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.scene2d.*
import kotlin.math.max

class NewGame(game: TerminalControl2): BasicScreen(game) {
    var currSelectedAirport: String? = null

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
                    //debugAll()
                    label("Choose airport:", "MenuHeader").cell(align = Align.center, padTop = 50f).setAlignment(Align.center)
                    row()
                    table {
                        scrollPane("NewGame") {
                            table {
                                for (icao in Constants.AVAIL_AIRPORTS) {
                                    textButton(icao,"NewGameAirport").cell(width = 200f, height = 100f).apply {
                                        addListener(object: ChangeListener() {
                                            override fun changed(event: ChangeEvent?, actor: Actor?) {
                                                val newArpt = this@apply.text.toString()
                                                if (currSelectedAirport != newArpt) {
                                                    currSelectedAirport = newArpt
                                                    //TODO Update placeholder text
                                                }
                                                event?.handle()
                                            }
                                        })
                                    }
                                    row()
                                }
                            }
                            setOverscroll(false, false)
                        }.cell(align = Align.top, width = 200f)
                        table {
                            // debugAll()
                            label("Placeholder", "NewGameAirportInfo").cell(width = 500f, expandY = true, preferredHeight = 400f).setAlignment(Align.top)
                            row().padTop(10f)
                            textButton("Start", "NewGameStart").cell(width = 400f, height = 75f)
                        }.cell(expandY = true).align(Align.top)
                    }.cell(expandY = true, padTop = 50f)
                    row().padTop(100f)
                    textButton("Back", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT, padBottom = Constants.BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            game.setScreen<MainMenu>()
                        }
                    })
                }
            }
        }
    }
}