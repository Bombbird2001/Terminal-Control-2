package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.scene2d.*

/** New game screen which extends [BasicUIScreen] */
class NewGame: BasicUIScreen() {
    companion object {
        const val SINGLE_PLAYER = "single_player"
        const val LAN_MULTIPLAYER = "multiplayer_lan"
        const val PUBLIC_MULTIPLAYER = "multiplayer_public"
        const val COMING_SOON_DESC = "Coming soon..."
    }

    private var currSelectedAirport: KTextButton? = null
    private var currSelectedMode: KTextButton? = null
    private lateinit var start: KTextButton
    private lateinit var scrollPane: KScrollPane
    private lateinit var descriptionLabel: Label

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    //debugAll()
                    label("Choose airport:", "MenuHeader").cell(align = Align.center, padTop = 50f).setAlignment(Align.center)
                    row()
                    table {
                        scrollPane("NewGame") {
                            table {
                                for (icao in AVAIL_AIRPORTS) {
                                    val finalDesc = icao.value
                                    textButton(icao.key,"NewLoadGameAirport").cell(growX = true, height = 150f).apply {
                                        addChangeListener { event, _ ->
                                            if (currSelectedAirport != this@apply) {
                                                currSelectedAirport?.isChecked = false
                                                currSelectedAirport = this@apply
                                                scrollPane.velocityY = 0f
                                                scrollPane.scrollY = 0f
                                                descriptionLabel.setText(finalDesc ?: COMING_SOON_DESC)
                                            } else {
                                                currSelectedAirport = null
                                                descriptionLabel.setText("")
                                            }
                                            if (this@NewGame::start.isInitialized) start.isVisible = (currSelectedAirport != null && descriptionLabel.text.toString() != COMING_SOON_DESC)
                                            event?.handle()
                                        }
                                    }
                                    row()
                                }
                            }
                            setOverscroll(false, false)
                        }.cell(align = Align.top, width = 300f, growY = true)
                        table {
                            // debugAll()
                            scrollPane = scrollPane("NewGameDescription") {
                                table {
                                    descriptionLabel = label("", "NewGameAirportInfo").apply {
                                        setAlignment(Align.topLeft)
                                        wrap = true
                                    }.cell(growX = true, growY = true)
                                }
                            }.cell(width = 800f, growY = true)
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
                                                    val chooseMax = GAME.getScreen<ChooseMaxPlayers>()
                                                    chooseMax.prevScreen = this@NewGame
                                                    chooseMax.setMultiplayerGameInfo(airportToHost, false, null)
                                                    GAME.setScreen<ChooseMaxPlayers>()
                                                }
                                                PUBLIC_MULTIPLAYER -> {
                                                    val chooseMax = GAME.getScreen<ChooseMaxPlayers>()
                                                    chooseMax.prevScreen = this@NewGame
                                                    chooseMax.setMultiplayerGameInfo(airportToHost, true, null)
                                                    GAME.setScreen<ChooseMaxPlayers>()
                                                }
                                                else -> FileLog.info("NewGame", "Unknown game mode ${mode.name}")
                                            }
                                        }
                                    } ?: FileLog.info("NewGame", "Start button pressed when airport selected is null")
                                    event?.handle()
                                }
                            }
                        }.cell(growY = true).align(Align.top)
                        table {
                            currSelectedMode = textButton("Singleplayer", "NewLoadGameAirport").cell(width = 300f, growY = true, uniformY = true).apply {
                                name = SINGLE_PLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                                isChecked = true
                            }
                            row()
                            textButton("Multiplayer\n(LAN)", "NewLoadGameAirport").cell(width = 300f, growY = true, uniformY = true).apply {
                                name = LAN_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                            textButton("Multiplayer\n(Public)", "NewLoadGameAirport").cell(width = 300f, growY = true, uniformY = true).apply {
                                name = PUBLIC_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                        }.cell(growY = true)
                    }.cell(growY = true, padTop = 50f)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainMenu>()
                    }
                }
            }
        }
    }
}