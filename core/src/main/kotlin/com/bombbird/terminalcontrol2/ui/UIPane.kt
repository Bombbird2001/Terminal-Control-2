package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.AirportInfo
import com.bombbird.terminalcontrol2.components.MetarInfo
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import ktx.ashley.get
import ktx.collections.toGdxArray
import ktx.graphics.moveTo
import ktx.scene2d.*
import kotlin.math.max

/** The main UI panel display that will integrate the main information pane, and the lateral, altitude and speed panes for controlling of aircraft
 *
 * The overall UI layout is generated on initialisation, and the exact content can be modified by accessing and modifying the relevant UI components stored as variables
 * */
class UIPane(private val uiStage: Stage) {
    var paneImage: KImageButton
    val paneWidth: Float
        get() = max(Variables.UI_WIDTH * 0.28f, 400f)

    // Main pane (when no aircraft selected)
    val mainInfoPane: KContainer<Actor>
    val metarScroll: KScrollPane
    val metarPane: KTableWidget
    val metarExpandSet = HashSet<String>()

    // Control pane (when aircraft is selected)
    val controlPane: KContainer<Actor>
    val lateralContainer: KContainer<Actor>

    val routeSubsectionTable: KTableWidget
    val routeTable: KTableWidget

    val holdTable: KTableWidget
    val vectorTable: KTableWidget

    // Route editing pane
    val routeEditPane: KContainer<Actor>
    val routeEditTable: KTableWidget

