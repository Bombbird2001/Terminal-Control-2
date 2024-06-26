package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.files.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.getDatetimeDifferenceString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ktx.async.KtxAsync
import ktx.collections.*
import ktx.scene2d.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Screen for searching game saves in the user app data */
class LoadGame: BasicUIScreen() {
    private val savedGamesTable: KTableWidget
    @Volatile
    private var loading = false

    private var currSelectedMode: KTextButton? = null
    private var currSelectedSaveButton: KTextButton? = null
    private var currSelectedSaveMeta: GameSaveMeta? = null
    private val startButton: KTextButton
    private val deleteButton: KTextButton
    private val exportButton: KTextButton
    private var modificationInProgress = false

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    table {
                        table {
                            textButton("Import save", "LoadGameDeleteExportImport").cell(width = 300f, growY = true, uniformY = true).addChangeListener { event, _ ->
                                importSave({
                                    showDialog(CustomDialog("Import success", "Imported save $it successfully", "", "Ok"))
                                    refreshSaveList()
                                }, {
                                    showDialog(CustomDialog("Import failed", it, "", "Ok"))
                                })
                                event?.handle()
                            }
                            row()
                            exportButton = textButton("Export save", "LoadGameDeleteExportImport").cell(width = 300f, growY = true, uniformY = true).apply {
                                isVisible = false
                            }
                            exportButton.addChangeListener { event, _ ->
                                val saveIdToDelete = currSelectedSaveButton?.name?.toInt()
                                val meta = currSelectedSaveMeta
                                if (saveIdToDelete != null && meta != null) {
                                    exportSave(saveIdToDelete, {
                                        showDialog(CustomDialog("Export success", "Exported save ${meta.mainName} successfully", "", "Ok"))
                                    }, {
                                        showDialog(CustomDialog("Export failed", it, "", "Ok"))
                                    })
                                }
                                event?.handle()
                            }
                            row()
                            deleteButton = textButton("Delete save", "LoadGameDeleteExportImport").cell(width = 300f, growY = true, uniformY = true).apply {
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
                                            deleteMainSave(saveIdToDelete)
                                            deleteBackupSave(saveIdToDelete)
                                            refreshSaveList()
                                    }))
                                }

                                event?.handle()
                            }
                            row()
                        }.cell(padTop = 50f, growY = true)
                        scrollPane("LoadGame") {
                            savedGamesTable = table { }
                            setOverscroll(false, false)
                        }.cell(width = 800f, padTop = 50f, growY = true)
                        table {
                            currSelectedMode = textButton("Singleplayer", "NewLoadGameAirport").cell(width = 300f, growY = true, uniformY = true).apply {
                                name = NewGame.SINGLE_PLAYER
                                addChangeListener { event, _ ->
                                    modeButtonClicked(this)
                                    event?.handle()
                                }
                                isChecked = true
                            }
                            row()
                            textButton("Multiplayer\n(LAN)", "NewLoadGameAirport").cell(width = 300f, growY = true, uniformY = true).apply {
                                name = NewGame.LAN_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    modeButtonClicked(this)
                                    event?.handle()
                                }
                            }
                            row()
                            textButton("Multiplayer\n(Public)", "NewLoadGameAirport").cell(width = 300f, growY = true, uniformY = true).apply {
                                name = NewGame.PUBLIC_MULTIPLAYER
                                addChangeListener { event, _ ->
                                    modeButtonClicked(this)
                                    event?.handle()
                                }
                            }
                            row()
                        }.cell(padTop = 50f, growY = true)
                    }.cell(growY = true)
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
                                                val chooseMax = GAME.getScreen<ChooseMaxPlayers>()
                                                chooseMax.prevScreen = this@LoadGame
                                                chooseMax.setMultiplayerGameInfo(airportToHost, false, saveId)
                                                GAME.setScreen<ChooseMaxPlayers>()
                                            }
                                            NewGame.PUBLIC_MULTIPLAYER -> {
                                                val chooseMax = GAME.getScreen<ChooseMaxPlayers>()
                                                chooseMax.prevScreen = this@LoadGame
                                                chooseMax.setMultiplayerGameInfo(airportToHost, true, saveId)
                                                GAME.setScreen<ChooseMaxPlayers>()
                                            }
                                            else -> FileLog.info("LoadGame", "Unknown game mode ${mode.name}")
                                        }
                                    }
                                }
                            } ?: FileLog.info("LoadGame", "Start button pressed when save selected is null")
                            event?.handle()
                        }
                    }
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainMenu>()
                    }
                }
            }
        }
    }

    /**
     * Called when a mode button is clicked
     * @param button Button that was clicked
     */
    private fun modeButtonClicked(button: KTextButton) {
        if (!modificationInProgress) {
            if (currSelectedMode == button) {
                button.isChecked = true
            } else {
                modificationInProgress = true
                currSelectedMode?.isChecked = false
                currSelectedMode = button
                modificationInProgress = false
            }
        }
    }

    /**
     * Sets the scroll pane to display a label telling the user saves are being searched, and sets the searching flag
     * to true
     */
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
                Gdx.app.postRunnable { loadingLabel.setText("Loading saves.") }
            }
        }, 1.5f, 1.5f)
    }

    /** Search for saved games open in the user data folder */
    private fun searchSavedGames() {
        if (loading) return
        Gdx.app.postRunnable { setSearchingSaves() }
        val gamesFound = getAvailableSaveGames()
        val sortedGameList = GdxArray<Pair<Int, GameSaveMeta>>(gamesFound.size)
        for (i in 0 until gamesFound.size) {
            val id = gamesFound.getKeyAt(i)
            val meta = gamesFound.getValueAt(i)
            sortedGameList.add(Pair(id, meta))
        }
        sortedGameList.sort { o1, o2 ->
            val o1Date = o1.second.lastPlayedDatetime
            val o2Date = o2.second.lastPlayedDatetime
            // Both has no date info, sort by ID (descending)
            if (o1Date == null && o2Date == null) return@sort o2.first - o1.first
            // Place save with no date info lower (bigger)
            if (o1Date == null) return@sort 1
            if (o2Date == null) return@sort -1

            // Sort by date (descending)
            o2Date.compareTo(o1Date)
        }
        Gdx.app.postRunnable { showFoundGames(sortedGameList) }
    }

    /** Displays all games found on the user data folder, and sets the loading flag to false */
    private fun showFoundGames(sortedGameList: GdxArray<Pair<Int, GameSaveMeta>>) {
        Timer.instance().clear()

        savedGamesTable.apply {
            clear()
            for (i in 0 until sortedGameList.size) { sortedGameList[i]?.let { game ->
                val meta = game.second
                val defaultDetails = "${meta.mainName} - Score: ${meta.score}   High score: ${meta.highScore}\nLanded: ${meta.landed}   Departed: ${meta.departed}"
                val configDetails = meta.configNames ?: ""
                val lastPlayedString = meta.lastPlayedDatetime?.let {
                    try {
                        val playedTemporal = ZonedDateTime.parse(it, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                        getDatetimeDifferenceString(playedTemporal, ZonedDateTime.now())
                    } catch (e: DateTimeParseException) {
                        null
                    }
                }
                val lastPlayed = if (lastPlayedString != null) "\nLast played: $lastPlayedString" else ""
                val saveButton = textButton(defaultDetails, "NewLoadGameAirport").cell(growX = true)
                saveButton.name = game.first.toString()
                saveButton.addChangeListener { _, _ ->
                    if (currSelectedSaveButton == saveButton) {
                        currSelectedSaveButton = null
                        currSelectedSaveMeta = null
                        startButton.isVisible = false
                        exportButton.isVisible = false
                        deleteButton.isVisible = false
                        saveButton.setText(defaultDetails)
                    } else {
                        currSelectedSaveButton?.isChecked = false
                        currSelectedSaveButton = saveButton
                        currSelectedSaveMeta = meta
                        startButton.isVisible = true
                        exportButton.isVisible = true
                        deleteButton.isVisible = true
                        saveButton.setText("$defaultDetails$configDetails$lastPlayed")
                    }
                }
                row()
            }}
            if (sortedGameList.size == 0) label("No saves found", "SearchingGame")
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