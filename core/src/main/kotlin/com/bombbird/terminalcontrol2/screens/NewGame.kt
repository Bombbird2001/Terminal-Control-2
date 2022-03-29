package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.scene2d.*

/** New game screen which extends [BasicUIScreen] */
class NewGame: BasicUIScreen() {
    var currSelectedAirport: KTextButton? = null
    lateinit var start: KTextButton

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
                table {
                    //debugAll()
                    label("Choose airport:", "MenuHeader").cell(align = Align.center, padTop = 65f).setAlignment(Align.center)
                    row()
                    table {
                        scrollPane("NewGame") {
                            table {
                                for (icao in Constants.AVAIL_AIRPORTS) {
                                    textButton(icao,"NewGameAirport").cell(width = 300f, height = 150f).apply {
                                        addListener(object: ChangeListener() {
                                            override fun changed(event: ChangeEvent?, actor: Actor?) {
                                                if (currSelectedAirport != this@apply) {
                                                    currSelectedAirport?.isChecked = false
                                                    currSelectedAirport = this@apply
                                                    //TODO Update placeholder text
                                                } else currSelectedAirport = null
                                                if (this@NewGame::start.isInitialized) start.isVisible = currSelectedAirport != null
                                                event?.handle()
                                            }
                                        })
                                    }
                                    row()
                                }
                            }
                            setOverscroll(false, false)
                        }.cell(align = Align.top, width = 300f)
                        table {
                            // debugAll()
                            label("Placeholder", "NewGameAirportInfo").cell(width = 800f, expandY = true, preferredHeight = 550f).setAlignment(Align.top)
                            row().padTop(10f)
                            start = textButton("Start", "NewGameStart").cell(width = 400f, height = 100f).apply {
                                isVisible = false
                                addListener(object: ChangeListener() {
                                    override fun changed(event: ChangeEvent?, actor: Actor?) {
                                        if (currSelectedAirport == null) Gdx.app.log("NewGame", "Start button pressed when airport selected is null")
                                            else {
                                            //TODO Call loading function
                                            Constants.GAME.setScreen<GameLoading>()
                                        }
                                        event?.handle()
                                    }
                                })
                            }
                        }.cell(expandY = true).align(Align.top)
                    }.cell(expandY = true, padTop = 65f)
                    row().padTop(100f)
                    textButton("Back", "Menu").cell(width = Constants.BIG_BUTTON_WIDTH, height = Constants.BIG_BUTTON_HEIGHT, padBottom = Constants.BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            Constants.GAME.setScreen<MainMenu>()
                        }
                    })
                }
            }
        }
    }
}