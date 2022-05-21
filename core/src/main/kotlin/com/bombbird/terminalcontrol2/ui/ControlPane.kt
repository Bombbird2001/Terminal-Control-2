package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.collections.toGdxArray
import ktx.scene2d.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

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

    private lateinit var undoButton: KTextButton
    private lateinit var transmitButton: KTextButton

    private var modificationInProgress = false

    private var directLeg: Route.Leg? = null
    private var directButtonArray = GdxArray<KTextButton>(10)

    private lateinit var vectorLabel: Label

    private var selectedHoldLeg: Route.HoldLeg? = null
    private lateinit var holdSelectBox: KSelectBox<String>
    private lateinit var holdLegDistLabel: Label
    private lateinit var holdInboundHdgLabel: Label
    private lateinit var holdAsPublishedButton: KTextButton
    private lateinit var holdCustomButton: KTextButton
    private lateinit var holdLeftButton: KTextButton
    private lateinit var holdRightButton: KTextButton

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
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            setPaneLateralMode(UIPane.MODE_ROUTE)
                        }
                    }
                    holdModeButton = textButton("Hold", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            setPaneLateralMode(UIPane.MODE_HOLD)
                        }
                    }
                    vectorModeButton = textButton("Vectors", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            setPaneLateralMode(UIPane.MODE_VECTOR)
                        }
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
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            parentPane.userClearanceState.clearedAlt = if (selected.contains("FL")) selected.substring(2).toInt() * 100 else selected.toInt()
                            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedAlt == parentPane.clearanceState.clearedAlt) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            updateUndoTransmitButtonStates()
                        }
                    }.cell(grow = true, preferredWidth = paneWidth * 0.37f)
                    textButton("Expedite", "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth * 0.26f)
                    spdSelectBox = selectBox<Short>("ControlPane").apply {
                        list.setAlignment(Align.center)
                        setAlignment(Align.center)
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            parentPane.userClearanceState.clearedIas = selected.toShort()
                            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedIas == parentPane.clearanceState.clearedIas) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            updateUndoTransmitButtonStates()
                        }
                    }.cell(grow = true, preferredWidth = paneWidth * 0.37f)
                }.cell(preferredWidth = paneWidth, height = UI_HEIGHT * 0.125f, growX = true)
                row()
                lateralContainer = container {  }.cell(grow = true, preferredWidth = paneWidth)
                row()
                table {
                    // Last row of buttons - Undo all, Acknowledge/Handover, Transmit
                    undoButton = textButton("Undo all", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ -> Gdx.app.postRunnable { parentPane.setSelectedAircraft(GAME.gameClientScreen?.selectedAircraft ?: return@postRunnable) }}
                    }
                    textButton("Handover\n-\nAcknowledge", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3).isVisible = false
                    transmitButton = textButton("Transmit", "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ -> GAME.gameClientScreen?.let { radarScreen -> radarScreen.selectedAircraft?.let { aircraft ->
                            val leg1 = if (parentPane.clearanceState.route.legs.size > 0) parentPane.clearanceState.route.legs[0] else null
                            val leg2 = directLeg
                            val directChanged = if (leg1 == null && leg2 == null) false else if (leg1 == null || leg2 == null) true else !compareLegEquality(leg1, leg2)
                            // Remove any non waypoint legs before directLeg
                            leg2?.also {
                                var index = 0
                                parentPane.userClearanceState.route.legs.apply { while (index < size) {
                                    val legToCheck = get(index)
                                    if (legToCheck is Route.WaypointLeg && compareLegEquality(legToCheck, leg2)) return@also // Direct reached
                                    if (legToCheck !is Route.WaypointLeg) {
                                        // Remove any non waypoint legs
                                        removeIndex(index)
                                        index--
                                    }
                                    index++
                                }}
                            }
                            if (checkClearanceEquality(parentPane.userClearanceState, parentPane.clearanceState) && !directChanged) return@addChangeListener // No need to update anything if no change to clearance
                            radarScreen.sendAircraftControlStateClearance(aircraft.entity[AircraftInfo.mapper]?.icaoCallsign ?: return@addChangeListener, parentPane.userClearanceState)
                            Gdx.app.postRunnable {
                                aircraft.entity[ClearanceAct.mapper]?.actingClearance?.actingClearance?.updateUIClearanceState(parentPane.userClearanceState)
                                parentPane.updateSelectedAircraft(aircraft)
                            }
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
                textButton("Edit\nroute", "ControlPaneButton").cell(growX = true, height = UI_HEIGHT * 0.15f).addChangeListener { _, _ -> setToEditRoutePane() }
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
                holdSelectBox = selectBox<String>("ControlPane") {
                    setAlignment(Align.center)
                    list.setAlignment(Align.center)
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        // Update the hold legs in route when selected hold leg changes
                        updateHoldClearanceState(parentPane.userClearanceState.route)
                        updateHoldTable(parentPane.userClearanceState.route)
                        updateUndoTransmitButtonStates()
                    }
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
                holdAsPublishedButton = textButton("As\n Published", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdCustomButton.isChecked = false
                            selectedHoldLeg?.let {
                                setHoldAsPublished(it)
                                updateHoldTable(parentPane.userClearanceState.route)
                            }
                            modificationInProgress = false
                        } else holdAsPublishedButton.isChecked = true
                    }
                }
                holdCustomButton = textButton("Custom", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f, padRight = 10f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdAsPublishedButton.isChecked = false
                            modificationInProgress = false
                        } else holdCustomButton.isChecked = true
                    }
                }
            }.cell(preferredWidth = paneWidth, growX = true, height = UI_HEIGHT * 0.1f, padTop = 20f)
            row()
            table {
                // debugAll()
                label("Legs:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, height = UI_HEIGHT * 0.1f, padRight = 10f, preferredWidth = 0.15f * paneWidth, align = Align.center)
                textButton("-", "ControlPaneHold").cell(grow = true, preferredWidth = 0.15f * paneWidth).addChangeListener { _, _ ->
                    val newDist = MathUtils.clamp(holdLegDistLabel.text.split(" ")[0].toInt() - 1, 3, 10)
                    selectedHoldLeg?.legDist = newDist.toByte()
                    holdLegDistLabel.setText("$newDist nm")
                    updateAsPublishedStatus()
                    updateHoldParameterChangedState(selectedHoldLeg)
                    updateUndoTransmitButtonStates()
                }
                holdLegDistLabel = label("5 nm", "ControlPaneHoldDist").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.15f * paneWidth, align = Align.center)
                textButton("+", "ControlPaneHold").cell(grow = true, padRight = 30f, preferredWidth = 0.15f * paneWidth).addChangeListener { _, _ ->
                    val newDist = MathUtils.clamp(holdLegDistLabel.text.split(" ")[0].toInt() + 1, 3, 10)
                    selectedHoldLeg?.legDist = newDist.toByte()
                    holdLegDistLabel.setText("$newDist nm")
                    updateAsPublishedStatus()
                    updateHoldParameterChangedState(selectedHoldLeg)
                    updateUndoTransmitButtonStates()
                }
                holdLeftButton = textButton("Left", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdRightButton.isChecked = false
                            modificationInProgress = false
                            updateAsPublishedStatus()
                            updateHoldParameterChangedState(selectedHoldLeg)
                            updateUndoTransmitButtonStates()
                        } else isChecked = true
                    }
                }
                holdRightButton = textButton("Right", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdLeftButton.isChecked = false
                            modificationInProgress = false
                            updateAsPublishedStatus()
                            updateHoldParameterChangedState(selectedHoldLeg)
                            updateUndoTransmitButtonStates()
                        } else isChecked = true
                    }
                }
            }.cell(preferredWidth = paneWidth, growX = true, height = UI_HEIGHT * 0.1f, padTop = 20f)
            row()
            table {
                label("Inbound\nheading:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.22f * paneWidth, padRight = 10f)
                table {
                    textButton("-20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(-20)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("-5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(-5)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        updateUndoTransmitButtonStates()
                    }
                }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
                holdInboundHdgLabel = label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.23f * paneWidth - 10f)
                table {
                    textButton("+20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(20)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("+5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(5)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        updateUndoTransmitButtonStates()
                    }
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
                    textButton("-90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).addChangeListener { _, _ ->
                        updateVectorHdgValue(-90)
                        updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("-10", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).addChangeListener { _, _ ->
                        updateVectorHdgValue(-10)
                        updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("-5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).addChangeListener { _, _ ->
                        updateVectorHdgValue(-5)
                        updateUndoTransmitButtonStates()
                    }
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padLeft = 10f)
                vectorLabel = label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.3f * paneWidth - 20f)
                table {
                    textButton("+90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).addChangeListener { _, _ ->
                        updateVectorHdgValue(90)
                        updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("+10", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).addChangeListener { _, _ ->
                        updateVectorHdgValue(10)
                        updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("+5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).addChangeListener { _, _ ->
                        updateVectorHdgValue(5)
                        updateUndoTransmitButtonStates()
                    }
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
            routeModeButton.isChecked = false
            holdModeButton.isChecked = false
            vectorModeButton.isChecked = true
            setPaneLateralMode(UIPane.MODE_VECTOR)
        } else {
            route.legs.apply {
                if ((size == 1 && first() is Route.HoldLeg) || (size >= 2 && (first() as? Route.WaypointLeg)?.wptId == (get(1) as? Route.HoldLeg)?.wptId)) {
                    // Hold mode active when the current leg is a hold leg, or when the aircraft is flying towards the waypoint it is cleared to hold at
                    routeModeButton.isChecked = false
                    holdModeButton.isChecked = true
                    vectorModeButton.isChecked = false
                    setPaneLateralMode(UIPane.MODE_HOLD)
                } else {
                    // Otherwise, use route mode
                    routeModeButton.isChecked = true
                    holdModeButton.isChecked = false
                    vectorModeButton.isChecked = false
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
        modificationInProgress = true
        altSelectBox.items = GdxArray<String>().apply {
            if (MIN_ALT % 1000 > 0) checkAltAndAddToArray(MIN_ALT, this)
            for (alt in minAlt .. maxAlt step 1000) {
                INTERMEDIATE_ALTS.also { while (intermediateQueueIndex < it.size) {
                    it[intermediateQueueIndex]?.let { intermediateAlt -> if (intermediateAlt <= alt) {
                        if (intermediateAlt < alt) checkAltAndAddToArray(intermediateAlt, this)
                        intermediateQueueIndex++
                    } else return@also } ?: intermediateQueueIndex++
                }}
                checkAltAndAddToArray(alt, this)
            }
            if (MAX_ALT % 1000 > 0) checkAltAndAddToArray(MAX_ALT, this)
        }
        modificationInProgress = false
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
        modificationInProgress = true
        spdSelectBox.items = GdxArray<Short>().apply {
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
        modificationInProgress = false
        altSelectBox.selected = if (clearedAlt >= TRANS_LVL * 100) "FL${clearedAlt / 100}" else clearedAlt.toString()
        spdSelectBox.selected = clearedSpd
    }

    /**
     * Updates the route list in [ControlPane.routeLegsTable] (Route sub-pane)
     * @param route the route to display in the route pane; should be the aircraft's latest cleared route or user input route
     * */
    fun updateRouteTable(route: Route) {
        routeLegsTable.clear()
        directButtonArray.clear()
        var firstDirectSet = false
        var firstAvailableLeg: Route.Leg? = null // The first leg that can be set as selected in case previous direct leg doesn't match any leg
        routeLegsTable.apply {
            for (i in 0 until route.legs.size) {
                route.legs[i].also { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.let { wpt -> if (!wpt.legActive) return@also
                        GAME.gameClientScreen?.waypoints?.get(wpt.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName } ?:
                    (leg as? Route.VectorLeg)?.heading?.let { hdg -> "HDG $hdg" } ?:
                    (leg as? Route.HoldLeg)?.wptId?.let { wptId -> "Hold at\n${GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(
                        WaypointInfo.mapper)?.wptName}" } ?:
                    (leg as? Route.DiscontinuityLeg)?.let { "Discontinuity" } ?:
                    (leg as? Route.InitClimbLeg)?.heading?.let { hdg -> "Climb on\nHDG $hdg" } ?: return@also
                    if (firstAvailableLeg == null) firstAvailableLeg = leg
                    val restrTriple = (leg as? Route.WaypointLeg)?.let { checkRestrChanged(parentPane.clearanceState.route, it) } ?: Triple(false, false, false)
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
                    } + (if (altRestrDisplay.isNotBlank() && !wptLeg.altRestrActive) "Cancel" else "") + if (restrTriple.first) "Changed" else ""
                    } ?: (leg as? Route.InitClimbLeg)?.let { "ControlPaneBottomAltRestr" } ?: "ControlPaneRoute"
                    val spdRestr = (leg as? Route.WaypointLeg)?.maxSpdKt?.let { spd -> "${spd}kts" } ?: ""
                    val spdRestrStyle = if ((leg as? Route.WaypointLeg)?.spdRestrActive == true) "ControlPaneRoute" else "ControlPaneSpdRestrCancel" + if (restrTriple.second) "Changed" else ""
                    directButtonArray.add(textButton("=>", if (!firstDirectSet) "ControlPaneRouteDirect" else "ControlPaneRouteDirectChanged").cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth, preferredHeight = 0.1f * UI_HEIGHT).apply {
                        if (!firstDirectSet && directLeg?.let { compareLegEquality(it, leg) } != false) {
                            // If this leg is equal to the selected direct leg, set as checked
                            isChecked = true
                            directLeg = leg
                            firstDirectSet = true
                        }
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            modificationInProgress = true
                            if (!isChecked) isChecked = true
                            else {
                                val prevLegIndex = directLeg?.let {
                                    // Find the index of the previously selected leg
                                    var index: Int? = null // Additional variable for finding index as making prevLegIndex a var prevents smart cast in this changing closure below
                                    for (j in 0 until route.legs.size) {
                                        if (compareLegEquality(it, route.legs[j])) {
                                            index = j
                                            break
                                        }
                                    }
                                    index
                                }
                                for (j in 0 until directButtonArray.size) {
                                    // Uncheck all buttons except for this one
                                    if (j != i) directButtonArray[j].isChecked = false
                                    (route.legs[j] as? Route.WaypointLeg)?.let {
                                        if (j < i) it.legActive = false // All legs before are no longer active
                                        else if (j == i) it.legActive = true // This leg is active
                                        else if (prevLegIndex != null && j <= prevLegIndex) it.legActive = true // If a leg was selected previously, set all legs from this to the previous leg as active
                                    }
                                }
                                directLeg = leg // Update the new direct leg to this
                            }
                            modificationInProgress = false
                            updateUndoTransmitButtonStates()
                        }
                    })
                    label(legDisplay, "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth)
                    label(altRestrDisplay, altRestrStyle).apply { setAlignment(Align.center) }.cell(expandX = true, padLeft = 10f, padRight = 10f)
                    label(spdRestr, spdRestrStyle).apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth)
                    row()
                }
            }
        }
        if (!firstDirectSet && route.legs.size > 0 && directButtonArray.size > 0) {
            // No selected leg was found - possibly due reasons such as the first leg being skipped in edit route pane
            // Set selection to the first available leg found earlier (i.e. the first non-skipped leg)
            directLeg = firstAvailableLeg
            modificationInProgress = true
            directButtonArray[0].isChecked = true
            modificationInProgress = false
        }
    }

    /**
     * Updates the hold table to the next cleared hold clearance if any
     * @param route the route to refer to; should be the aircraft's latest cleared route or user input route
     * */
    private fun updateHoldTable(route: Route) {
        modificationInProgress = true
        holdSelectBox.items = GdxArray<String>().apply {
            if (parentPane.clearanceState.route.legs.size > 0) { (parentPane.clearanceState.route.legs[0] as? Route.HoldLeg)?.let {
                add(if (it.wptId.toInt() == -1) "Present position" else GAME.gameClientScreen?.waypoints?.get(it.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName)
                return@apply // Only allow the current hold leg in the selection if aircraft is already holding
            }}
            add("Present position")
            for (i in 0 until route.legs.size) route.legs[i]?.let {
                if (it is Route.WaypointLeg) GAME.gameClientScreen?.waypoints?.get(it.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName?.also { name -> add(name) }
            }
        }
        selectedHoldLeg?.apply {
            val wptName = if (wptId.toInt() == -1) "Present position" else GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName ?: return@apply
            holdSelectBox.selected = wptName
            holdLegDistLabel.setText("$legDist nm")
            holdInboundHdgLabel.setText(inboundHdg.toString())
            holdLeftButton.isChecked = turnDir == CommandTarget.TURN_LEFT
            holdRightButton.isChecked = turnDir == CommandTarget.TURN_RIGHT
            GAME.gameClientScreen?.publishedHolds?.get(wptName)?.entity?.get(PublishedHoldInfo.mapper)?.let {
                holdAsPublishedButton.isVisible = true
                val asPublished = legDist == it.legDistNm && inboundHdg == it.inboundHdgDeg && turnDir == it.turnDir
                holdAsPublishedButton.isChecked = asPublished
                holdCustomButton.isChecked = !asPublished
            } ?: run {
                holdAsPublishedButton.isChecked = false
                holdAsPublishedButton.isVisible = false
            }
            updateHoldParameterChangedState(this)
        } ?: run {
            Gdx.app.log("ControlPane", "Null selectedHoldLeg; should not be null")
            holdSelectBox.selectedIndex = 0
            holdLegDistLabel.setText("5 nm")
            holdInboundHdgLabel.setText("360")
        }
        modificationInProgress = false
    }

    /**
     * Updates the user cleared hold inbound heading with the input delta value, and updates the [holdInboundHdgLabel] as well
     * @param change the change in heading that will be added to the user cleared hold inbound heading
     * */
    private fun updateHoldHdgValue(change: Short) {
        selectedHoldLeg?.apply {
            inboundHdg = (inboundHdg + change).toShort().let {
                val rectifiedHeading = if (change >= 0) (it / 5f).toInt() * 5 else ceil(it / 5f).roundToInt() * 5
                modulateHeading(rectifiedHeading.toFloat()).toInt().toShort()
            }
            holdInboundHdgLabel.setText(inboundHdg.toString())

        }
    }

    /**
     * Updates the styles for the hold parameter input fields (selectBox, Labels, etc.) given the input hold leg
     * @param holdLeg the hold leg to compare with the acting clearance state
     * */
    private fun updateHoldParameterChangedState(holdLeg: Route.HoldLeg?) {
        holdLeg?.apply {
            val sameLeg = parentPane.clearanceState.route.findFirstHoldLegWithID(wptId)
            holdSelectBox.style = Scene2DSkin.defaultSkin[if (sameLeg == null) "ControlPaneChanged" else "ControlPane", SelectBoxStyle::class.java]
            val distChanged = sameLeg?.legDist != legDist
            holdLegDistLabel.style = Scene2DSkin.defaultSkin[if (distChanged) "ControlPaneHoldDistChanged" else "ControlPaneHoldDist", LabelStyle::class.java]
            val hdgChanged = sameLeg?.inboundHdg != inboundHdg
            holdInboundHdgLabel.style = Scene2DSkin.defaultSkin[if (hdgChanged) "ControlPaneHdgChanged" else "ControlPaneHdg", LabelStyle::class.java]
            val turnDirChanged = sameLeg?.turnDir != turnDir
            holdRightButton.style = Scene2DSkin.defaultSkin[if (turnDirChanged && holdRightButton.isChecked) "ControlPaneSelectedChanged" else "ControlPaneSelected", TextButtonStyle::class.java]
            holdLeftButton.style = Scene2DSkin.defaultSkin[if (turnDirChanged && holdLeftButton.isChecked) "ControlPaneSelectedChanged" else "ControlPaneSelected", TextButtonStyle::class.java]
        }
    }

     /**
      * Updates the heading display in [vectorTable]
      * @param vectorHdg the currently cleared vector heading
      * */
     fun updateVectorTable(vectorHdg: Short?) {
         vectorLabel.setText(vectorHdg?.toString() ?: "0")
         vectorLabel.style = Scene2DSkin.defaultSkin["ControlPaneHdg${if (parentPane.clearanceState.vectorHdg != parentPane.userClearanceState.vectorHdg) "Changed" else ""}", LabelStyle::class.java]
    }

    /**
     * Updates the user clearance vector heading with the input delta value, and updates the [vectorLabel] as well
     * @param change the change in heading that will be added to the user clearance vector heading
     * */
    private fun updateVectorHdgValue(change: Short) {
        parentPane.userClearanceState.vectorHdg = parentPane.userClearanceState.vectorHdg?.plus(change)?.toShort()?.let {
            val rectifiedHeading = if (change >= 0) (it / 5f).toInt() * 5 else ceil(it / 5f).roundToInt() * 5
            modulateHeading(rectifiedHeading.toFloat()).toInt().toShort()
        }
        updateVectorTable(parentPane.userClearanceState.vectorHdg)
    }

    /**
     * Updates the lateral mode button checked status as well as the pane being displayed
     *
     * Called when user taps on a lateral mode button
     * @param mode the pane mode to show
     * */
    private fun setPaneLateralMode(mode: Byte) {
        modificationInProgress = true
        when (mode) {
            UIPane.MODE_ROUTE -> {
                if (!routeModeButton.isChecked) return
                holdModeButton.isChecked = false
                vectorModeButton.isChecked = false
                routeTable.isVisible = true
                holdTable.isVisible = false
                vectorTable.isVisible = false
                lateralContainer.actor = routeTable
                parentPane.userClearanceState.vectorHdg = null
                updateUndoTransmitButtonStates()
            }
            UIPane.MODE_HOLD -> {
                if (!holdModeButton.isChecked) return
                routeModeButton.isChecked = false
                vectorModeButton.isChecked = false
                routeTable.isVisible = false
                holdTable.isVisible = true
                vectorTable.isVisible = false
                lateralContainer.actor = holdTable
                selectedHoldLeg = parentPane.userClearanceState.route.getNextHoldLeg()
                if (selectedHoldLeg == null) updateHoldClearanceState(parentPane.userClearanceState.route)
                updateHoldTable(parentPane.userClearanceState.route)
                updateUndoTransmitButtonStates()
            }
            UIPane.MODE_VECTOR -> {
                if (!vectorModeButton.isChecked) return
                routeModeButton.isChecked = false
                holdModeButton.isChecked = false
                routeTable.isVisible = false
                holdTable.isVisible = false
                vectorTable.isVisible = true
                lateralContainer.actor = vectorTable
                if (parentPane.userClearanceState.vectorHdg == null && parentPane.clearanceState.vectorHdg == null)
                    parentPane.userClearanceState.vectorHdg = GAME.gameClientScreen?.selectedAircraft?.entity?.get(CommandTarget.mapper)?.targetHdgDeg?.roundToInt()?.toShort() ?: 360
                else if (parentPane.userClearanceState.vectorHdg == null) parentPane.userClearanceState.vectorHdg = parentPane.clearanceState.vectorHdg
                updateVectorTable(parentPane.userClearanceState.vectorHdg)
                updateUndoTransmitButtonStates()
            }
            else -> Gdx.app.log("UIPane", "Unknown lateral mode $mode")
        }
        modificationInProgress = false
    }

    /** Sets the style of the Undo All and Transmit buttons to that of when the clearance state is changed */
    private fun setUndoTransmitButtonsChanged() {
        val newStyle = Scene2DSkin.defaultSkin["ControlPaneButtonChanged", TextButtonStyle::class.java]
        transmitButton.style = newStyle
        undoButton.style = newStyle
    }

    /** Sets the style of the Undo All and Transmit buttons to that of when the clearance state is unchanged */
    fun setUndoTransmitButtonsUnchanged() {
        val newStyle = Scene2DSkin.defaultSkin["ControlPaneButton", TextButtonStyle::class.java]
        transmitButton.style = newStyle
        undoButton.style = newStyle
    }

    /**
     * Updates the appropriate changed/unchanged button styles for the Undo and Transmit buttons depending on the current
     * state of [UIPane.clearanceState] and [UIPane.userClearanceState]
     * */
    fun updateUndoTransmitButtonStates() {
        val leg1 = if (parentPane.clearanceState.route.legs.size > 0) parentPane.clearanceState.route.legs[0] else null
        val leg2 = directLeg
        val directChanged = if (leg1 == null && leg2 == null) false else if (leg1 == null || leg2 == null) true else !compareLegEquality(leg1, leg2)
        if (checkClearanceEquality(parentPane.clearanceState, parentPane.userClearanceState) && !directChanged) setUndoTransmitButtonsUnchanged()
        else setUndoTransmitButtonsChanged()
    }

    /**
     * Updates the appropriate hold legs in the route clearance; should be called when the selected hold leg in [holdTable]
     * have been changed
     *
     * Also updates the [selectedHoldLeg] with the new selected holding leg
     * @param route the route to update the hold legs
     * */
    private fun updateHoldClearanceState(route: Route) {
        (selectedHoldLeg?.wptId)?.let {
            // Look for hold legs that are present in the selected clearance but not the acting clearance
            for (i in 0 until route.legs.size) (route.legs[i] as? Route.HoldLeg)?.apply {
                if ((it.toInt() == -1 && wptId.toInt() == -1) || it == wptId) {
                    // Found in selected clearance
                    for (j in 0 until parentPane.clearanceState.route.legs.size) (parentPane.clearanceState.route.legs[j] as? Route.HoldLeg)?.also { actLeg ->
                        if ((it.toInt() == -1 && actLeg.wptId.toInt() == -1) || it == actLeg.wptId) {
                            // Also found in acting clearance, don't remove
                            return@let
                        }
                    }
                    // Not found in acting clearance, remove from selected clearance
                    route.legs.removeIndex(i)
                    selectedHoldLeg = null
                    return@let
                }
            }
        }

        (holdSelectBox.selected ?: "Present position").let {
            val selectedInboundHdg = holdInboundHdgLabel.text.toString().toShort()
            val selectedLegDist = holdLegDistLabel.text.split(" ")[0].toByte()
            val selectedTurnDir = if (holdLeftButton.isChecked) CommandTarget.TURN_LEFT else CommandTarget.TURN_RIGHT
            if (it != "Present position") {
                // Find the corresponding hold or waypoint leg in route (until a non waypoint/hold leg is found)
                route.legs.also { legs -> for (i in 0 until legs.size) legs[i]?.apply {
                    // Search for hold leg first
                        if (this is Route.HoldLeg && GAME.gameClientScreen?.updatedWaypointMapping?.get(it) == wptId) {
                            // Hold already exists in route, update to selected parameters
                            inboundHdg = selectedInboundHdg
                            legDist = selectedLegDist
                            turnDir = selectedTurnDir
                            selectedHoldLeg = this
                            return@also
                        } else if (this !is Route.HoldLeg && this !is Route.WaypointLeg) return@apply // Non waypoint/hold leg reached
                    }

                    // Hold leg not found, search for waypoint leg instead
                    for (i in 0 until legs.size) legs[i]?.apply {
                        if (this is Route.WaypointLeg && GAME.gameClientScreen?.updatedWaypointMapping?.get(it) == wptId) {
                            // Add a new hold leg after this waypoint leg (phase will be the same as the parent waypoint leg)
                            val newHold = GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName?.let { name ->
                                // If this leg is a published hold leg, set to published by default
                                GAME.gameClientScreen?.publishedHolds?.get(name)?.entity?.get(PublishedHoldInfo.mapper)?.let { pubHold ->
                                    Route.HoldLeg(wptId, pubHold.maxAltFt, pubHold.minAltFt, pubHold.maxSpdKtLower, pubHold.maxSpdKtHigher,
                                        pubHold.inboundHdgDeg, pubHold.legDistNm, pubHold.turnDir, phase)
                                }} ?: Route.HoldLeg(wptId, null, null, 230, 240, selectedInboundHdg, selectedLegDist, selectedTurnDir, phase)
                            route.legs.insert(i + 1, newHold)
                            selectedHoldLeg = newHold
                            return@also
                        } else if (this !is Route.HoldLeg && this !is Route.WaypointLeg) return@also // Non waypoint/hold leg reached
                    }
                }
            } else {
                // Present position hold - create/update custom waypoint
                // Check if first leg is already present hold position
                if (route.legs.size >= 1) (route.legs[0] as? Route.HoldLeg)?.apply {
                    if (wptId.toInt() == -1) {
                        // First leg is present position hold
                        inboundHdg = selectedInboundHdg
                        legDist = selectedLegDist
                        turnDir = selectedTurnDir
                        selectedHoldLeg = this
                        return@let
                    } else return@apply
                }
                // Empty route or first leg is not present position hold leg
                // Add a new present hold leg as the first leg (phase will be the same as the subsequent leg, or normal if no subsequent legs exist)
                val phaseToUse = if (route.legs.size > 0) route.legs[0]?.phase ?: Route.Leg.NORMAL else Route.Leg.NORMAL
                val newHold = Route.HoldLeg(-1, null, null, 230, 240, selectedInboundHdg, selectedLegDist, selectedTurnDir, phaseToUse)
                route.legs.insert(0, newHold)
                selectedHoldLeg = newHold
            }
        }
    }

    /**
     * Sets the parameters of the hold leg to be the same as published holds
     * @param holdWpt the hold leg
     * */
    private fun setHoldAsPublished(holdWpt: Route.HoldLeg) {
        val name = GAME.gameClientScreen?.waypoints?.get(holdWpt.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName ?: return
        val publishedHold = GAME.gameClientScreen?.publishedHolds?.get(name)?.entity?.get(PublishedHoldInfo.mapper) ?: return // Return if no published hold found
        holdWpt.apply {
            maxAltFt = publishedHold.maxAltFt
            minAltFt = publishedHold.minAltFt
            maxSpdKtLower = publishedHold.maxSpdKtLower
            maxSpdKtHigher = publishedHold.maxSpdKtHigher
            inboundHdg = publishedHold.inboundHdgDeg
            legDist = publishedHold.legDistNm
            turnDir = publishedHold.turnDir
        }
    }

    /** Updates the "As Published" and "Custom" buttons state for the selected hold waypoint */
    private fun updateAsPublishedStatus() {
        selectedHoldLeg?.apply {
            val name = GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName ?: return
            val pubHold = GAME.gameClientScreen?.publishedHolds?.get(name)?.entity?.get(PublishedHoldInfo.mapper)
            modificationInProgress = true
            if (pubHold == null || pubHold.maxAltFt != maxAltFt || pubHold.minAltFt != minAltFt || pubHold.inboundHdgDeg != inboundHdg ||
                    pubHold.maxSpdKtLower != maxSpdKtLower || pubHold.maxSpdKtHigher != maxSpdKtHigher || pubHold.legDistNm != legDist || pubHold.turnDir != turnDir) {
                // No published hold, or selected hold differs from published hold
                holdAsPublishedButton.isChecked = false
                holdCustomButton.isChecked = true
            } else {
                holdAsPublishedButton.isChecked = true
                holdCustomButton.isChecked = false
            }
            modificationInProgress = false
        }
    }

    /** Resets [directLeg] back to null, called when a new aircraft is being set in [parentPane] */
    fun resetDirectButton() {
        directLeg = null
    }
}