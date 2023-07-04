package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import ktx.assets.toInternalFile
import ktx.scene2d.*

/** Open source software license screen which extends [BasicUIScreen] */
class SoftwareLicenses: BasicUIScreen() {
    private val libraryToLicenseMap = LinkedHashMap<String, String>()
    @OptIn(ExperimentalStdlibApi::class)
    private val licenseInfoMoshi = Moshi.Builder().build().adapter<LicenseInformation>()

    init {
        loadLicenseJson()

        stage.actors {
            // UI Container
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    label("Software and libraries used", "MenuHeader").cell(padTop = 65f)
                    row()
                    scrollPane("SoftwareLicenses") {
                        table {
                            var rightButton = false
                            for (licenseEntry in libraryToLicenseMap) {
                                textButton(licenseEntry.key, "SoftwareLicenseButton").cell(width = BUTTON_WIDTH_BIG,
                                    height = BUTTON_HEIGHT_BIG, padRight = if (rightButton) 0f else 50f, align = Align.center).apply {
                                        label.wrap = true
                                }.addChangeListener { _, _ ->
                                    GAME.getScreen<IndividualSoftwareLicense>().setLicense(licenseEntry.value)
                                    GAME.setScreen<IndividualSoftwareLicense>()
                                }
                                rightButton = !rightButton
                                if (!rightButton) row().padTop(20f)
                            }
                        }
                    }.cell(growY = true, padTop = 50f)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, expandY = true, align = Align.bottom).addChangeListener { _, _ ->
                        GAME.setScreen<AboutGame>()
                    }
                }
            }
        }
    }

    /** Helper class that specifies the JSON format of licenses.json */
    @JsonClass(generateAdapter = true)
    class LicenseInformation(val dependencies: List<IndividualLicense>)

    /** Helper class that specifies the JSON format of each individual software license entry */
    @JsonClass(generateAdapter = true)
    class IndividualLicense(val moduleName: String, val moduleLicense: String? = null)

    /** Loads the license JSON file from assets and assigns the license name for each library */
    private fun loadLicenseJson() {
        val fileHandle = "Licenses/licenses.json".toInternalFile()
        if (!fileHandle.exists()) return

        val json = fileHandle.readString()
        val licenseInfo = licenseInfoMoshi.fromJson(json) ?: return
        for (license in licenseInfo.dependencies) {
            if (license.moduleLicense == null) continue
            libraryToLicenseMap[license.moduleName] = license.moduleLicense
        }
    }
}