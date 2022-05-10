package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.MathTools.byte
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.graphics.moveTo
import ktx.scene2d.*
import kotlin.math.max

/** The main UI panel display that will integrate the main information pane, and the lateral, altitude and speed panes for controlling of aircraft
 *
 * The overall UI layout is generated on initialisation, and the exact content can be modified by accessing and modifying the relevant UI components stored as variables
 * */
class UIPane(private val uiStage: Stage) {
    companion object {
        const val MODE_ROUTE: Byte = 0
        const val MODE_HOLD: Byte = 1
        const val MODE_VECTOR: Byte = 2
    }

    var paneImage: KImageButton
    val paneWidth: Float
        get() = max(Variables.UI_WIDTH * 0.28f, 400f)

    // Main pane (when no aircraft selected)
    val mainInfoPane: KContainer<Actor>

    // Control pane (when aircraft is selected)
    val controlPane: KContainer<Actor>

    // Route editing pane
    val routeEditPane: KContainer<Actor>

    init {
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                // debugAll()
                setSize(paneWidth, Variables.UI_HEIGHT)
            }
            mainInfoPane = mainInfoPane(paneWidth)
            controlPane = controlPane(paneWidth)
            routeTable = routeTable(paneWidth) {
                setToEditRoutePane()
            }
            holdTable = holdTable(paneWidth)
            vectorTable = vectorTable(paneWidth)
            lateralContainer.actor = routeTable
            routeEditPane = routeEditPane(paneWidth) {
                setToControlPane()
            }
        }
        uiStage.camera.apply {
            moveTo(Vector2(Variables.UI_WIDTH / 2, Variables.UI_HEIGHT / 2))
            update()
        }
    }

    /** Resize the pane and containers */
    fun resize(width: Int, height: Int) {
        uiStage.viewport.update(width, height, true)
        uiStage.camera.apply {
            moveTo(Vector2(Variables.UI_WIDTH / 2, Variables.UI_HEIGHT / 2))
            update()
        }
        paneImage.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
        mainInfoPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
        controlPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
        routeEditPane.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(0f, 0f)
        }
    }

    /** Gets the required x offset for radarDisplayStage's camera at a zoom level */
    fun getRadarCameraOffsetForZoom(zoom: Float): Float {
        return -paneWidth / 2 * zoom // TODO Change depending on pane position
    }

    /** Sets the selected UI aircraft to the passed [aircraft]
     *
     * The pane is shown only if aircraft has the Controllable component and is in the player's sector
     * */
    fun setSelectedAircraft(aircraft: Aircraft) {
        aircraft.entity.apply {
            val controllable = get(Controllable.mapper) ?: return
            if (controllable.sectorId != 0.byte) return // TODO Check for player's sector ID
        }
        val latestClearance = aircraft.entity[ClearanceAct.mapper]?.clearance ?: return
        updateRouteTable(latestClearance.route)
        updateClearanceMode(latestClearance.route, latestClearance.vectorHdg)
        updateAltSpdClearances(latestClearance.clearedAlt, latestClearance.clearedIas, 150, 250)
        controlPane.isVisible = true
        routeEditPane.isVisible = false
        mainInfoPane.isVisible = false
    }

    /** Updates currently selected aircraft pane navigation state to match the updated [aircraft]
     *
     * The pane is updated only if aircraft has the Controllable component and is in the player's sector
     * */
    fun updateSelectedAircraft(aircraft: Aircraft) {
        aircraft.entity.apply {
            val controllable = get(Controllable.mapper) ?: return
            if (controllable.sectorId != 0.byte) return // TODO Check for player's sector ID
        }
        val latestClearance = aircraft.entity[ClearanceAct.mapper]?.clearance ?: return
        updateRouteTable(latestClearance.route)
        updateClearanceMode(latestClearance.route, latestClearance.vectorHdg)
        updateAltSpdClearances(latestClearance.clearedAlt, latestClearance.clearedIas, 150, 250)
    }

    /** Unset the selected UI aircraft */
    fun deselectAircraft() {
        controlPane.isVisible = false
        routeEditPane.isVisible = false
        mainInfoPane.isVisible = true
    }

    /** Helper function to set the UI pane to show [routeEditPane] from [controlPane] */
    private fun setToEditRoutePane() {
        routeEditPane.isVisible = true
        controlPane.isVisible = false
    }

    /** Helper function to set the UI pane to show [controlPane] from [routeEditPane] */
    private fun setToControlPane() {
        routeEditPane.isVisible = false
        controlPane.isVisible = true
    }

    /** Updates [altSelectBox] with the appropriate altitude choices for [Variables.MIN_ALT] and [Variables.MAX_ALT]
     *
     * Call after updating [Variables.MIN_ALT] and [Variables.MAX_ALT]
     * */
    fun updateAltSelectBoxChoices() {
        /** Checks the [alt] according to [Variables.TRANS_ALT] and [Variables.TRANS_LVL] before adding it to the [array] */
        fun checkAltAndAddToArray(alt: Int, array: GdxArray<String>) {
            if (alt > Variables.TRANS_ALT && alt < Variables.TRANS_LVL * 100) return
            if (alt <= Variables.TRANS_ALT) array.add(alt.toString())
            else if (alt >= Variables.TRANS_LVL * 100) array.add("FL${alt / 100}")
        }

        val minAlt = if (Variables.MIN_ALT % 1000 > 0) (Variables.MIN_ALT / 1000 + 1) * 1000 else Variables.MIN_ALT
        val maxAlt = if (Variables.MAX_ALT % 1000 > 0) (Variables.MAX_ALT / 1000) * 1000 else Variables.MAX_ALT
        altSelectBox.items = GdxArray<String>().apply {
            clear()
            if (Variables.MIN_ALT % 1000 > 0) checkAltAndAddToArray(Variables.MIN_ALT, this)
            for (alt in minAlt .. maxAlt step 1000) checkAltAndAddToArray(alt, this)
            if (Variables.MAX_ALT % 1000 > 0) checkAltAndAddToArray(Variables.MAX_ALT, this)
        }
    }

    /** Updates the cleared altitude, speed in the pane */
    private fun updateAltSpdClearances(clearedAlt: Int, clearedSpd: Short, minSpd: Short, maxSpd: Short) {
        val minSpdRounded = if (minSpd % 10 > 0) ((minSpd / 10 + 1) * 10).toShort() else minSpd
        val maxSpdRounded = if (maxSpd % 10 > 0) ((maxSpd / 10) * 10).toShort() else maxSpd
        spdSelectBox.items = GdxArray<Short>().apply {
            clear()
            if (minSpd % 10 > 0) add(minSpd)
            for (spd in minSpdRounded .. maxSpdRounded step 10) add(spd.toShort())
            if (maxSpd % 10 > 0) add(maxSpd)
        }
        altSelectBox.selected = if (clearedAlt >= Variables.TRANS_LVL * 100) "FL${clearedAlt / 100}" else clearedAlt.toString()
        spdSelectBox.selected = clearedSpd
    }

    /** Updates the route list in [routeLegsTable] (Route tab) */
    private fun updateRouteTable(route: Route) {
        routeLegsTable.clear()
        routeLegsTable.apply {
            var firstDirectSet = false
            for (i in 0 until route.legs.size) {
                route.legs[i].let { leg ->
                    val legDisplay = (leg as? Route.WaypointLeg)?.wptId?.let { wptId -> Constants.GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName } ?:
                                     (leg as? Route.VectorLeg)?.heading?.let { hdg -> "HDG $hdg" } ?:
                                     (leg as? Route.HoldLeg)?.wptId?.let { wptId -> "Hold at\n${Constants.GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName}" } ?:
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
                    textButton("=>", "ControlPaneRouteDirect").cell(growX = true, preferredWidth = 0.2f * paneWidth, preferredHeight = 0.1f * Variables.UI_HEIGHT).apply {
                        if (!firstDirectSet) {
                            isChecked = true
                            firstDirectSet = true
                        }
                    }
                    label(legDisplay, "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * paneWidth)
                    label(altRestrDisplay, altRestrStyle).apply { setAlignment(Align.center) }.cell(expandX = true, padLeft = 10f, padRight = 10f)
                    label(spdRestr, spdRestrStyle).apply { setAlignment(Align.center) }.cell(growX = true, preferredWidth = 0.2f * paneWidth)
                    row()
                }
            }
        }
    }
}