package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.PublishedHoldInfo
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.findFirstHoldLegWithID
import com.bombbird.terminalcontrol2.utilities.modulateHeading
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.scene2d.*
import kotlin.math.ceil
import kotlin.math.roundToInt

class HoldSubpane {
    private lateinit var parentControlPane: ControlPane
    private val parentPane: UIPane
        get() = parentControlPane.parentPane
    private var modificationInProgress: Boolean
        get() = parentControlPane.modificationInProgress
        set(value) {
            parentControlPane.modificationInProgress = value
        }
    private var selectedHoldLeg: Route.HoldLeg?
        get() = parentControlPane.selectedHoldLeg
        set(value) {
            parentControlPane.selectedHoldLeg = value
        }
    private var directLeg: Route.Leg?
        get() = parentControlPane.directLeg
        set(value) {
            parentControlPane.directLeg = value
        }

    private lateinit var holdTable: KTableWidget
    private lateinit var holdSelectBox: KSelectBox<String>
    private lateinit var holdLegDistLabel: Label
    private lateinit var holdInboundHdgLabel: Label
    private lateinit var holdAsPublishedButton: KTextButton
    private lateinit var holdCustomButton: KTextButton
    private lateinit var holdLeftButton: KTextButton
    private lateinit var holdRightButton: KTextButton

    var isVisible: Boolean
        get() = holdTable.isVisible
        set(value) {
            holdTable.isVisible = value
        }
    val actor: Actor
        get() = holdTable

