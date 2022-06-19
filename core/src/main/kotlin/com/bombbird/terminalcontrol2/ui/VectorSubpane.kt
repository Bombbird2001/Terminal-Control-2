package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.compareLegEquality
import com.bombbird.terminalcontrol2.utilities.getAfterWptHdgLeg
import com.bombbird.terminalcontrol2.utilities.modulateHeading
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.scene2d.*
import kotlin.math.ceil
import kotlin.math.roundToInt

class VectorSubpane {
    private lateinit var parentControlPane: ControlPane
    private val parentPane: UIPane
        get() = parentControlPane.parentPane
    private var modificationInProgress: Boolean
        get() = parentControlPane.modificationInProgress
        set(value) {
            parentControlPane.modificationInProgress = value
        }
    private var afterWptHdgLeg: Route.WaypointLeg?
        get() = parentControlPane.afterWptHdgLeg
        set(value) {
            parentControlPane.afterWptHdgLeg = value
        }

    private lateinit var vectorTable: KTableWidget
    private lateinit var vectorLabel: Label
    private lateinit var leftButton: KTextButton
    private lateinit var rightButton: KTextButton
    private lateinit var afterWaypointSelectBox: KSelectBox<String>

    private lateinit var left90Button: KTextButton
    private lateinit var left10Button: KTextButton
    private lateinit var left5Button: KTextButton
    private lateinit var right90Button: KTextButton
    private lateinit var right10Button: KTextButton
    private lateinit var right5Button: KTextButton

    var isVisible: Boolean
        get() = vectorTable.isVisible
        set(value) {
            vectorTable.isVisible = value
        }
    val actor: Actor
        get() = vectorTable

