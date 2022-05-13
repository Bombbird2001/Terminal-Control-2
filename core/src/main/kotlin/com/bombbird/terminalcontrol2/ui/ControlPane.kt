package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.addChangeListener
import com.bombbird.terminalcontrol2.utilities.removeMouseScrollListeners
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.collections.toGdxArray
import ktx.scene2d.*
import kotlin.math.min

/** Helper object for UI pane's control pane */
class ControlPane {
    private lateinit var parentPane: UIPane

    private lateinit var lateralContainer: KContainer<Actor>
    private lateinit var routeModeButton: KTextButton
    private lateinit var holdModeButton: KTextButton
    private lateinit var vectorModeButton: KTextButton
    private lateinit var altSelectBox: KSelectBox<String>
    private lateinit var spdSelectBox: KSelectBox<Short>

    private lateinit var routeTable: KTableWidget
    private lateinit var routeLegsTable: KTableWidget
    private lateinit var holdTable: KTableWidget
    private lateinit var vectorTable: KTableWidget

    /**
     * Creates a new table with elements of the control pane
     *
     * Also creates the sub-tables for different modes for the control pane
     * @param uiPane the parent UI pane this control pane belongs to
     * @param widget the widget to add this control pane to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the container
     * @param setToEditRoutePane a function that will be called when the "Edit Route" button is pressed
     * @return a [KContainer] used to contain a table with the elements of the control pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    fun controlPane(uiPane: UIPane, widget: KWidget<Actor>, paneWidth: Float, setToEditRoutePane: () -> Unit): KContainer<Actor> {
        parentPane = uiPane
        routeTable = routeTable(widget, paneWidth, setToEditRoutePane)
        holdTable = holdTable(widget, paneWidth)
        vectorTable = vectorTable(widget, paneWidth)
        return widget.container {
            fill()
            setSize(paneWidth, UI_HEIGHT)
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
                }.cell(preferredWidth = paneWidth, height = UI_HEIGHT * 0.125f, growX = true, align = Align.top)
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
                }.cell(preferredWidth = paneWidth, height = UI_HEIGHT * 0.125f, growX = true)
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
                }.cell(preferredWidth = paneWidth, height = UI_HEIGHT * 0.125f, growX = true)
                row()
                lateralContainer = container {  }.cell(grow = true, preferredWidth = paneWidth)
                row()
                table {
                    // Last row of buttons - Undo all, Acknowledge/Handover, Transmit
                    textButton("Undo all", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3)
                    textButton("Handover\n-\nAcknowledge", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3)
                    textButton("Transmit", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ -> GAME.gameClientScreen?.let { radarScreen -> radarScreen.selectedAircraft?.entity?.let { entity ->
                            radarScreen.sendAircraftControlStateClearance(entity[AircraftInfo.mapper]?.icaoCallsign ?: return@addChangeListener, parentPane.clearanceState)
                        }}}
                    }
                }.cell(preferredWidth = paneWidth, height = UI_HEIGHT * 0.125f, growX = true, align = Align.bottom)
                align(Align.top)
            }
            isVisible = false
        }
    }

    /**
     * @param widget the widget to add this route table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the table
     * @param setToEditRoutePane is the function that will be run when the "Edit route" button is clicked
     * @return a [KTableWidget] used to contain the elements of the route sub-pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    private fun routeTable(widget: KWidget<Actor>, paneWidth: Float, setToEditRoutePane: () -> Unit): KTableWidget {
        return widget.table {
            // debugAll()
            scrollPane("ControlPaneRoute") {
                routeLegsTable = table {
                    // debugAll()
                    align(Align.top)
                }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(preferredWidth = 0.81f * paneWidth, preferredHeight = 0.6f * UI_HEIGHT, grow = true, padTop = 5f, align = Align.top)
            table {
                textButton("Edit\nroute", "ControlPaneButton").cell(growX = true, height = UI_HEIGHT * 0.15f).addListener(object: ChangeListener() {
                    override fun changed(event: ChangeEvent?, actor: Actor?) {
                        setToEditRoutePane()
                    }
                })
                row()
                textButton("CDA", "ControlPaneSelected").cell(growX = true, height = UI_HEIGHT * 0.15f)
            }.cell(preferredWidth = 0.19f * paneWidth, padTop = 20f, align = Align.top)
            isVisible = false
        }
    }

    /**
     * @param widget the widget to add this hold table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the table
     * @return a [KTableWidget] used to contain the elements of the hold sub-pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    private fun holdTable(widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        return widget.table {
            // debugAll()
            table {
                selectBox<String>("ControlPane") {
                    items = arrayOf("Present position", "JAMMY", "MARCH").toGdxArray()
                    setAlignment(Align.center)
                    list.setAlignment(Align.center)
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
                textButton("As\n Published", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f, padRight = 10f)
                textButton("Custom", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f)
            }.cell(preferredWidth = paneWidth, growX = true, height = UI_HEIGHT * 0.1f, padTop = 20f)
            row()
            table {
                // debugAll()
                label("Legs:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, height = UI_HEIGHT * 0.1f, padRight = 10f, preferredWidth = 0.15f * paneWidth, align = Align.center)
                textButton("-", "ControlPaneHold").cell(grow = true, preferredWidth = 0.15f * paneWidth)
                label("5 nm", "ControlPaneHoldDist").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.15f * paneWidth, align = Align.center)
                textButton("+", "ControlPaneHold").cell(grow = true, padRight = 30f, preferredWidth = 0.15f * paneWidth)
                textButton("Left", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f)
                textButton("Right", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f)
            }.cell(preferredWidth = paneWidth, growX = true, height = UI_HEIGHT * 0.1f, padTop = 20f)
            row()
            table {
                label("Inbound\nheading:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.22f * paneWidth, padRight = 10f)
                table {
                    textButton("-20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f)
                    row()
                    textButton("-5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f)
                }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
                label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.23f * paneWidth - 10f)
                table {
                    textButton("+20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f)
                    row()
                    textButton("+5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f)
                }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.4f * UI_HEIGHT - 80f, growX = true, padTop = 20f, padBottom = 20f)
            isVisible = false
        }
    }

    /**
     * @param widget the widget to add this vector table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the table
     * @return a [KTableWidget] used to contain the elements of the vector sub-pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    private fun vectorTable(widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        return widget.table {
            table {
                textButton("Left", "ControlPaneHdgLight").cell(grow = true, preferredWidth = 0.5f * paneWidth - 10f)
                textButton("Right", "ControlPaneHdgLight").cell(grow = true, preferredWidth = 0.5f * paneWidth - 10f)
            }.cell(padTop = 20f, height = 0.1f * UI_HEIGHT, padLeft = 10f, padRight = 10f)
            row()
            table {
                table {
                    textButton("-90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3)
                    row()
                    textButton("-10", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3)
                    row()
                    textButton("-5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3)
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padLeft = 10f)
                label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.3f * paneWidth - 20f)
                table {
                    textButton("+90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3)
                    row()
                    textButton("+10", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3)
                    row()
                    textButton("+5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3)
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT - 40f, padBottom = 20f)
            isVisible = false
        }
    }

    /**
     * Updates the style of the clearance mode buttons depending on the aircraft's cleared navigation state
     * @param route the aircraft's latest cleared route
     * @param vectorHdg the aircraft's latest cleared vector heading; is null if aircraft is not being vectored
     * */
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

