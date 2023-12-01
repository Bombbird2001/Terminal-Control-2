package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.app.KtxScreen
import ktx.scene2d.*

/** The parent settings screen that allows the user to select the subcategory of settings they wish to modify */
class MainSettings: BasicUIScreen() {
    lateinit var prevScreen: KtxScreen
    private val gameSettingsButton: KTextButton

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    row().padTop(150f)
                    gameSettingsButton = textButton("Game settings", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<GameSettings>()) GAME.addScreen(GameSettings())
                            GAME.getScreen<GameSettings>().setToCurrentGameSettings()
                            GAME.setScreen<GameSettings>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padRight = 40f)
                    textButton("Display", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<DisplaySettings>()) GAME.addScreen(DisplaySettings())
                            GAME.getScreen<DisplaySettings>().setToCurrentClientSettings()
                            GAME.setScreen<DisplaySettings>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row().padTop(30f)
                    textButton("Datatag", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<DatatagSettings>()) GAME.addScreen(DatatagSettings())
                            GAME.getScreen<DatatagSettings>().setToCurrentClientSettings()
                            GAME.setScreen<DatatagSettings>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padRight = 40f)
                    textButton("Alerts", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<AlertSettings>()) GAME.addScreen(AlertSettings())
                            GAME.getScreen<AlertSettings>().setToCurrentClientSettings()
                            GAME.setScreen<AlertSettings>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row().padTop(30f)
                    textButton("Sounds", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<SoundSettings>()) GAME.addScreen(SoundSettings())
                            GAME.getScreen<SoundSettings>().setToCurrentClientSettings()
                            GAME.setScreen<SoundSettings>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padRight = 40f)
                    textButton("Others", "MainSettings").apply {
                        addChangeListener { _, _ ->
                            if (!GAME.containsScreen<OtherSettings>()) GAME.addScreen(OtherSettings())
                            GAME.getScreen<OtherSettings>().setToCurrentClientSettings()
                            GAME.setScreen<OtherSettings>()
                        }
                    }.cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG)
                    row().padTop(130f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, colspan = 2, expandY = true, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen(prevScreen::class.java)
                    }
                }
            }
        }
    }

    override fun show() {
        super.show()
        gameSettingsButton.isDisabled = GAME.gameServer == null
    }
}