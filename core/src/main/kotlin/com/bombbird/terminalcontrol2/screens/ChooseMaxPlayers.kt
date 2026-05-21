package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.getMaxPlayersForMap
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.app.KtxScreen
import ktx.scene2d.*
import kotlin.math.roundToInt

/** Screen to choose max number of players allowed in multiplayer game */
class ChooseMaxPlayers: BasicUIScreen() {
    companion object {
        const val START_BUTTON_1_TEXT = "Launch Server (V1)"
    }

    lateinit var prevScreen: KtxScreen
    private val playersAllowedLabel: Label
    private val maxPlayersSlider: Slider
    private var mainName = ""
    private var isPublic = false
    private var gameSaveId: Int? = null
    private var startButton1: TextButton
    private var startButton2: TextButton

    init {
        stage.actors {
            // UI container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    playersAllowedLabel = label("Max players allowed: 4", "ChooseMaxPlayers").cell(padBottom = 20f, padTop = 300f)
                    row()
                    maxPlayersSlider = slider(1f, 4f, 1f, style = "ChooseMaxPlayers") {
                        addChangeListener { _, _ ->
                            playersAllowedLabel.setText("Max players allowed: ${value.roundToInt()}")
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row().padTop(25f)
                    table {
                        startButton1 = textButton(START_BUTTON_1_TEXT, "NewLoadGameStart").cell(width = 300f, height = 125f, padRight = 75f).apply {
                            addChangeListener { _, _ ->
                                launchGame(false)
                            }
                        }
                        startButton2 = textButton("Launch Server (V2 alpha-testing)", "NewLoadGameStart").cell(width = 300f, height = 125f).apply {
                            label.wrap = true
                            addChangeListener { _, _ ->
                                launchGame(true)
                            }
                        }
                    }
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG,
                        padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, expandX = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen(prevScreen::class.java)
                    }
                }
            }
        }
    }

    /**
     * Loads the max players allowed for the map and displays it on the slider
     * @param mapName name of the map
     */
    fun setMultiplayerGameInfo(mapName: String, public: Boolean, saveId: Int?) {
        val maxAllowed = getMaxPlayersForMap(mapName)
        maxPlayersSlider.setRange(1f, maxAllowed.toFloat())
        maxPlayersSlider.value = maxAllowed.toFloat()
        isPublic = public
        gameSaveId = saveId
        mainName = mapName

        if (isPublic) {
            startButton1.setText(START_BUTTON_1_TEXT)
            startButton2.isDisabled = false
        } else {
            startButton1.setText("Start")
            startButton2.isDisabled = true
        }
    }

    private fun launchGame(useRelayV2: Boolean) {
        val public = isPublic
        val saveId = gameSaveId
        val airportToHost = mainName
        val maxPlayersAllowed = maxPlayersSlider.value.roundToInt().toByte()
        if (public) {
            if (saveId == null) {
                GAME.addScreen(GameLoading.newPublicMultiplayerGameLoading(airportToHost, maxPlayersAllowed, useRelayV2))
                GAME.setScreen<GameLoading>()
            } else {
                GAME.addScreen(GameLoading.loadPublicMultiplayerGameLoading(airportToHost, saveId, maxPlayersAllowed, useRelayV2))
                GAME.setScreen<GameLoading>()
            }
        } else {
            if (saveId == null) {
                GAME.addScreen(GameLoading.newLANMultiplayerGameLoading(airportToHost, maxPlayersAllowed))
                GAME.setScreen<GameLoading>()
            } else {
                GAME.addScreen(GameLoading.loadLANMultiplayerGameLoading(airportToHost, saveId, maxPlayersAllowed))
                GAME.setScreen<GameLoading>()
            }
        }
    }
}