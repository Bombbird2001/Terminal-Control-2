package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.global.UI_WIDTH
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/**
 * The screen shown when loading the game
 * @param hostAddress the address of the host server; set to 127.0.0.1 if opening the game as a host
 * @param airportToHost the main airport name being hosted; needed only if opening the game as a host, otherwise set to
 * @param saveId the ID of the save file to load, or null if no save is being loaded
 * null
 */
class GameLoading(private val hostAddress: String, private val airportToHost: String?, private val saveId: Int? = null): BasicUIScreen() {
    var pBar: ProgressBar

    init {
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
                                        GAME.setScreen<RadarScreen>()
                                        GAME.removeScreen<GameLoading>()
                                    }
                                }, 0.35f)
                            }
                        }
                        setAnimateDuration(0.25f)
                    }
                }
            }
        }
    }

    /** Schedule the progress bar value animation when the screen is shown */
    override fun show() {
        super.show()

        pBar.value = 0.2f
        GAME.gameClientScreen = RadarScreen(hostAddress, airportToHost, saveId).apply {
            GAME.addScreen(this)
            dataLoadedCallback = { pBar.value = 0.5f }
            connectedToHostCallback = { pBar.value = 1f }
        }
    }
}