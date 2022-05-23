package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
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
import kotlin.math.min
import kotlin.math.roundToInt

/** Helper object for UI pane's control pane */
class ControlPane {
    lateinit var parentPane: UIPane

    private lateinit var lateralContainer: KContainer<Actor>
    private lateinit var routeModeButton: KTextButton
    private lateinit var holdModeButton: KTextButton
    private lateinit var vectorModeButton: KTextButton
    private lateinit var altSelectBox: KSelectBox<String>
    private lateinit var spdSelectBox: KSelectBox<Short>

    private val routeSubpaneObj = RouteSubpane()
    private val holdSubpaneObj = HoldSubpane()
    private val vectorSubpaneObj = VectorSubpane()

    private lateinit var undoButton: KTextButton
    private lateinit var transmitButton: KTextButton

    var modificationInProgress = false

    var directLeg: Route.Leg? = null
    var selectedHoldLeg: Route.HoldLeg? = null
    var afterWptHdgLeg: Route.WaypointLeg? = null

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
        routeSubpaneObj.routeTable(this, widget, paneWidth, setToEditRoutePane)
        holdSubpaneObj.holdTable(this, widget, paneWidth)
        vectorSubpaneObj.vectorTable(this, widget, paneWidth)
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
                        list.alignment = Align.center
                        setAlignment(Align.center)
                    }.cell(grow = true, preferredWidth = paneWidth / 2)
                    selectBox<String>("ControlPane").apply {
                        items = arrayOf("Via vectors", "Via JAMMY", "Via FETUS", "Via MARCH").toGdxArray()
                        list.alignment = Align.center
                        setAlignment(Align.center)
                    }.cell(grow = true, preferredWidth = paneWidth / 2)
                }.cell(preferredWidth = paneWidth, height = UI_HEIGHT * 0.125f, growX = true)
                row()
                table {
                    // Third row of selectBoxes, button - Altitude, Expedite, Speed
                    altSelectBox = selectBox<String>("ControlPane").apply {
                        items = GdxArray()
                        list.alignment = Align.center
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
                        list.alignment = Align.center
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
                                    if (compareLegEquality(legToCheck, leg2)) return@also // Leg reached
                                    if (legToCheck !is Route.WaypointLeg) {
                                        // Remove any non waypoint legs along the way
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
                routeSubpaneObj.isVisible = true
                holdSubpaneObj.isVisible = false
                vectorSubpaneObj.isVisible = false
                lateralContainer.actor = routeSubpaneObj.actor
                parentPane.userClearanceState.vectorHdg = null
                routeSubpaneObj.updateRouteTable(parentPane.userClearanceState.route)
                updateUndoTransmitButtonStates()
            }
            UIPane.MODE_HOLD -> {
                if (!holdModeButton.isChecked) return
                routeModeButton.isChecked = false
                vectorModeButton.isChecked = false
                routeSubpaneObj.isVisible = false
                holdSubpaneObj.isVisible = true
                vectorSubpaneObj.isVisible = false
                lateralContainer.actor = holdSubpaneObj.actor
                selectedHoldLeg = parentPane.userClearanceState.route.getNextHoldLeg()
                if (selectedHoldLeg == null) holdSubpaneObj.updateHoldClearanceState(parentPane.userClearanceState.route)
                holdSubpaneObj.updateHoldTable(parentPane.userClearanceState.route, selectedHoldLeg)
                updateUndoTransmitButtonStates()
            }
            UIPane.MODE_VECTOR -> {
                if (!vectorModeButton.isChecked) return
                routeModeButton.isChecked = false
                holdModeButton.isChecked = false
                routeSubpaneObj.isVisible = false
                holdSubpaneObj.isVisible = false
                vectorSubpaneObj.isVisible = true
                lateralContainer.actor = vectorSubpaneObj.actor
                afterWptHdgLeg = parentPane.userClearanceState.route.getNextAfterWptHdgLeg()
                if (parentPane.userClearanceState.vectorHdg == null && parentPane.clearanceState.vectorHdg == null)
                    parentPane.userClearanceState.vectorHdg = GAME.gameClientScreen?.selectedAircraft?.entity?.get(CommandTarget.mapper)?.targetHdgDeg?.roundToInt()?.toShort() ?: 360
                else if (parentPane.userClearanceState.vectorHdg == null) parentPane.userClearanceState.vectorHdg = parentPane.clearanceState.vectorHdg
                vectorSubpaneObj.updateVectorTable(parentPane.userClearanceState.route, parentPane.userClearanceState.vectorHdg, parentPane.userClearanceState.vectorTurnDir)
                updateUndoTransmitButtonStates()
            }
            else -> Gdx.app.log("UIPane", "Unknown lateral mode $mode")
        }
        modificationInProgress = false
    }

    /**
     * Updates the route table belonging to the [routeSubpaneObj] of this control pane with the input route
     * @param route the route to set the route table to display
     * */
    fun updateRouteTable(route: Route) {
        routeSubpaneObj.updateRouteTable(route)
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
        val leg1 = parentPane.clearanceState.route.legs.let {
            var currDirectLeg: Route.Leg? = null // Additional variable for finding current direct leg as making leg1 a var prevents smart cast in this changing closure below
            for (i in 0 until it.size) {
                it[i]?.apply {
                    if (this is Route.WaypointLeg && !this.legActive) return@apply // Do not choose a skipped waypoint leg as the current direct
                    currDirectLeg = this
                }
                if (currDirectLeg != null) break
            }
            currDirectLeg
        }
        val leg2 = directLeg
        val directChanged = if (leg1 == null && leg2 == null) false else if (leg1 == null || leg2 == null) true else !compareLegEquality(leg1, leg2)
        if (checkClearanceEquality(parentPane.clearanceState, parentPane.userClearanceState) && !directChanged) setUndoTransmitButtonsUnchanged()
        else setUndoTransmitButtonsChanged()
    }

    /** Resets [directLeg] back to null, called when a new aircraft is being set in [parentPane] */
    fun resetDirectButton() {
        directLeg = null
    }
}