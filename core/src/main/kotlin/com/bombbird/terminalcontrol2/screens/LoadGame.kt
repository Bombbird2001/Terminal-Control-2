package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.files.GameSaveMeta
import com.bombbird.terminalcontrol2.files.getExtDir
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
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

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    scrollPane("LoadGame") {
                        savedGamesTable = table { }
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
    @OptIn(ExperimentalStdlibApi::class)
    private fun searchSavedGames() {
        if (loading) return
        Gdx.app.postRunnable { setSearchingSaves() }
        gamesFound.clear()
        val saveFolderHandle = getExtDir("Saves") ?: return
        if (saveFolderHandle.exists()) {
            val moshiAdapter = Moshi.Builder().build().adapter<GameSaveMeta>()
            val metaOrJsonFound = GdxArrayMap<Int, GameSaveMeta?>()
            saveFolderHandle.list().forEach {
                val id = it.nameWithoutExtension().toInt()
                if (it.extension() == "json" || it.extension() == "meta") {
                    if (metaOrJsonFound.containsKey(id)) {
                        val meta: GameSaveMeta = metaOrJsonFound[id] ?: moshiAdapter.fromJson(it.readString()) ?: return@forEach
                        gamesFound.add(Pair(id, meta))
                    }
                    else metaOrJsonFound[id] = if (it.extension() == "meta") moshiAdapter.fromJson(it.readString()) else null
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
                textButton("${meta.mainName} - Score: ${meta.score}   High score: ${meta.highScore}\nLanded: ${meta.landed}   Departed: ${meta.departed}", "JoinGameAirport").addChangeListener { _, _ ->
                    GAME.addScreen(GameLoading(LOCALHOST, meta.mainName, game.first, null))
                    GAME.setScreen<GameLoading>()
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