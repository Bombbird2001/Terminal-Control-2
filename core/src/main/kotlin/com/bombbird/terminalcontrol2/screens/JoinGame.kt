package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import kotlinx.coroutines.*
import ktx.async.KtxAsync
import ktx.collections.GdxArray
import ktx.scene2d.*

/** Screen for searching and joining multiplayer games on the LAN */
class JoinGame: BasicUIScreen() {
    private val refreshButton: KTextButton
    private val lanGamesTable: KTableWidget
    private val addressData = GdxArray<Pair<String, ByteArray>>()
    @Volatile
    private var searching = false

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    refreshButton = textButton("Refresh", "JoinGameRefresh").cell(padTop = 70f).apply {
                        addChangeListener { _, _ ->
                            KtxAsync.launch(Dispatchers.IO) { searchLanGames() }
                        }
                    }
                    row()
                    scrollPane("JoinGame") {
                        lanGamesTable = table { }
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

    /**
     * Sets the scroll pane to display a label telling the user games are being searched, and sets the searching flag
     * to true
     * */
    private fun setSearchingGames() {
        refreshButton.isDisabled = true
        searching = true
        lanGamesTable.clear()
        val loadingLabel = lanGamesTable.label("Searching games.", "SearchingGame")
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
     * Search for multiplayer games open on the LAN
     *
     * Before running discover hosts, [addressData] is passed to the client discovery handler so that it can add newly
     * discovered servers and their data packets to the array, which is used after discovery times out to display the games
     * found
     * */
    private fun searchLanGames() {
        if (searching) return
        Gdx.app.postRunnable { setSearchingGames() }
        addressData.clear()
        GAME.LANClientDiscoveryHandler.onDiscoveredHostDataMap = addressData
        GAME.gameClient.discoverHosts(UDP_PORT, 2000)
        Gdx.app.postRunnable { showFoundGames() }
    }

    /** Displays all games found on the LAN, and sets the searching flag to false */
    private fun showFoundGames() {
        Timer.instance().clear()
        lanGamesTable.apply {
            clear()
            for (i in 0 until addressData.size) { addressData[i]?.let { game ->
                val decodedData = decodePacketData(game.second) ?: return@let
                val players = decodedData.first
                val airport = decodedData.second
                textButton("$airport - $players player${if (players > 1) "s" else ""}          ${game.first}          Join", "JoinGameAirport").addChangeListener { _, _ ->
                    GAME.addScreen(GameLoading(game.first, null))
                    GAME.setScreen<GameLoading>()
                }
                row()
            }}
            if (addressData.size == 0) label("No games found", "SearchingGame")
        }
        searching = false
        refreshButton.isDisabled = false
    }

    /** Refresh the LAN games available everytime the screen is shown */
    override fun show() {
        super.show()

        // Don't wait for the search to complete, so use a non-default dispatcher (I love KtxAsync)
        KtxAsync.launch(Dispatchers.IO) { searchLanGames() }
    }

    /**
     * Decodes the byte array into player count and airport name data
     * @param byteArray the byte array received from the server
     * @return a pair, the first being a byte that represents the current number of players in game, the second being a
     * string that represents the current game world's main airport; returns null if the byte array length does not match
     * */
    private fun decodePacketData(byteArray: ByteArray): Pair<Byte, String>? {
        if (byteArray.size != 9) return null
        var players: Byte = -1
        var airport = ""
        var pos = 0
        while (pos < byteArray.size - 1) {
            if (pos == 0) {
                players = byteArray[0]
                pos++
            } else {
                airport += Char(byteArray[pos] * 255 + byteArray[pos + 1])
                pos += 2
            }
        }

        return Pair(players, airport)
    }
}