package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.AirportArrivalStats
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.ArrivalClosed
import com.bombbird.terminalcontrol2.components.DepartureInfo
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.ui.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.ashley.remove
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
    private val airportClosedSelections = GdxArrayMap<Byte, Pair<CheckBox, CheckBox>>(AIRPORT_SIZE)
    private val airportTrafficSliders = GdxArrayMap<Byte, Slider>(AIRPORT_SIZE)
    private val airportTrafficLabels = GdxArrayMap<Byte, Label>(AIRPORT_SIZE)

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
                    row()
                    scrollPane("SettingsPane") {
                        table {
                            val arptEntries = Entries(GAME.gameServer?.airports ?: return@table)
                            for (airportEntry in arptEntries) {
                                val airport = airportEntry.value
                                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                                defaultSettingsLabel("${arptInfo.icaoCode}:").cell(width = 100f, padRight = 60f)
                                val arrBox = checkBox("   Arrivals", "TrafficCheckbox").cell(height = BUTTON_HEIGHT_BIG / 1.25f, padRight = 80f, fillY = true)
                                val depBox = checkBox("   Departures", "TrafficCheckbox").cell(height = BUTTON_HEIGHT_BIG / 1.25f, fillY = true)
                                airportClosedSelections[arptInfo.arptId] = Pair(arrBox, depBox)
                                val slider = slider(4f, 40f, 1f, style = "DatatagSpacing") {
                                    addChangeListener { _, _ ->
                                        airportTrafficLabels[arptInfo.arptId]?.let { label ->
                                            setTrafficLabelValue(label, value.roundToInt(), trafficModeSelectBox.selected)
                                        }
                                    }
                                }.cell(width = BUTTON_WIDTH_BIG / 2 - 75, height = BUTTON_HEIGHT_BIG / 1.25f, padLeft = 70f)
                                airportTrafficSliders[arptInfo.arptId] = slider
                                val label = label("?", "SettingsOption").cell(padLeft = 30f, width = 100f, height = BUTTON_HEIGHT_BIG / 1.25f)
                                label.isVisible = false
                                airportTrafficLabels[arptInfo.arptId] = label
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
                    FileLog.info("TrafficSettings", "Unknown traffic mode setting $trafficMode")
                    NORMAL
                }
            }

            updateTrafficValueElements()
            for (airportEntry in Entries(airports)) {
                val airport = airportEntry.value
                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                val arrClosed = airport.entity.has(ArrivalClosed.mapper)
                val depInfo = airport.entity[DepartureInfo.mapper] ?: continue
                val depClosed = depInfo.closed
                val checkboxes = airportClosedSelections[arptInfo.arptId] ?: continue
                checkboxes.first.isChecked = !arrClosed
                checkboxes.second.isChecked = !depClosed
                val arrivalStats = airport.entity[AirportArrivalStats.mapper] ?: continue
                airportTrafficSliders[arptInfo.arptId]?.value = arrivalStats.targetTrafficValue.toFloat()
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
                    FileLog.info("TrafficSettings", "Unknown traffic mode selection $selectedMode")
                    TrafficMode.NORMAL
                }
            }
            if (trafficMode != TrafficMode.NORMAL) {
                trafficValue = 6f
            } else {
                if (prevTrafficMode == TrafficMode.FLOW_RATE) trafficValue = 6f
                trafficValue = MathUtils.clamp(trafficValue, 4f, MAX_ARRIVALS.toFloat())
            }
            for (airportEntry in Entries(airports)) {
                val airport = airportEntry.value
                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                val checkboxes = airportClosedSelections[arptInfo.arptId] ?: continue
                if (checkboxes.first.isChecked) airport.entity.remove<ArrivalClosed>()
                else airport.entity += ArrivalClosed()
                airport.entity[DepartureInfo.mapper]?.closed = !checkboxes.second.isChecked
                airportTrafficSliders[arptInfo.arptId]?.value?.toInt()?.let {
                    airport.entity[AirportArrivalStats.mapper]?.targetTrafficValue = it
                }
            }
            sendTrafficSettings()
        }
    }

    /**
     * Updates the traffic label and slider to show/hide and display the appropriate text depending on the traffic mode
     * selection
     */
    private fun updateTrafficValueElements() {
        val selectedMode = trafficModeSelectBox.selected
        val airports = GAME.gameServer?.airports ?: return
        val maxTrafficRatio = airports.values().maxOf { it.entity[AirportInfo.mapper]?.tfcRatio ?: 0 }
        airportTrafficSliders.forEach {
            val trafficValueSlider = it.value
            trafficValueSlider.isVisible = selectedMode != NORMAL
            if (selectedMode == NORMAL) return@forEach
            val currPercent = trafficValueSlider.percent
            val stepSize = if (selectedMode == ARRIVAL_FLOW_RATE) 5f else 1f
            val tfcRatio = airports[it.key]?.entity?.get(AirportInfo.mapper)?.tfcRatio ?: return@forEach
            val trafficFlowMin = if (tfcRatio <= maxTrafficRatio / 2) 5f else 10f
            val trafficFlowMax = if (tfcRatio <= maxTrafficRatio / 2) 40f else 80f
            val arrControlMin = if (tfcRatio <= maxTrafficRatio / 2) 1f else 4f
            val arrControlMax = if (tfcRatio <= maxTrafficRatio / 2) 20f else 40f
            trafficValueSlider.setRange(
                if (selectedMode == ARRIVAL_FLOW_RATE) trafficFlowMin else arrControlMin,
                if (selectedMode == ARRIVAL_FLOW_RATE) trafficFlowMax else arrControlMax)
            trafficValueSlider.stepSize = stepSize
            val rawValue = (trafficValueSlider.maxValue - trafficValueSlider.minValue) * currPercent + trafficValueSlider.minValue
            trafficValueSlider.value = (rawValue / stepSize).roundToInt() * stepSize
        }
        airportTrafficLabels.forEach {
            val label = it.value
            label.isVisible = selectedMode != NORMAL
            setTrafficLabelValue(label, airportTrafficSliders[it.key].value.roundToInt(), selectedMode)
        }
    }

    private fun setTrafficLabelValue(label: Label, value: Int, selectedMode: String) {
        label.setText("$value${if (selectedMode == ARRIVAL_FLOW_RATE) "/hr" else ""}")
    }
}