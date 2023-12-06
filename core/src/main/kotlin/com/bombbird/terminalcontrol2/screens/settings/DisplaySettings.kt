package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.savePlayerSettings
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsLabel
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.ui.newSettingsRow
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.scene2d.*

/** Settings screen for display settings */
class DisplaySettings: BaseSettings() {
    companion object {
        private const val ALL = "All"
        private const val NM_SUFFIX = " nm"
        private const val ILS_SIMPLE = "Simple"
        private const val ILS_REALISTIC = "Realistic"
        private const val MORE_COLOURFUL = "More colourful"
        private const val MORE_STANDARDISED = "More standardised"
        private const val ARRIVALS_ONLY = "Arrivals only"
        private const val ALL_AIRCRAFT = "All aircraft"
    }

    private val trajectorySelectBox: KSelectBox<String>
    private val radarSweepSelectBox: KSelectBox<String>
    private val aircraftTrailSelectBox: KSelectBox<String>
    private val uncontrolledTrailSelectBox: KSelectBox<String>
    private val rangeRingsSelectBox: KSelectBox<String>
    private val mvaSectorSelectBox: KSelectBox<String>
    private val ilsDisplaySelectBox: KSelectBox<String>
    private val colourStyleSelectBox: KSelectBox<String>
    private val distToGoSelectBox: KSelectBox<String>

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Trajectory line duration:")
                            trajectorySelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, "30$SECONDS_SUFFIX", "60$SECONDS_SUFFIX", "90$SECONDS_SUFFIX", "120$SECONDS_SUFFIX")
                            }
                            defaultSettingsLabel("Radar sweep:")
                            radarSweepSelectBox = defaultSettingsSelectBox<String>().apply {
                                isDisabled = true
                                setItems("0.5$SECONDS_SUFFIX", "1$SECONDS_SUFFIX", "2$SECONDS_SUFFIX", "4$SECONDS_SUFFIX", "10$SECONDS_SUFFIX") // TODO Incremental unlocks
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Aircraft trail:")
                            aircraftTrailSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, "60$SECONDS_SUFFIX", "90$SECONDS_SUFFIX", "120$SECONDS_SUFFIX", "180$SECONDS_SUFFIX", "240$SECONDS_SUFFIX")
                            }
                            defaultSettingsLabel("Show trail for uncontrolled aircraft:")
                            uncontrolledTrailSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, WHEN_SELECTED, ALWAYS)
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Range rings interval:")
                            rangeRingsSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, "5$NM_SUFFIX", "10$NM_SUFFIX", "15$NM_SUFFIX", "20$NM_SUFFIX")
                            }
                            defaultSettingsLabel("Show MVA sector altitude:")
                            mvaSectorSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, ON)
                            }
                            newSettingsRow()
                            defaultSettingsLabel("ILS display:")
                            ilsDisplaySelectBox = defaultSettingsSelectBox<String>().apply {
                                isDisabled = true
                                setItems(ILS_REALISTIC, ILS_SIMPLE)
                            }
                            defaultSettingsLabel("Colour style:")
                            colourStyleSelectBox = defaultSettingsSelectBox<String>().apply {
                                isDisabled = true
                                setItems(MORE_COLOURFUL, MORE_STANDARDISED)
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Show distance to go:")
                            distToGoSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, ARRIVALS_ONLY, ALL_AIRCRAFT)
                            }
                        }
                        setOverscroll(false, false)
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
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the relevant display settings
     * and set the select box choices based on them
     */
    override fun setToCurrentClientSettings() {
        trajectorySelectBox.selected = if (TRAJECTORY_DURATION_S == 0) OFF else "$TRAJECTORY_DURATION_S$SECONDS_SUFFIX"
        val removeTrailingZeroes = "\\.?0+$".toRegex()
        radarSweepSelectBox.selected = "${RADAR_REFRESH_INTERVAL_S.toString().replace(removeTrailingZeroes, "")}$SECONDS_SUFFIX"
        aircraftTrailSelectBox.selected = when (TRAIL_DURATION_S) {
            0 -> OFF
            else -> "$TRAIL_DURATION_S$SECONDS_SUFFIX"
        }
        uncontrolledTrailSelectBox.selected = when (SHOW_UNCONTROLLED_AIRCRAFT_TRAIL) {
            UNCONTROLLED_AIRCRAFT_TRAIL_OFF -> OFF
            UNCONTROLLED_AIRCRAFT_TRAIL_SELECTED -> WHEN_SELECTED
            UNCONTROLLED_AIRCRAFT_TRAIL_SHOW -> ALWAYS
            else -> {
                FileLog.info("DisplaySettings", "Unknown uncontrolled aircraft trail setting $SHOW_UNCONTROLLED_AIRCRAFT_TRAIL")
                WHEN_SELECTED
            }
        }
        rangeRingsSelectBox.selected = if (RANGE_RING_INTERVAL_NM == 0) OFF else "$RANGE_RING_INTERVAL_NM$NM_SUFFIX"
        mvaSectorSelectBox.selected = if (SHOW_MVA_ALTITUDE) ON else OFF
        ilsDisplaySelectBox.selected = if (REALISTIC_ILS_DISPLAY) ILS_REALISTIC else ILS_SIMPLE
        colourStyleSelectBox.selected = if (COLOURFUL_STYLE) MORE_COLOURFUL else MORE_STANDARDISED
        distToGoSelectBox.selected = when (SHOW_DIST_TO_GO) {
            SHOW_DIST_TO_GO_HIDE -> OFF
            SHOW_DIST_TO_GO_ARRIVALS -> ARRIVALS_ONLY
            SHOW_DIST_TO_GO_ALL -> ALL_AIRCRAFT
            else -> {
                FileLog.info("DisplaySettings", "Unknown dist to go display setting $SHOW_DIST_TO_GO")
                ALL_AIRCRAFT
            }
        }
    }

    /**
     * Overrides the base [BaseSettings.setToCurrentClientSettings] function; will take the select box choices
     * and set the relevant display settings based on them
     */
    override fun updateClientSettings() {
        TRAJECTORY_DURATION_S = trajectorySelectBox.selected.let { if (it == OFF) 0 else it.replace(SECONDS_SUFFIX, "").toInt() }
        RADAR_REFRESH_INTERVAL_S = radarSweepSelectBox.selected.replace(SECONDS_SUFFIX, "").toFloat()
        TRAIL_DURATION_S = aircraftTrailSelectBox.selected.let {
            when (it) {
                OFF -> 0
                ALL -> Int.MAX_VALUE
                else -> it.replace(SECONDS_SUFFIX, "").toInt()
            }
        }
        SHOW_UNCONTROLLED_AIRCRAFT_TRAIL = when (uncontrolledTrailSelectBox.selected) {
            OFF -> UNCONTROLLED_AIRCRAFT_TRAIL_OFF
            WHEN_SELECTED -> UNCONTROLLED_AIRCRAFT_TRAIL_SELECTED
            ALWAYS -> UNCONTROLLED_AIRCRAFT_TRAIL_SHOW
            else -> {
                FileLog.info("DisplaySettings", "Unknown uncontrolled aircraft trail selection ${uncontrolledTrailSelectBox.selected}")
                UNCONTROLLED_AIRCRAFT_TRAIL_SELECTED
            }
        }
        RANGE_RING_INTERVAL_NM = rangeRingsSelectBox.selected.let { if (it == OFF) 0 else it.replace(NM_SUFFIX, "").toInt() }
        SHOW_MVA_ALTITUDE = mvaSectorSelectBox.selected != OFF
        REALISTIC_ILS_DISPLAY = ilsDisplaySelectBox.selected != ILS_SIMPLE
        COLOURFUL_STYLE = colourStyleSelectBox.selected != MORE_STANDARDISED
        SHOW_DIST_TO_GO = when (distToGoSelectBox.selected) {
            OFF -> SHOW_DIST_TO_GO_HIDE
            ARRIVALS_ONLY -> SHOW_DIST_TO_GO_ARRIVALS
            ALL_AIRCRAFT -> SHOW_DIST_TO_GO_ALL
            else -> {
                FileLog.info("DisplaySettings", "Unknown dist to go selection ${distToGoSelectBox.selected}")
                SHOW_DIST_TO_GO_ALL
            }
        }
        savePlayerSettings()
        CLIENT_SCREEN?.apply {
            updateRangeRings()
            updateMVADisplay()
        }
    }
}