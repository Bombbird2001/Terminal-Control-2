package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.ArrivalClosed
import com.bombbird.terminalcontrol2.components.DepartureInfo
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.ui.*
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.ashley.remove
import ktx.collections.GdxArrayMap
import ktx.collections.set
import ktx.scene2d.*
import kotlin.math.round
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
                    row().padTop(100f)
                    defaultSettingsLabel("Traffic mode:")
                    trafficModeSelectBox = defaultSettingsSelectBox<String>().apply {
                        setItems(NORMAL, ARRIVALS_TO_CONTROL, ARRIVAL_FLOW_RATE)
                        addChangeListener { _, _ -> updateTrafficValueElements() }
                    }
                    trafficValueLabel = defaultSettingsLabel("Arrival count:")
                    sliderValueLabel = label("?", "SettingsOption").cell(padLeft = 30f, width = 70f, height = BUTTON_HEIGHT_BIG / 1.5f)
                    trafficValueSlider = slider(4f, 40f, 1f, style = "DatatagSpacing") {
                        addChangeListener { _, _ -> updateSliderLabelText() }
                    }.cell(width = BUTTON_WIDTH_BIG / 2 - 75, height = BUTTON_HEIGHT_BIG / 1.5f, padLeft = 20f)
                    row()
                    scrollPane("SettingsPane") {
                        table {
                            for (airport in GAME.gameServer?.airports?.values() ?: return@table) {
                                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                                defaultSettingsLabel("${arptInfo.icaoCode}:").cell(width = 100f, padRight = 60f)
                                val arrBox = checkBox("   Arrivals", "TrafficCheckbox").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 80f, fillY = true)
                                val depBox = checkBox("   Departures", "TrafficCheckbox").cell(height = BUTTON_HEIGHT_BIG / 1.5f, fillY = true)
                                airportClosedSelections[arptInfo.arptId] = Pair(arrBox, depBox)
                                row().padTop(30f)
                            }
                            align(Align.top)
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
        GAME.gameServer?.apply {
            trafficModeSelectBox.selected = when (trafficMode) {
                TrafficMode.NORMAL -> NORMAL
                TrafficMode.ARRIVALS_TO_CONTROL -> ARRIVALS_TO_CONTROL
                TrafficMode.FLOW_RATE -> ARRIVAL_FLOW_RATE
                else -> {
                    Log.info("TrafficSettings", "Unknown traffic mode setting $trafficMode")
                    NORMAL
                }
            }

            trafficValueSlider.value = round(trafficValue)
            updateTrafficValueElements()
            for (airport in airports.values()) {
                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                val arrClosed = airport.entity.has(ArrivalClosed.mapper)
                val depInfo = airport.entity[DepartureInfo.mapper] ?: continue
                val depClosed = depInfo.closed
                val checkboxes = airportClosedSelections[arptInfo.arptId] ?: continue
                checkboxes.first.isChecked = !arrClosed
                checkboxes.second.isChecked = !depClosed
            }
        }
    }

    /** Takes the UI element choices and sets the traffic info to the current game's airports */
    override fun updateCurrentGameSettings() {
        GAME.gameServer?.apply {
            val prevTrafficMode = trafficMode
            trafficMode = when (val selectedMode = trafficModeSelectBox.selected) {
                NORMAL -> TrafficMode.NORMAL
                ARRIVALS_TO_CONTROL -> TrafficMode.ARRIVALS_TO_CONTROL
                ARRIVAL_FLOW_RATE -> TrafficMode.FLOW_RATE
                else -> {
                    Log.info("TrafficSettings", "Unknown traffic mode selection $selectedMode")
                    TrafficMode.NORMAL
                }
            }
            if (trafficMode != TrafficMode.NORMAL) trafficValue = trafficValueSlider.value
            else if (prevTrafficMode == TrafficMode.FLOW_RATE) trafficValue = 1f
            for (airport in airports.values()) {
                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                val checkboxes = airportClosedSelections[arptInfo.arptId] ?: continue
                if (checkboxes.first.isChecked) airport.entity.remove<ArrivalClosed>()
                else airport.entity += ArrivalClosed()
                airport.entity[DepartureInfo.mapper]?.closed = !checkboxes.second.isChecked
            }
            sendTrafficSettings()
        }
    }

    /** Updates the traffic slider label to the latest slider value */
    private fun updateSliderLabelText() {
        sliderValueLabel.setText("${trafficValueSlider.value.roundToInt()}${if (trafficModeSelectBox.selected == ARRIVAL_FLOW_RATE) "/hr" else ""}")
    }

    /**
     * Updates the traffic label and slider to show/hide and display the appropriate text depending on the traffic mode
     * selection
     * */
    private fun updateTrafficValueElements() {
        val selectedMode = trafficModeSelectBox.selected
        trafficValueLabel.setText(when (selectedMode) {
            ARRIVALS_TO_CONTROL -> "Arrival count:"
            ARRIVAL_FLOW_RATE -> "Arrival flow rate:"
            else -> ""
        })
        val currPercent = trafficValueSlider.percent
        val stepSize = if (selectedMode == ARRIVAL_FLOW_RATE) 5f else 1f
        trafficValueSlider.setRange(if (selectedMode == ARRIVAL_FLOW_RATE) 10f else 4f, if (selectedMode == ARRIVAL_FLOW_RATE) 120f else 40f)
        trafficValueSlider.stepSize = stepSize
        val rawValue = (trafficValueSlider.maxValue - trafficValueSlider.minValue) * currPercent + trafficValueSlider.minValue
        trafficValueSlider.value = (rawValue / stepSize).roundToInt() * stepSize
        updateSliderLabelText()
        trafficValueLabel.isVisible = selectedMode != NORMAL
        sliderValueLabel.isVisible = selectedMode != NORMAL
        trafficValueSlider.isVisible = selectedMode != NORMAL
    }
}