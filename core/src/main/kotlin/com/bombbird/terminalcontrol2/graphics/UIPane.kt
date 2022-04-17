package com.bombbird.terminalcontrol2.graphics

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.ashley.get
import ktx.scene2d.*
import kotlin.math.max

/** The main UI panel display that will integrate the main information pane, and the lateral, altitude and speed panes for controlling of aircraft */
class UIPane(private val uiStage: Stage) {
    var paneImage: KImageButton
    val paneWidth: Float
        get() = max(Variables.UI_WIDTH * 0.28f, 400f)

    // Information pane elements
    var infoPane: KContainer<Actor>
    var metarScroll: KScrollPane
    var metarPane: KTableWidget
    var metarExpandSet = HashSet<String>()

    init {
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                // debugAll()
                setSize(paneWidth, Variables.UI_HEIGHT)
                setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
            }
            infoPane = container {
                fill()
                setSize(paneWidth, Variables.UI_HEIGHT)
                table {
                    metarScroll = scrollPane("MetarPane") {
                        metarPane = table {
                            debugAll()
                            align(Align.top)
                        }
                        setOverscroll(false, false)
                    }.cell(growX = true, padTop = 20f, align = Align.topLeft, height = Variables.UI_HEIGHT * 0.4f)
                }.apply {
                    align(Align.top)
                }
                setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
            }
        }
    }

    /** Resize the pane and containers */
    fun resize(width: Int, height: Int) {
        uiStage.viewport.update(width, height)
        metarScroll.apply {
            setHeight(Variables.UI_HEIGHT * 0.4f)
            invalidateHierarchy()
        }
        infoPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
        }
        paneImage.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
        }
    }

    /** Gets the required x offset for radarDisplayStage's camera at a zoom level */
    fun getRadarCameraOffsetForZoom(zoom: Float): Float {
        return -paneWidth / 2 * zoom // TODO Change depending on pane position
    }

    /** Updates the METAR pane displays (for new METAR information) */
    fun updateMetarInformation() {
        metarPane.clear()
        Constants.CLIENT_SCREEN?.let {
            var padTop = false
            for (airport in it.airport.values) {
                val arptInfo = airport.entity[AirportInfo.mapper] ?: continue
                val metarInfo = airport.entity[MetarInfo.mapper] ?: continue
                val text =
                    """
                    ${arptInfo.icaoCode} - ${metarInfo.letterCode}
                    DEP - ???, ???     ARR - ???, ???
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    """.trimIndent()
                val expandedText = """
                    ${arptInfo.icaoCode} - ${metarInfo.letterCode}
                    DEP - ???, ???     ARR - ???, ???
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    Winds: ${metarInfo.windHeadingDeg}@${metarInfo.windSpeedKt}kt ${if (metarInfo.windGustKt > 0) "gusting to ${metarInfo.windGustKt}kt" else ""}
                    Visibility: ${if (metarInfo.visibilityM == 9999.toShort()) 10000 else metarInfo.visibilityM}m
                    Ceiling: ${metarInfo.ceilingHundredFtAGL?.let { ceiling -> "${ceiling * 100} feet" } ?: "None"}
                    """.trimIndent() + if (metarInfo.windshear.isNotEmpty()) "\nWindshear: ${metarInfo.windshear}" else ""
                val linkedButton = metarPane.textButton(if (metarExpandSet.contains(arptInfo.icaoCode)) expandedText else text, "Metar").apply {
                    label.setAlignment(Align.left)
                    label.wrap = true
                    cell(growX = true, padLeft = 20f, padRight = 10f, padTop = if (padTop) 30f else 0f, align = Align.left)
                }
                metarPane.textButton(if (metarExpandSet.contains(arptInfo.icaoCode)) "-" else "+", "MetarExpand").apply {
                    cell(height = 50f, width = 50f, padLeft = 10f, padRight = 20f, padTop = if (padTop) 30f else 0f, align = Align.topRight)
                    addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            if (metarExpandSet.contains(arptInfo.icaoCode)) {
                                // Expanded, hide it
                                this@apply.label.setText("+")
                                linkedButton.label.setText(text)
                                metarExpandSet.remove(arptInfo.icaoCode)
                            } else {
                                // Hidden, expand it
                                this@apply.label.setText("-")
                                linkedButton.label.setText(expandedText)
                                metarExpandSet.add(arptInfo.icaoCode)
                            }
                        }
                    })
                }
                metarPane.row()
                padTop = true
            }
        }
        metarPane.padBottom(20f)
    }
}