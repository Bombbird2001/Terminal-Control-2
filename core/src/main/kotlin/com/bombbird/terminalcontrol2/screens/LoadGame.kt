package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.files.GameSaveMeta
import com.bombbird.terminalcontrol2.files.getExtDir
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.esotericsoftware.minlog.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ktx.async.KtxAsync
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import ktx.scene2d.*

/** Screen for searching game saves in the user app data */
class LoadGame: BasicUIScreen() {
    private val savedGamesTable: KTableWidget
    private val gamesFound = GdxArray<Pair<Int, GameSaveMeta>>()
    @Volatile
    private var loading = false

    @OptIn(ExperimentalStdlibApi::class)
    private val moshiGameMetaAdapter = Moshi.Builder().build().adapter<GameSaveMeta>()

    private var currSelectedMode: KTextButton? = null

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    table {
                        scrollPane("LoadGame") {
                            savedGamesTable = table { }
                            setOverscroll(false, false)
                        }.cell(width = 800f, padTop = 100f, expandY = true)
                        table {
                            currSelectedMode = textButton("Singleplayer", "NewGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = NewGame.SINGLE_PLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                                isChecked = true
                            }
                            row()
                            textButton("Multiplayer\n(LAN)", "NewGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = NewGame.LAN_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                            textButton("Multiplayer\n(Public)", "NewGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = NewGame.PUBLIC_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                        }.cell(padTop = 100f, expandY = true)
                    }
                    row().padTop(100f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainMenu>()
                    }
                }
            }
        }
    }

    /**
     * Sets the scroll pane to display a label telling the user saves are being searched, and sets the searching flag
     * to true
     * */
    private fun setSearchingSaves() {
        loading = true
        savedGamesTable.clear()
        val loadingLabel = savedGamesTable.label("Loading saves.", "SearchingGame")
        Timer.schedule(object: Timer.Task() {
            override fun run() {
                Gdx.app.postRunnable { loadingLabel.setText("Loading saves..") }
            }
        }, 0.5f, 1.5f)
        Timer.schedule(object: Timer.Task() {
            override fun run() {
                Gdx.app.postRunnable { loadingLabel.setText("Loading saves...") }
            }
        }, 1f, 1.5f)
        Timer.schedule(object: Timer.Task() {
            override fun run() {
                Gdx.app.postRunnable { loadingLabel.setText("Searching games.") }
            }
        }, 1.5f, 1.5f)
    }

    /** Search for saved games open in the user data folder */
    private fun searchSavedGames() {
        if (loading) return
        Gdx.app.postRunnable { setSearchingSaves() }
        gamesFound.clear()
        val saveFolderHandle = getExtDir("Saves") ?: return
        if (saveFolderHandle.exists()) {
            val metaOrJsonFound = GdxArrayMap<Int, GameSaveMeta?>()
            saveFolderHandle.list().forEach {
                val id = it.nameWithoutExtension().toInt()
                if (it.extension() == "json" || it.extension() == "meta") {
                    if (metaOrJsonFound.containsKey(id)) {
                        val meta: GameSaveMeta = metaOrJsonFound[id] ?: moshiGameMetaAdapter.fromJson(it.readString()) ?: return@forEach
                        gamesFound.add(Pair(id, meta))
                    }
                    else metaOrJsonFound[id] = if (it.extension() == "meta") moshiGameMetaAdapter.fromJson(it.readString()) else null
                }
            }
        }
        Gdx.app.postRunnable { showFoundGames() }
    }

    /** Displays all games found on the user data folder, and sets the loading flag to false */
    private fun showFoundGames() {
        Timer.instance().clear()
        savedGamesTable.apply {
            clear()
            for (i in 0 until gamesFound.size) { gamesFound[i]?.let { game ->
                val meta = game.second
                textButton("${meta.mainName} - Score: ${meta.score}   High score: ${meta.highScore}\nLanded: ${meta.landed}   Departed: ${meta.departed}", "JoinGameAirport").cell(growX = true).addChangeListener { _, _ ->
                    currSelectedMode?.let { mode ->
                        when (mode.name) {
                            NewGame.SINGLE_PLAYER -> {
                                GAME.addScreen(GameLoading.loadSinglePlayerGameLoading(meta.mainName, game.first))
                                GAME.setScreen<GameLoading>()
                            }
                            NewGame.LAN_MULTIPLAYER -> {
                                GAME.addScreen(GameLoading.loadLANMultiplayerGameLoading(meta.mainName, game.first))
                                GAME.setScreen<GameLoading>()
                            }
                            NewGame.PUBLIC_MULTIPLAYER -> {
                                GAME.addScreen(GameLoading.loadPublicMultiplayerGameLoading(meta.mainName, game.first))
                                GAME.setScreen<GameLoading>()
                            }
                            else -> Log.info("LoadGame", "Unknown game mode ${mode.name}")
                        }
                    }
                }
                row()
            }}
            if (gamesFound.size == 0) label("No saves found", "SearchingGame")
        }
        loading = false
    }

    /** Search for saves when the screen is shown */
    override fun show() {
        super.show()

        // Don't wait for the search to complete, so use a non-default dispatcher (I love KtxAsync)
        KtxAsync.launch(Dispatchers.IO) { searchSavedGames() }
    }
}