    /**
     * @param controlPane the parent [ControlPane] to refer to
     * @param widget the widget to add this vector table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the table
     * @return a [KTableWidget] used to contain the elements of the vector sub-pane, which has been added to the [KWidget]
     * */
    @Scene2dDsl
    fun vectorTable(controlPane: ControlPane, widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        parentControlPane = controlPane
        vectorTable = widget.table {
            table {
                leftButton = textButton("Left", "ControlPaneHdgDir").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        modificationInProgress = true
                        if (isChecked) {
                            afterWptHdgLeg?.apply { getAfterWptHdgLeg(this, parentPane.userClearanceState.route)?.turnDir = CommandTarget.TURN_LEFT } ?:
                            run { parentPane.userClearanceState.vectorTurnDir = CommandTarget.TURN_LEFT }
                            rightButton.isChecked = false
                        } else {
                            afterWptHdgLeg?.apply { getAfterWptHdgLeg(this, parentPane.userClearanceState.route)?.turnDir = CommandTarget.TURN_DEFAULT } ?:
                            run { parentPane.userClearanceState.vectorTurnDir = CommandTarget.TURN_DEFAULT }
                        }
                        updateVectorTable(parentPane.userClearanceState.route, parentPane.userClearanceState.vectorHdg, parentPane.userClearanceState.vectorTurnDir)
                        parentControlPane.updateUndoTransmitButtonStates()
                        modificationInProgress = false
                    }
                }
                afterWaypointSelectBox = selectBox<String>("ControlPane") {
                    setAlignment(Align.center)
                    list.alignment = Align.center
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        updateVectorHdgClearanceState(0, parentPane.userClearanceState.route, afterWptHdgLeg)
                        parentControlPane.updateUndoTransmitButtonStates()
                    }
                    disallowDisabledClickThrough()
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth)
                rightButton = textButton("Right", "ControlPaneHdgDir").cell(grow = true, preferredWidth = 0.3f * paneWidth - 10f).apply {
                    addChangeListener { _, _ ->
                        if (modificationInProgress) return@addChangeListener
                        modificationInProgress = true
                        if (isChecked) {
                            afterWptHdgLeg?.apply { getAfterWptHdgLeg(this, parentPane.userClearanceState.route)?.turnDir = CommandTarget.TURN_RIGHT } ?:
                            run { parentPane.userClearanceState.vectorTurnDir = CommandTarget.TURN_RIGHT }
                            leftButton.isChecked = false
                        } else {
                            afterWptHdgLeg?.apply { getAfterWptHdgLeg(this, parentPane.userClearanceState.route)?.turnDir = CommandTarget.TURN_DEFAULT } ?:
                            run { parentPane.userClearanceState.vectorTurnDir = CommandTarget.TURN_DEFAULT }
                        }
                        updateVectorTable(parentPane.userClearanceState.route, parentPane.userClearanceState.vectorHdg, parentPane.userClearanceState.vectorTurnDir)
                        parentControlPane.updateUndoTransmitButtonStates()
                        modificationInProgress = false
                    }
                }
            }.cell(padTop = 20f, height = 0.1f * UI_HEIGHT, padLeft = 10f, padRight = 10f)
            row()
            table {
                table {
                    left90Button = textButton("-90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).apply {
                        addChangeListener { _, _ ->
                            updateVectorHdgClearanceState(-90, parentPane.userClearanceState.route, afterWptHdgLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    }
                    row()
                    left10Button = textButton("-10", "ControlPaneHdgLight").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).apply {
                        addChangeListener { _, _ ->
                            updateVectorHdgClearanceState(-10, parentPane.userClearanceState.route, afterWptHdgLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    }
                    row()
                    left5Button = textButton("-5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).apply {
                        addChangeListener { _, _ ->
                            updateVectorHdgClearanceState(-5, parentPane.userClearanceState.route, afterWptHdgLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    }
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padLeft = 10f)
                vectorLabel = label("360", "ControlPaneHdg").apply { setAlignment(Align.center) }.cell(grow = true, preferredWidth = 0.3f * paneWidth - 20f)
                table {
                    right90Button = textButton("+90", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).apply {
                        addChangeListener { _, _ ->
                            updateVectorHdgClearanceState(90, parentPane.userClearanceState.route, afterWptHdgLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    }
                    row()
                    right10Button = textButton("+10", "ControlPaneHdgLight").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).apply {
                        addChangeListener { _, _ ->
                            updateVectorHdgClearanceState(10, parentPane.userClearanceState.route, afterWptHdgLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    }
                    row()
                    right5Button = textButton("+5", "ControlPaneHdgDark").cell(grow = true, preferredHeight = (0.5f * UI_HEIGHT - 40f) / 3).apply {
                        addChangeListener { _, _ ->
                            updateVectorHdgClearanceState(5, parentPane.userClearanceState.route, afterWptHdgLeg)
                            parentControlPane.updateUndoTransmitButtonStates()
                        }
                    }
                }.cell(grow = true, preferredWidth = 0.4f * paneWidth, padRight = 10f)
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT - 40f, padBottom = 20f)
            isVisible = false
        }
        return vectorTable
    }

    /**
     * Updates the heading display in [vectorTable]
     * @param route the route to refer to; should be the aircraft's latest cleared route or user input route
     * @param vectorHdg the currently selected vector heading
     * @param vectorTurnDir the currently selected turn direction
     * */
    fun updateVectorTable(route: Route, vectorHdg: Short?, vectorTurnDir: Byte?) {
        modificationInProgress = true
        afterWaypointSelectBox.items = GdxArray<String>().apply {
            add("Now")
            for (i in 0 until parentPane.userClearanceState.route.size) {
                (parentPane.userClearanceState.route[i] as? Route.WaypointLeg)?.let {
                    val wptName = GAME.gameClientScreen?.waypoints?.get(it.wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
                    add("After $wptName")
                }
            }
        }
        var leftChanged = false
        var rightChanged = false
        afterWptHdgLeg?.apply {
            val newAftWptLeg = getAfterWptHdgLeg(this, route) ?: return@apply
            vectorLabel.setText(newAftWptLeg.heading.toString())
            val prevAftWptLeg = getAfterWptHdgLeg(this, parentPane.clearanceState.route)
            vectorLabel.style = Scene2DSkin.defaultSkin["ControlPaneHdg${if (prevAftWptLeg?.heading != newAftWptLeg.heading) "Changed" else ""}", Label.LabelStyle::class.java]
            val wptName = GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            afterWaypointSelectBox.selected = if (wptName == null) "Now" else "After $wptName"
            // Set style as changed if new after waypoint leg does not yet exist in acting clearance
            afterWaypointSelectBox.style = Scene2DSkin.defaultSkin[if (wptName == null || getAfterWptHdgLeg(wptName, parentPane.clearanceState.route) == null) "ControlPaneChanged" else "ControlPane", SelectBoxStyle::class.java]
            leftButton.isChecked = newAftWptLeg.turnDir == CommandTarget.TURN_LEFT
            rightButton.isChecked = newAftWptLeg.turnDir == CommandTarget.TURN_RIGHT
            leftChanged = (prevAftWptLeg?.turnDir == CommandTarget.TURN_LEFT && newAftWptLeg.turnDir == CommandTarget.TURN_DEFAULT) ||
                    (newAftWptLeg.turnDir == CommandTarget.TURN_LEFT && prevAftWptLeg?.turnDir != CommandTarget.TURN_LEFT)
            rightChanged = (prevAftWptLeg?.turnDir == CommandTarget.TURN_RIGHT && newAftWptLeg.turnDir == CommandTarget.TURN_DEFAULT) ||
                    (newAftWptLeg.turnDir == CommandTarget.TURN_RIGHT && prevAftWptLeg?.turnDir != CommandTarget.TURN_RIGHT)
        } ?: run {
            vectorLabel.setText(vectorHdg?.toString() ?: "0")
            vectorLabel.style = Scene2DSkin.defaultSkin["ControlPaneHdg${if (parentPane.clearanceState.vectorHdg != parentPane.userClearanceState.vectorHdg) "Changed" else ""}", Label.LabelStyle::class.java]
            afterWaypointSelectBox.selectedIndex = 0
            afterWaypointSelectBox.style = Scene2DSkin.defaultSkin[if (parentPane.clearanceState.vectorHdg == null) "ControlPaneChanged" else "ControlPane", SelectBoxStyle::class.java]
            leftButton.isChecked = vectorTurnDir == CommandTarget.TURN_LEFT
            rightButton.isChecked = vectorTurnDir == CommandTarget.TURN_RIGHT
            leftChanged = (parentPane.clearanceState.vectorTurnDir == CommandTarget.TURN_LEFT && parentPane.userClearanceState.vectorTurnDir == CommandTarget.TURN_DEFAULT) ||
                    (parentPane.userClearanceState.vectorTurnDir == CommandTarget.TURN_LEFT && parentPane.clearanceState.vectorTurnDir != CommandTarget.TURN_LEFT)
            rightChanged = (parentPane.clearanceState.vectorTurnDir == CommandTarget.TURN_RIGHT && parentPane.userClearanceState.vectorTurnDir == CommandTarget.TURN_DEFAULT) ||
                    (parentPane.userClearanceState.vectorTurnDir == CommandTarget.TURN_RIGHT && parentPane.clearanceState.vectorTurnDir != CommandTarget.TURN_RIGHT)
        }
        leftButton.style = Scene2DSkin.defaultSkin[if (leftChanged) "ControlPaneHdgDirChanged" else "ControlPaneHdgDir", TextButton.TextButtonStyle::class.java]
        rightButton.style = Scene2DSkin.defaultSkin[if (rightChanged) "ControlPaneHdgDirChanged" else "ControlPaneHdgDir", TextButton.TextButtonStyle::class.java]

        modificationInProgress = false
    }

    /**
     * Updates the user clearance vector heading or after waypoint heading with the input delta value, and updates the
     * [vectorLabel] as well
     * @param change the change in heading that will be added to the user clearance vector heading
     * @param route the route to refer to; should be the aircraft's latest cleared route or user input route
     * @param selectedAftWpt the currently selected after waypoint leg, before the changes from the select box are applied
     * */
    private fun updateVectorHdgClearanceState(change: Short, route: Route, selectedAftWpt: Route.WaypointLeg?) {
        selectedAftWpt?.let {
            // Look for after waypoint vector legs that are present in the selected clearance but not the acting clearance
            var i = 0
            while (i < route.size - 1) {
                (route[i] as? Route.WaypointLeg)?.apply {
                    // Skip if no vector legs after selected waypoint found
                    val nextLeg = route[i + 1]
                    if (nextLeg !is Route.VectorLeg) return@apply
                    // Found in selected clearance
                    for (j in 0 until parentPane.clearanceState.route.size - 1) (parentPane.clearanceState.route[j] as? Route.WaypointLeg)?.also {
                        val actNextLeg = parentPane.clearanceState.route[j + 1]
                        // Also found in acting clearance, don't remove
                        if (compareLegEquality(this, it) && actNextLeg is Route.VectorLeg) return@apply
                    }
                    // Not found in acting clearance, remove from selected clearance
                    route.removeIndex(i + 1)
                    afterWptHdgLeg = null
                }
                i++
            }
        }

        (afterWaypointSelectBox.selected ?: "Now").let { selectedOption ->
            if (selectedOption != "Now") {
                parentPane.userClearanceState.vectorHdg = null
                parentPane.userClearanceState.vectorTurnDir = null
                val wpt = selectedOption.replace("After ", "")
                val newAftWptLeg = getAfterWptHdgLeg(wpt, route)?.let { pairFound ->
                    afterWptHdgLeg = pairFound.second // Also set the selected after waypoint leg to that returned by the method
                    pairFound.first
                } ?: Route.VectorLeg(vectorLabel.text.toString().toShort()).also { addedLeg ->
                    // If no current after waypoint vector leg exists, add one after the selected waypoint
                    for (i in 0 until route.size) (route[i] as? Route.WaypointLeg)?.apply {
                        if (GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName == wpt) {
                            // Remove hold leg if it exists
                            if (i + 1 < route.size && route[i + 1] is Route.HoldLeg) route.removeIndex(i + 1)
                            route.insert(i + 1, addedLeg)
                            afterWptHdgLeg = this
                            return@also
                        }
                    }
                }
                newAftWptLeg.heading = (newAftWptLeg.heading + change).toShort().let {
                    val rectifiedHeading = if (change >= 0) (it / 5f).toInt() * 5 else ceil(it / 5f).roundToInt() * 5
                    modulateHeading(rectifiedHeading.toFloat()).toInt().toShort()
                }
            } else {
                parentPane.userClearanceState.vectorHdg = ((parentPane.userClearanceState.vectorHdg ?: vectorLabel.text.toString().toShort()) + change).toShort().let {
                    val rectifiedHeading = if (change >= 0) (it / 5f).toInt() * 5 else ceil(it / 5f).roundToInt() * 5
                    modulateHeading(rectifiedHeading.toFloat()).toInt().toShort()
                }
                afterWptHdgLeg = null
            }
        }
        updateVectorTable(parentPane.userClearanceState.route, parentPane.userClearanceState.vectorHdg, parentPane.userClearanceState.vectorTurnDir)
    }

    /**
     * Sets whether the heading elements (value buttons, direction buttons, after waypoint select box) are disabled
     * @param disabled whether to disable the heading elements
     */
    fun setHdgElementsDisabled(disabled: Boolean) {
        afterWaypointSelectBox.isDisabled = disabled

        leftButton.isDisabled = disabled
        left90Button.isDisabled = disabled
        left10Button.isDisabled = disabled
        left5Button.isDisabled = disabled

        rightButton.isDisabled = disabled
        right90Button.isDisabled = disabled
        right10Button.isDisabled = disabled
        right5Button.isDisabled = disabled
    }
}