    init {
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                // debugAll()
                setSize(paneWidth, Variables.UI_HEIGHT)
            }
            mainInfoPane = container {
                // debugAll()
                fill()
                setSize(paneWidth, Variables.UI_HEIGHT)
                table {
                    // debugAll()
                    metarScroll = scrollPane("MetarPane") {
                        // debugAll()
                        metarPane = table {
                            align(Align.top)
                        }
                        setOverscroll(false, false)
                    }.cell(padTop = 20f, align = Align.top, preferredWidth = paneWidth, preferredHeight = Variables.UI_HEIGHT * 0.4f, growX = true)
                    align(Align.top)
                }
                isVisible = false
            }
            controlPane = container {
                fill()
                setSize(paneWidth, Variables.UI_HEIGHT)
                // debugAll()
                table {
                    table {
                        // First row of mode buttons - Route, Hold, Vectors
                        textButton("Route", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3)
                        textButton("Hold", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3)
                        textButton("Vectors", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3)
                    }.cell(preferredWidth = paneWidth, height = Variables.UI_HEIGHT * 0.125f, growX = true, align = Align.top)
                    row()
                    table {
                        // Second row of selectBoxes - Approach, Transition
                        selectBox<String>("ControlPane").apply {
                            items = arrayOf("Approach", "ILS05L", "ILS05R").toGdxArray()
                            list.setAlignment(Align.center)
                            setAlignment(Align.center)
                        }.cell(grow = true, preferredWidth = paneWidth / 2)
                        selectBox<String>("ControlPane").apply {
                            items = arrayOf("Via vectors", "Via JAMMY", "Via FETUS", "Via MARCH").toGdxArray()
                            list.setAlignment(Align.center)
                            setAlignment(Align.center)
                        }.cell(grow = true, preferredWidth = paneWidth / 2)
                    }.cell(preferredWidth = paneWidth, height = Variables.UI_HEIGHT * 0.125f, growX = true)
                    row()
                    table {
                        // Third row of selectBoxes, button - Altitude, Expedite, Speed
                        selectBox<Int>("ControlPane").apply {
                            items = (2000..20000 step 1000).toGdxArray()
                            list.setAlignment(Align.center)
                            setAlignment(Align.center)
                        }.cell(grow = true, preferredWidth = paneWidth * 0.37f)
                        textButton("Expedite", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth * 0.26f)
                        selectBox<Int>("ControlPane").apply {
                            items = (150 .. 250 step 10).toGdxArray()
                            list.setAlignment(Align.center)
                            setAlignment(Align.center)
                        }.cell(grow = true, preferredWidth = paneWidth * 0.37f)
                    }.cell(preferredWidth = paneWidth, height = Variables.UI_HEIGHT * 0.125f, growX = true)
                    row()
                    lateralContainer = container {  }.cell(grow = true, preferredWidth = paneWidth)
                    row()
                    table {
                        // Last row of buttons - Undo all, Acknowledge/Handover, Transmit
                        textButton("Undo all", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3)
                        textButton("Handover\n-\nAcknowledge", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3)
                        textButton("Transmit", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3)
                    }.cell(preferredWidth = paneWidth, height = Variables.UI_HEIGHT * 0.125f, growX = true, align = Align.bottom)
                    align(Align.top)
                }
                isVisible = false
            }
            routeSubsectionTable = table {
                // debugAll()
                scrollPane("ControlPaneRoute") {
                    routeTable = table {
                        // debugAll()
                        textButton("=>", "ControlPaneRouteDirect").cell(growX = true, preferredWidth = 0.2f * paneWidth, preferredHeight = 0.1f * Variables.UI_HEIGHT)
                        label("WPT01", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * paneWidth)
                        label("9000\n5000", "ControlPaneBothAltRestr").apply { setAlignment(Align.center) }.cell(expandX = true, padLeft = 10f, padRight = 10f)
                        label("250kts", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * paneWidth)
                        row()
                        textButton("=>", "ControlPaneRouteDirect").cell(growX = true, preferredWidth = 0.2f * paneWidth, preferredHeight = 0.1f * Variables.UI_HEIGHT)
                        label("WPT02", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * paneWidth)
                        label("3000", "ControlPaneBottomAltRestr").apply { setAlignment(Align.center) }.cell(expandX = true, padLeft = 10f, padRight = 10f)
                        label("230kts", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * paneWidth)
                        align(Align.top)
                    }
                    setOverscroll(false, false)
                }.cell(preferredWidth = 0.81f * paneWidth, preferredHeight = 0.6f * Variables.UI_HEIGHT, grow = true, padTop = 20f, align = Align.top)
                table {
                    textButton("Edit\nroute", "ControlPaneButton").cell(growX = true, height = Variables.UI_HEIGHT * 0.15f)
                    row()
                    textButton("CDA", "ControlPaneSelected").cell(growX = true, height = Variables.UI_HEIGHT * 0.15f)
                }.cell(preferredWidth = 0.19f * paneWidth, padTop = 20f, align = Align.top)
            }
            holdTable = table {
                // debugAll()
                table {
                    selectBox<String>("ControlPane") {
                        items = arrayOf("Present position", "JAMMY", "MARCH").toGdxArray()
                        setAlignment(Align.center)
                        list.setAlignment(Align.center)
                    }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
                    textButton("As\n Published", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f, padRight = 10f)
                    textButton("Custom", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f)
                }.cell(preferredWidth = paneWidth, growX = true, height = Variables.UI_HEIGHT * 0.1f, padTop = 20f)
                row()
                table {
                    // debugAll()
                    label("Legs:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, height = Variables.UI_HEIGHT * 0.1f, padRight = 10f, preferredWidth = 0.15f * paneWidth, align = Align.center)
                    textButton("-", "ControlPaneHold").cell(grow = true, preferredWidth = 0.15f * paneWidth)
                    label("5 nm", "ControlPaneHoldDist").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.15f * paneWidth, align = Align.center)
                    textButton("+", "ControlPaneHold").cell(grow = true, padRight = 30f, preferredWidth = 0.15f * paneWidth)
                    textButton("Left", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f)
                    textButton("Right", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f)
                }.cell(preferredWidth = paneWidth, growX = true, height = Variables.UI_HEIGHT * 0.1f, padTop = 20f)
                row()
                table {
                    label("Inbound\nheading:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.22f * paneWidth, padRight = 10f)
                    table {
                        textButton("-20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * Variables.UI_HEIGHT - 40f)
                        row()
                        textButton("-5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * Variables.UI_HEIGHT - 40f)
                    }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
                    label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.23f * paneWidth - 10f)
                    table {
                        textButton("+20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * Variables.UI_HEIGHT - 40f)
                        row()
                        textButton("+5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * Variables.UI_HEIGHT - 40f)
                    }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
                }.cell(preferredWidth = paneWidth, preferredHeight = 0.4f * Variables.UI_HEIGHT - 80f, growX = true, padTop = 20f, padBottom = 20f)
                isVisible = false
            }
            vectorTable = table {
                table {
                    textButton("Left", "ControlPaneHdgLight").cell(grow = true, preferredWidth = 0.5f * paneWidth - 10f)
                    textButton("Right", "ControlPaneHdgLight").cell(grow = true, preferredWidth = 0.5f * paneWidth - 10f)
                }.cell(padTop = 20f, height = 0.1f * Variables.UI_HEIGHT, padLeft = 10f, padRight = 10f)
                row()
                table {
                    table {
                        textButton("-90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * Variables.UI_HEIGHT - 40f) / 3)
                        row()
                        textButton("-10", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * Variables.UI_HEIGHT - 40f) / 3)
                        row()
                        textButton("-5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * Variables.UI_HEIGHT - 40f) / 3)
                    }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padLeft = 10f)
                    label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.3f * paneWidth - 20f)
                    table {
                        textButton("+90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * Variables.UI_HEIGHT - 40f) / 3)
                        row()
                        textButton("+10", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * Variables.UI_HEIGHT - 40f) / 3)
                        row()
                        textButton("+5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * Variables.UI_HEIGHT - 40f) / 3)
                    }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
                }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * Variables.UI_HEIGHT - 40f, padBottom = 20f)
                isVisible = false
            }
            lateralContainer.actor = routeSubsectionTable
            routeEditPane = container {
                fill()
                setSize(paneWidth, Variables.UI_HEIGHT)
                // debugAll()
                table {
                    table {
                        textButton("Cancel all\nAlt restr.", "ControlPaneButton").cell(grow = true, preferredWidth = 0.3f * paneWidth)
                        textButton("Cancel all\nSpd restr.", "ControlPaneButton").cell(grow = true, preferredWidth = 0.3f * paneWidth)
                        selectBox<String>("ControlPane") {
                            items = arrayOf("Change STAR", "TNN1B", "TONGA1A", "TONGA1B").toGdxArray()
                            setAlignment(Align.center)
                            list.setAlignment(Align.center)
                        }.cell(grow = true, preferredWidth = 0.4f * paneWidth)
                    }.cell(growX = true, height = 0.1f * Variables.UI_HEIGHT)
                    row()
                    scrollPane("ControlPaneRoute") {
                        routeEditTable = table {
                            // debugAll()
                            label("WPT01", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, height = 0.125f * Variables.UI_HEIGHT, padLeft = 10f, padRight = 10f)
                            textButton("9000B\n5000A", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                            textButton("250kts", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                            textButton("SKIP", "ControlPaneSelected").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                            row()
                            label("WPT02", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, height = 0.125f * Variables.UI_HEIGHT, padLeft = 10f, padRight = 10f)
                            textButton("3000A", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                            textButton("230kts", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                            textButton("SKIP", "ControlPaneSelected").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                            align(Align.top)
                        }
                        setOverscroll(false, false)
                    }.cell(preferredWidth = paneWidth, preferredHeight = 0.8f * Variables.UI_HEIGHT - 40f, grow = true, padTop = 20f, padBottom = 20f, align = Align.top)
                    row()
                    table {
                        textButton("Undo", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth)
                        textButton("Confirm", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth)
                    }.cell(growX = true, height = 0.1f * Variables.UI_HEIGHT)
                }
                isVisible = true
                setSize(paneWidth, Variables.UI_HEIGHT)
            }
        }
        uiStage.camera.apply {
            moveTo(Vector2(Variables.UI_WIDTH / 2, Variables.UI_HEIGHT / 2))
            update()
        }
    }

    /** Resize the pane and containers */
    fun resize(width: Int, height: Int) {
        uiStage.viewport.update(width, height, true)
        uiStage.camera.apply {
            moveTo(Vector2(Variables.UI_WIDTH / 2, Variables.UI_HEIGHT / 2))
            update()
        }
        paneImage.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
        mainInfoPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
        controlPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
        routeEditPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
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
                val airportInfo = airport.entity[AirportInfo.mapper] ?: continue
                val metarInfo = airport.entity[MetarInfo.mapper] ?: continue
                val text =
                    """
                    ${airportInfo.icaoCode} - ${metarInfo.letterCode}
                    DEP - ???, ???     ARR - ???, ???
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    """.trimIndent()
                val expandedText = """
                    ${airportInfo.icaoCode} - ${metarInfo.letterCode}
                    DEP - ???, ???     ARR - ???, ???
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    Winds: ${if (metarInfo.windSpeedKt.toInt() == 0) "Calm" else "${if (metarInfo.windHeadingDeg == 0.toShort()) "VRB" else metarInfo.windHeadingDeg}@${metarInfo.windSpeedKt}kt ${if (metarInfo.windGustKt > 0) "gusting to ${metarInfo.windGustKt}kt" else ""}"}
                    Visibility: ${if (metarInfo.visibilityM == 9999.toShort()) 10000 else metarInfo.visibilityM}m
                    Ceiling: ${metarInfo.ceilingHundredFtAGL?.let { ceiling -> "${ceiling * 100} feet" } ?: "None"}
                    """.trimIndent() + if (metarInfo.windshear.isNotEmpty()) "\nWindshear: ${metarInfo.windshear}" else ""
                val linkedButton = metarPane.textButton(if (metarExpandSet.contains(airportInfo.icaoCode)) expandedText else text, "Metar").apply {
                    label.setAlignment(Align.left)
                    label.wrap = true
                    cell(growX = true, padLeft = 20f, padRight = 10f, padTop = if (padTop) 30f else 0f, align = Align.left)
                }
                metarPane.textButton(if (metarExpandSet.contains(airportInfo.icaoCode)) "-" else "+", "MetarExpand").apply {
                    cell(height = 50f, width = 50f, padLeft = 10f, padRight = 20f, padTop = if (padTop) 30f else 0f, align = Align.topRight)
                    addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            if (metarExpandSet.contains(airportInfo.icaoCode)) {
                                // Expanded, hide it
                                this@apply.label.setText("+")
                                linkedButton.label.setText(text)
                                metarExpandSet.remove(airportInfo.icaoCode)
                            } else {
                                // Hidden, expand it
                                this@apply.label.setText("-")
                                linkedButton.label.setText(expandedText)
                                metarExpandSet.add(airportInfo.icaoCode)
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