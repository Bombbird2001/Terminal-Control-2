package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.esotericsoftware.minlog.Log
import ktx.scene2d.*

/** New game screen which extends [BasicUIScreen] */
class NewGame: BasicUIScreen() {
    companion object {
        const val SINGLE_PLAYER = "single_player"
        const val LAN_MULTIPLAYER = "multiplayer_lan"
        const val PUBLIC_MULTIPLAYER = "multiplayer_public"
    }

    private var currSelectedAirport: KTextButton? = null
    private var currSelectedMode: KTextButton? = null
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
                                    textButton(icao,"NewLoadGameAirport").cell(growX = true, height = 150f).apply {
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
                            start = textButton("Start", "NewLoadGameStart").cell(width = 400f, height = 100f).apply {
                                isVisible = false
                                addChangeListener { event, _ ->
                                    currSelectedAirport?.let {
                                        val airportToHost = it.text.toString()
                                        currSelectedMode?.let { mode ->
                                            when (mode.name) {
                                                SINGLE_PLAYER -> {
                                                    GAME.addScreen(GameLoading.newSinglePlayerGameLoading(airportToHost))
                                                    GAME.setScreen<GameLoading>()
                                                }
                                                LAN_MULTIPLAYER -> {
                                                    GAME.addScreen(GameLoading.newLANMultiplayerGameLoading(airportToHost))
                                                    GAME.setScreen<GameLoading>()
                                                }
                                                PUBLIC_MULTIPLAYER -> {
                                                    GAME.addScreen(GameLoading.newPublicMultiplayerGameLoading(airportToHost))
                                                    GAME.setScreen<GameLoading>()
                                                }
                                                else -> Log.info("NewGame", "Unknown game mode ${mode.name}")
                                            }
                                        }
                                    } ?: Log.info("NewGame", "Start button pressed when airport selected is null")
                                    event?.handle()
                                }
                            }
                        }.cell(expandY = true).align(Align.top)
                        table {
                            currSelectedMode = textButton("Singleplayer", "NewLoadGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = SINGLE_PLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                                isChecked = true
                            }
                            row()
                            textButton("Multiplayer\n(LAN)", "NewLoadGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = LAN_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                            textButton("Multiplayer\n(Public)", "NewLoadGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = PUBLIC_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                        }
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