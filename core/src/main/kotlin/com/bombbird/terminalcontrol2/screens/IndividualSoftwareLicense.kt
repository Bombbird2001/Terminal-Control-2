package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.assets.toInternalFile
import ktx.scene2d.*

/** Individual source software license display screen which extends [BasicUIScreen] */
class IndividualSoftwareLicense: BasicUIScreen() {
    private val loadedLicenses = HashMap<String, String>()
    private val scrollPane: KScrollPane
    private val licenseLabel: Label

    init {
        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    scrollPane = scrollPane("SoftwareLicenses") {
                        table {
                            licenseLabel = label("", "IndividualLicenseText").apply {
                                wrap = true
                                setAlignment(Align.left)
                            }.cell(growX = true, padLeft = 100f, padRight = 100f)
                        }
                    }.cell(growX = true, expandY = true, padTop = 50f)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG,
                        padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, expandX = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<SoftwareLicenses>()
                    }
                }
            }
        }
    }

    /**
     * Sets the screen display [licenseName]
     * @param licenseName the name of the license that will be displayed
     */
    fun setLicense(licenseName: String) {
        if (!loadedLicenses.containsKey(licenseName)) {
            // Load from file
            val licenseFileHandle = "Licenses/$licenseName".toInternalFile()
            loadedLicenses[licenseName] =  if (!licenseFileHandle.exists()) "License not found"
            else licenseFileHandle.readString()
        }

        licenseLabel.setText(loadedLicenses[licenseName])
    }

    override fun show() {
        super.show()

        scrollPane.velocityY = 0f
        scrollPane.scrollY = 0f
    }
}