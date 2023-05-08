package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.esotericsoftware.minlog.Log
import ktx.scene2d.*

/** New game screen which extends [BasicUIScreen] */
class NewGame: BasicUIScreen() {
    private var currSelectedAirport: KTextButton? = null
    private lateinit var start: KTextButton

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    //debugAll()
                    label("Choose airport:", "MenuHeader").cell(align = Align.center, padTop = 65f).setAlignment(Align.center)
                    row()
                    table {
                        scrollPane("NewGame") {
                            table {
                                for (icao in AVAIL_AIRPORTS) {
                                    textButton(icao,"NewGameAirport").cell(width = 300f, height = 150f).apply {
                                        addChangeListener { event, _ ->
                                            if (currSelectedAirport != this@apply) {
                                                currSelectedAirport?.isChecked = false
                                                currSelectedAirport = this@apply
                                                // TODO Update placeholder text
                                            } else currSelectedAirport = null
                                            if (this@NewGame::start.isInitialized) start.isVisible = currSelectedAirport != null
                                            event?.handle()
                                        }
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
                                addChangeListener { event, _ ->
                                    if (currSelectedAirport == null) Log.info("NewGame", "Start button pressed when airport selected is null")
                                    else {
                                        // GAME.addScreen(GameLoading(LOCALHOST, currSelectedAirport?.text?.toString(), null, false, null))
                                        GAME.addScreen(GameLoading(Secrets.RELAY_ADDRESS, currSelectedAirport?.text?.toString(), null, true, null))
                                        GAME.setScreen<GameLoading>()
                                    }
                                    event?.handle()
                                }
                            }
                        }.cell(expandY = true).align(Align.top)
                    }.cell(expandY = true, padTop = 65f)
                    row().padTop(100f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainMenu>()
                    }
                }
            }
        }
    }
}