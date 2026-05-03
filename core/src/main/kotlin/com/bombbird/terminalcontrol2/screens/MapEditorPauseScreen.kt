package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/** In-editor menu: resume, dummy save, exit to main menu (with unsaved confirmation). */
class MapEditorPauseScreen : BasicUIScreen() {
    var editor: MapEditorScreen? = null

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    textButton("Resume", "PauseScreen").cell(padRight = 20f, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            GAME.setScreen<MapEditorScreen>()
                        }
                    textButton("Save", "PauseScreen").cell(padRight = 20f, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            editor?.dummySave()
                        }
                    textButton("Exit", "PauseScreen")
                        .cell(width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG).addChangeListener { _, _ ->
                            requestExitToMainMenu()
                        }
                    row().padTop(30f)
                    textButton("Resume", "PauseScreen").cell(align = Align.center, colspan = 3, width = BUTTON_WIDTH_MEDIUM, height = BUTTON_HEIGHT_BIG)
                        .addChangeListener { _, _ ->
                            GAME.setScreen<MapEditorScreen>()
                        }
                }
            }
        }
    }

    private fun requestExitToMainMenu() {
        val ed = editor
        if (ed == null) {
            GAME.setScreen<MainMenu>()
            return
        }
        if (!ed.hasUnsavedChanges()) {
            finishExitToMainMenu()
            return
        }
        showDialog(
            CustomDialog(
                title = "Unsaved changes",
                text = "Save before exiting to the main menu?",
                negative = "Quit without saving",
                positive = "Save & Quit",
                onNegative = { finishExitToMainMenu() },
                onPositive = {
                    ed.dummySave()
                    finishExitToMainMenu()
                },
            ),
        )
    }

    private fun finishExitToMainMenu() {
        GAME.removeScreen<MapEditorScreen>()
        editor = null
        GAME.setScreen<MainMenu>()
    }
}
