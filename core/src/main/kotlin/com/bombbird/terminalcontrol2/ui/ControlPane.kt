package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.traffic.getAvailableApproaches
import com.bombbird.terminalcontrol2.utilities.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.get
import ktx.ashley.remove
import ktx.collections.GdxArray
import ktx.scene2d.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Helper object for UI pane's control pane */
class ControlPane {
    companion object {
        const val PANE_ROUTE: Byte = 0
        const val PANE_HOLD: Byte = 1
        const val PANE_VECTOR: Byte = 2

        const val ROUTE = "Route"
        const val HOLD = "Hold"
        const val VECTORS = "Vectors"
        const val EXPEDITE = "Expedite"
        const val ACKNOWLEDGE = "Acknowledge"
        const val HANDOVER = "Handover"
        const val TRANSMIT = "Transmit"
        const val UNDO_ALL = "Undo all"
    }
    lateinit var parentPane: UIPane

    private lateinit var lateralContainer: KContainer<Actor>
    private lateinit var routeModeButton: KTextButton
    private lateinit var holdModeButton: KTextButton
    private lateinit var vectorModeButton: KTextButton
    private lateinit var appSelectBox: KSelectBox<String>
    private lateinit var transitionSelectBox: KSelectBox<String>
    private lateinit var altSelectBox: KSelectBox<String>
    private lateinit var expediteButton: KTextButton
    private lateinit var spdSelectBox: KSelectBox<Short>

    private val routeSubpaneObj = RouteSubpane()
    private val holdSubpaneObj = HoldSubpane()
    private val vectorSubpaneObj = VectorSubpane()

