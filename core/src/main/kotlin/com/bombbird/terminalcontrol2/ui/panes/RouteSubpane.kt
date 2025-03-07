package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.scene2d.*

class RouteSubpane {
    private lateinit var parentControlPane: ControlPane
    private val parentPane: UIPane
        get() = parentControlPane.parentPane
    private var modificationInProgress: Boolean
        get() = parentControlPane.modificationInProgress
        set(value) {
            parentControlPane.modificationInProgress = value
        }
    private var directLeg: Route.Leg?
        get() = parentControlPane.directLeg
        set(value) {
            parentControlPane.directLeg = value
        }

    private lateinit var routeTable: KTableWidget
    private lateinit var routeLegsTable: KTableWidget
    private val directButtonArray = GdxArray<KTextButton>(10)
    private lateinit var goAroundButton: KTextButton

    var isVisible: Boolean
        get() = routeTable.isVisible
        set(value) {
            routeTable.isVisible = value
        }
    val actor: Actor
        get() = routeTable

    /**
     * @param controlPane the parent [ControlPane] to refer to
     * @param widget the widget to add this route table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the table
     * @param setToEditRoutePane is the function that will be run when the "Edit route" button is clicked
     * @return a [KTableWidget] used to contain the elements of the route sub-pane, which has been added to the [KWidget]
     */
    @Scene2dDsl
    fun routeTable(controlPane: ControlPane, widget: KWidget<Actor>, paneWidth: Float, setToEditRoutePane: () -> Unit): KTableWidget {
        parentControlPane = controlPane
        routeTable = widget.table {
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
                goAroundButton = textButton("Go\nAround", "ControlPaneSelectedSameTextColour").cell(growX = true, height = UI_HEIGHT * 0.15f).apply {
                    addChangeListener { _, _ ->
                        parentPane.userClearanceState.initiateGoAround = isChecked
                        style = Scene2DSkin.defaultSkin[
                            if (parentPane.userClearanceState.initiateGoAround == parentPane.clearanceState.initiateGoAround) "ControlPaneSelectedSameTextColour"
                            else "ControlPaneSelectedChanged", TextButtonStyle::class.java]
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                }
            }.cell(preferredWidth = 0.19f * paneWidth, padTop = 20f, align = Align.top)
            isVisible = false
        }
        return routeTable
    }