    /**
     * @param controlPane the parent [ControlPane] to refer to
     * @param widget the widget to add this hold table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the table
     * @return a [KTableWidget] used to contain the elements of the hold sub-pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    fun holdTable(controlPane: ControlPane, widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        parentControlPane = controlPane
        holdTable = widget.table {
            // debugAll()
            table {
                holdSelectBox = selectBox<String>("ControlPane") {
                    setAlignment(Align.center)
                    list.alignment = Align.center
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        // Update the hold legs in route when selected hold leg changes
                        updateHoldClearanceState(parentPane.userClearanceState.route)
                        updateHoldTable(parentPane.userClearanceState.route, selectedHoldLeg)
                        parentControlPane.updateAltSelectBoxChoices(parentPane.aircraftMaxAlt, parentPane.userClearanceState)
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
                holdAsPublishedButton = textButton("As\n Published", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdCustomButton.isChecked = false
                            selectedHoldLeg?.let {
                                setHoldAsPublished(it)
                                updateHoldTable(parentPane.userClearanceState.route, selectedHoldLeg)
                                parentControlPane.updateUndoTransmitButtonStates()
                            }
                            modificationInProgress = false
                        } else holdAsPublishedButton.isChecked = true
                    }
                }
                holdCustomButton = textButton("Custom", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f, padRight = 10f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdAsPublishedButton.isChecked = false
                            modificationInProgress = false
                        } else holdCustomButton.isChecked = true
                    }
                }
            }.cell(preferredWidth = paneWidth, growX = true, height = UI_HEIGHT * 0.1f, padTop = 20f)
            row()
            table {
                // debugAll()
                label("Legs:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, height = UI_HEIGHT * 0.1f, padRight = 10f, preferredWidth = 0.15f * paneWidth, align = Align.center)
                textButton("-", "ControlPaneHold").cell(grow = true, preferredWidth = 0.15f * paneWidth).addChangeListener { _, _ ->
                    val newDist = MathUtils.clamp(holdLegDistLabel.text.split(" ")[0].toInt() - 1, 3, 10)
                    selectedHoldLeg?.legDist = newDist.toByte()
                    holdLegDistLabel.setText("$newDist nm")
                    updateAsPublishedStatus()
                    updateHoldParameterChangedState(selectedHoldLeg)
                    parentControlPane.updateUndoTransmitButtonStates()
                }
                holdLegDistLabel = label("5 nm", "ControlPaneHoldDist").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.15f * paneWidth, align = Align.center)
                textButton("+", "ControlPaneHold").cell(grow = true, padRight = 30f, preferredWidth = 0.15f * paneWidth).addChangeListener { _, _ ->
                    val newDist = MathUtils.clamp(holdLegDistLabel.text.split(" ")[0].toInt() + 1, 3, 10)
                    selectedHoldLeg?.legDist = newDist.toByte()
                    holdLegDistLabel.setText("$newDist nm")
                    updateAsPublishedStatus()
                    updateHoldParameterChangedState(selectedHoldLeg)
                    parentControlPane.updateUndoTransmitButtonStates()
                }
                holdLeftButton = textButton("Left", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdRightButton.isChecked = false
                            modificationInProgress = false
                            selectedHoldLeg?.turnDir = CommandTarget.TURN_LEFT
                            updateAsPublishedStatus()
                            updateHoldParameterChangedState(selectedHoldLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        } else isChecked = true
                    }
                }
                holdRightButton = textButton("Right", "ControlPaneSelected").cell(grow = true, preferredWidth = 0.2f * paneWidth - 20f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        if (isChecked) {
                            modificationInProgress = true
                            holdLeftButton.isChecked = false
                            modificationInProgress = false
                            selectedHoldLeg?.turnDir = CommandTarget.TURN_RIGHT
                            updateAsPublishedStatus()
                            updateHoldParameterChangedState(selectedHoldLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        } else isChecked = true
                    }
                }
            }.cell(preferredWidth = paneWidth, growX = true, height = UI_HEIGHT * 0.1f, padTop = 20f)
            row()
            table {
                label("Inbound\nheading:", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.22f * paneWidth, padRight = 10f)
                table {
                    textButton("-20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(-20)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("-5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(-5)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
                holdInboundHdgLabel = label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.23f * paneWidth - 10f)
                table {
                    textButton("+20", "ControlPaneHdgDark").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(20)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                    row()
                    textButton("+5", "ControlPaneHdgLight").cell(grow = true, preferredHeight = 0.2f * UI_HEIGHT - 40f).addChangeListener { _, _ ->
                        updateHoldHdgValue(5)
                        updateAsPublishedStatus()
                        updateHoldParameterChangedState(selectedHoldLeg)
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                }.cell(grow = true, preferredWidth = 0.275f * paneWidth)
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.4f * UI_HEIGHT - 80f, growX = true, padTop = 20f, padBottom = 20f)
            isVisible = false
        }
        return holdTable
    }

    /**
     * Updates the hold table to the next cleared hold clearance if any
     * @param route the route to refer to; should be the aircraft's latest cleared route or user input route
     * @param selectedHold the current user selected hold leg
     * */
    fun updateHoldTable(route: Route, selectedHold: Route.HoldLeg?) {
        modificationInProgress = true
        holdSelectBox.items = GdxArray<String>().apply {
            if (parentPane.clearanceState.route.size > 0) { (parentPane.clearanceState.route[0] as? Route.HoldLeg)?.let {
                add(if (it.wptId.toInt() <= -1) "Present position" else CLIENT_SCREEN?.waypoints?.get(it.wptId)?.entity?.get(
                    WaypointInfo.mapper)?.wptName)
                return@apply // Only allow the current hold leg in the selection if aircraft is already holding
            }}
            add("Present position")
            for (i in 0 until route.size) route[i].let {
                if (it is Route.WaypointLeg) CLIENT_SCREEN?.waypoints?.get(it.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName?.also { name -> add(name) }
            }
        }
        selectedHold?.apply {
            val wptName = if (wptId.toInt() <= -1) "Present position" else CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(
                WaypointInfo.mapper)?.wptName ?: return@apply
            holdSelectBox.selected = wptName
            holdLegDistLabel.setText("$legDist nm")
            holdInboundHdgLabel.setText(inboundHdg.toString())
            holdLeftButton.isChecked = turnDir == CommandTarget.TURN_LEFT
            holdRightButton.isChecked = turnDir == CommandTarget.TURN_RIGHT
            CLIENT_SCREEN?.publishedHolds?.get(wptName)?.entity?.get(PublishedHoldInfo.mapper)?.let {
                holdAsPublishedButton.isVisible = true
                val asPublished = legDist == it.legDistNm && inboundHdg == it.inboundHdgDeg && turnDir == it.turnDir
                holdAsPublishedButton.isChecked = asPublished
                holdCustomButton.isChecked = !asPublished
            } ?: run {
                holdAsPublishedButton.isChecked = false
                holdAsPublishedButton.isVisible = false
            }
            updateHoldParameterChangedState(this)
        } ?: run {
            Gdx.app.log("ControlPane", "Null selectedHoldLeg; should not be null")
            holdSelectBox.selectedIndex = 0
            holdLegDistLabel.setText("5 nm")
            holdInboundHdgLabel.setText("360")
        }
        updateAsPublishedStatus()
        modificationInProgress = false
    }

    /**
     * Updates the user cleared hold inbound heading with the input delta value, and updates the [holdInboundHdgLabel] as well
     * @param change the change in heading that will be added to the user cleared hold inbound heading
     * */
    private fun updateHoldHdgValue(change: Short) {
        selectedHoldLeg?.apply {
            inboundHdg = (inboundHdg + change).toShort().let {
                val rectifiedHeading = if (change >= 0) (it / 5f).toInt() * 5 else ceil(it / 5f).roundToInt() * 5
                modulateHeading(rectifiedHeading.toFloat()).toInt().toShort()
            }
            holdInboundHdgLabel.setText(inboundHdg.toString())

        }
    }

    /**
     * Updates the styles for the hold parameter input fields (selectBox, Labels, etc.) given the input hold leg
     * @param holdLeg the hold leg to compare with the acting clearance state
     * */
    private fun updateHoldParameterChangedState(holdLeg: Route.HoldLeg?) {
        holdLeg?.apply {
            val sameLeg = findFirstHoldLegWithID(wptId, parentPane.clearanceState.route)
            holdSelectBox.style = Scene2DSkin.defaultSkin[if (sameLeg == null) "ControlPaneChanged" else "ControlPane", SelectBox.SelectBoxStyle::class.java]
            val distChanged = sameLeg?.legDist != legDist
            holdLegDistLabel.style = Scene2DSkin.defaultSkin[if (distChanged) "ControlPaneHoldDistChanged" else "ControlPaneHoldDist", Label.LabelStyle::class.java]
            val hdgChanged = sameLeg?.inboundHdg != inboundHdg
            holdInboundHdgLabel.style = Scene2DSkin.defaultSkin[if (hdgChanged) "ControlPaneHdgChanged" else "ControlPaneHdg", Label.LabelStyle::class.java]
            val turnDirChanged = sameLeg?.turnDir != turnDir
            holdRightButton.style = Scene2DSkin.defaultSkin[if (turnDirChanged && holdRightButton.isChecked) "ControlPaneSelectedChanged" else "ControlPaneSelected", TextButton.TextButtonStyle::class.java]
            holdLeftButton.style = Scene2DSkin.defaultSkin[if (turnDirChanged && holdLeftButton.isChecked) "ControlPaneSelectedChanged" else "ControlPaneSelected", TextButton.TextButtonStyle::class.java]
        }
    }

    /**
     * Updates the appropriate hold legs in the route clearance; should be called when the selected hold leg in [holdTable]
     * have been changed
     *
     * Also updates the [selectedHoldLeg] with the new selected holding leg
     * @param route the route to update the hold legs
     * */
    fun updateHoldClearanceState(route: Route) {
        (selectedHoldLeg?.wptId)?.let {
            // Look for hold legs that are present in the selected clearance but not the acting clearance
            for (i in 0 until route.size) (route[i] as? Route.HoldLeg)?.apply {
                if ((it.toInt() == -1 && wptId.toInt() == -1) || it == wptId) {
                    // Found in selected clearance
                    for (j in 0 until parentPane.clearanceState.route.size) (parentPane.clearanceState.route[j] as? Route.HoldLeg)?.also { actLeg ->
                        if ((it.toInt() == -1 && actLeg.wptId.toInt() == -1) || it == actLeg.wptId) {
                            // Also found in acting clearance, don't remove
                            return@let
                        }
                    }
                    // Not found in acting clearance, remove from selected clearance
                    route.removeIndex(i)
                    selectedHoldLeg = null
                    return@let
                }
            }
        }

        (holdSelectBox.selected ?: "Present position").let {
            val selectedInboundHdg = holdInboundHdgLabel.text.toString().toShort()
            val selectedLegDist = holdLegDistLabel.text.split(" ")[0].toByte()
            val selectedTurnDir = if (holdLeftButton.isChecked) CommandTarget.TURN_LEFT else CommandTarget.TURN_RIGHT
            if (it != "Present position") {
                // Find the corresponding hold or waypoint leg in route (until a non waypoint/hold leg is found)
                route.also { legs -> for (i in 0 until legs.size) legs[i].apply {
                    // Search for hold leg first
                    if (this is Route.HoldLeg && CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName == it) {
                        // Hold already exists in route, update to selected parameters
                        inboundHdg = selectedInboundHdg
                        legDist = selectedLegDist
                        turnDir = selectedTurnDir
                        selectedHoldLeg = this
                        return@also
                    } else if (this !is Route.HoldLeg && this !is Route.WaypointLeg) return@apply // Non waypoint/hold leg reached
                }

                    // Hold leg not found, search for waypoint leg instead
                    for (i in 0 until legs.size) legs[i].apply {
                        if (this is Route.WaypointLeg && CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName == it) {
                            // Add a new hold leg after this waypoint leg (phase will be the same as the parent waypoint leg)
                            val newHold = CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName?.let { name ->
                                // If this leg is a published hold leg, set to published by default
                                CLIENT_SCREEN?.publishedHolds?.get(name)?.entity?.get(PublishedHoldInfo.mapper)?.let { pubHold ->
                                    Route.HoldLeg(wptId, pubHold.maxAltFt, pubHold.minAltFt, pubHold.maxSpdKtLower, pubHold.maxSpdKtHigher,
                                        pubHold.inboundHdgDeg, pubHold.legDistNm, pubHold.turnDir, phase)
                                }} ?: Route.HoldLeg(wptId, null, null, 230, 240, selectedInboundHdg, selectedLegDist, selectedTurnDir, phase)
                            // Remove after waypoint heading leg if it exists
                            if (i + 1 < legs.size && legs[i + 1] is Route.VectorLeg) route.removeIndex(i + 1)
                            route.insert(i + 1, newHold)
                            selectedHoldLeg = newHold
                            // If direct leg was previously set to present position hold due to default hold selection, set it back to first leg
                            (directLeg as? Route.HoldLeg)?.let { selectedHold -> if (selectedHold.wptId.toInt() == -1) directLeg = legs[0] }
                            return@also
                        } else if (this !is Route.HoldLeg && this !is Route.WaypointLeg) return@also // Non waypoint/hold leg reached
                    }
                }
            } else {
                // Present position hold - create/update custom waypoint
                // Check if first leg is already present hold position
                if (route.size >= 1) (route[0] as? Route.HoldLeg)?.apply {
                    if (wptId.toInt() == -1) {
                        // First leg is present position hold
                        inboundHdg = selectedInboundHdg
                        legDist = selectedLegDist
                        turnDir = selectedTurnDir
                        selectedHoldLeg = this
                        return@let
                    } else return@apply
                }
                // Empty route or first leg is not present position hold leg
                // Add a new present hold leg as the first leg (phase will be the same as the subsequent leg, or normal if no subsequent legs exist)
                val phaseToUse = if (route.size > 0) route[0].phase else Route.Leg.NORMAL
                val newHold = Route.HoldLeg(-1, null, null, 230, 240, selectedInboundHdg, selectedLegDist, selectedTurnDir, phaseToUse)
                route.insert(0, newHold)
                selectedHoldLeg = newHold
                directLeg = newHold
            }
        }
    }

    /**
     * Sets the parameters of the hold leg to be the same as published holds
     * @param holdWpt the hold leg
     * */
    private fun setHoldAsPublished(holdWpt: Route.HoldLeg) {
        val name = CLIENT_SCREEN?.waypoints?.get(holdWpt.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName ?: return
        val publishedHold = CLIENT_SCREEN?.publishedHolds?.get(name)?.entity?.get(PublishedHoldInfo.mapper) ?: return // Return if no published hold found
        holdWpt.apply {
            maxAltFt = publishedHold.maxAltFt
            minAltFt = publishedHold.minAltFt
            maxSpdKtLower = publishedHold.maxSpdKtLower
            maxSpdKtHigher = publishedHold.maxSpdKtHigher
            inboundHdg = publishedHold.inboundHdgDeg
            legDist = publishedHold.legDistNm
            turnDir = publishedHold.turnDir
        }
    }

    /** Updates the "As Published" and "Custom" buttons state for the selected hold waypoint */
    private fun updateAsPublishedStatus() {
        selectedHoldLeg?.apply {
            val name = if (wptId.toInt() == -1) "" else CLIENT_SCREEN?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName ?: return
            val pubHold = CLIENT_SCREEN?.publishedHolds?.get(name)?.entity?.get(PublishedHoldInfo.mapper)
            modificationInProgress = true
            if (pubHold == null || pubHold.maxAltFt != maxAltFt || pubHold.minAltFt != minAltFt || pubHold.inboundHdgDeg != inboundHdg ||
                pubHold.maxSpdKtLower != maxSpdKtLower || pubHold.maxSpdKtHigher != maxSpdKtHigher || pubHold.legDistNm != legDist || pubHold.turnDir != turnDir) {
                // No published hold, or selected hold differs from published hold
                holdAsPublishedButton.isChecked = false
                holdCustomButton.isChecked = true
            } else {
                holdAsPublishedButton.isChecked = true
                holdCustomButton.isChecked = false
            }
            modificationInProgress = false
        }
    }
}