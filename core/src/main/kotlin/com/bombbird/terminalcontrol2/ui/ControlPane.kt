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
import ktx.scene2d.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Helper object for UI pane's control pane */
class ControlPane {
    lateinit var parentPane: UIPane

    private lateinit var lateralContainer: KContainer<Actor>
    private lateinit var routeModeButton: KTextButton
    private lateinit var holdModeButton: KTextButton
    private lateinit var vectorModeButton: KTextButton
    private lateinit var appSelectBox: KSelectBox<String>
    private lateinit var transitionSelectBox: KSelectBox<String>
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
                    appSelectBox = selectBox<String>("ControlPane").apply {
                        items = GdxArray()
                        list.alignment = Align.center
                        setAlignment(Align.center)
                        addChangeListener { event, _ ->
                            event?.handle()
                            if (modificationInProgress) return@addChangeListener
                            updateTransitionSelectBoxChoices(parentPane.aircraftArrivalArptId, selected)
                            val appName = if (selected == NO_APP_SELECTION) null else selected
                            parentPane.userClearanceState.clearedApp = appName
                            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedApp == parentPane.clearanceState.clearedApp) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            val transName = if (transitionSelectBox.selected == "$TRANS_PREFIX$NO_TRANS_SELECTION") null
                            else transitionSelectBox.selected.replace(TRANS_PREFIX, "")
                            transitionSelectBox.style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedTrans == parentPane.clearanceState.clearedTrans) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            updateApproachRoute(parentPane.aircraftArrivalArptId, appName, transName)
                            updateAltSelectBoxChoices(parentPane.aircraftMaxAlt)
                            updateUndoTransmitButtonStates()
                        }
                    }.cell(grow = true, preferredWidth = paneWidth / 2)
                    transitionSelectBox = selectBox<String>("ControlPane").apply {
                        items = GdxArray()
                        list.alignment = Align.center
                        setAlignment(Align.center)
                        isDisabled = true
                        addChangeListener { event, _ ->
                            event?.handle()
                            if (modificationInProgress) return@addChangeListener
                            val transName = if (selected == "$TRANS_PREFIX$NO_TRANS_SELECTION") null else selected.replace(TRANS_PREFIX, "")
                            parentPane.userClearanceState.clearedTrans = transName
                            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedTrans == parentPane.clearanceState.clearedTrans) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            val appName = if (appSelectBox.selected == NO_APP_SELECTION) null else appSelectBox.selected
                            updateApproachRoute(parentPane.aircraftArrivalArptId, appName, transName)
                            updateAltSelectBoxChoices(parentPane.aircraftMaxAlt)
                            updateUndoTransmitButtonStates()
                        }
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
     * hold waypoint is selected
     * */
    fun updateClearanceMode(route: Route, vectorHdg: Short?, appTrackCaptured: Boolean) {
        if (vectorHdg != null || (route.legs.isEmpty && appTrackCaptured)) {
            // Vector mode active or localizer/visual track captured in vector mode (no route legs)
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
     * Updates [ControlPane.appSelectBox] and [ControlPane.transitionSelectBox] for the provided arrival airport ID
     *
     * If the ID provided is null, the boxes will be disabled
     * @param airportId the airport to refer to when selecting approaches that can be cleared
     * */
    fun updateApproachSelectBoxChoices(airportId: Byte?) {
        modificationInProgress = true
        GAME.gameClientScreen?.airports?.get(airportId)?.entity?.let { arpt ->
            updateAppTransBoxesDisabled(false)
            appSelectBox.apply {
                items = getAvailableApproaches(arpt)
            }
        } ?: updateAppTransBoxesDisabled(true)
        modificationInProgress = false
    }

    /**
     * Updates [ControlPane.transitionSelectBox] with possible transitions for the selected approach given the airport ID
     *
     * If the ID provided is null, the boxes (including [appSelectBox]) will be disabled
     * */
    private fun updateTransitionSelectBoxChoices(airportId: Byte?, selectedApp: String) {
        modificationInProgress = true
        transitionSelectBox.apply {
            GAME.gameClientScreen?.airports?.get(airportId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(selectedApp)?.let { app ->
                items = GdxArray<String>().also { array ->
                    app.transitions.keys().map { "$TRANS_PREFIX$it" }.forEach { array.add(it) }
                }
                if (!selection.isEmpty) parentPane.userClearanceState.clearedTrans = if (selected == "$TRANS_PREFIX$NO_TRANS_SELECTION") null
                else selected.replace(TRANS_PREFIX, "")
                isDisabled = false
            } ?: run {
                isDisabled = true
                items = GdxArray<String>().apply { add("$TRANS_PREFIX$NO_TRANS_SELECTION") }
            }
        }
        modificationInProgress = false
    }

    /**
     * Updates [ControlPane.altSelectBox] with the appropriate altitude choices for [MIN_ALT], [MAX_ALT] and [aircraftMaxAlt]
     * @param aircraftMaxAlt the maximum altitude the aircraft can fly at, or null if none provided in which it will be
     * ignored
     * */
    fun updateAltSelectBoxChoices(aircraftMaxAlt: Int?) {
        /**
         * Checks the input altitude according to transition altitude and transition level parsing it into the FLXXX format
         * if necessary, before adding it to the string array
         * @param alt the altitude value to add
         * @param array the string [GdxArray] to add the value into
         * */
        fun checkAltAndAddToArray(alt: Int, array: GdxArray<String>) {
            if (alt > TRANS_ALT && alt < TRANS_LVL * 100) return
            if (alt <= TRANS_ALT) array.add(alt.toString())
            else if (alt >= TRANS_LVL * 100) array.add("FL${alt / 100}")
        }

        /**
         * Selects the appropriate selection in the altitude box for the input altitude value, accounting for altitudes
         * above the transition altitude being represented in the FLXXX format
         * @param alt the altitude value to set
         * */
        fun setToAltValue(alt: Int) {
            if (alt <= TRANS_ALT) altSelectBox.selected = alt.toString()
            else altSelectBox.selected = "FL${alt / 100}"
        }

        val effectiveMinAlt: Int
        val effectiveMaxAlt: Int
        if (parentPane.userClearanceState.clearedApp != null && parentPane.glidePathCaptured) {
            effectiveMinAlt = parentPane.userClearanceState.clearedAlt
            effectiveMaxAlt = effectiveMinAlt
        } else if (parentPane.userClearanceState.clearedApp != null && hasOnlyWaypointLegsTillMissed(directLeg, parentPane.userClearanceState.route)) {
            // Check if aircraft is cleared for the approach with no interruptions (i.e. no discontinuity, vector or hold legs)
            parentPane.userClearanceState.route.legs.apply {
                // Set to the FAF altitude (i.e. the minimum altitude restriction of the last waypoint)
                val faf = (get(size - 1) as? Route.WaypointLeg)?.minAltFt ?: run {
                    Gdx.app.log("ControlPane", "No FAF altitude found for ${parentPane.userClearanceState.clearedApp}")
                    MIN_ALT
                }
                effectiveMinAlt = faf
                effectiveMaxAlt = faf
            }
        } else {
            val nextHold = getNextHoldLeg(parentPane.userClearanceState.route)
            val holdMinAlt = nextHold?.minAltFt
            val holdMaxAlt = nextHold?.maxAltFt
            effectiveMinAlt = if (holdMinAlt == null) MIN_ALT else max(MIN_ALT, holdMinAlt)
            effectiveMaxAlt = if (holdMaxAlt == null) MAX_ALT else min(MAX_ALT, holdMaxAlt)
        }
        val roundedMinAlt = if (effectiveMinAlt % 1000 > 0) (effectiveMinAlt / 1000 + 1) * 1000 else effectiveMinAlt
        val maxAltAircraft = if (aircraftMaxAlt != null) aircraftMaxAlt - aircraftMaxAlt % 1000 else null
        val roundedMaxAlt = if (maxAltAircraft != null) min(effectiveMaxAlt - effectiveMaxAlt % 1000, maxAltAircraft) else effectiveMaxAlt - effectiveMaxAlt % 1000
        var intermediateQueueIndex = 0
        modificationInProgress = true
        val initialising = altSelectBox.items.isEmpty // If the selection is previously empty, the box is still being initialised, hence do not update the user clearance state below
        altSelectBox.items = GdxArray<String>().apply {
            if (effectiveMinAlt % 1000 > 0) checkAltAndAddToArray(effectiveMinAlt, this)
            for (alt in roundedMinAlt .. roundedMaxAlt step 1000) {
                INTERMEDIATE_ALTS.also { while (intermediateQueueIndex < it.size) {
                    it[intermediateQueueIndex]?.let { intermediateAlt -> if (intermediateAlt <= alt) {
                        if (intermediateAlt < alt && intermediateAlt in (effectiveMinAlt + 1) until effectiveMaxAlt) checkAltAndAddToArray(intermediateAlt, this)
                        intermediateQueueIndex++
                    } else return@also } ?: intermediateQueueIndex++
                }}
                checkAltAndAddToArray(alt, this)
            }
            if (effectiveMaxAlt % 1000 > 0 && effectiveMaxAlt > effectiveMinAlt) checkAltAndAddToArray(effectiveMaxAlt, this)
        }
        if (altSelectBox.selection.size() >= 1) altSelectBox.selected?.let {
            val selAlt = if (it.contains("FL")) it.replace("FL", "").toInt() * 100
            else it.toInt()
            if (selAlt < effectiveMinAlt) setToAltValue(effectiveMinAlt)
            else if (selAlt > effectiveMaxAlt) setToAltValue(effectiveMaxAlt)
        }
        // Do a final setting of the user clearance state after any changes to the selected value in the box has been made
        // unless the box has just been initialised and hence ignore the default value that is selected
        if (!initialising) altSelectBox.selected?.let {
            parentPane.userClearanceState.clearedAlt = if (it.contains("FL")) it.replace("FL", "").toInt() * 100
            else it.toInt()
            altSelectBox.style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedAlt == parentPane.clearanceState.clearedAlt) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
        }
        modificationInProgress = false
    }

    /** Clears all the choices in the altitude select box; should be used when deselecting an aircraft */
    fun clearAltSelectBoxChoices() {
        modificationInProgress = true
        altSelectBox.items = GdxArray()
        modificationInProgress = false
    }

    /**
     * Updates the cleared altitude, speed, approach and approach transition in the pane
     * @param clearedAlt the altitude to set as selected in [ControlPane.altSelectBox]
     * @param clearedSpd the IAS to set as selected in [ControlPane.spdSelectBox]
     * @param minSpd the minimum IAS that can be selected
     * @param maxSpd the maximum IAS that can be selected
     * @param optimalSpd the optimal IAS that the aircraft will select by default without player intervention
     * @param appName the cleared approach, or null if no approach is cleared
     * @param transName the cleared approach transition, or null if no approach has been cleared
     * */
    fun updateAltSpdAppClearances(clearedAlt: Int, clearedSpd: Short, minSpd: Short, maxSpd: Short, optimalSpd: Short, appName: String?, transName: String?) {
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
        appSelectBox.apply {
            if (selection.isEmpty || selected != (appName ?: NO_APP_SELECTION)) selected = appName ?: NO_APP_SELECTION
            // Explicitly set style as change event is not getting fired for some reason
            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedApp == parentPane.clearanceState.clearedApp) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
        }
        if (appName != null && transName != null) transitionSelectBox.apply {
            if (selection.isEmpty || selected != "$TRANS_PREFIX$transName") selected = "$TRANS_PREFIX$transName"
            // Explicitly set style as change event is not getting fired for some reason
            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedTrans == parentPane.clearanceState.clearedTrans) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
        }
        else {
            transitionSelectBox.isDisabled = true
            transitionSelectBox.items = GdxArray<String>().apply { add("$TRANS_PREFIX$NO_TRANS_SELECTION") }
        }
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
                updateApproachSelectBoxChoices(parentPane.aircraftArrivalArptId)
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
                updateAltSelectBoxChoices(parentPane.aircraftMaxAlt)
                selectedHoldLeg = getNextHoldLeg(parentPane.userClearanceState.route)
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
                afterWptHdgLeg = getNextAfterWptHdgLeg(parentPane.userClearanceState.route)
                if (parentPane.userClearanceState.vectorHdg == null && parentPane.clearanceState.vectorHdg == null)
                    parentPane.userClearanceState.vectorHdg = GAME.gameClientScreen?.selectedAircraft?.entity?.get(CommandTarget.mapper)?.targetHdgDeg?.roundToInt()?.toShort() ?: 360
                else if (parentPane.userClearanceState.vectorHdg == null) parentPane.userClearanceState.vectorHdg = parentPane.clearanceState.vectorHdg
                vectorSubpaneObj.setHdgElementsDisabled(parentPane.appTrackCaptured)
                vectorSubpaneObj.updateVectorTable(parentPane.userClearanceState.route, parentPane.userClearanceState.vectorHdg, parentPane.userClearanceState.vectorTurnDir)
                updateUndoTransmitButtonStates()
            }
            else -> Gdx.app.log("UIPane", "Unknown lateral mode $mode")
        }
        modificationInProgress = false
    }

    /**
     * Updates the route to include the input approach and transition given the arrival airport
     * @param arptId the ID of the arrival airport
     * @param appName the name of the approach, or null if none cleared
     * @param transName the name of the approach transition, or null if no approach cleared
     * */
    private fun updateApproachRoute(arptId: Byte?, appName: String?, transName: String?) {
        removeApproachLegs(parentPane.userClearanceState.route, parentPane.userClearanceState.hiddenLegs)
        if (arptId == null || appName == null || transName == null) return updateRouteTable(parentPane.userClearanceState.route)
        val app = GAME.gameClientScreen?.airports?.get(arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(appName) ?: return
        val trans = app.transitions[transName] ?: null // Force to nullable Route? type, instead of a Route! type
        // Search for the first leg in the current route that matches the first leg in the transition
        val matchingIndex = if (transName != "vectors" && (trans?.legs?.size ?: 0) > 0) (trans?.legs?.first() as? Route.WaypointLeg)?.let { firstTransWpt ->
            parentPane.userClearanceState.route.also { currRoute -> for (i in 0 until currRoute.legs.size) {
                if (firstTransWpt.wptId == (currRoute.legs[i] as? Route.WaypointLeg)?.wptId) return@let i
                }}
            null
        } else null
        parentPane.userClearanceState.route.apply {
            if (matchingIndex == null) {
                legs.add(Route.DiscontinuityLeg(Route.Leg.APP_TRANS))
                if (trans != null) extendRouteCopy(trans)
            } else {
                // Add the legs after the transition waypoint to hidden legs
                for (i in matchingIndex + 1 until legs.size) parentPane.userClearanceState.hiddenLegs.legs.add(legs[i])
                if (matchingIndex <= legs.size - 2) legs.removeRange(matchingIndex + 1, legs.size - 1) // Remove them from the current route if waypoints exist after
                if (trans != null) {
                    extendRouteCopy(trans)
                    // Remove the duplicate waypoint
                    legs.removeIndex(matchingIndex + 1)
                }
            }
            extendRouteCopy(app.routeLegs)
            extendRouteCopy(app.missedLegs)
            updateRouteTable(parentPane.userClearanceState.route)
        }
    }

    /**
     * Remove the current approach, approach transition, missed approach legs from route, and adds all the hidden route
     * legs back
     *
     * Also clears the hidden leg route
     * @param route the route to remove approach legs from
     * @param hiddenLegs the route containing currently hidden legs to add back to the route
     * */
    private fun removeApproachLegs(route: Route, hiddenLegs: Route) {
        for (i in route.legs.size - 1 downTo 0) {
            if (route.legs[i].phase == Route.Leg.NORMAL) break // Once a normal leg is encountered, break from loop
            route.legs.removeIndex(i)
        }
        route.extendRoute(hiddenLegs)
        hiddenLegs.legs.clear()
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

    /**
     * Sets the disabled status of the approach and transition select boxes
     *
     * Will also clear any items in the select boxes' lists if disabled
     * @param disabled whether to disable the select boxes
     * */
    private fun updateAppTransBoxesDisabled(disabled: Boolean) {
        appSelectBox.isDisabled = disabled
        // Transition select box will also be disabled if no approach is cleared
        transitionSelectBox.isDisabled = disabled || appSelectBox.selection.isEmpty || appSelectBox.selected == "Approach"
        if (disabled) {
            // Clear all items if disabled
            appSelectBox.items = GdxArray<String>().apply { add("Approach") }
            transitionSelectBox.items = GdxArray<String>().apply { add("Via ...") }
        }
    }
}