package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.playerclient.LANClient
import com.bombbird.terminalcontrol2.networking.relaygateway.RelayGatewayHost
import com.bombbird.terminalcontrol2.networking.relaygateway.RelayReachability
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.*
import ktx.async.KtxAsync
import ktx.scene2d.*
import kotlin.collections.ArrayList

/** Screen for searching and joining multiplayer games on the LAN */
class JoinGame: BasicUIScreen() {
    private val publicServerStatusLabel: Label
    private val refreshButton: KTextButton
    private val gamesTable: KTableWidget
    @Volatile
    private var searching = false

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
     * For relay games, an HTTP request is sent to the relay server endpoint to get game data
     */
    private suspend fun searchGames() {
        if (searching) return

        Gdx.app.postRunnable {
            setSearchingGames()
        }

        val aliveChecks = ArrayList<Deferred<RelayReachability>>(Secrets.RELAY_INSTANCES.size)
        Secrets.RELAY_INSTANCES.forEach { host ->
            aliveChecks.add(KtxAsync.async(Dispatchers.IO) {
                HttpRequest.sendPublicServerAlive(host)
            })
        }

        // Collect alive check results
        val relayStatuses = aliveChecks.awaitAll()
        val combinedText = relayStatuses.mapIndexed { index, status -> "Server ${index + 1}: $status" }.joinToString("; ")
        Gdx.app.postRunnable { publicServerStatusLabel.setText(combinedText) }

        val pendingJobs = ArrayList<Deferred<List<PublicMultiplayerGameInfo>>>()
        Secrets.RELAY_INSTANCES.forEachIndexed { index, host ->
            if (relayStatuses[index] != RelayReachability.UP) return@forEachIndexed
            pendingJobs.add(KtxAsync.async(Dispatchers.IO) {
                HttpRequest.sendPublicGamesRequest(host).map {
                    PublicMultiplayerGameInfo(it, host)
                }
            })
        }

        val lanPending = KtxAsync.async(Dispatchers.IO) {
            LANClient.discoverLANHosts()
        }

        val publicGames = pendingJobs.awaitAll().flatten()
        val lanGames = lanPending.await()
        Gdx.app.postRunnable { showFoundGames(publicGames, lanGames) }
    }

    /**
     * Class encapsulating the info related to a multiplayer game
     * @param address the address to connect to
     * @param port the port to connect to
     * @param players the current number of players in game
     * @param maxPlayers the max number of players allowed in game
     * @param airportName the name of the airport being hosted
     * @param roomId the ID of the room (only for public multiplayer relay servers)
     */
    @JsonClass(generateAdapter = true)
    class MultiplayerGameInfo(
        val address: String, val port: Int, val players: Byte, val maxPlayers: Byte,
        val airportName: String, val roomId: Short?, val tcpPort: Int? = null,
        val relayProtocol: Int = 1,
    )

    class PublicMultiplayerGameInfo(
        val gameInfo: MultiplayerGameInfo,
        val relayInfo: RelayGatewayHost
    )

    /** Displays all games found on LAN and public relay server, and sets the searching flag to false */
    private fun showFoundGames(publicGames: List<PublicMultiplayerGameInfo>, lanGames: List<LANClient.DiscoveredHost>) {
        Timer.instance().clear()

        val filteredLan = lanGames.filter { it.playerCount < it.maxPlayers }
        val filteredPublic = publicGames.filter { it.gameInfo.roomId != null && it.gameInfo.players < it.gameInfo.maxPlayers }

        gamesTable.apply {
            clear()
            for (game in filteredLan) {
                textButton("${game.mapName} - ${game.playerCount}/${game.maxPlayers} player${if (game.maxPlayers > 1) "s" else ""}          ${game.address}          Join", "JoinGameAirport").addChangeListener { _, _ ->
                    GAME.addScreen(GameLoading.joinLANMultiplayerGameLoading(game.address, game.tcpPort, game.udpPort))
                    GAME.setScreen<GameLoading>()
                }
                row()
            }
            for (publicGame in filteredPublic) {
                val game = publicGame.gameInfo
                val roomId = game.roomId ?: continue
                textButton("${game.airportName} - ${game.players}/${game.maxPlayers} player${if (game.maxPlayers > 1) "s" else ""}          Public server ${if (game.relayProtocol > 1) "(V${game.relayProtocol})" else "    "}      Join", "JoinGameAirport").addChangeListener { _, _ ->
                    GAME.addScreen(GameLoading.joinPublicMultiplayerGameLoading(roomId, publicGame))
                    GAME.setScreen<GameLoading>()
                }
                row()
            }
            if (filteredPublic.size + filteredLan.size == 0) label("No games found", "SearchingGame")
        }
        searching = false
        refreshButton.isDisabled = false
    }
}