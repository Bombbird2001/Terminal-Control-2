package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.addChangeListener
import com.bombbird.terminalcontrol2.utilities.checkLegChanged
import com.bombbird.terminalcontrol2.utilities.checkRestrChanged
import com.bombbird.terminalcontrol2.utilities.removeMouseScrollListeners
import ktx.ashley.get
import ktx.collections.toGdxArray
import ktx.scene2d.*

/** Helper object for UI pane's route edit pane */
class RouteEditPane {
    private lateinit var parentPane: UIPane

    private lateinit var routeEditTable: KTableWidget

    /**
     * @param uiPane the parent UI pane this control pane belongs to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the container
     * @param confirmFunction a function that will be called when the "Confirm" button is pressed
     * @return a [KContainer] used to contain a table with the elements of the route edit pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    fun routeEditPane(uiPane: UIPane, widget: KWidget<Actor>, paneWidth: Float, confirmFunction: () -> Unit): KContainer<Actor> {
        parentPane = uiPane
        return widget.container {
            fill()
            setSize(paneWidth, UI_HEIGHT)
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
                    textButton("Undo", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).addChangeListener { _, _ ->
                        // Reset the user clearance route to that of the default clearance
                        parentPane.userClearanceState.route.setToRouteCopy(parentPane.clearanceState.route)
                        parentPane.userClearanceState.hiddenLegs.setToRouteCopy(parentPane.clearanceState.hiddenLegs)
                        updateEditRouteTable(parentPane.userClearanceState.route)
                    }
                    textButton("Confirm", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).addChangeListener { _, _ ->
                        confirmFunction()
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
     * */
    fun updateEditRouteTable(route: Route) {
        routeEditTable.clear()
        routeEditTable.apply {
            var lastWptLegIndex = -1
            for (i in route.legs.size - 1 downTo 0) if (route.legs[i] is Route.WaypointLeg) {
                lastWptLegIndex = i
                break
            }
            for (i in 0 until route.legs.size) {
                route.legs[i].let { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.wptId?.let { wptId ->
                        GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
                    } ?:
                    (leg as? Route.VectorLeg)?.heading?.let { hdg -> "HDG $hdg" } ?:
                    (leg as? Route.HoldLeg)?.wptId?.let { wptId -> "Hold at\n${
                        GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(
                            WaypointInfo.mapper)?.wptName}" } ?:
                    (leg as? Route.DiscontinuityLeg)?.let { "Discontinuity" } ?:
                    (leg as? Route.InitClimbLeg)?.heading?.let { hdg -> "Climb on\nHDG $hdg" } ?: return@let
                    val altRestr = (leg as? Route.WaypointLeg)?.let { wptLeg ->
                        var restr = wptLeg.maxAltFt?.let { maxAltFt -> "${maxAltFt}B" } ?: ""
                        restr += wptLeg.minAltFt?.toString()?.let { minAlt -> "${if (restr.isNotBlank()) "\n" else ""}${minAlt}A" } ?: ""
                        restr
                    } ?: (leg as? Route.InitClimbLeg)?.minAltFt?.let { minAlt -> "$minAlt" } ?: ""
                    val spdRestr = (leg as? Route.WaypointLeg)?.maxSpdKt?.let { spd -> "${spd}kts" } ?: ""
                    val skipText = when (leg) {
                        is Route.WaypointLeg -> "SKIP"
                        else -> "REMOVE"
                    }
                    val legChanged = checkLegChanged(parentPane.clearanceState.route, leg)
                    val restrTriple = (leg as? Route.WaypointLeg)?.let { checkRestrChanged(parentPane.clearanceState.route, it) } ?: Triple(false, false, false)
                    val altRestrChanged = legChanged || restrTriple.first
                    val spdRestrChanged = legChanged || restrTriple.second
                    val skippedChanged = restrTriple.third
                    val legLabel = label(legDisplay, "ControlPaneRoute${if (legChanged) "Changed" else ""}").apply { setAlignment(Align.center) }.cell(growX = true, height = 0.125f * UI_HEIGHT, padLeft = 10f, padRight = 10f)
                    val altButton = textButton(altRestr, "ControlPaneRestr${if (altRestrChanged) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        isChecked = (leg as? Route.WaypointLeg)?.altRestrActive == false
                        if (altRestr.isNotBlank()) addChangeListener { _, _ -> (leg as? Route.WaypointLeg)?.let {
                            it.altRestrActive = !isChecked
                            style = toggleTextColor(style)
                        } ?: run { isChecked = false } }
                    }
                    val spdButton = textButton(spdRestr, "ControlPaneRestr${if (spdRestrChanged) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        isChecked = (leg as? Route.WaypointLeg)?.spdRestrActive == false
                        if (spdRestr.isNotBlank()) addChangeListener { _, _ -> (leg as? Route.WaypointLeg)?.let {
                            it.spdRestrActive = !isChecked
                            style = toggleTextColor(style)
                        } ?: run { isChecked = false } }
                    }
                    if (i < lastWptLegIndex || (lastWptLegIndex == -1 && i < route.legs.size - 1)) textButton(skipText, "ControlPaneSelected${if (skippedChanged) "Changed" else ""}").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        if ((leg as? Route.WaypointLeg)?.legActive == false) isChecked = true
                        addChangeListener { _, _ -> Gdx.app.postRunnable {
                                // Set skipped status for waypoint legs
                                (leg as? Route.WaypointLeg)?.let {
                                    val legIndex = route.legs.indexOf(it, false)
                                    it.legActive = !isChecked
                                    style = Scene2DSkin.defaultSkin[if (style.fontColor == Color.WHITE || style.fontColor == Color(160f / 255, 160f / 255, 160f / 255, 1f)) "ControlPaneSelectedChanged" else "ControlPaneSelected", TextButtonStyle::class.java]
                                    // Remove subsequent leg if it is a vector/hold/init climb
                                    if (route.legs.size > legIndex + 1) route.legs[legIndex + 1]?.let { nextLeg ->
                                        if (nextLeg is Route.HoldLeg || nextLeg is Route.VectorLeg || nextLeg is Route.InitClimbLeg) {
                                            route.legs.removeIndex(legIndex + 1)
                                            updateEditRouteTable(route)
                                        }
                                    }
                                } ?: run {
                                    // Remove the leg if it is a hold/vector/init climb/discontinuity
                                    legLabel.remove()
                                    altButton.remove()
                                    spdButton.remove()
                                    remove()
                                    route.legs.removeValue(leg, false)
                                }
                            }}
                    }
                    row()
                }
            }
        }
    }

    /**
     * Toggles between the unchanged and changed font colours for the waypoint leg restriction text buttons
     * @param style the [TextButtonStyle] to update
     * */
    private fun toggleTextColor(style: TextButtonStyle): TextButtonStyle {
        return Scene2DSkin.defaultSkin[if (style.fontColor == Color.WHITE) "ControlPaneRestrChanged" else "ControlPaneRestr", TextButtonStyle::class.java]
    }
}
