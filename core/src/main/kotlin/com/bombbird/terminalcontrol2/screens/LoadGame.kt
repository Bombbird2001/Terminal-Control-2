package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.files.GameSaveMeta
import com.bombbird.terminalcontrol2.files.deleteSave
import com.bombbird.terminalcontrol2.files.exportSave
import com.bombbird.terminalcontrol2.files.getExtDir
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.CustomDialog
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
    private var currSelectedSaveButton: KTextButton? = null
    private var currSelectedSaveMeta: GameSaveMeta? = null
    private val startButton: KTextButton
    private val deleteButton: KTextButton
    private val exportButton: KTextButton

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    table {
                        table {
                            textButton("Import save", "LoadGameDeleteExportImport").cell(width = 300f, height = 550f / 3).addChangeListener { event, _ ->
                                event?.handle()
                            }
                            row()
                            exportButton = textButton("Export save", "LoadGameDeleteExportImport").cell(width = 300f, height = 550f / 3).apply {
                                isVisible = false
                            }
                            exportButton.addChangeListener { event, _ ->
                                val saveIdToDelete = currSelectedSaveButton?.name?.toInt()
                                val meta = currSelectedSaveMeta
                                if (saveIdToDelete != null && meta != null) {
                                    if (exportSave(saveIdToDelete))
                                        showDialog(CustomDialog("Export success", "Exported save ${meta.mainName} successfully", "", "Ok"))
                                    else
                                        showDialog(CustomDialog("Export failed", "Could not export save ${meta.mainName}", "", "Ok"))
                                }
                                event?.handle()
                            }
                            row()
                            deleteButton = textButton("Delete save", "LoadGameDeleteExportImport").cell(width = 300f, height = 550f / 3).apply {
                                isVisible = false
                            }
                            deleteButton.addChangeListener { event, _ ->
                                val saveIdToDelete = currSelectedSaveButton?.name?.toInt()
                                val meta = currSelectedSaveMeta
                                if (saveIdToDelete != null && meta != null) {
                                    val toDisplay = "Score: ${meta.score}   High score: ${meta.highScore}\n" +
                                            "Landed: ${meta.landed}   Departed: ${meta.departed}"
                                    showDialog(CustomDialog("Delete save", "Delete save ${meta.mainName}?\n$toDisplay",
                                        "No", "Delete", onPositive = {
                                            deleteSave(saveIdToDelete)
                                            refreshSaveList()
                                    }))
                                }

                                event?.handle()
                            }
                            row()
                        }.cell(padTop = 100f, expandY = true)
                        scrollPane("LoadGame") {
                            savedGamesTable = table { }
                            setOverscroll(false, false)
                        }.cell(width = 800f, padTop = 100f, expandY = true)
                        table {
                            currSelectedMode = textButton("Singleplayer", "NewLoadGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = NewGame.SINGLE_PLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                                isChecked = true
                            }
                            row()
                            textButton("Multiplayer\n(LAN)", "NewLoadGameAirport").cell(width = 300f, height = 550f / 3).apply {
                                name = NewGame.LAN_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    currSelectedMode?.isChecked = false
                                    currSelectedMode = this
                                    event?.handle()
                                }
                            }
                            row()
                            textButton("Multiplayer\n(Public)", "NewLoadGameAirport").cell(width = 300f, height = 550f / 3).apply {
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
                    row().padTop(25f)
                    startButton = textButton("Start", "NewLoadGameStart").cell(width = 400f, height = 100f).apply {
                        isVisible = false
                        addChangeListener { event, _ ->
                            currSelectedSaveButton?.let {
                                val saveId = it.name.toString().toInt()
                                currSelectedSaveMeta?.let { meta ->
                                    val airportToHost = meta.mainName
                                    currSelectedMode?.let { mode ->
                                        when (mode.name) {
                                            NewGame.SINGLE_PLAYER -> {
                                                GAME.addScreen(GameLoading.loadSinglePlayerGameLoading(airportToHost, saveId))
                                                GAME.setScreen<GameLoading>()
                                            }
                                            NewGame.LAN_MULTIPLAYER -> {
                                                GAME.addScreen(GameLoading.loadLANMultiplayerGameLoading(airportToHost, saveId))
                                                GAME.setScreen<GameLoading>()
                                            }
                                            NewGame.PUBLIC_MULTIPLAYER -> {
                                                GAME.addScreen(GameLoading.loadPublicMultiplayerGameLoading(airportToHost, saveId))
                                                GAME.setScreen<GameLoading>()
                                            }
                                            else -> Log.info("LoadGame", "Unknown game mode ${mode.name}")
                                        }
                                    }
                                }
                            } ?: Log.info("LoadGame", "Start button pressed when save selected is null")
                            event?.handle()
                        }
                    }
                    row().padTop(50f)
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
                val saveButton = textButton("${meta.mainName} - Score: ${meta.score}   High score: ${meta.highScore}\nLanded: ${meta.landed}   Departed: ${meta.departed}", "NewLoadGameAirport").cell(growX = true)
                saveButton.name = game.first.toString()
                saveButton.addChangeListener { _, _ ->
                    currSelectedSaveButton?.isChecked = false
                    currSelectedSaveButton = saveButton
                    currSelectedSaveMeta = meta
                    startButton.isVisible = true
                    exportButton.isVisible = true
                    deleteButton.isVisible = true
                }
                row()
            }}
            if (gamesFound.size == 0) label("No saves found", "SearchingGame")
        }
        loading = false
    }

    private fun refreshSaveList() {
        savedGamesTable.clear()

        // Don't wait for the search to complete, so use a non-default dispatcher (I love KtxAsync)
        KtxAsync.launch(Dispatchers.IO) { searchSavedGames() }
    }

    /** Search for saves when the screen is shown */
    override fun show() {
        super.show()

        refreshSaveList()
    }
}