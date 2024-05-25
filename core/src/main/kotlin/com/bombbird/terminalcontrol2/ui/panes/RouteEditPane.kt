package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.RouteSegment
import com.bombbird.terminalcontrol2.components.STARChildren
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.CHANGED_YELLOW
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.*
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.disallowDisabledClickThrough
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import ktx.ashley.get
import ktx.collections.toGdxArray
import ktx.scene2d.*

/** Helper object for UI pane's route edit pane */
class RouteEditPane {
    private lateinit var parentPane: UIPane

    private lateinit var routeEditTable: KTableWidget
    private lateinit var changeStarBox: KSelectBox<String>

    private lateinit var undoButton: KTextButton
    private lateinit var confirmButton: KTextButton

    /**
     * @param uiPane the parent UI pane this control pane belongs to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the container
     * @param confirmFunction a function that will be called when the "Confirm" button is pressed
     * @return a [KContainer] used to contain a table with the elements of the route edit pane, which has been added to the [KWidget]
     */
    @Scene2dDsl
    fun routeEditPane(uiPane: UIPane, widget: KWidget<Actor>, paneWidth: Float, confirmFunction: () -> Unit): KContainer<Actor> {
        parentPane = uiPane
        return widget.container {
            fill()
            setSize(paneWidth, UI_HEIGHT)
            // debugAll()
            table {
                table {
                    textButton("Cancel all\nAlt restr.", "ControlPaneButton").cell(grow = true, preferredWidth = 0.3f * paneWidth).addChangeListener { _, _ ->
                        val route = parentPane.userClearanceState.route
                        for (i in 0 until route.size) (route[i] as? Route.WaypointLeg)?.apply { if (minAltFt != null || maxAltFt != null) altRestrActive = false }
                        updateEditRouteTable(route)
                        updateRouteLegSegments(parentPane.userClearanceState.route)
                        updateUndoTransmitButtonStates()
                    }
                    textButton("Cancel all\nSpd restr.", "ControlPaneButton").cell(grow = true, preferredWidth = 0.3f * paneWidth).addChangeListener { _, _ ->
                        val route = parentPane.userClearanceState.route
                        for (i in 0 until route.size) (route[i] as? Route.WaypointLeg)?.apply { if (maxSpdKt != null) spdRestrActive = false }
                        parentPane.userClearanceState.cancelLastMaxSpd = true
                        updateEditRouteTable(route)
                        updateRouteLegSegments(parentPane.userClearanceState.route)
                        updateUndoTransmitButtonStates()
                    }
                    changeStarBox = selectBox<String>("ControlPane") {
                        setItems("Change STAR")
                        setAlignment(Align.center)
                        list.alignment = Align.center
                        disallowDisabledClickThrough()
                        addChangeListener { _, _ ->
                            if (selected == "Change STAR") return@addChangeListener
                            if (selected == parentPane.userClearanceState.routePrimaryName) return@addChangeListener
                            updateNewStarSelection(parentPane.userClearanceState.route, parentPane.userClearanceState.hiddenLegs,
                                parentPane.userClearanceState.clearedApp, parentPane.userClearanceState.clearedTrans,
                                parentPane.aircraftArrivalArptId, selected)
                            updateEditRouteTable(parentPane.userClearanceState.route)
                            updateRouteLegSegments(parentPane.userClearanceState.route)
                            updateUndoTransmitButtonStates()
                            parentPane.updateWaypointDisplay()
                        }
                    }.cell(grow = true, preferredWidth = 0.4f * paneWidth)
                }.cell(growX = true, height = 0.1f * UI_HEIGHT)
                row()
                scrollPane("ControlPaneRoute") {
                    routeEditTable = table {
                        // debugAll()
                        align(Align.top)
                    }
                    setOverscroll(false, false)
                    removeMouseScrollListeners()
                }.cell(preferredWidth = paneWidth, preferredHeight = 0.8f * UI_HEIGHT - 40f, grow = true, padTop = 20f, padBottom = 20f, align = Align.top)
                row()
                table {
                    undoButton = textButton("Undo", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).apply {
                        addChangeListener { _, _ ->
                            // Reset the user clearance route to that of the default clearance
                            parentPane.userClearanceState.route.setToRouteCopy(parentPane.clearanceState.route)
                            parentPane.userClearanceState.hiddenLegs.setToRouteCopy(parentPane.clearanceState.hiddenLegs)
                            parentPane.userClearanceState.cancelLastMaxSpd = parentPane.clearanceState.cancelLastMaxSpd
                            changeStarBox.selected = "Change STAR"
                            updateEditRouteTable(parentPane.userClearanceState.route)
                            updateRouteLegSegments(parentPane.userClearanceState.route)
                            setUndoConfirmButtonsUnchanged()
                            parentPane.updateWaypointDisplay()
                        }
                    }
                    confirmButton = textButton("Confirm", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).apply {
                        addChangeListener { _, _ ->
                            confirmFunction()
                        }
                    }
                }.cell(growX = true, height = 0.1f * UI_HEIGHT)
            }
            isVisible = false
            setSize(paneWidth, UI_HEIGHT)
        }
    }

