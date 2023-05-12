package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/** The screen shown when loading the game */
class GameLoading private constructor(): BasicUIScreen() {
    private var pBar: ProgressBar

    companion object {
        /**
         * Creates a new instance of GameLoading screen and loads the relevant objects for a single player game
         * @param airportToHost the ICAO code of airport to play
         */
        fun newSinglePlayerGameLoading(airportToHost: String): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.newSinglePlayerGameServer(airportToHost).apply {
                serverStartedCallback = gameLoading::gameServerLoaded
            }
            val rs = RadarScreen.newSinglePlayerRadarScreen().apply {
                dataLoadedCallback = gameLoading::gameClientLoaded
                connectedToHostCallback = gameLoading::connectedToGameServer
            }
            GAME.gameClientScreen = rs
            GAME.addScreen(rs)
            return gameLoading
        }

        /**
         * Creates a new instance of GameLoading screen and loads the relevant objects for a LAN multiplayer game
         * @param airportToHost the ICAO code of airport to play
         */
        fun newLANMultiplayerGameLoading(airportToHost: String): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.newLANMultiplayerGameServer(airportToHost).apply {
                serverStartedCallback = gameLoading::gameServerLoaded
            }
            val rs = RadarScreen.newLANMultiplayerRadarScreen(LOCALHOST).apply {
                dataLoadedCallback = gameLoading::gameClientLoaded
                connectedToHostCallback = gameLoading::connectedToGameServer
            }
            GAME.gameClientScreen = rs
            GAME.addScreen(rs)
            return gameLoading
        }

        /**
         * Creates a new instance of GameLoading screen and loads the relevant objects for a public multiplayer game
         * @param airportToHost the ICAO code of airport to play
         */
        fun newPublicMultiplayerGameLoading(airportToHost: String): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.newPublicMultiplayerGameServer(airportToHost).apply {
                serverStartedCallback = gameLoading::gameServerLoaded
            }
            val rs = RadarScreen.newPublicMultiplayerRadarScreen().apply {
                dataLoadedCallback = gameLoading::gameClientLoaded
                connectedToHostCallback = gameLoading::connectedToGameServer
            }
            GAME.gameClientScreen = rs
            GAME.addScreen(rs)
            return gameLoading
        }

        /**
         * Creates a new instance of GameLoading screen and loads the relevant objects to join a LAN multiplayer game
         * @param lanAddress the address of the LAN server to connect to
         */
        fun joinLANMultiplayerGameLoading(lanAddress: String): GameLoading {
            val gameLoading = GameLoading()
            val rs = RadarScreen.newLANMultiplayerRadarScreen(lanAddress).apply {
                dataLoadedCallback = gameLoading::gameClientLoaded
                connectedToHostCallback = gameLoading::connectedToGameServer
            }
            GAME.gameClientScreen = rs
            GAME.addScreen(rs)
            return gameLoading
        }

        /**
         * Creates a new instance of GameLoading screen and loads the relevant objects to join a public multiplayer game
         * @param roomId the ID of the room to join
         */
        fun joinPublicMultiplayerGameLoading(roomId: Short): GameLoading {
            val gameLoading = GameLoading()
            val rs = RadarScreen.joinPublicMultiplayerRadarScreen(roomId).apply {
                dataLoadedCallback = gameLoading::gameClientLoaded
                connectedToHostCallback = gameLoading::connectedToGameServer
            }
            GAME.gameClientScreen = rs
            GAME.addScreen(rs)
            return gameLoading
        }
    }

    init {
        stage.actors {
            // UI container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    label("Loading game...", "LoadingGame").cell(padBottom = 20f)
                    row()
                    pBar = progressBar(style = "LoadingGame").cell(width = 450f).apply {
                        addChangeListener { _, _ ->
                            if (this@apply.percent >= 1) {
                                Timer.schedule(object: Timer.Task() {
                                    override fun run() {
                                        if (GAME.containsScreen<RadarScreen>()) GAME.setScreen<RadarScreen>()
                                        GAME.removeScreen<GameLoading>()
                                    }
                                }, 0.35f)
                            }
                        }
                        setAnimateDuration(0.25f)
                    }
                }
            }
        }
    }

    /** Schedule the progress bar value animation when the screen is shown */
    override fun show() {
        super.show()

        pBar.value = 0.2f
    }

    /** Function to be called by GameServer to indicate that the server has loaded successfully */
    private fun gameServerLoaded() {
        pBar.value = 0.7f
    }

    /** Function to be called by RadarScreen to indicate client-side data has loaded */
    private fun gameClientLoaded() {
        pBar.value = 0.4f
    }

    /** Function to be called by RadarScreen after it has successfully connected to the server */
    private fun connectedToGameServer() {
        pBar.value = 1f
    }
}