    /**
     * Updates [ControlPane.altSelectBox] with the appropriate altitude choices for [MIN_ALT], [MAX_ALT] and [aircraftMaxAlt]
     * @param aircraftMaxAlt the maximum altitude the aircraft can fly at
     * */
    fun updateAltSelectBoxChoices(aircraftMaxAlt: Int) {
        /** Checks the [alt] according to [TRANS_ALT] and [TRANS_LVL] before adding it to the [array] */
        fun checkAltAndAddToArray(alt: Int, array: GdxArray<String>) {
            if (alt > TRANS_ALT && alt < TRANS_LVL * 100) return
            if (alt <= TRANS_ALT) array.add(alt.toString())
            else if (alt >= TRANS_LVL * 100) array.add("FL${alt / 100}")
        }

        val minAlt = if (MIN_ALT % 1000 > 0) (MIN_ALT / 1000 + 1) * 1000 else MIN_ALT
        val maxAltAircraft = aircraftMaxAlt - aircraftMaxAlt % 1000
        val maxAlt = min(MAX_ALT - MAX_ALT % 1000, maxAltAircraft)
        var intermediateQueueIndex = 0
        altSelectBox.items = GdxArray<String>().apply {
            clear()
            if (MIN_ALT % 1000 > 0) checkAltAndAddToArray(MIN_ALT, this)
            for (alt in minAlt .. maxAlt step 1000) {
                INTERMEDIATE_ALTS.also { while (intermediateQueueIndex < it.size) {
                    it[intermediateQueueIndex]?.let { intermediateAlt -> if (intermediateAlt <= alt) {
                        if (intermediateAlt < alt) checkAltAndAddToArray(intermediateAlt, this)
                        intermediateQueueIndex++
                    } else return@also } ?: intermediateQueueIndex++
                }

                }
                checkAltAndAddToArray(alt, this)
            }
            if (MAX_ALT % 1000 > 0) checkAltAndAddToArray(MAX_ALT, this)
        }
    }