    /**
     * Updates the route list in [routeEditTable] (Edit route pane)
     * @param route the route to display in the route pane; should be the aircraft's latest cleared route
     */
    fun updateEditRouteTable(route: Route) {
        routeEditTable.clear()
        routeEditTable.apply {
            var prevPhase: Byte? = null
            var lastWptLegIndex = -1
            for (i in route.size - 1 downTo 0) if (route[i] is Route.WaypointLeg) {
                lastWptLegIndex = i
                break
            }
            val lastMaxSpdRestr = parentPane.lastMaxSpdKt
            lastMaxSpdRestr?.let {
                val changed = parentPane.clearanceState.cancelLastMaxSpd != parentPane.userClearanceState.cancelLastMaxSpd
                label("Current", "ControlPaneRoute${if (changed) "Changed" else ""}").apply {
                    setAlignment(Align.center)
                }.cell(growX = true, height = 0.125f * UI_HEIGHT, padLeft = 10f, padRight = 10f)
                textButton("", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT)
                textButton("${it}kts", "ControlPaneRestr${if (changed) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                    isChecked = parentPane.clearanceState.cancelLastMaxSpd
                    addChangeListener { _, _ ->
                        parentPane.userClearanceState.cancelLastMaxSpd = isChecked
                        style = toggleTextColor(style)
                        updateUndoTransmitButtonStates()
                    }
                }
                row()
            }
            for (i in 0 until route.size) {
                route[i].let { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.let { wpt ->
                        when (wpt.turnDir) {
                            CommandTarget.TURN_LEFT -> "Turn left\n"
                            CommandTarget.TURN_RIGHT -> "Turn right\n"
                            else -> ""
                        } + CLIENT_SCREEN?.waypoints?.get(wpt.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
                    } ?:
                    (leg as? Route.VectorLeg)?.let { vec -> "${when (vec.turnDir) {
                        CommandTarget.TURN_LEFT -> "Left "
                        CommandTarget.TURN_RIGHT -> "Right "
                        else -> ""
                    }}HDG ${vec.heading}" } ?:
                    (leg as? Route.HoldLeg)?.wptId?.let {
                            wptId -> "Hold ${
                                if (wptId >= 0) "at\n" + CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName else "here"
                            }"
                    } ?:
                    (leg as? Route.DiscontinuityLeg)?.let { "Discontinuity" } ?:
                    (leg as? Route.InitClimbLeg)?.heading?.let { hdg -> "Climb on\nHDG $hdg" } ?: return@let
                    val altRestr = (leg as? Route.WaypointLeg)?.let { wptLeg ->
                        if (wptLeg.minAltFt == wptLeg.maxAltFt) wptLeg.minAltFt?.let { "$it" } ?: ""
                        else (wptLeg.maxAltFt?.let { "${it}B" } ?: "") + (wptLeg.minAltFt?.let { "${it}A" } ?: "")
                    } ?: (leg as? Route.InitClimbLeg)?.minAltFt?.let { minAlt -> "$minAlt" } ?: ""
                    val spdRestr = (leg as? Route.WaypointLeg)?.maxSpdKt?.let { spd -> "${spd}kts" } ?: ""
                    val skipText = when (leg) {
                        is Route.WaypointLeg -> "SKIP"
                        else -> "REMOVE"
                    }
                    val legChanged = checkLegChanged(parentPane.clearanceState.route, leg)
                    if (prevPhase != leg.phase && leg.phase == Route.Leg.MISSED_APP && leg is Route.DiscontinuityLeg) {
                        label("Missed approach", "ControlPaneRoute${if (legChanged) "Changed" else ""}")
                            .cell(colspan = 4, padLeft = 10f, padRight = 10f, preferredHeight = 0.1f * UI_HEIGHT)
                        row()
                        return@let // Continue to next leg once the missed approach discontinuity leg has been determined
                    }
                    prevPhase = leg.phase
                    val restrTriple = (leg as? Route.WaypointLeg)?.let { checkRestrChanged(parentPane.clearanceState.route, it) } ?: Triple(false, false, false)
                    val altRestrChanged = legChanged || restrTriple.first
                    val spdRestrChanged = legChanged || restrTriple.second
                    val skippedChanged = restrTriple.third
                    val showRestrDisplay = leg is Route.WaypointLeg || leg is Route.InitClimbLeg
                    val legLabel = label(legDisplay, "ControlPaneRoute${if (legChanged) "Changed" else ""}")
                        .apply { setAlignment(if (showRestrDisplay) Align.center else Align.left) }
                        .cell(growX = true, height = 0.125f * UI_HEIGHT, padLeft = if (showRestrDisplay) 10f else 25f, padRight = 10f, colspan = if (showRestrDisplay) null else 3)
                    if (showRestrDisplay) textButton(altRestr, "ControlPaneRestr${if (altRestrChanged) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        isChecked = (leg as? Route.WaypointLeg)?.altRestrActive == false
                        if (altRestr.isNotBlank()) addChangeListener { _, _ -> (leg as? Route.WaypointLeg)?.let {
                            it.altRestrActive = !isChecked
                            style = toggleTextColor(style)
                            updateUndoTransmitButtonStates()
                        } ?: run { isChecked = false } }
                    }
                    if (showRestrDisplay) textButton(spdRestr, "ControlPaneRestr${if (spdRestrChanged) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        isChecked = (leg as? Route.WaypointLeg)?.spdRestrActive == false
                        if (spdRestr.isNotBlank()) addChangeListener { _, _ -> (leg as? Route.WaypointLeg)?.let {
                            it.spdRestrActive = !isChecked
                            style = toggleTextColor(style)
                            updateUndoTransmitButtonStates()
                        } ?: run { isChecked = false } }
                    }
                    if (i < lastWptLegIndex || (lastWptLegIndex == -1 && i < route.size - 1)) textButton(skipText, "ControlPaneSelected${if (skippedChanged) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        if ((leg as? Route.WaypointLeg)?.legActive == false) isChecked = true
                        addChangeListener { _, _ -> Gdx.app.postRunnable {
                            // Set skipped status for waypoint legs
                            (leg as? Route.WaypointLeg)?.let {
                                val legIndex = route.indexOf(it)
                                it.legActive = !isChecked
                                style = Scene2DSkin.defaultSkin[if (style.fontColor == Color.WHITE || style.fontColor == CHANGED_YELLOW) "ControlPaneSelectedChanged" else "ControlPaneSelected", TextButtonStyle::class.java]
                                // Remove subsequent leg if it is a vector/hold/init climb
                                if (route.size > legIndex + 1) route[legIndex + 1].let { nextLeg ->
                                    if (nextLeg is Route.HoldLeg || nextLeg is Route.VectorLeg || nextLeg is Route.InitClimbLeg) {
                                        route.removeIndex(legIndex + 1)
                                        updateEditRouteTable(route)
                                    }
                                }
                            } ?: run {
                                // Remove the leg if it is a hold/vector/init climb/discontinuity
                                legLabel.remove()
                                remove()
                                route.removeValue(leg)
                            }
                            updateRouteLegSegments(parentPane.userClearanceState.route)
                            updateUndoTransmitButtonStates()
                        }}
                    }
                    row()
                }
            }
        }
    }

    /**
     * Updates the options available in the Change STAR select box
     * @param arptId the ID of the airport to get the STARs for
     */
    fun updateChangeStarOptions(arptId: Byte?) {
        val arptStars = GAME.gameClientScreen?.airports?.get(arptId)?.entity?.get(STARChildren.mapper)?.starMap
        if (arptStars == null) {
            changeStarBox.setItems("Change STAR")
            return
        }
        val sortedStars = arptStars.map { it.key }.sorted().toGdxArray()
        sortedStars.insert(0, "Change STAR")
        changeStarBox.items = sortedStars
        changeStarBox.selectedIndex = 0
    }

    /**
     * Updates the route given the newly chosen STAR; all current legs will be removed and the new STAR legs added, and
     * the approach and/or transition legs will be re-added based on the current selection
     *
     * The route will start with a discontinuity
     * @param route the route to edit
     * @param hiddenLegs the clearance's hidden legs
     * @param clearedApp the cleared approach name
     * @param clearedTrans the cleared transition name
     * @param arptId the airport ID to get the new STAR from
     * @param newStar the name of the new STAR to clear
     */
    private fun updateNewStarSelection(route: Route, hiddenLegs: Route, clearedApp: String?, clearedTrans: String?, arptId: Byte?, newStar: String) {
        if (newStar == "Change STAR" || arptId == null) return
        val star = CLIENT_SCREEN?.airports?.get(arptId)?.entity?.get(STARChildren.mapper)?.starMap?.get(newStar) ?: return
        parentPane.userClearanceState.routePrimaryName = newStar
        route.clear()
        hiddenLegs.clear()
        route.add(Route.DiscontinuityLeg())
        route.extendRouteCopy(star.routeLegs)
        updateApproachRoute(route, hiddenLegs, arptId, clearedApp, clearedTrans)
    }

    /**
     * Updates the route leg segments and compares differences in order for changes to be reflected on the radar screen
     * @param route the route
     */
    private fun updateRouteLegSegments(route: Route) {
        calculateRouteSegments(route, parentPane.userClearanceRouteSegments, if (route.size > 0) route[0] else null)
        parentPane.selAircraft?.entity?.get(RouteSegment.mapper)?.segments?.let { segments ->
            checkRouteSegmentChanged(segments, parentPane.userClearanceRouteSegments)
        }
    }

    /**
     * Toggles between the unchanged and changed font colours for the waypoint leg restriction text buttons
     * @param style the [TextButtonStyle] to update
     */
    private fun toggleTextColor(style: TextButtonStyle): TextButtonStyle {
        return Scene2DSkin.defaultSkin[if (style.fontColor == Color.WHITE) "ControlPaneRestrChanged" else "ControlPaneRestr", TextButtonStyle::class.java]
    }

    /** Sets the style of the Undo and Confirm buttons to that of when the route is changed */
    private fun setUndoConfirmButtonsChanged() {
        val newStyle = Scene2DSkin.defaultSkin["ControlPaneButtonChanged", TextButtonStyle::class.java]
        confirmButton.style = newStyle
        undoButton.style = newStyle
    }

    /** Sets the style of the Undo and Confirm buttons to that of when the route is unchanged */
    private fun setUndoConfirmButtonsUnchanged() {
        val newStyle = Scene2DSkin.defaultSkin["ControlPaneButton", TextButtonStyle::class.java]
        confirmButton.style = newStyle
        undoButton.style = newStyle
    }

    /**
     * Updates the appropriate changed/unchanged button styles for the Undo and Confirm buttons depending on the current
     * state of [Route] in the UI pane's clearance states
     */
    fun updateUndoTransmitButtonStates() {
        if (checkRouteEqualityStrict(parentPane.clearanceState.route, parentPane.userClearanceState.route)
            && parentPane.userClearanceState.cancelLastMaxSpd == parentPane.clearanceState.cancelLastMaxSpd)
            setUndoConfirmButtonsUnchanged()
        else setUndoConfirmButtonsChanged()
    }

    /**
     * Sets whether the "Change STAR" select box is disabled
     * @param disabled whether to disable the select box
     */
    fun setChangeStarDisabled(disabled: Boolean) {
        changeStarBox.isDisabled = disabled
    }
}
