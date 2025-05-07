package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.systems.RenderingSystemClient
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.ashley.get
import ktx.ashley.getSystem
import ktx.ashley.has
import ktx.ashley.hasNot
import ktx.collections.GdxArray
import ktx.graphics.moveTo
import ktx.scene2d.*
import kotlin.math.max

/**
 * The main UI panel display that will integrate the main information pane, and the lateral, altitude and speed panes for controlling of aircraft
 *
 * The overall UI layout is generated on initialisation, and the exact content can be modified by accessing and modifying the relevant UI components stored as variables
 */
class UIPane(private val uiStage: Stage) {
    companion object {
        const val MODE_ROUTE: Byte = 0
        const val MODE_HOLD: Byte = 1
        const val MODE_VECTOR: Byte = 2
    }

    private var paneImage: KImageButton
    val paneWidth: Float
        get() = max(UI_WIDTH * 0.28f, 460f)

    // Main pane (when no aircraft selected)
    val mainInfoObj = MainInfoPane()
    private val mainInfoPane: KContainer<Actor>
    val commsPane: CommsPane
        get() = mainInfoObj.commsPaneObj
    val sectorPane: SectorPane
        get() = mainInfoObj.sectorPaneObj

    //  Multiplayer coordination pane (when aircraft outside of player's sector is selected in multiplayer)
    private val multiplayerCoordinationObj = MultiplayerCoordinationPane()
    private val multiplayerCoordinationPane: KContainer<Actor>

    // Control pane (when aircraft is selected)
    private val controlObj = ControlPane()
    private val controlPane: KContainer<Actor>

    // Route editing pane
    private val routeEditObj = RouteEditPane()
    private val routeEditPane: KContainer<Actor>

    // Clearance state of the UI pane
    val clearanceState = ClearanceState() // Aircraft's current state (without user changes)
    val userClearanceState = ClearanceState() // User's chosen state
    val userClearanceRouteSegments: GdxArray<Route.LegSegment> = GdxArray() // The calculated route segments of user's chosen route state

    // Selected aircraft
    var selAircraft: Aircraft? = null
    // Max alt, arrival airport, approach track capture status of the aircraft, and clearance modification state, for persistence across panes
    var aircraftMaxAlt: Int? = null
    var aircraftArrivalArptId: Byte? = null
    var appTrackCaptured = false
    var isEmergencyNotReadyForApproach = false
    var lastMaxSpdKt: Short? = null

