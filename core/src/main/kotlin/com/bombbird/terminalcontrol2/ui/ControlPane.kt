package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.addChangeListener
import com.bombbird.terminalcontrol2.utilities.removeMouseScrollListeners
import ktx.collections.GdxArray
import ktx.collections.toGdxArray
import ktx.scene2d.*

lateinit var lateralContainer: KContainer<Actor>
lateinit var routeModeButton: KTextButton
lateinit var holdModeButton: KTextButton
lateinit var vectorModeButton: KTextButton
lateinit var altSelectBox: KSelectBox<String>
lateinit var spdSelectBox: KSelectBox<Short>

lateinit var routeTable: KTableWidget
lateinit var routeLegsTable: KTableWidget
lateinit var holdTable: KTableWidget
lateinit var vectorTable: KTableWidget

@Scene2dDsl
fun <S> KWidget<S>.controlPane(paneWidth: Float): KContainer<Actor> {
    return container {
        fill()
        setSize(paneWidth, Variables.UI_HEIGHT)
        // debugAll()
        table {
            table {
                // First row of mode buttons - Route, Hold, Vectors
                routeModeButton = textButton("Route", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                    addChangeListener { _, _ -> setPaneLateralMode(UIPane.MODE_ROUTE) }
                }
                holdModeButton = textButton("Hold", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                    addChangeListener { _, _ -> setPaneLateralMode(UIPane.MODE_HOLD) }
                }
                vectorModeButton = textButton("Vectors", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                    addChangeListener { _, _ -> setPaneLateralMode(UIPane.MODE_VECTOR) }
                }
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
                altSelectBox = selectBox<String>("ControlPane").apply {
                    items = GdxArray()
                    list.setAlignment(Align.center)
                    setAlignment(Align.center)
                }.cell(grow = true, preferredWidth = paneWidth * 0.37f)
                textButton("Expedite", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth * 0.26f)
                spdSelectBox = selectBox<Short>("ControlPane").apply {
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
}

@Scene2dDsl
fun <S> KWidget<S>.routeTable(paneWidth: Float, setToEditRoutePane: () -> Unit): KTableWidget {
    return table {
        // debugAll()
        scrollPane("ControlPaneRoute") {
            routeLegsTable = table {
                // debugAll()
                align(Align.top)
            }
            setOverscroll(false, false)
            removeMouseScrollListeners()
        }.cell(preferredWidth = 0.81f * paneWidth, preferredHeight = 0.6f * Variables.UI_HEIGHT, grow = true, padTop = 5f, align = Align.top)
        table {
            textButton("Edit\nroute", "ControlPaneButton").cell(growX = true, height = Variables.UI_HEIGHT * 0.15f).addListener(object: ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    setToEditRoutePane()
                }
            })
            row()
            textButton("CDA", "ControlPaneSelected").cell(growX = true, height = Variables.UI_HEIGHT * 0.15f)
        }.cell(preferredWidth = 0.19f * paneWidth, padTop = 20f, align = Align.top)
    }
}

@Scene2dDsl
fun <S> KWidget<S>.holdTable(paneWidth: Float): KTableWidget {
    return table {
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
}

@Scene2dDsl
fun <S> KWidget<S>.vectorTable(paneWidth: Float): KTableWidget {
    return table {
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
}

/** Updates the style of the clearance mode buttons depending on the aircraft's cleared navigation state */
fun updateClearanceMode(route: Route, vectorHdg: Short?) {
    if (vectorHdg != null) {
        // Vector mode active
        vectorModeButton.isChecked = true
        setPaneLateralMode(UIPane.MODE_VECTOR)
    } else {
        vectorModeButton.isChecked = false
        route.legs.apply {
            if ((size == 1 && first() is Route.HoldLeg) || (size >= 2 && (first() as? Route.WaypointLeg)?.wptId == (get(1) as? Route.HoldLeg)?.wptId)) {
                // Hold mode active when the current leg is a hold leg, or when the aircraft is flying towards the waypoint it is cleared to hold at
                holdModeButton.isChecked = true
                setPaneLateralMode(UIPane.MODE_HOLD)
            } else {
                // Otherwise, use route mode
                routeModeButton.isChecked = true
                setPaneLateralMode(UIPane.MODE_ROUTE)
            }
        }
    }
}

/** Updates the lateral mode button checked status as well as the pane being displayed
 *
 * Called when user taps on a lateral mode button
 * */
private fun setPaneLateralMode(mode: Byte) {
    when (mode) {
        UIPane.MODE_ROUTE -> {
            if (!routeModeButton.isChecked) return
            holdModeButton.isChecked = false
            vectorModeButton.isChecked = false
            routeTable.isVisible = true
            holdTable.isVisible = false
            vectorTable.isVisible = false
        }
        UIPane.MODE_HOLD -> {
            if (!holdModeButton.isChecked) return
            routeModeButton.isChecked = false
            vectorModeButton.isChecked = false
            routeTable.isVisible = false
            holdTable.isVisible = true
            vectorTable.isVisible = false
        }
        UIPane.MODE_VECTOR -> {
            if (!vectorModeButton.isChecked) return
            routeModeButton.isChecked = false
            holdModeButton.isChecked = false
            routeTable.isVisible = false
            holdTable.isVisible = false
            vectorTable.isVisible = true
        }
        else -> Gdx.app.log("UIPane", "Unknown lateral mode $mode")
    }
}