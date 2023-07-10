package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.GameServer.Companion.WEATHER_STATIC
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBoxMedium
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBoxSmall
import com.bombbird.terminalcontrol2.utilities.byte
import com.bombbird.terminalcontrol2.utilities.setAirportStaticWeather
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.collections.GdxMap
import ktx.collections.set
import ktx.scene2d.*
import kotlin.math.min

/** Settings screen for custom weather settings */
class CustomWeatherSettings: BaseGameSettings() {
    private val airportWindSelectBoxes: GdxMap<Byte, Array<SelectBox<Byte>>> = GdxMap(AIRPORT_SIZE)
    private val airportVisibilityCeilingSelectBoxes: GdxMap<Byte, Pair<SelectBox<String>, SelectBox<String>>> = GdxMap(AIRPORT_SIZE)
    private val ceilingSelections = GdxArray<String>(arrayOf("None", "0", "100", "200", "500", "1000", "2000", "3000", "5000", "8000", "12000", "17000", "23000", "30000", "38000"))

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table unused@{
                    label("Use heading 000 for variable (VRB) wind direction", "SettingsHeader").cell(padTop = 70f, colspan = 2)
                    row().padTop(50f)
                    scrollPane("SettingsPane") {
                        table {
                            CLIENT_SCREEN?.apply {for (airportEntry in Entries(airports)) {
                                val airport = airportEntry.value
                                airport?.entity?.get(AirportInfo.mapper)?.let { arptInfo ->
                                    row().padTop(30f)
                                    label("${arptInfo.icaoCode}:", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 20f)
                                    val hdgBox1 = defaultSettingsSelectBoxSmall<Byte>().apply {
                                        setItems(0, 1, 2, 3)
                                    }
                                    val hdgBox2 = defaultSettingsSelectBoxSmall<Byte>().apply {
                                        setItems(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
                                    }
                                    val hdgBox3 = defaultSettingsSelectBoxSmall<Byte>().apply {
                                        setItems(0, 5)
                                    }
                                    label("@", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 10f)
                                    val hdgBox4 = defaultSettingsSelectBoxSmall<Byte>().apply {
                                        setItems(0, 1, 2, 3, 4)
                                    }
                                    val hdgBox5 = defaultSettingsSelectBoxSmall<Byte>().apply {
                                        setItems(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
                                    }
                                    label("kts", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padLeft = 10f, padRight = 70f)
                                    airportWindSelectBoxes[arptInfo.arptId] = arrayOf(hdgBox1, hdgBox2, hdgBox3, hdgBox4, hdgBox5)
                                    label("Visibility:", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 20f)
                                    val visBox = defaultSettingsSelectBoxMedium<String>().apply {
                                        val visArray = GdxArray<String>()
                                        for (i in 0..9500 step 500) visArray.add(i.toString())
                                        visArray.add(">9999")
                                        setItems(visArray)
                                    }
                                    label("m", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padLeft = 10f, padRight = 60f)
                                    label("Ceiling:", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 20f)
                                    val ceilingBox = defaultSettingsSelectBoxMedium<String>().apply {
                                        setItems(ceilingSelections)
                                    }
                                    label("ft AGL", "SettingsOption").cell(height = BUTTON_HEIGHT_BIG / 1.5f, padLeft = 10f)
                                    airportVisibilityCeilingSelectBoxes[arptInfo.arptId] = Pair(visBox, ceilingBox)
                                }
                            }}
                        }
                    }.cell(growY = true, padTop = 70f)
                    row().padTop(50f)
                    table {
                        textButton("Cancel", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, padRight = 100f, align = Align.bottom).addChangeListener { _, _ ->
                            GAME.setScreen<GameSettings>()
                        }
                        textButton("Confirm", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                            updateCurrentGameSettings()
                            GAME.setScreen<GameSettings>()
                        }
                    }
                }
            }
        }
    }

    /** Updates the weather information from the current game airports to the selections */
    override fun setToCurrentGameSettings() {
        GAME.gameServer?.apply {
            Entries(airports).forEach {
                val arpt = it.value
                val arptInfo = arpt.entity[AirportInfo.mapper] ?: return@forEach
                val metarInfo = arpt.entity[MetarInfo.mapper] ?: return@forEach

                // Set the wind first
                val windBoxes = airportWindSelectBoxes[arptInfo.arptId] ?: return@forEach
                val windHdg = metarInfo.windHeadingDeg
                val windSpd = min(metarInfo.windSpeedKt.toInt(), 49)
                windBoxes[0].selected = (windHdg / 100).toByte()
                windBoxes[1].selected = ((windHdg / 10) % 10).toByte()
                windBoxes[2].selected = (windHdg % 10).toByte()
                modulateWindHdgChoices(arptInfo.arptId)
                windBoxes[3].selected = (windSpd / 10).toByte()
                windBoxes[4].selected = (windSpd % 10).toByte()

                val visCeilBoxes = airportVisibilityCeilingSelectBoxes[arptInfo.arptId] ?: return@forEach
                // Set the visibility
                visCeilBoxes.first.selected = if (metarInfo.visibilityM >= 9999) ">9999" else metarInfo.visibilityM.toString()
                val correctedCeiling = metarInfo.ceilingHundredFtAGL?.let { ceil ->
                    for (i in 1 until ceilingSelections.size) {
                        if (ceil * 100 <= ceilingSelections[i].toInt()) return@let ceilingSelections[i]
                    }
                    "None"
                } ?: "None"
                visCeilBoxes.second.selected = correctedCeiling
            }
        }
    }

    /** Takes the select box choices and sets the static weather to the current game's airports */
    override fun updateCurrentGameSettings() {
        GAME.gameServer?.apply {
            weatherMode = WEATHER_STATIC
            Entries(airports).forEach {
                val arpt = it.value
                val arptInfo = arpt.entity[AirportInfo.mapper] ?: return@forEach

                // Set the wind heading and speed
                val windBoxes = airportWindSelectBoxes[arptInfo.arptId] ?: return@forEach
                val windHdg = (windBoxes[0].selected * 100 + windBoxes[1].selected * 10 + windBoxes[2].selected).toShort()
                val windSpd = (windBoxes[3].selected * 10 + windBoxes[4].selected).toShort()

                val visCeilBoxes = airportVisibilityCeilingSelectBoxes[arptInfo.arptId] ?: return@forEach
                // Set the visibility
                val vis = if (visCeilBoxes.first.selected == ">9999") 10000 else visCeilBoxes.first.selected.toShort()

                // Set the ceiling
                val ceilHundred = if (visCeilBoxes.second.selected == "None") null else (visCeilBoxes.second.selected.toInt() / 100).toShort()

                val worldTemp = MathUtils.random(20, 35)
                val worldDewDelta = -MathUtils.random(2, 8)
                val worldQnh = MathUtils.random(1005, 1019)

                setAirportStaticWeather(arpt.entity, windHdg, windSpd, vis, ceilHundred, worldTemp, worldDewDelta, worldQnh)
            }
            sendMetarTCPToAll()
        }
    }

    /**
     * Modulates the choices for the 2nd and 3rd select boxes with the currently selected values
     * @param arptId ID of the airport to modulate
     */
    private fun modulateWindHdgChoices(arptId: Byte) {
        airportWindSelectBoxes[arptId]?.apply {
            if (get(0).selected == 3.byte) {
                get(1).setItems(0, 1, 2, 3, 4, 5, 6)
                if (get(1).selected == 6.byte) get(2).setItems(0)
            }
        }
    }
}