    private lateinit var undoButton: KTextButton
    private lateinit var handoverAckButton: KTextButton
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
     */
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
                    routeModeButton = textButton(ROUTE, "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            if (!isChecked) {
                                isChecked = true
                                return@addChangeListener
                            }
                            setPaneLateralMode(UIPane.MODE_ROUTE)
                        }
                    }
                    holdModeButton = textButton(HOLD, "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            if (!isChecked) {
                                isChecked = true
                                return@addChangeListener
                            }
                            setPaneLateralMode(UIPane.MODE_HOLD)
                        }
                    }
                    vectorModeButton = textButton(VECTORS, "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            if (!isChecked) {
                                isChecked = true
                                return@addChangeListener
                            }
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
                            updateTransitionSelectBoxChoices(parentPane.aircraftArrivalArptId, selected, null)
                            val appName = if (selected == NO_APP_SELECTION) null else selected
                            parentPane.userClearanceState.clearedApp = appName
                            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedApp == parentPane.clearanceState.clearedApp) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            val transName = if (transitionSelectBox.selected == "$TRANS_PREFIX$NO_TRANS_SELECTION") null
                            else transitionSelectBox.selected.replace(TRANS_PREFIX, "")
                            transitionSelectBox.style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.clearedTrans == parentPane.clearanceState.clearedTrans) "ControlPane" else "ControlPaneChanged", SelectBoxStyle::class.java]
                            updateApproachRoute(parentPane.userClearanceState.route, parentPane.userClearanceState.hiddenLegs,
                                parentPane.aircraftArrivalArptId, appName, transName)
                            updateRouteTable(parentPane.userClearanceState.route)
                            updateAltSelectBoxChoices(parentPane.aircraftMaxAlt, parentPane.userClearanceState)
                            updateUndoTransmitButtonStates()
                        }
                        disallowDisabledClickThrough()
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
                            updateApproachRoute(parentPane.userClearanceState.route, parentPane.userClearanceState.hiddenLegs,
                                parentPane.aircraftArrivalArptId, appName, transName)
                            updateRouteTable(parentPane.userClearanceState.route)
                            updateAltSelectBoxChoices(parentPane.aircraftMaxAlt, parentPane.userClearanceState)
                            updateUndoTransmitButtonStates()
                        }
                        disallowDisabledClickThrough()
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
                    expediteButton = textButton(EXPEDITE, "ControlPaneSelected").cell(grow = true, preferredWidth = paneWidth * 0.26f).apply {
                        addChangeListener { _, _ ->
                            if (modificationInProgress) return@addChangeListener
                            parentPane.userClearanceState.expedite = isChecked
                            style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.expedite == parentPane.clearanceState.expedite) "ControlPaneSelected" else "ControlPaneSelectedChanged", TextButtonStyle::class.java]
                            updateUndoTransmitButtonStates()
                        }
                    }
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
                    undoButton = textButton(UNDO_ALL, "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ -> Gdx.app.postRunnable { parentPane.setSelectedAircraft(parentPane.selAircraft ?: return@postRunnable) }}
                    }
                    handoverAckButton = textButton(ACKNOWLEDGE, "ControlPaneButtonChanged").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        isVisible = false
                        addChangeListener { _, _ ->
                            Gdx.app.postRunnable {
                                parentPane.selAircraft?.let {
                                    it.entity.remove<ContactNotification>()
                                    it.entity[Datatag.mapper]?.let { datatag ->
                                        setDatatagFlash(datatag, it, false)
                                    }
                                }
                                if (text.toString() == HANDOVER) {
                                    parentPane.selAircraft?.entity?.let { ac ->
                                        val callsign = ac[AircraftInfo.mapper]?.icaoCallsign
                                        val newSector = ac[CanBeHandedOver.mapper]?.nextSector
                                        if (callsign != null && newSector != null) CLIENT_SCREEN?.sendAircraftHandOverRequest(callsign, newSector)
                                    }
                                }
                            }
                            isVisible = false
                        }
                    }
                    transmitButton = textButton(TRANSMIT, "ControlPaneButton").cell(grow = true, preferredWidth = paneWidth / 3).apply {
                        addChangeListener { _, _ -> CLIENT_SCREEN?.let { radarScreen -> parentPane.selAircraft?.let { aircraft ->
                            val leg1 = if (parentPane.clearanceState.route.size > 0) parentPane.clearanceState.route[0] else null
                            val leg2 = directLeg
                            val directChanged = if (leg1 == null && leg2 == null) false else if (leg1 == null || leg2 == null) true else !compareLegEquality(leg1, leg2)
                            // Remove any non waypoint legs before directLeg
                            leg2?.also {
                                var index = 0
                                parentPane.userClearanceState.route.apply { while (index < size) {
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
                            if (checkClearanceEquality(parentPane.userClearanceState, parentPane.clearanceState, !parentPane.appTrackCaptured) && !directChanged) return@addChangeListener // No need to update anything if no change to clearance
                            radarScreen.sendAircraftControlStateClearance(aircraft.entity[AircraftInfo.mapper]?.icaoCallsign ?: return@addChangeListener, parentPane.userClearanceState)
                            // After sending the request, remove any non-active waypoint legs before the direct leg
                            leg2?.also {
                                var index = 0
                                parentPane.userClearanceState.route.apply { while (index < size) {
                                    val legToCheck = get(index)
                                    if (compareLegEquality(legToCheck, leg2)) return@also // Leg reached
                                    if (legToCheck is Route.WaypointLeg && !legToCheck.legActive) {
                                        // Remove any non-active waypoint legs along the way
                                        removeIndex(index)
                                        index--
                                    }
                                    index++
                                }}
                            }
                            Gdx.app.postRunnable {
                                aircraft.entity[ClearanceAct.mapper]?.actingClearance?.clearanceState?.updateUIClearanceState(parentPane.userClearanceState)
                                parentPane.updateSelectedAircraft(aircraft)
                                parentPane.selAircraft?.let {
                                    it.entity.remove<ContactNotification>()
                                    it.entity[Datatag.mapper]?.let { datatag ->
                                        setDatatagFlash(datatag, it, false)
                                    }
                                }
                                // Manually hide acknowledge button since the removal of ContactNotification is not immediate
                                if (handoverAckButton.text.toString() == ACKNOWLEDGE) handoverAckButton.isVisible = false
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
     * Gets the default pane the control pane should be on given the current clearance state
     * @param route the clearance route
     * @param vectorHdg the clearance vector
     * @param appTrackCaptured whether the aircraft has captured the approach track
     */
    private fun getDefaultPaneForClearanceState(route: Route, vectorHdg: Short?, appTrackCaptured: Boolean): Byte {
        // Vector mode active or localizer/visual track captured in vector mode (no route legs)
        if (vectorHdg != null || (route.size == 0 && appTrackCaptured)) return PANE_VECTOR
        route.apply {
            // Hold mode active when the current leg is a hold leg, or when the aircraft is flying towards the waypoint
            // it is cleared to hold at
            if ((size >= 1 && get(0) is Route.HoldLeg) ||
                (size >= 2 && get(0) is Route.WaypointLeg && get(1) is Route.HoldLeg &&
                        (get(0) as? Route.WaypointLeg)?.wptId == (get(1) as? Route.HoldLeg)?.wptId)) return PANE_HOLD
            // Otherwise, use route mode
            return PANE_ROUTE
        }
    }

    /**
     * Updates the style of the clearance mode buttons depending on the aircraft's cleared navigation state
     * @param route the aircraft's latest cleared route
     * @param vectorHdg the aircraft's latest cleared vector heading; is null if aircraft is not being vectored
     * @param ignoreUserPane whether to ignore the pane the player is currently on and forcefully set the pane
     * hold waypoint is selected
     */
    fun updateClearanceMode(route: Route, vectorHdg: Short?, appTrackCaptured: Boolean, ignoreUserPane: Boolean) {
        val currPane = when {
            routeModeButton.isChecked -> PANE_ROUTE
            holdModeButton.isChecked -> PANE_HOLD
            vectorModeButton.isChecked -> PANE_VECTOR
            else -> -1
        }
        val defaultClearancePane = if (ignoreUserPane) -1
        else getDefaultPaneForClearanceState(parentPane.clearanceState.route, parentPane.clearanceState.vectorHdg, appTrackCaptured)
        if (!ignoreUserPane && defaultClearancePane != currPane) return setPaneLateralMode(currPane)
        when (getDefaultPaneForClearanceState(route, vectorHdg, appTrackCaptured)) {
            PANE_VECTOR -> {
                routeModeButton.isChecked = false
                holdModeButton.isChecked = false
                vectorModeButton.isChecked = true
                setPaneLateralMode(UIPane.MODE_VECTOR)
            }
            PANE_HOLD -> {
                routeModeButton.isChecked = false
                holdModeButton.isChecked = true
                vectorModeButton.isChecked = false
                setPaneLateralMode(UIPane.MODE_HOLD)
            }
            PANE_ROUTE -> {
                routeModeButton.isChecked = true
                holdModeButton.isChecked = false
                vectorModeButton.isChecked = false
                setPaneLateralMode(UIPane.MODE_ROUTE)
            }
        }
    }

    /**
     * Updates [ControlPane.appSelectBox] and [ControlPane.transitionSelectBox] for the provided arrival airport ID
     *
     * If the ID provided is null, the boxes will be disabled
     * @param airportId the airport to refer to when selecting approaches that can be cleared
     */
    private fun updateApproachSelectBoxChoices(airportId: Byte?) {
        modificationInProgress = true
        CLIENT_SCREEN?.airports?.get(airportId)?.entity?.let { arpt ->
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
     */
    private fun updateTransitionSelectBoxChoices(airportId: Byte?, selectedApp: String, selectedTrans: String?) {
        modificationInProgress = true
        transitionSelectBox.apply {
            CLIENT_SCREEN?.airports?.get(airportId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(selectedApp)?.let { app ->
                var transFound = false
                items = GdxArray<String>().also { array ->
                    app.transitions.keys().map { "$TRANS_PREFIX$it" }.forEach {
                        if (it == parentPane.userClearanceState.clearedTrans) transFound = true
                        array.add(it)
                    }
                }
                if (selectedTrans == null) {
                    if (!selection.isEmpty && !transFound) parentPane.userClearanceState.clearedTrans = if (selected == "$TRANS_PREFIX$NO_TRANS_SELECTION") null
                    else selected.replace(TRANS_PREFIX, "")
                }
                isDisabled = false
            } ?: run {
                isDisabled = true
                items = GdxArray<String>().apply { add("$TRANS_PREFIX$NO_TRANS_SELECTION") }
                parentPane.userClearanceState.clearedTrans = null
            }
        }
        val transName = parentPane.userClearanceState.clearedTrans
        transitionSelectBox.selected = if (transName != null) "$TRANS_PREFIX$transName" else "$TRANS_PREFIX$NO_TRANS_SELECTION"
        modificationInProgress = false
    }

    /**
     * Updates [ControlPane.altSelectBox] with the appropriate altitude choices for [MIN_ALT], [MAX_ALT] and [aircraftMaxAlt]
     *
     * If the select box was not previously empty, this will also update the user selected cleared altitude to match the
     * selection in the box in case of any changes made due to the updated list of possible selections
     * @param aircraftMaxAlt the maximum altitude the aircraft can fly at, or null if none provided in which it will be
     * @param userClearanceState the user selected clearance state
     * ignored
     */
    fun updateAltSelectBoxChoices(aircraftMaxAlt: Int?, userClearanceState: ClearanceState) {
        /**
         * Checks the input altitude according to transition altitude and transition level parsing it into the FLXXX format
         * if necessary, before adding it to the string array
         * @param alt the altitude value to add
         * @param array the string [GdxArray] to add the value into
         */
        fun checkAltAndAddToArray(alt: Int, array: GdxArray<String>) {
            if (alt > TRANS_ALT && alt < TRANS_LVL * 100) return
            if (alt <= TRANS_ALT) array.add(alt.toString())
            else if (alt >= TRANS_LVL * 100) array.add("FL${alt / 100}")
        }

        /**
         * Selects the appropriate selection in the altitude box for the input altitude value, accounting for altitudes
         * above the transition altitude being represented in the FLXXX format
         * @param alt the altitude value to set
         */
        fun setToAltValue(alt: Int) {
            if (alt <= TRANS_ALT) altSelectBox.selected = alt.toString()
            else altSelectBox.selected = "FL${alt / 100}"
        }

        val effectiveMinAlt: Int
        val effectiveMaxAlt: Int
        if (userClearanceState.clearedApp != null && hasOnlyWaypointLegsTillMissed(directLeg, userClearanceState.route)) {
            // Check if aircraft is cleared for the approach with no interruptions (i.e. no discontinuity, vector or hold legs)
            userClearanceState.route.apply {
                // Set to the FAF altitude (i.e. the minimum altitude restriction of the last waypoint)
                val faf = getFafAltitude(userClearanceState.route) ?: run {
                    // If aircraft has flown past last waypoint, use the currently cleared altitude
                    userClearanceState.clearedAlt
                }
                effectiveMinAlt = faf
                effectiveMaxAlt = faf
            }
        } else {
            val nextHold = getNextHoldLeg(userClearanceState.route)
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
            userClearanceState.clearedAlt = if (it.contains("FL")) it.replace("FL", "").toInt() * 100
            else it.toInt()
        }
        modificationInProgress = false
    }

    /**
     * Updates all elements to reflect any differences between the user clearance state and the cleared clearance state
     * @param userClearanceState the user selected clearance state
     * @param clearanceState the currently cleared clearance state
     */
    fun updateChangedStates(userClearanceState: ClearanceState, clearanceState: ClearanceState) {
        altSelectBox.style = Scene2DSkin.defaultSkin[if (userClearanceState.clearedAlt == clearanceState.clearedAlt) "ControlPane"
        else "ControlPaneChanged", SelectBoxStyle::class.java]
        spdSelectBox.style = Scene2DSkin.defaultSkin[if (userClearanceState.clearedIas == clearanceState.clearedIas) "ControlPane"
        else "ControlPaneChanged", SelectBoxStyle::class.java]
        appSelectBox.style = Scene2DSkin.defaultSkin[if (userClearanceState.clearedApp == clearanceState.clearedApp) "ControlPane"
        else "ControlPaneChanged", SelectBoxStyle::class.java]
        transitionSelectBox.style = Scene2DSkin.defaultSkin[if (userClearanceState.clearedTrans == clearanceState.clearedTrans) "ControlPane"
        else "ControlPaneChanged", SelectBoxStyle::class.java]
        expediteButton.style = Scene2DSkin.defaultSkin[if (parentPane.userClearanceState.expedite == parentPane.clearanceState.expedite) "ControlPaneSelected"
        else "ControlPaneSelectedChanged", TextButtonStyle::class.java]
    }

    /** Clears all the choices in the altitude select box; should be used when deselecting an aircraft */
    private fun clearAltSelectBoxChoices() {
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
     */
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

        clearAltSelectBoxChoices()
        updateAltSelectBoxChoices(parentPane.aircraftMaxAlt, parentPane.userClearanceState)
        updateApproachSelectBoxChoices(parentPane.aircraftArrivalArptId)

        altSelectBox.selected = if (clearedAlt >= TRANS_LVL * 100) "FL${clearedAlt / 100}" else clearedAlt.toString()
        spdSelectBox.selected = clearedSpd
        modificationInProgress = true
        appSelectBox.selected = appName ?: NO_APP_SELECTION
        modificationInProgress = false
        if (appName != null) updateTransitionSelectBoxChoices(parentPane.aircraftArrivalArptId, appName, transName) else {
            transitionSelectBox.isDisabled = true
            transitionSelectBox.items = GdxArray<String>().apply { add("$TRANS_PREFIX$NO_TRANS_SELECTION") }
        }
    }

    /**
     * Updates the cleared expedite status in the pane
     * @param expedite the expedite status to set
     */
    fun updateExpediteClearance(expedite: Boolean) {
        modificationInProgress = true
        expediteButton.isChecked = expedite
        modificationInProgress = false
    }

    /**
     * Updates the lateral mode button checked status as well as the pane being displayed
     *
     * Called when user taps on a lateral mode button
     * @param mode the pane mode to show
     */
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
                // altSelectBox.selected = parentPane.userClearanceState.clearedAlt.let { if (it > TRANS_ALT) "FL${it / 100}" else it.toString() }
                updateAltSelectBoxChoices(parentPane.aircraftMaxAlt, parentPane.userClearanceState)
                parentPane.userClearanceState.vectorHdg = null
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
                // If the aircraft has not been cleared for vector in acting and user clearance, and there isn't an after
                // waypoint vector leg selected, set the user clearance vector heading to the heading of the aircraft
                if (parentPane.userClearanceState.vectorHdg == null && parentPane.clearanceState.vectorHdg == null && afterWptHdgLeg == null)
                    parentPane.userClearanceState.vectorHdg = parentPane.selAircraft?.entity?.get(CommandTarget.mapper)?.targetHdgDeg?.roundToInt()?.toShort() ?: 360
                else if (parentPane.userClearanceState.vectorHdg == null) parentPane.userClearanceState.vectorHdg = parentPane.clearanceState.vectorHdg
                vectorSubpaneObj.setHdgElementsDisabled(parentPane.appTrackCaptured)
                vectorSubpaneObj.updateVectorTable(parentPane.userClearanceState.route, parentPane.userClearanceState.vectorHdg, parentPane.userClearanceState.vectorTurnDir)
                updateUndoTransmitButtonStates()
            }
            else -> FileLog.info("UIPane", "Unknown lateral mode $mode")
        }
        modificationInProgress = false
    }

    /**
     * Updates the route table belonging to the [routeSubpaneObj] of this control pane with the input route
     * @param route the route to set the route table to display
     */
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
     */
    fun updateUndoTransmitButtonStates() {
        val leg1 = parentPane.clearanceState.route.let {
            var currDirectLeg: Route.Leg? = null // Additional variable for finding current direct leg as making leg1 a var prevents smart cast in this changing closure below
            for (i in 0 until it.size) {
                it[i].apply {
                    if (this is Route.WaypointLeg && !this.legActive) return@apply // Do not choose a skipped waypoint leg as the current direct
                    currDirectLeg = this
                }
                if (currDirectLeg != null) break
            }
            currDirectLeg
        }
        val leg2 = directLeg ?: if (parentPane.userClearanceState.route.size > 0) parentPane.userClearanceState.route[0] else null
        val defaultDirect = if (parentPane.clearanceState.route.size > 0) parentPane.clearanceState.route[0] else null
        calculateRouteSegments(parentPane.userClearanceState.route, parentPane.userClearanceRouteSegments, directLeg)
        parentPane.selAircraft?.entity?.get(RouteSegment.mapper)?.segments?.let { segments ->
            calculateRouteSegments(parentPane.clearanceState.route, segments, defaultDirect)
            checkRouteSegmentChanged(segments, parentPane.userClearanceRouteSegments)
        }
        // Direct changed if the 2 legs are not equal to each other, unless both are hold legs with wptId < 0 (i.e. both are custom hold legs)
        val directChanged = if ((leg1 == null && leg2 == null) || leg2 is Route.DiscontinuityLeg ||
            (leg1 is Route.HoldLeg && leg2 is Route.HoldLeg && leg1.wptId < 0 && leg2.wptId < 0)) false
        else if (leg1 == null || leg2 == null) true else !compareLegEquality(leg1, leg2)
        if (checkClearanceEquality(parentPane.clearanceState, parentPane.userClearanceState, !parentPane.appTrackCaptured) && !directChanged) setUndoTransmitButtonsUnchanged()
        else setUndoTransmitButtonsChanged()
    }

    /**
     * Updates the state of the handover/acknowledge button; if both are false the button will be hidden
     * @param handover whether the button should display handover and perform handover functionality when clicked
     * @param acknowledge whether the button should display acknowledge and perform acknowledge functionality when
     * clicked; will be overridden by [handover] if it is true
     */
    fun updateHandoverAcknowledgeButton(handover: Boolean, acknowledge: Boolean) {
        handoverAckButton.isVisible = true
        if (handover) handoverAckButton.setText(HANDOVER)
        else if (acknowledge) handoverAckButton.setText(ACKNOWLEDGE)
        else handoverAckButton.isVisible = false
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
     */
    private fun updateAppTransBoxesDisabled(disabled: Boolean) {
        appSelectBox.isDisabled = disabled
        // Transition select box will also be disabled if no approach is cleared
        transitionSelectBox.isDisabled = disabled || appSelectBox.selection.isEmpty || appSelectBox.selected == "Approach"
        if (disabled) {
            // Clear all items if disabled
            appSelectBox.items = GdxArray<String>().apply { add(NO_APP_SELECTION) }
            transitionSelectBox.items = GdxArray<String>().apply { add("$TRANS_PREFIX$NO_TRANS_SELECTION") }
        }
    }
}