    /**
     * Updates the cleared altitude, speed in the pane
     * @param clearedAlt the altitude to set as selected in [ControlPane.altSelectBox]
     * @param clearedSpd the IAS to set as selected in [ControlPane.spdSelectBox]
     * @param minSpd the minimum IAS that can be selected
     * @param maxSpd the maximum IAS that can be selected
     * @param optimalSpd the optimal IAS that the aircraft will select by default without player intervention
     * */
    fun updateAltSpdClearances(clearedAlt: Int, clearedSpd: Short, minSpd: Short, maxSpd: Short, optimalSpd: Short) {
        val minSpdRounded = if (minSpd % 10 > 0) ((minSpd / 10 + 1) * 10).toShort() else minSpd
        val maxSpdRounded = if (maxSpd % 10 > 0) ((maxSpd / 10) * 10).toShort() else maxSpd
        spdSelectBox.items = GdxArray<Short>().apply {
            clear()
            if (minSpd % 10 > 0) add(minSpd)
            for (spd in minSpdRounded .. maxSpdRounded step 10) {
                if (optimalSpd < spd && spd - optimalSpd <= 9) add(optimalSpd)
                add(spd.toShort())
            }
            if (maxSpd % 10 > 0) {
                if (optimalSpd < maxSpd && maxSpd - optimalSpd < maxSpd % 10) add(optimalSpd)
                add(maxSpd)
            }
        }
        altSelectBox.selected = if (clearedAlt >= TRANS_LVL * 100) "FL${clearedAlt / 100}" else clearedAlt.toString()
        spdSelectBox.selected = clearedSpd
    }

    /**
     * Updates the route list in [ControlPane.routeLegsTable] (Route sub-pane)
     * @param route the route to display in the route pane; should be the aircraft's latest cleared route
     * */
    fun updateRouteTable(route: Route) {
        routeLegsTable.clear()
        routeLegsTable.apply {
            var firstDirectSet = false
            for (i in 0 until route.legs.size) {
                route.legs[i].let { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.wptId?.let { wptId -> GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(
                        WaypointInfo.mapper)?.wptName } ?:
                    (leg as? Route.VectorLeg)?.heading?.let { hdg -> "HDG $hdg" } ?:
                    (leg as? Route.HoldLeg)?.wptId?.let { wptId -> "Hold at\n${GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(
                        WaypointInfo.mapper)?.wptName}" } ?:
                    (leg as? Route.DiscontinuityLeg)?.let { "Discontinuity" } ?:
                    (leg as? Route.InitClimbLeg)?.heading?.let { hdg -> "Climb on\nHDG $hdg" } ?: return@let
                    val altRestrDisplay = (leg as? Route.WaypointLeg)?.let { wptLeg ->
                        var restr = wptLeg.maxAltFt?.toString() ?: ""
                        restr += wptLeg.minAltFt?.toString()?.let { minAlt -> "${if (restr.isNotBlank()) "\n" else ""}$minAlt" } ?: ""
                        restr
                    } ?: (leg as? Route.InitClimbLeg)?.minAltFt?.let { minAlt -> "$minAlt" } ?: ""
                    val altRestrStyle = (leg as? Route.WaypointLeg)?.let { wptLeg -> when {
                        wptLeg.minAltFt != null && wptLeg.maxAltFt != null -> "ControlPaneBothAltRestr"
                        wptLeg.minAltFt != null -> "ControlPaneBottomAltRestr"
                        wptLeg.maxAltFt != null -> "ControlPaneTopAltRestr"
                        else -> "ControlPaneRoute"
                    } + if (altRestrDisplay.isNotBlank() && !wptLeg.altRestrActive) "Cancel" else ""} ?: (leg as? Route.InitClimbLeg)?.let { "ControlPaneBottomAltRestr" } ?: "ControlPaneRoute"
                    val spdRestr = (leg as? Route.WaypointLeg)?.maxSpdKt?.let { spd -> "${spd}kts" } ?: ""
                    val spdRestrStyle = if ((leg as? Route.WaypointLeg)?.spdRestrActive == true) "ControlPaneRoute" else "ControlPaneSpdRestrCancel"
                    textButton("=>", "ControlPaneRouteDirect").cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth, preferredHeight = 0.1f * UI_HEIGHT).apply {
                        if (!firstDirectSet) {
                            isChecked = true
                            firstDirectSet = true
                        }
                    }
                    label(legDisplay, "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth)
                    label(altRestrDisplay, altRestrStyle).apply { setAlignment(Align.center) }.cell(expandX = true, padLeft = 10f, padRight = 10f)
                    label(spdRestr, spdRestrStyle).apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth)
                    row()
                }
            }
        }
    }

    /**
     * Updates the lateral mode button checked status as well as the pane being displayed
     *
     * Called when user taps on a lateral mode button
     * @param mode the pane mode to show
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
                lateralContainer.actor = routeTable
            }
            UIPane.MODE_HOLD -> {
                if (!holdModeButton.isChecked) return
                routeModeButton.isChecked = false
                vectorModeButton.isChecked = false
                routeTable.isVisible = false
                holdTable.isVisible = true
                vectorTable.isVisible = false
                lateralContainer.actor = holdTable
            }
            UIPane.MODE_VECTOR -> {
                if (!vectorModeButton.isChecked) return
                routeModeButton.isChecked = false
                holdModeButton.isChecked = false
                routeTable.isVisible = false
                holdTable.isVisible = false
                vectorTable.isVisible = true
                lateralContainer.actor = vectorTable
            }
            else -> Gdx.app.log("UIPane", "Unknown lateral mode $mode")
        }
    }
}