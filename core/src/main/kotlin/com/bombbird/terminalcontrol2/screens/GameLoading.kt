package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.scene2d.*

class GameLoading: BasicUIScreen() {
    var pBar: ProgressBar

    init {
        stage.actors {
            // UI container
            container = container {
                fill()
                setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
                table {
                    // debugAll()
                    label("Loading game...", "LoadingGame").cell(padBottom = 20f)
                    row()
                    pBar = progressBar(style = "LoadingGame").cell(width = 350f).apply {
                        addListener(object: ChangeListener() {
                            override fun changed(event: ChangeEvent?, actor: Actor?) {
                                if (this@apply.percent >= 1) {
                                    Constants.GAME.addScreen(RadarScreen())
                                    Constants.GAME.setScreen<RadarScreen>()
                                }
                            }
                        })
                        setAnimateDuration(0.25f)
                    }
                }
            }
        }
    }

    override fun show() {
        super.show()

        Timer.schedule(object: Timer.Task() {
            override fun run() {
                pBar.value = 0.5f
            }
        }, 0.5f)

        Timer.schedule(object: Timer.Task() {
            override fun run() {
                pBar.value = 1f
            }
        }, 1.5f)
    }
}