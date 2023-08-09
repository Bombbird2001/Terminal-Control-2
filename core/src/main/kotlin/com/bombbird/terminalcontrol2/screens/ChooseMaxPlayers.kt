package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.getMaxPlayersForMap
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.app.KtxScreen
import ktx.scene2d.*
import kotlin.math.roundToInt

/** Screen to choose max number of players allowed in multiplayer game */
class ChooseMaxPlayers: BasicUIScreen() {
    lateinit var prevScreen: KtxScreen
    private val playersAllowedLabel: Label
    private val maxPlayersSlider: Slider
    private var mainName = ""
    private var isPublic = false
    private var gameSaveId: Int? = null

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
                    textButton("Start", "NewLoadGameStart").cell(width = 400f, height = 100f).addChangeListener { _, _ ->
                        val public = isPublic
                        val saveId = gameSaveId
                        val airportToHost = mainName
                        val maxPlayersAllowed = maxPlayersSlider.value.roundToInt().toByte()
                        if (public) {
                            if (saveId == null) {
                                GAME.addScreen(GameLoading.newPublicMultiplayerGameLoading(airportToHost, maxPlayersAllowed))
                                GAME.setScreen<GameLoading>()
                            } else {
                                GAME.addScreen(GameLoading.loadPublicMultiplayerGameLoading(airportToHost, saveId, maxPlayersAllowed))
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
    }
}