package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.utilities.getRandomTip
import com.bombbird.terminalcontrol2.utilities.loadTips
import ktx.scene2d.*

/** The screen shown when loading the game */
class GameLoading private constructor(): BasicUIScreen() {
    private var pBar: ProgressBar

    companion object {
        /**
         * Creates a new instance of GameLoading screen and loads the relevant objects for a single player game
         * @param airportToHost the ICAO code of airport to play
         * @return the GameLoading screen
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
         * @param maxPlayers maximum number of players allowed in the game
         * @return the GameLoading screen
         */
        fun newLANMultiplayerGameLoading(airportToHost: String, maxPlayers: Byte): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.newLANMultiplayerGameServer(airportToHost, maxPlayers).apply {
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
         * @param maxPlayers maximum number of players allowed in the game
         * @return the GameLoading screen
         */
        fun newPublicMultiplayerGameLoading(airportToHost: String, maxPlayers: Byte): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.newPublicMultiplayerGameServer(airportToHost, maxPlayers).apply {
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
         * Creates a new instance of GameLoading screen and loads the relevant objects for a single player game
         * @param airportToHost the ICAO code of airport to play
         * @param saveId ID of the save file to load
         * @return the GameLoading screen
         */
        fun loadSinglePlayerGameLoading(airportToHost: String, saveId: Int): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.loadSinglePlayerGameServer(airportToHost, saveId).apply {
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
         * @param saveId ID of the save file to load
         * @param maxPlayers maximum number of players allowed in the game
         * @return the GameLoading screen
         */
        fun loadLANMultiplayerGameLoading(airportToHost: String, saveId: Int, maxPlayers: Byte): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.loadLANMultiplayerGameServer(airportToHost, saveId, maxPlayers).apply {
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
         * @param saveId ID of the save file to load
         * @param maxPlayers maximum number of players allowed in the game
         * @return the GameLoading screen
         */
        fun loadPublicMultiplayerGameLoading(airportToHost: String, saveId: Int, maxPlayers: Byte): GameLoading {
            val gameLoading = GameLoading()
            GAME.gameServer = GameServer.loadPublicMultiplayerGameServer(airportToHost, saveId, maxPlayers).apply {
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
         * @param tcpPort TCP port of the LAN server
         * @param udpPort UDP port of the LAN server
         * @return the GameLoading screen
         */
        fun joinLANMultiplayerGameLoading(lanAddress: String, tcpPort: Int, udpPort: Int): GameLoading {
            val gameLoading = GameLoading()
            val rs = RadarScreen.joinLANMultiplayerRadarScreen(lanAddress, tcpPort, udpPort).apply {
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
         * @return the GameLoading screen
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
        loadTips()
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
                    row()
                    label(getRandomTip(), "LoadingGameTip").cell(padTop = 50f, width = 1350f).apply {
                        wrap = true
                        setAlignment(Align.center)
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
        Gdx.app.postRunnable {
            pBar.value = 0.7f
        }
    }

    /** Function to be called by RadarScreen to indicate client-side data has loaded */
    private fun gameClientLoaded() {
        Gdx.app.postRunnable {
            pBar.value = 0.4f
        }
    }

    /** Function to be called by RadarScreen after it has successfully connected to the server */
    private fun connectedToGameServer() {
        Gdx.app.postRunnable {
            pBar.value = 1f
        }
    }
}