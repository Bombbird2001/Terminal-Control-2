package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.EMERGENCY_HIGH
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.EMERGENCY_LOW
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.EMERGENCY_MEDIUM
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.EMERGENCY_OFF
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.STORMS_HIGH
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.STORMS_LOW
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.STORMS_MEDIUM
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.STORMS_NIGHTMARE
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.STORMS_OFF
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_LIVE
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_RANDOM
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_STATIC
import com.bombbird.terminalcontrol2.ui.*
import ktx.collections.GdxArray
import ktx.scene2d.*

/** Settings screen for game-specific settings */
class GameSettings: BaseGameSettings() {
    companion object {
        private const val LIVE_WEATHER = "Live weather"
        private const val RANDOM_WEATHER = "Random weather"
        private const val STATIC_WEATHER = "Static weather"
        private const val SET_CUSTOM_WEATHER = "Set custom weather..."
        private const val OFF = "Off"
        private const val ON = "On"
        private const val LOW = "Low"
        private const val MEDIUM = "Medium"
        private const val HIGH = "High"
        private const val NIGHTMARE = "Nightmare"
    }

    private val weatherSelectBox: KSelectBox<String>
    private val emergencySelectBox: KSelectBox<String>
    private val stormSelectBox: KSelectBox<String>
    private var gameSpeedSelectBox: KSelectBox<String>? = null
    private val nightModeSelectBox: KSelectBox<String>
    private val nightModeTimeLabel: Label
    private val nightModeStartHourSelectBox: KSelectBox<String>
    private val nightModeStartMinSelectBox: KSelectBox<String>
    private val nightModeTimeToLabel: Label
    private val nightModeEndHourSelectBox: KSelectBox<String>
    private val nightModeEndMinSelectBox: KSelectBox<String>

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    label("These settings are specific to the current game", "SettingsHeader").cell(padTop = 70f, colspan = 2)
                    row().padTop(50f)
                    scrollPane("SettingsPane") {
                        table {
                            defaultSettingsLabel("Weather:")
                            weatherSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(LIVE_WEATHER, RANDOM_WEATHER, STATIC_WEATHER, SET_CUSTOM_WEATHER)
                                addChangeListener { _, _ ->
                                    if (selected == SET_CUSTOM_WEATHER) {
                                        if (!GAME.containsScreen<CustomWeatherSettings>()) GAME.addScreen(CustomWeatherSettings())
                                        GAME.getScreen<CustomWeatherSettings>().setToCurrentGameSettings()
                                        GAME.setScreen<CustomWeatherSettings>()
                                        selected = STATIC_WEATHER
                                    }
                                }
                            }
                            defaultSettingsLabel("Emergencies:")
                            emergencySelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, LOW, MEDIUM, HIGH)
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Storms:")
                            stormSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, LOW, MEDIUM, HIGH, NIGHTMARE)
                            }
                            if (GAME.gameServer?.playerNo?.get() == 1) {
                                defaultSettingsLabel("Game speed:")
                                gameSpeedSelectBox = defaultSettingsSelectBox<String>().apply {
                                    setItems("1x", "2x", "4x", "8x")
                                }
                            }
                            newSettingsRow()
                            defaultSettingsLabel("Night mode:")
                            nightModeSelectBox = defaultSettingsSelectBox<String>().apply {
                                setItems(OFF, ON)
                                addChangeListener { _, _ ->
                                     setNightModeTimeElementsVisibility(selected == ON)
                                }
                            }
                            newSettingsRow()
                            nightModeTimeLabel = defaultSettingsLabel("Active from:")
                            table {
                                nightModeStartHourSelectBox = defaultSettingsSelectBoxSmall<String>().apply {
                                    val newArray = GdxArray<String>(24)
                                    for (i in 0..23) newArray.add(if (i < 10) "0$i" else i.toString())
                                    setItems(newArray)
                                }
                                nightModeStartMinSelectBox = defaultSettingsSelectBoxSmall<String>().apply {
                                    setItems("00", "15", "30", "45")
                                }
                                nightModeTimeToLabel = label("to", "SettingsOption").cell(padRight = 20f)
                                nightModeEndHourSelectBox = defaultSettingsSelectBoxSmall<String>().apply {
                                    val newArray = GdxArray<String>(24)
                                    for (i in 0..23) newArray.add(if (i < 10) "0$i" else i.toString())
                                    setItems(newArray)
                                }
                                nightModeEndMinSelectBox = defaultSettingsSelectBoxSmall<String>().apply {
                                    setItems("00", "15", "30", "45")
                                }
                            }.cell(height = BUTTON_HEIGHT_BIG / 1.5f, align = Align.left, colspan = 3)
                        }
                        setOverscroll(false, false)
                    }.cell(growY = true, padRight = 100f)
                    table {
                        textButton("Manage traffic", "SettingsSubpane").cell(width = BUTTON_WIDTH_BIG / 2f, height = BUTTON_HEIGHT_BIG ).addChangeListener { _, _ ->
                            if (!GAME.containsScreen<TrafficSettings>()) GAME.addScreen(TrafficSettings())
                            GAME.getScreen<TrafficSettings>().setToCurrentGameSettings()
                            GAME.setScreen<TrafficSettings>()
                        }
                        row().padTop(50f)
                        textButton("Custom aircraft", "SettingsSubpane").cell(width = BUTTON_WIDTH_BIG / 2f, height = BUTTON_HEIGHT_BIG)
                    }
                    row().padTop(50f)
                    table {
                        textButton("Cancel", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, padRight = 100f, align = Align.bottom).addChangeListener { _, _ ->
                            GAME.setScreen<MainSettings>()
                        }
                        textButton("Confirm", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                            updateCurrentGameSettings()
                            GAME.setScreen<MainSettings>()
                        }
                    }.cell(colspan = 2)
                }
            }
        }
    }

    /** Takes the settings specific to the current game and set the select box choices based on them */
    override fun setToCurrentGameSettings() {
        GAME.gameServer?.apply {
            weatherSelectBox.selected = when (weatherMode) {
                WEATHER_LIVE -> LIVE_WEATHER
                WEATHER_RANDOM -> RANDOM_WEATHER
                WEATHER_STATIC -> SET_CUSTOM_WEATHER
                else -> {
                    Gdx.app.log("GameSettings", "Unknown weather mode setting $weatherMode")
                    LIVE_WEATHER
                }
            }
            emergencySelectBox.selected = when (emergencyRate) {
                EMERGENCY_OFF -> OFF
                EMERGENCY_LOW -> LOW
                EMERGENCY_MEDIUM -> MEDIUM
                EMERGENCY_HIGH -> HIGH
                else -> {
                    Gdx.app.log("GameSettings", "Unknown emergency rate setting $weatherMode")
                    LOW
                }
            }
            stormSelectBox.selected = when (stormsDensity) {
                STORMS_OFF -> OFF
                STORMS_LOW -> LOW
                STORMS_MEDIUM -> MEDIUM
                STORMS_HIGH -> HIGH
                STORMS_NIGHTMARE -> NIGHTMARE
                else -> {
                    Gdx.app.log("GameSettings", "Unknown storm density setting $weatherMode")
                    OFF
                }
            }
            if (playerNo.get() > 1) gameSpeedSelectBox?.selected = "1x"
            else gameSpeedSelectBox?.selected = "${gameSpeed}x"
            nightModeSelectBox.selected = if (nightModeStart == -1 || nightModeEnd == -1) OFF else ON
            setNightModeTimeElementsVisibility(nightModeStart != -1 && nightModeEnd != -1)
            nightModeStartHourSelectBox.selected = if (nightModeStart == -1) "22" else {
                val hour = nightModeStart / 100
                if (hour < 10) "0$hour" else hour.toString()
            }
            nightModeStartMinSelectBox.selected = if (nightModeStart == -1) "00" else {
                val min = nightModeStart % 100
                if (min < 10) "0$min" else min.toString()
            }
            nightModeEndHourSelectBox.selected = if (nightModeEnd == -1) "06" else {
                val hour = nightModeEnd / 100
                if (hour < 10) "0$hour" else hour.toString()
            }
            nightModeEndMinSelectBox.selected = if (nightModeEnd == -1) "00" else {
                val min = nightModeEnd % 100
                if (min < 10) "0$min" else min.toString()
            }
        }
    }

    /** Takes the select box choices and sets the settings specific to the current game */
    override fun updateCurrentGameSettings() {
        GAME.gameServer?.apply {
            weatherMode = when (weatherSelectBox.selected) {
                LIVE_WEATHER -> WEATHER_LIVE
                RANDOM_WEATHER -> WEATHER_RANDOM
                STATIC_WEATHER -> WEATHER_STATIC
                else -> {
                    Gdx.app.log("GameSettings", "Unknown weather mode selection ${weatherSelectBox.selected}")
                    WEATHER_LIVE
                }
            }
            emergencyRate = when (emergencySelectBox.selected) {
                OFF -> EMERGENCY_OFF
                LOW -> EMERGENCY_LOW
                MEDIUM -> EMERGENCY_MEDIUM
                HIGH -> EMERGENCY_HIGH
                else -> {
                    Gdx.app.log("GameSettings", "Unknown emergency rate selection ${emergencySelectBox.selected}")
                    EMERGENCY_LOW
                }
            }
            stormsDensity = when (stormSelectBox.selected) {
                OFF -> STORMS_OFF
                LOW -> STORMS_LOW
                MEDIUM -> STORMS_MEDIUM
                HIGH -> STORMS_HIGH
                NIGHTMARE -> STORMS_NIGHTMARE
                else -> {
                    Gdx.app.log("GameSettings", "Unknown storm density selection ${stormSelectBox.selected}")
                    STORMS_OFF
                }
            }
            gameSpeed = if (playerNo.get() > 1) 1
            else gameSpeedSelectBox?.selected?.replace("x", "")?.toInt() ?: 1
            nightModeStart = if (nightModeSelectBox.selected == ON) {
                nightModeStartHourSelectBox.selected.toInt() * 100 + nightModeStartMinSelectBox.selected.toInt()
            } else -1
            nightModeEnd = if (nightModeSelectBox.selected == ON) {
                nightModeEndHourSelectBox.selected.toInt() * 100 + nightModeEndMinSelectBox.selected.toInt()
            } else -1
        }
    }

    /**
     * Sets whether to show or hide the night mode start and end time elements
     * @param show whether to show or hide the elements
     */
    private fun setNightModeTimeElementsVisibility(show: Boolean) {
        nightModeTimeLabel.isVisible = show
        nightModeStartHourSelectBox.isVisible = show
        nightModeStartMinSelectBox.isVisible = show
        nightModeTimeToLabel.isVisible = show
        nightModeEndHourSelectBox.isVisible = show
        nightModeEndMinSelectBox.isVisible = show
    }
}