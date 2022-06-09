package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import ktx.ashley.get
import ktx.ashley.has
import ktx.graphics.moveTo
import ktx.scene2d.*
import kotlin.math.max

/**
 * The main UI panel display that will integrate the main information pane, and the lateral, altitude and speed panes for controlling of aircraft
 *
 * The overall UI layout is generated on initialisation, and the exact content can be modified by accessing and modifying the relevant UI components stored as variables
 * */
class UIPane(private val uiStage: Stage) {
    companion object {
        const val MODE_ROUTE: Byte = 0
        const val MODE_HOLD: Byte = 1
        const val MODE_VECTOR: Byte = 2
    }

    private var paneImage: KImageButton
    val paneWidth: Float
        get() = max(UI_WIDTH * 0.28f, 400f)

    // Main pane (when no aircraft selected)
    private val mainInfoPane: KContainer<Actor>

    // Control pane (when aircraft is selected)
    private val controlObj = ControlPane()
    private val controlPane: KContainer<Actor>

    // Route editing pane
    private val routeEditObj = RouteEditPane()
    private val routeEditPane: KContainer<Actor>

    // Clearance state of the UI pane
    val clearanceState: ClearanceState = ClearanceState() // Aircraft's current state (without user changes)
    val userClearanceState: ClearanceState = ClearanceState() // User's chosen state

    // Max alt, arrival airport and approach track capture status of the aircraft, for persistence across panes
    var aircraftMaxAlt: Int? = null
    var aircraftArrivalArptId: Byte? = null
    var appTrackCaptured = false
    // var glidePathCaptured = false

    init {
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                // debugAll()
                setSize(paneWidth, UI_HEIGHT)
                addChangeListener { event, _ -> event?.handle() } // Catch mis-clicks to prevent hiding the UI pane
            }
            mainInfoPane = mainInfoPane(paneWidth)
            controlPane = controlObj.controlPane(this@UIPane, this, paneWidth) {
                routeEditObj.updateEditRouteTable(userClearanceState.route)
                routeEditObj.updateUndoTransmitButtonStates()
                setToEditRoutePane()
            }
            routeEditPane = routeEditObj.routeEditPane(this@UIPane, this, paneWidth) {
                controlObj.updateRouteTable(userClearanceState.route)
                controlObj.updateUndoTransmitButtonStates()
                setToControlPane()
            }
        }
        uiStage.camera.apply {
            moveTo(Vector2(UI_WIDTH / 2, UI_HEIGHT / 2))
            update()
        }
    }

    /**
     * Resize the pane and containers
     * @param width the new width of the application
     * @param height the new height of the application
     * */
    fun resize(width: Int, height: Int) {
        uiStage.viewport.update(width, height, true)
        uiStage.camera.apply {
            moveTo(Vector2(UI_WIDTH / 2, UI_HEIGHT / 2))
            update()
        }
        paneImage.apply {
            setSize(paneWidth, UI_HEIGHT)
            setPosition(0f, 0f)
        }
        mainInfoPane.apply {
            setSize(paneWidth, UI_HEIGHT)
            setPosition(0f, 0f)
        }
        controlPane.apply {
            setSize(paneWidth, UI_HEIGHT)
            setPosition(0f, 0f)
        }
        routeEditPane.apply {
            setSize(paneWidth, UI_HEIGHT)
            setPosition(0f, 0f)
        }
    }

    /**
     * Gets the required x offset for radarDisplayStage's camera at a zoom level
     * @param zoom the zoom of the radarDisplayStage camera
     * */
    fun getRadarCameraOffsetForZoom(zoom: Float): Float {
        return -paneWidth / 2 * zoom // TODO Change depending on pane position
    }

    /**
     * Sets the selected UI aircraft to the passed [aircraft]
     *
     * The pane is shown only if aircraft has the Controllable component and is in the player's sector
     * @param aircraft the [Aircraft] whose clearance information will be displayed in the pane
     * */
    fun setSelectedAircraft(aircraft: Aircraft) {
        aircraft.entity.apply {
            val controllable = get(Controllable.mapper) ?: return
            if (controllable.sectorId != GAME.gameClientScreen?.playerSector) return
        }
        aircraftMaxAlt = aircraft.entity[AircraftInfo.mapper]?.aircraftPerf?.maxAlt ?: return
        aircraftArrivalArptId = aircraft.entity[ArrivalAirport.mapper]?.arptId
        appTrackCaptured = aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper)
        // glidePathCaptured = aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(GlideSlopeCaptured.mapper)
        val latestClearance = aircraft.entity[ClearanceAct.mapper]?.actingClearance ?: return
        userClearanceState.updateUIClearanceState(latestClearance.actingClearance)
        clearanceState.updateUIClearanceState(latestClearance.actingClearance)
        controlObj.resetDirectButton()
        controlObj.updateAltSpdAppClearances(userClearanceState.clearedAlt, userClearanceState.clearedIas,
            userClearanceState.minIas, userClearanceState.maxIas, userClearanceState.optimalIas,
            userClearanceState.clearedApp, userClearanceState.clearedTrans)
        controlObj.updateClearanceMode(userClearanceState.route, userClearanceState.vectorHdg,
            aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper))
        controlObj.setUndoTransmitButtonsUnchanged()
        routeEditObj.setChangeStarDisabled(aircraftArrivalArptId == null)
        controlPane.isVisible = true
        routeEditPane.isVisible = false
        mainInfoPane.isVisible = false
    }

    /**
     * Updates currently selected aircraft pane navigation state to match the updated [aircraft]; the [aircraft] should
     * not have changed from a previous call of [setSelectedAircraft] or [updateSelectedAircraft], otherwise the UI may
     * not display the data correctly as desired
     *
     * The pane is updated only if aircraft has the Controllable component and is in the player's sector
     * @param aircraft the [Aircraft] whose clearance information will be displayed in the pane
     * */
    fun updateSelectedAircraft(aircraft: Aircraft) {
        aircraft.entity.apply {
            val controllable = get(Controllable.mapper) ?: return
            if (controllable.sectorId != GAME.gameClientScreen?.playerSector) return
        }
        aircraftArrivalArptId = aircraft.entity[ArrivalAirport.mapper]?.arptId
        appTrackCaptured = aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper)
        // glidePathCaptured = aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(GlideSlopeCaptured.mapper)
        val latestClearance = aircraft.entity[ClearanceAct.mapper]?.actingClearance ?: return
        userClearanceState.updateUIClearanceState(latestClearance.actingClearance, clearanceState)
        clearanceState.updateUIClearanceState(latestClearance.actingClearance)
        controlObj.updateClearanceMode(userClearanceState.route, userClearanceState.vectorHdg,
            aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper))
        controlObj.updateAltSelectBoxChoices(aircraftMaxAlt)
        controlObj.updateAltSpdAppClearances(userClearanceState.clearedAlt, userClearanceState.clearedIas,
            userClearanceState.minIas, userClearanceState.maxIas, userClearanceState.optimalIas,
            userClearanceState.clearedApp, userClearanceState.clearedTrans)
        controlObj.updateUndoTransmitButtonStates()
        routeEditObj.setChangeStarDisabled(aircraftArrivalArptId == null)
    }

    /** Unset the selected UI aircraft */
    fun deselectAircraft() {
        controlPane.isVisible = false
        routeEditPane.isVisible = false
        mainInfoPane.isVisible = true
        clearanceState.route.legs.clear()
        userClearanceState.route.legs.clear()
        aircraftMaxAlt = null
        aircraftArrivalArptId = null
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
}