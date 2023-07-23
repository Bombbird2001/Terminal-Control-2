package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.getExtDir
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.app.KtxScreen
import ktx.scene2d.*

/** Bug reporting screen which extends [BasicUIScreen] */
class ReportBug: BasicUIScreen() {
    lateinit var prevScreen: KtxScreen
    private val textInput: TextArea
    private val sentItemsLabel: Label
    private val imageCheckbox: CheckBox
    private val submitButton: KTextButton
    private lateinit var backButton: KTextButton
    private var saveString = ""

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    label("Describe the bug here. If possible, please include the actions performed before encountering the bug:\n" +
                            "(just created/loaded game, cleared aircraft to climb, etc.)", "DescribeBugLabel").cell(align = Align.left, padTop = 65f, padLeft = 100f)
                    row().padTop(20f)
                    textInput = textArea("", "DescribeBugField").cell(align = Align.center, growX = true, growY = true, padLeft = 100f, padRight = 100f).apply {
                        alignment = Align.left
                    }
                    row().padTop(50f)
                    table {
                        table {
                            imageCheckbox = checkBox("  Attach screenshots/videos (will open in email app)", "AttachImageCheckbox").cell(align = Align.left)
                            row().padTop(20f)
                            sentItemsLabel = label("Game logs${if (saveString.isNotBlank()) ", save file" else ""} will be sent to " +
                                    "the developer", "DescribeBugLabel").cell(align = Align.left)
                        }.cell(align = Align.left, padLeft = 100f)
                        table {
                            submitButton = textButton("Send report", "SendReportButton").cell(align = Align.left,
                                width = BUTTON_WIDTH_MAIN, height = BUTTON_HEIGHT_BIG, padLeft = 150f, expandX = true).apply {
                                addChangeListener { _, _ ->
                                    if (textInput.text.isBlank()) {
                                        CustomDialog("Empty description", "Please describe the bug.",
                                            "", "Ok").show(stage)
                                        return@addChangeListener
                                    }
                                    isDisabled = true
                                    backButton.isDisabled = true
                                    setText("Sending report...")
                                    val logs = getExtDir("Logs/BUILD $BUILD_VERSION.log")?.readString() ?: "Logs not found"
                                    val multiplayerType = GAME.gameServer?.let {
                                        if (it.isPublicMultiplayer()) "Public multiplayer"
                                        else if (it.maxPlayersAllowed > 1) "LAN multiplayer"
                                        else "Singleplayer"
                                    } ?: GAME.gameClientScreen?.let {
                                        if (it.isPublicMultiplayer()) "Public multiplayer"
                                        else "LAN multiplayer/Singleplayer"
                                    } ?: "Unknown"
                                    HttpRequest.sendBugReport(textInput.text, logs, saveString, multiplayerType, {
                                        val sendEmail = imageCheckbox.isChecked
                                        CustomDialog("Report sent", "Bug report sent successfully. Thank you for reporting the bug!" +
                                                if (sendEmail) " Press Ok to open your email app to send the screenshots/videos." else "",
                                            "", "Ok", onPositive = {
                                                textInput.text = ""
                                                imageCheckbox.isChecked = false
                                                isDisabled = false
                                                backButton.isDisabled = false
                                                setText("Send report")
                                                if (!sendEmail) return@CustomDialog
                                                val url = ("mailto:bombbirddev@gmail.com?subject=Terminal Control 2 Bug Report&body=Report ID:" +
                                                        " $it\n\nAttach a screenshot or video of the bug here.").replace(" ", "%20").replace("\n", "%0A")
                                                Gdx.net.openURI(url)
                                        }).show(stage)
                                    }, {
                                        isDisabled = false
                                        backButton.isDisabled = false
                                        setText("Send report")
                                        CustomDialog("Error", "An error occurred when sending the bug report. Please try again.",
                                            "", "Ok").show(stage)
                                    })
                                }
                            }
                        }.cell(growX = true)
                    }.cell(padLeft = 100f, padRight = 100f, growX = true)
                    row().padTop(50f)
                    backButton = textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG,
                        padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).apply {
                        addChangeListener { _, _ ->
                            GAME.setScreen(prevScreen::class.java)
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets the save game string to be sent to the developer
     * @param saveGame the save game string in JSON format
     */
    fun setSaveGame(saveGame: String) {
        saveString = saveGame
        sentItemsLabel.setText("Game logs${if (saveString.isNotBlank()) ", save file" else ""} will be sent to the developer")
    }
}