    init {
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                // debugAll()
                setSize(paneWidth, UI_HEIGHT)
                addChangeListener { event, _ -> event?.handle() } // Catch mis-clicks to prevent hiding the UI pane
            }
            mainInfoPane = mainInfoObj.mainInfoPane(this, paneWidth)
            controlPane = controlObj.controlPane(this@UIPane, this, paneWidth) {
                routeEditObj.updateEditRouteTable(userClearanceState.route)
                routeEditObj.updateChangeStarOptions(aircraftArrivalArptId)
                routeEditObj.updateUndoTransmitButtonStates()
                setToEditRoutePane()
            }
            routeEditPane = routeEditObj.routeEditPane(this@UIPane, this, paneWidth) {
                if (userClearanceState.routePrimaryName != clearanceState.routePrimaryName) controlObj.directLeg = userClearanceState.route[0]
                controlObj.updateRouteTable(userClearanceState.route)
                controlObj.updateAltSelectBoxChoices(aircraftMaxAlt, userClearanceState, false)
                controlObj.updateUndoTransmitButtonStates()
                setToControlPane()
            }
            multiplayerCoordinationPane = multiplayerCoordinationObj.multiplayerCoordinationPane(this@UIPane, this, paneWidth)
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
     */
    fun resize(width: Int, height: Int) {
        uiStage.viewport.setWorldSize(UI_WIDTH, UI_HEIGHT)
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
     */
    fun getRadarCameraOffsetForZoom(zoom: Float): Float {
        return -paneWidth / 2 * zoom // TODO Change depending on pane position
    }

    /**
     * Sets the selected UI aircraft to the passed [aircraft]
     *
     * The pane is shown only if aircraft has the Controllable component and is in the player's sector
     * @param aircraft the [Aircraft] whose clearance information will be displayed in the pane
     */
    fun setSelectedAircraft(aircraft: Aircraft) {
        deselectAircraft()
        selAircraft = aircraft
        updateWaypointDisplay()
        aircraft.entity.apply {
            val controllable = get(Controllable.mapper) ?: return
            if (controllable.sectorId != CLIENT_SCREEN?.playerSector) {
                clearanceState.updateUIClearanceState(aircraft.entity[ClearanceAct.mapper]?.actingClearance?.clearanceState ?: return)
                if (controllable.sectorId != SectorInfo.TOWER
                    && controllable.sectorId != SectorInfo.CENTRE
                    && (CLIENT_SCREEN?.sectors?.size ?: 0) > 1) {
                    // Only show coordination pane if there are multiple sectors
                    // and aircraft is not under tower or ACC control
                    mainInfoPane.isVisible = false
                    multiplayerCoordinationPane.isVisible = true
                }
                return
            }
        }
        aircraftMaxAlt = aircraft.entity[AircraftInfo.mapper]?.aircraftPerf?.maxAlt ?: return
        aircraftArrivalArptId = aircraft.entity[ArrivalAirport.mapper]?.arptId
        appTrackCaptured = aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper)
        isEmergencyNotReadyForApproach = aircraft.entity[EmergencyPending.mapper]?.active == true && aircraft.entity.hasNot(ReadyForApproachClient.mapper)
        val latestActing = aircraft.entity[ClearanceAct.mapper]?.actingClearance ?: return
        userClearanceState.updateUIClearanceState(latestActing.clearanceState)
        clearanceState.updateUIClearanceState(latestActing.clearanceState)
        val flightType = aircraft.entity[FlightType.mapper]?.type ?: return
        lastMaxSpdKt = if (flightType == FlightType.ARRIVAL) aircraft.entity[LastRestrictions.mapper]?.maxSpdKt else null
        controlObj.resetDirectButton()
        controlObj.updateAltSpdAppClearances(userClearanceState.clearedAlt, userClearanceState.clearedIas,
            userClearanceState.minIas, userClearanceState.maxIas, userClearanceState.optimalIas,
            userClearanceState.clearedApp, userClearanceState.clearedTrans)
        controlObj.updateExpediteClearance(userClearanceState.expedite)
        controlObj.updateChangedStates(userClearanceState, clearanceState)
        controlObj.updateClearanceMode(userClearanceState.route, userClearanceState.vectorHdg,
            aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper), true)
        controlObj.setUndoTransmitButtonsUnchanged()
        controlObj.updateHandoverAcknowledgeGoAroundButton(
            aircraft.entity.has(CanBeHandedOver.mapper),
            shouldShowAcknowledgeButton(aircraft.entity),
            aircraft.entity.has(CanGoAround.mapper)
        )

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
     */
    fun updateSelectedAircraft(aircraft: Aircraft) {
        updateWaypointDisplay()
        aircraft.entity.apply {
            val controllable = get(Controllable.mapper) ?: return
            if (controllable.sectorId != CLIENT_SCREEN?.playerSector) return
        }
        aircraftArrivalArptId = aircraft.entity[ArrivalAirport.mapper]?.arptId
        appTrackCaptured = aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper)
        val latestActing = aircraft.entity[ClearanceAct.mapper]?.actingClearance ?: return
        userClearanceState.updateUIClearanceState(latestActing.clearanceState, clearanceState)
        clearanceState.updateUIClearanceState(latestActing.clearanceState)
        val flightType = aircraft.entity[FlightType.mapper]?.type ?: return
        lastMaxSpdKt = if (flightType == FlightType.ARRIVAL) aircraft.entity[LastRestrictions.mapper]?.maxSpdKt else null
        controlObj.updateClearanceMode(userClearanceState.route, userClearanceState.vectorHdg,
            aircraft.entity.has(VisualCaptured.mapper) || aircraft.entity.has(LocalizerCaptured.mapper), false)
        controlObj.updateAltSelectBoxChoices(aircraftMaxAlt, userClearanceState, false)
        controlObj.updateAltSpdAppClearances(userClearanceState.clearedAlt, userClearanceState.clearedIas,
            userClearanceState.minIas, userClearanceState.maxIas, userClearanceState.optimalIas,
            userClearanceState.clearedApp, userClearanceState.clearedTrans)
        controlObj.updateExpediteClearance(userClearanceState.expedite)
        controlObj.updateChangedStates(userClearanceState, clearanceState)
        controlObj.updateUndoTransmitButtonStates()
        controlObj.updateHandoverAcknowledgeGoAroundButton(
            aircraft.entity.has(CanBeHandedOver.mapper),
            shouldShowAcknowledgeButton(aircraft.entity),
            aircraft.entity.has(CanGoAround.mapper)
        )
        routeEditObj.setChangeStarDisabled(aircraftArrivalArptId == null)
    }

    /** Unset the selected UI aircraft */
    fun deselectAircraft() {
        selAircraft = null
        controlPane.isVisible = false
        routeEditPane.isVisible = false
        mainInfoPane.isVisible = true
        multiplayerCoordinationPane.isVisible = false
        clearanceState.route.clear()
        clearanceState.vectorHdg = null
        userClearanceState.route.clear()
        userClearanceRouteSegments.clear()
        userClearanceState.vectorHdg = null
        aircraftMaxAlt = null
        aircraftArrivalArptId = null
        isEmergencyNotReadyForApproach = false
        updateWaypointDisplay()
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

    /**
     * Updates the state of the handover/acknowledge/go around button state depending on [aircraft] components
     *
     * If the input aircraft does not match the selected aircraft, the function will return
     */
    fun updateHandoverAckButtonState(aircraft: Entity) {
        val callsign = aircraft[AircraftInfo.mapper]?.icaoCallsign ?: return
        if (callsign != selAircraft?.entity?.get(AircraftInfo.mapper)?.icaoCallsign) return
        controlObj.updateHandoverAcknowledgeGoAroundButton(
            aircraft.has(CanBeHandedOver.mapper),
            shouldShowAcknowledgeButton(aircraft),
            aircraft.has(CanGoAround.mapper)
        )
    }

    /** Updates the rendered waypoints for currently selected aircraft */
    fun updateWaypointDisplay() {
        getEngine(true).getSystem<RenderingSystemClient>().updateWaypointDisplay(selAircraft)
    }

    /** Returns whether the acknowledge button should be shown for the input [aircraft] */
    private fun shouldShowAcknowledgeButton(aircraft: Entity): Boolean {
        return aircraft.has(ContactNotification.mapper)
                || aircraft[AircraftRequestNotification.mapper]?.requestTypes?.notEmpty() == true
                || aircraft.has(AircraftPointOutNotification.mapper)
    }
}