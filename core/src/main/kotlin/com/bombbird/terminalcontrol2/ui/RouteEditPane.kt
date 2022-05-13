package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.addChangeListener
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
     * @param setToControlPane a function that will be called when the "Confirm" button is pressed
     * @return a [KContainer] used to contain a table with the elements of the route edit pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    fun routeEditPane(uiPane: UIPane, widget: KWidget<Actor>, paneWidth: Float, setToControlPane: () -> Unit): KContainer<Actor> {
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
                    textButton("Undo", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth)
                    textButton("Confirm", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).addListener(object: ChangeListener() {
                        override fun changed(event: ChangeEvent?, actor: Actor?) {
                            setToControlPane()
                        }
                    })
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
            for (i in 0 until route.legs.size) {
                route.legs[i].let { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.wptId?.let { wptId -> GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(
                        WaypointInfo.mapper)?.wptName } ?:
                    (leg as? Route.VectorLeg)?.heading?.let { hdg -> "HDG $hdg" } ?:
                    (leg as? Route.HoldLeg)?.wptId?.let { wptId -> "Hold at\n${
                        GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(
                            WaypointInfo.mapper)?.wptName}" } ?:
                    (leg as? Route.DiscontinuityLeg)?.let { "Discontinuity" } ?:
                    (leg as? Route.InitClimbLeg)?.heading?.let { hdg -> "Climb on\nHDG $hdg" } ?: return@let
                    val altRestrDisplay = (leg as? Route.WaypointLeg)?.let { wptLeg ->
                        var restr = wptLeg.maxAltFt?.let { maxAltFt -> "${maxAltFt}B" } ?: ""
                        restr += wptLeg.minAltFt?.toString()?.let { minAlt -> "${if (restr.isNotBlank()) "\n" else ""}${minAlt}A" } ?: ""
                        restr
                    } ?: (leg as? Route.InitClimbLeg)?.minAltFt?.let { minAlt -> "$minAlt" } ?: ""
                    val spdRestr = (leg as? Route.WaypointLeg)?.maxSpdKt?.let { spd -> "${spd}kts" } ?: ""
                    val skipText = when (leg) {
                        is Route.WaypointLeg -> "SKIP"
                        else -> "REMOVE"
                    }
                    val legLabel = label(legDisplay, "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, height = 0.125f * UI_HEIGHT, padLeft = 10f, padRight = 10f)
                    val altButton = textButton(altRestrDisplay, "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT)
                    val spdButton = textButton(spdRestr, "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT)
                    textButton(skipText, "ControlPaneSelected").cell(growX = true, preferredWidth = 0.25f * parentPane.paneWidth, height = 0.125f * UI_HEIGHT).apply {
                        if (skipText == "REMOVE") addChangeListener { _, _ ->
                            // Remove the leg if it is a hold/vector/init climb/discontinuity
                            legLabel.remove()
                            altButton.remove()
                            spdButton.remove()
                            remove()
                        }
                    }
                    row()
                }
            }
        }
    }
}
