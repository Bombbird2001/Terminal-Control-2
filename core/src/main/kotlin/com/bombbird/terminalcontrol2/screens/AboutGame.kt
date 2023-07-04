package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/** About game screen which extends [BasicUIScreen] */
class AboutGame: BasicUIScreen() {
    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    // debugAll()
                    label("Terminal Control 2", "MenuHeader").cell(padTop = 50f)
                    row().padTop(5f)
                    label("Version $GAME_VERSION, build $BUILD_VERSION", "GameInfo")
                    row().padTop(5f)
                    label("Developed by Bombbird", "GameInfo")
                    row().padTop(50f)
                    table {
                        textButton("Data & Privacy Policy", "GameInfoButton").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padRight = 50f).addChangeListener { _, _ ->
                            GAME.setScreen<PrivacyPolicy>()
                        }
                        textButton("Software & Licenses", "GameInfoButton").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG).addChangeListener { _, _ ->
                            GAME.setScreen<SoftwareLicenses>()
                        }
                    }.cell(expandY = true, padTop = 50f)
                    row().padTop(30f)
                    textButton("Bug Report", "GameInfoButton").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG).addChangeListener { _, _ ->
                        val url = ("mailto:bombbirddev@gmail.com?subject=Terminal Control 2 Bug Report&body=Build version:" +
                                " $BUILD_VERSION\n\nPlease describe the bug here. If possible, please attach a screenshot" +
                                " or video of the bug.").replace(" ", "%20").replace("\n", "%0A")
                        Gdx.net.openURI(url)
                    }
                    row().padTop(50f)
                    label("While we make effort to ensure that this game is as realistic as possible, please note" +
                            " that this game is not a completely accurate depiction of real life air traffic control." +
                            " It is intended only for entertainment and should not be used for purposes such as real life" +
                            " training. SID, STAR and other navigation data are fictitious and must not be used for real" +
                            " life navigation.", "Disclaimer").cell(padLeft = 100f, padRight = 100f, growX = true).wrap = true
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, expandY = true, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<MainMenu>()
                    }
                }
            }
        }
    }
}