    /**
     * Updates the route list in [routeLegsTable]
     * @param route the route to display in the route pane; should be the aircraft's latest cleared route or user input route
     */
    fun updateRouteTable(route: Route) {
        routeLegsTable.clear()
        directButtonArray.clear()
        var firstDirectSet = false
        var firstAvailableLeg: Route.Leg? = null // The first leg that can be set as selected in case previous direct leg doesn't match any leg
        routeLegsTable.apply {
            var prevPhase: Byte? = null
            for (i in 0 until route.size) {
                route[i].also { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.let { wpt -> if (!wpt.legActive && firstDirectSet) return@also
                        when (wpt.turnDir) {
                            CommandTarget.TURN_LEFT -> "Turn left\n"
                            CommandTarget.TURN_RIGHT -> "Turn right\n"
                            else -> ""
                        } + CLIENT_SCREEN?.waypoints?.get(wpt.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName } ?:
                    (leg as? Route.VectorLeg)?.let { vec -> "${when (vec.turnDir) {
                        CommandTarget.TURN_LEFT -> "Left "
                        CommandTarget.TURN_RIGHT -> "Right "
                        else -> ""
                    }}HDG ${vec.heading}" } ?:
                    (leg as? Route.HoldLeg)?.wptId?.let { wptId -> "Hold ${
                        if (wptId >= 0) "at\n" + CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName else "here"
                    }" } ?:
                    (leg as? Route.DiscontinuityLeg)?.let {
                        if (prevPhase != leg.phase && leg.phase == Route.Leg.MISSED_APP) "Missed approach" else "Discontinuity"
                    } ?: (leg as? Route.InitClimbLeg)?.heading?.let { hdg -> "Climb on\nHDG $hdg" } ?: return@also
                    if (firstAvailableLeg == null) firstAvailableLeg = leg
                    val restrTriple = (leg as? Route.WaypointLeg)?.let { checkRestrChanged(parentPane.clearanceState.route, it) } ?:
                    (leg as? Route.InitClimbLeg)?.let { if (checkLegChanged(parentPane.clearanceState.route, it)) Triple(true, true, true)
                    else Triple(false, false, false)} ?:
                    Triple(false, false, false)
                    val legChanged = checkLegChanged(parentPane.clearanceState.route, leg)
                    prevPhase = leg.phase
                    val altRestrDisplay = (leg as? Route.WaypointLeg)?.let { wptLeg ->
                        var restr = wptLeg.maxAltFt?.toString() ?: ""
                        if (wptLeg.maxAltFt != wptLeg.minAltFt) restr += wptLeg.minAltFt?.toString()?.let { minAlt -> "${if (restr.isNotBlank()) "\n" else ""}$minAlt" } ?: ""
                        restr
                    } ?: (leg as? Route.InitClimbLeg)?.minAltFt?.let { minAlt -> "$minAlt" } ?: ""
                    val altRestrStyle = ((leg as? Route.WaypointLeg)?.let { wptLeg -> when {
                        wptLeg.minAltFt != null && wptLeg.maxAltFt != null -> "ControlPaneBothAltRestr"
                        wptLeg.minAltFt != null -> "ControlPaneBottomAltRestr"
                        wptLeg.maxAltFt != null -> "ControlPaneTopAltRestr"
                        else -> "ControlPaneRoute"
                    } + (if (altRestrDisplay.isNotBlank() && !wptLeg.altRestrActive) "Cancel" else "")
                    } ?: (leg as? Route.InitClimbLeg)?.let { "ControlPaneBottomAltRestr" } ?: "ControlPaneRoute") + if (restrTriple.first) "Changed" else ""
                    val spdRestr = (leg as? Route.WaypointLeg)?.maxSpdKt?.let { spd -> "${spd}kts" } ?: ""
                    val spdRestrStyle = (if ((leg as? Route.WaypointLeg)?.spdRestrActive == true) "ControlPaneRoute" else "ControlPaneSpdRestrCancel") + if (restrTriple.second) "Changed" else ""
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
                            // Only allow player to clear direct to this if it is a waypoint leg,
                            // and it does not go from non MISSED_APP to MISSED_APP phase
                            // otherwise undo the change
                            if ((leg !is Route.WaypointLeg || (route[0].phase != Route.Leg.MISSED_APP && leg.phase == Route.Leg.MISSED_APP))) {
                                isChecked = !isChecked
                                modificationInProgress = false
                                return@addChangeListener
                            }
                            if (!isChecked) isChecked = true
                            else {
                                val prevLegIndex = directLeg?.let {
                                    // Find the index of the previously selected leg
                                    var index: Int? = null // Additional variable for finding index as making prevLegIndex a var prevents smart cast in this changing closure below
                                    for (j in 0 until route.size) {
                                        if (compareLegEquality(it, route[j])) {
                                            index = j
                                            break
                                        }
                                    }
                                    index
                                }
                                for (j in 0 until directButtonArray.size) {
                                    // Uncheck all buttons except for this one
                                    if (j != i) directButtonArray[j].isChecked = false
                                    if (j >= route.size) break
                                    (route[j] as? Route.WaypointLeg)?.let {
                                        if (j < i) it.legActive = false // All legs before are no longer active
                                        else if (j == i) it.legActive = true // This leg is active
                                        else if (prevLegIndex != null && j <= prevLegIndex) it.legActive = true // If a leg was selected previously, set all legs from this to the previous leg as active
                                    }
                                }
                                directLeg = leg // Update the new direct leg to this
                                parentControlPane.updateAltSelectBoxChoices(parentPane.aircraftMaxAlt, parentPane.userClearanceState, false)
                            }
                            modificationInProgress = false
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    })
                    val showRestrDisplay = leg is Route.WaypointLeg || leg is Route.InitClimbLeg
                    label(legDisplay, "ControlPaneRoute${if (legChanged) "Changed" else ""}")
                        .apply { setAlignment(if (showRestrDisplay) Align.center else Align.left) }
                        .cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth, colspan = if (showRestrDisplay) null else 3, padLeft = if (showRestrDisplay) 0f else 15f)
                    if (showRestrDisplay) label(altRestrDisplay, altRestrStyle).apply { setAlignment(Align.center) }.cell(expandX = true, padLeft = 10f, padRight = 10f)
                    if (showRestrDisplay) label(spdRestr, spdRestrStyle).apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * parentPane.paneWidth)
                    row()
                }
            }
        }
        if (!firstDirectSet && route.size > 0 && directButtonArray.size > 0) {
            // No selected leg was found - possibly due reasons such as the first leg being skipped in edit route pane
            // Set selection to the first available leg found earlier (i.e. the first non-skipped leg)
            directLeg = firstAvailableLeg
            modificationInProgress = true
            directButtonArray[0].isChecked = true
            modificationInProgress = false
        }
    }

    /**
     * Updates all elements to reflect any differences between the user clearance state and the cleared clearance state
     * @param userClearanceState the user selected clearance state
     * @param clearanceState the currently cleared clearance state
     */
    fun updateChangedStates(userClearanceState: ClearanceState, clearanceState: ClearanceState) {
        goAroundButton.isChecked = userClearanceState.initiateGoAround
        goAroundButton.style = Scene2DSkin.defaultSkin[
            if (userClearanceState.initiateGoAround == clearanceState.initiateGoAround) "ControlPaneSelectedSameTextColour"
            else "ControlPaneSelectedChanged", TextButtonStyle::class.java]
    }

    /** Sets the go around button to visible if [goAround] is true, else hides it */
    fun setGoAroundButtonState(goAround: Boolean) {
        goAroundButton.isVisible = goAround
        if (!goAround) goAroundButton.isChecked = false
    }
}