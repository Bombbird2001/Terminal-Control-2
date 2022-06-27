package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.*
import ktx.ashley.get
import ktx.collections.GdxArrayMap
import ktx.collections.set
import ktx.scene2d.*
import kotlin.math.roundToInt

/** Settings screen for custom traffic settings */
class TrafficSettings: BaseGameSettings() {
    companion object {
        const val NORMAL = "Normal"
        const val ARRIVALS_TO_CONTROL = "Arrivals to control"
        const val ARRIVAL_FLOW_RATE = "Arrival flow rate"
    }

    private val trafficModeSelectBox: KSelectBox<String>
    private val trafficValueLabel: Label
    private val sliderValueLabel: Label
    private val trafficValueSlider: Slider
    private val airportClosedSelections = GdxArrayMap<Byte, Pair<CheckBox, CheckBox>>(AIRPORT_SIZE)

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    row().padTop(70f)
                    defaultSettingsLabel("Traffic mode:")
                    trafficModeSelectBox = defaultSettingsSelectBox<String>().apply {
                        setItems(NORMAL, ARRIVALS_TO_CONTROL, ARRIVAL_FLOW_RATE)
                        addChangeListener { _, _ -> updateTrafficValueLabel() }
                    }
                    trafficValueLabel = defaultSettingsLabel("Arrival count:")
                    sliderValueLabel = label("?", "SettingsOption").cell(width = 20f, height = BUTTON_HEIGHT_BIG / 1.5f)
                    trafficValueSlider = slider(4f, 40f, 1f, style = "DatatagSpacing") {
                        addChangeListener { _, _ ->
                            sliderValueLabel.setText(value.roundToInt().toString())
                        }
                    }.cell(width = BUTTON_WIDTH_BIG / 2 - 75, height = BUTTON_HEIGHT_BIG / 1.5f, padLeft = 20f)
                    row()
                    scrollPane("SettingsPane") {
                        table {
                            var newRow = false
                            for (airport in GAME.gameServer?.airports?.values() ?: return@table) {
                                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                                defaultSettingsLabel("${arptInfo.icaoCode}:")
                                val arrBox = checkBox("   Arrivals", "TrafficCheckbox").cell(padRight = 50f)
                                val depBox = checkBox("   Departures", "TrafficCheckbox")
                                airportClosedSelections[arptInfo.arptId] = Pair(arrBox, depBox)
                                newRow = if (!newRow) true
                                else {
                                    row()
                                    false
                                }
                            }
                        }
                        setOverscroll(false, false)
                    }.cell(growY = true, padTop = 50f, colspan = 5)
                    row().padTop(50f)
                    table {
                        textButton("Cancel", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, padRight = 100f, align = Align.bottom).addChangeListener { _, _ ->
                            GAME.setScreen<GameSettings>()
                        }
                        textButton("Confirm", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                            updateCurrentGameSettings()
                            GAME.setScreen<GameSettings>()
                        }
                    }.cell(colspan = 5)
                }
            }
        }
    }

    /** Updates the traffic information from the current game airports to the selections */
    override fun setToCurrentGameSettings() {

    }

    /** Takes the UI element choices and sets the traffic info to the current game's airports */
    override fun updateCurrentGameSettings() {

    }

    /** Updates the traffic slider label to show the appropriate text depending on the traffic mode selection */
    private fun updateTrafficValueLabel() {
        trafficValueLabel.setText(when (trafficModeSelectBox.selected) {
            ARRIVALS_TO_CONTROL -> "Arrival count:"
            ARRIVAL_FLOW_RATE -> "Arrival flow rate:"
            else -> ""
        })
    }
}