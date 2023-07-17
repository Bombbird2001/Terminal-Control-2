package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.*

/** Data, privacy policy screen which extends [BasicUIScreen] */
class PrivacyPolicy: BasicUIScreen() {
    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    label("Data & Privacy Policy", "MenuHeader").cell(align = Align.center, growX = true, padTop = 65f).setAlignment(Align.center)
                    row()
                    scrollPane("PrivacyPolicy") {
                        table {
                            var policyText = """
                                Terminal Control 2 collects the following data when the game accesses functionalities from our server, which are stored in server access logs:
                                - Date, time of access
                                - Public IP address
                                
                                When a game crash or a game load error occurs, the following data are also sent to the server:
                                - Game version
                                - Date, time of error occurrence
                                - Crash logs
                                - Game logs
                                - Game save data (if game load error occurs)
                            """.trimIndent()

                            if (Gdx.app.type == Application.ApplicationType.Android) policyText += """
                                
                                
                                The following data is also collected through Google LLC's Google Play Console when a crash occurs:
                                - Device model, OS version
                                - Game version
                                - Date, time of occurrence
                                - Crash logs
                            """.trimIndent()

                            policyText += "\n\nThe above data are used solely for the purpose of diagnosing and fixing errors, crashes, and bugs, and are not shared with any 3rd party entities."

                            if (Gdx.app.type == Application.ApplicationType.Android) policyText += """
                                
                                If you sign in to Google Play Games, and/or use the cloud save feature, Google LLC may collect information used to identify you. See the privacy policy here: https://policies.google.com/privacy
                            """.trimIndent()

                            label(policyText, "PrivacyPolicy").cell(align = Align.center, padLeft = 100f, padRight = 100f, growX = true).apply {
                                setAlignment(Align.left)
                                wrap = true
                            }
                        }
                    }.cell(align = Align.top, expandY = true, growX = true, padTop = 50f)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<AboutGame>()
                    }
                }
            }
        }
    }
}