package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.getSaveJSONString
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.scene2d.*

/** Pause screen which extends [BasicUIScreen] */
class PauseScreen: BasicUIScreen() {
    var radarScreen: RadarScreen? = null
    private val quitButton: KTextButton

    init {
        stage.actors {
            // UI container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    textButton("Resume", "PauseScreen").cell(padRight = 20f, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            radarScreen?.resumeGame()
                            GAME.setScreen<RadarScreen>()
                        }
                    textButton("Settings", "PauseScreen").cell(padRight = 20f, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            GAME.getScreen<MainSettings>().prevScreen = this@PauseScreen
                            GAME.setScreen<MainSettings>()
                        }
                    quitButton = textButton("Save & Quit", "PauseScreen")
                        .cell(width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG).apply {
                            addChangeListener { _, _ -> GAME.quitCurrentGame() }
                        }
                    row().padTop(30f)
                    textButton("Bug Report", "PauseScreen").cell(align = Align.center, colspan = 3, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            // Log connection status before bug report
                            GAME.gameServer?.let { FileLog.info("PauseScreen", it.networkServer.getConnectionStatus()) }
                            GAME.gameClientScreen?.let { FileLog.info("PauseScreen", it.networkClient.getConnectionStatus()) }
                            val bugReportScreen = GAME.getScreen<ReportBug>()
                            bugReportScreen.prevScreen = this@PauseScreen
                            bugReportScreen.setSaveGame(GAME.gameServer?.let { getSaveJSONString(it) } ?: "")
                            GAME.setScreen<ReportBug>()
                        }
                }
            }
        }
    }

    override fun show() {
        super.show()
        quitButton.setText(if (GAME.gameServer == null) "Quit" else "Save & Quit")
    }
}