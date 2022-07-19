package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.ui.newSettingsRow
import ktx.scene2d.*
import kotlin.math.roundToInt

class DatatagSettings: BaseSettings() {
    private val styleSelectBox: SelectBox<String>
    private val backgroundSelectBox: SelectBox<String>
    private val borderSelectBox: SelectBox<String>
    private val rowSpacingSlide: Slider
    private val rowSpacingLabel: Label

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Datatag style:")
                            styleSelectBox = defaultSettingsSelectBox<String>().apply {
                                // TODO Custom datatag layouts
                                setItems("Default", "Compact")
                            }
                            defaultSettingsLabel("Show datatag background:")
                            backgroundSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, WHEN_SELECTED, ALWAYS)
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Show datatag border:")
                            borderSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, WHEN_SELECTED, ALWAYS)
                            }
                            defaultSettingsLabel("Datatag row spacing:")
                            table {
                                rowSpacingSlide = slider(1f, 10f, 1f, style = "DatatagSpacing") {
                                    addChangeListener { _, _ ->
                                        updateRowSpacingLabel(value)
                                    }
                                }.cell(width = BUTTON_WIDTH_BIG / 2 - 75, height = BUTTON_HEIGHT_BIG / 1.5f)
                                rowSpacingLabel = label("?px", "SettingsOption").cell(width = 60f, height = BUTTON_HEIGHT_BIG / 1.5f, padLeft = 20f)
                            }
                        }
                    }.cell(growY = true, padTop = 70f)
                    row().padTop(50f)
                    table {
                        textButton("Cancel", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, padRight = 100f, align = Align.bottom).addChangeListener { _, _ ->
                            GAME.setScreen<MainSettings>()
                        }
                        textButton("Confirm", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                            updateClientSettings()
                            GAME.setScreen<MainSettings>()
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the row spacing label to display the new selection of the slider
     * @param newSpacingPx the new row spacing in pixels
     */
    private fun updateRowSpacingLabel(newSpacingPx: Float) {
        rowSpacingLabel.setText("${newSpacingPx.roundToInt()}px")
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the relevant datatag settings
     * and set the select box choices based on them
     */
    override fun setToCurrentClientSettings() {
        backgroundSelectBox.selected = when (DATATAG_BACKGROUND) {
            DATATAG_BACKGROUND_OFF -> OFF
            DATATAG_BACKGROUND_SELECTED -> WHEN_SELECTED
            DATATAG_BACKGROUND_ALWAYS -> ALWAYS
            else -> {
                Gdx.app.log("DatatagSettings", "Unknown datatag background setting $DATATAG_BACKGROUND")
                ALWAYS
            }
        }
        borderSelectBox.selected = when (DATATAG_BORDER) {
            DATATAG_BORDER_OFF -> OFF
            DATATAG_BORDER_SELECTED -> WHEN_SELECTED
            DATATAG_BORDER_ALWAYS -> ALWAYS
            else -> {
                Gdx.app.log("DatatagSettings", "Unknown datatag border setting $DATATAG_BORDER")
                ALWAYS
            }
        }
        rowSpacingSlide.value = DATATAG_ROW_SPACING_PX.toFloat()
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the select box choices
     * and set the relevant datatag settings based on them
     */
    override fun updateClientSettings() {
        DATATAG_BACKGROUND = when (backgroundSelectBox.selected) {
            OFF -> DATATAG_BACKGROUND_OFF
            WHEN_SELECTED -> DATATAG_BACKGROUND_SELECTED
            ALWAYS -> DATATAG_BACKGROUND_ALWAYS
            else -> {
                Gdx.app.log("DatatagSettings", "Unknown datatag background selection ${backgroundSelectBox.selected}")
                DATATAG_BACKGROUND_ALWAYS
            }
        }
        DATATAG_BORDER = when (borderSelectBox.selected) {
            OFF -> DATATAG_BORDER_OFF
            WHEN_SELECTED -> DATATAG_BORDER_SELECTED
            ALWAYS -> DATATAG_BORDER_ALWAYS
            else -> {
                Gdx.app.log("DatatagSettings", "Unknown datatag border selection ${borderSelectBox.selected}")
                DATATAG_BORDER_ALWAYS
            }
        }
        DATATAG_ROW_SPACING_PX = rowSpacingSlide.value.roundToInt().toByte()
        CLIENT_SCREEN?.updateAllDatatagStyles()
    }
}