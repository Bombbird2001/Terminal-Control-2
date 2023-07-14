package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.*
import ktx.async.KtxAsync
import ktx.collections.GdxArray
import ktx.scene2d.*

/** Screen for searching and joining multiplayer games on the LAN */
class JoinGame: BasicUIScreen() {
    private val publicServerStatusLabel: Label
    private val refreshButton: KTextButton
    private val gamesTable: KTableWidget
    private val lanGamesData = GdxArray<MultiplayerGameInfo>()
    @Volatile
    private var searching = false
    private val publicGamesData = GdxArray<MultiplayerGameInfo>()

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    publicServerStatusLabel = label("Public server status: Checking...").cell(padTop = 70f).apply {
                        setAlignment(Align.center)
                    }
                    row()
                    refreshButton = textButton("Refresh", "JoinGameRefresh").cell(padTop = 30f).apply {
                        addChangeListener { _, _ ->
                            KtxAsync.launch(Dispatchers.IO) { searchGames() }
                        }
                    }
                    row()
                    scrollPane("JoinGame") {
                        gamesTable = table { }
                        setOverscroll(false, false)
                    }.cell(width = 800f, padTop = 100f, expandY = true)
                    row().padTop(100f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainMenu>()
                    }
                }
            }
        }
    }

    /** Refresh the LAN games available everytime the screen is shown */
    override fun show() {
        super.show()

        // Don't wait for the search to complete, so use a non-default dispatcher (I love KtxAsync)
        KtxAsync.launch(Dispatchers.IO) { searchGames() }
    }

    /**
     * Sets the scroll pane to display a label telling the user games are being searched, and sets the searching flag
     * to true
     */
    private fun setSearchingGames() {
        refreshButton.isDisabled = true
        searching = true
        gamesTable.clear()
        publicServerStatusLabel.setText("Public server status: Checking...")
        val loadingLabel = gamesTable.label("Searching games.", "SearchingGame")
        Timer.schedule(object: Timer.Task() {
            override fun run() {
                Gdx.app.postRunnable { loadingLabel.setText("Searching games..") }
            }
        }, 0.5f, 1.5f)
        Timer.schedule(object: Timer.Task() {
            override fun run() {
                Gdx.app.postRunnable { loadingLabel.setText("Searching games...") }
            }
        }, 1f, 1.5f)
        Timer.schedule(object: Timer.Task() {
            override fun run() {
                Gdx.app.postRunnable { loadingLabel.setText("Searching games.") }
            }
        }, 1.5f, 1.5f)
    }

    /**
     * Search for multiplayer games open on the LAN as well as the public relay server
     *
     * Before discovering LAN hosts, [lanGamesData] is passed to the client discovery handler so that it can add newly
     * discovered servers and their data packets to the array, which is used after discovery times out to display the games
     * found
     *
     * For relay games, an HTTP request is sent to the relay server endpoint to get game data
     */
    private fun searchGames() {
        if (searching) return
        Gdx.app.postRunnable {
            setSearchingGames()
            HttpRequest.sendPublicServerAlive {
                publicServerStatusLabel.setText("Public server status: $it")
            }
        }
        publicGamesData.clear()
        // Non-blocking HTTP request
        HttpRequest.sendPublicGamesRequest { games ->
            for (game in games) publicGamesData.add(game)
        }
        lanGamesData.clear()
        GAME.lanClientDiscoveryHandler.onDiscoveredHostDataMap = lanGamesData
        for (udpPort in LAN_UDP_PORTS) {
            // Blocks this thread
            GAME.lanClient.discoverHosts(udpPort)
        }
        Gdx.app.postRunnable { showFoundGames() }
    }

    /**
     * Class encapsulating the info related to a multiplayer game
     * @param address the address to connect to
     * @param players the current number of players in game
     * @param maxPlayers the max number of players allowed in game
     * @param airportName the name of the airport being hosted
     * @param roomId the ID of the room (only for public multiplayer relay servers)
     */
    @JsonClass(generateAdapter = true)
    class MultiplayerGameInfo(val address: String, val players: Byte, val maxPlayers: Byte, val airportName: String,
                              val roomId: Short?)

    /** Displays all games found on LAN and public relay server, and sets the searching flag to false */
    private fun showFoundGames() {
        Timer.instance().clear()
        gamesTable.apply {
            clear()
            var added = 0
            for (i in 0 until lanGamesData.size) { lanGamesData[i]?.let { game ->
                if (game.players >= game.maxPlayers) return@let // Server is full
                textButton("${game.airportName} - ${game.players}/${game.maxPlayers} player${if (game.maxPlayers > 1) "s" else ""}          ${game.address}          Join", "JoinGameAirport").addChangeListener { _, _ ->
                    GAME.addScreen(GameLoading.joinLANMultiplayerGameLoading(game.address))
                    GAME.setScreen<GameLoading>()
                }
                row()
                added++
            }}
            for (i in 0 until publicGamesData.size) { publicGamesData[i]?.let { game ->
                val roomId = game.roomId ?: return@let // Public games should have a room ID
                if (game.players >= game.maxPlayers) return@let // Server is full
                textButton("${game.airportName} - ${game.players}/${game.maxPlayers} player${if (game.maxPlayers > 1) "s" else ""}          Public server           Join", "JoinGameAirport").addChangeListener { _, _ ->
                    GAME.addScreen(GameLoading.joinPublicMultiplayerGameLoading(roomId))
                    GAME.setScreen<GameLoading>()
                }
                row()
                added++
            }}
            if (added == 0) label("No games found", "SearchingGame")
        }
        searching = false
        refreshButton.isDisabled = false
    }
}