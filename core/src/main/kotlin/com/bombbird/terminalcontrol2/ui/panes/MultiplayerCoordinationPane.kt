package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.components.AircraftHandoverCoordinationRequest
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.KContainer
import ktx.scene2d.KTextButton
import ktx.scene2d.KWidget
import ktx.scene2d.Scene2dDsl
import ktx.scene2d.checkBox
import ktx.scene2d.container
import ktx.scene2d.label
import ktx.scene2d.selectBox
import ktx.scene2d.table
import ktx.scene2d.textButton

/** Helper object for UI pane's multiplayer coordination pane */
class MultiplayerCoordinationPane {
    companion object {
        const val POINT_OUT = "Point Out"
    }

    lateinit var parentPane: UIPane

    lateinit var altAtOrAboveButton: KTextButton
    lateinit var altAtButton: KTextButton
    lateinit var altAtOrBelowButton: KTextButton

    var setAltitude = false
    var altitudeConstraint = AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
    var setHeading = false
    var setSpeed = false
    var speedConstraint = AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
    var setApproach = false

    @Scene2dDsl
    fun multiplayerCoordinationPane(uiPane: UIPane, widget: KWidget<Actor>, paneWidth: Float): KContainer<Actor> {
        parentPane = uiPane
        return widget.container {
            fill()
            setSize(paneWidth, UI_HEIGHT)
            // debugAll()
            table {
                table {
                    // Point out button
                    val cooldownTimer = Timer()
                    textButton(POINT_OUT, "PointOutButton").cell(growX = true, pad = 20f, height = 100f).apply {
                        addChangeListener { _, _ ->
                            parentPane.selAircraft?.let {
                                CLIENT_SCREEN?.sendPointOutRequest(it, false)

                                // Disable button for 10 seconds
                                isDisabled = true
                                cooldownTimer.clear()
                                cooldownTimer.scheduleTask(object : Timer.Task() {
                                    override fun run() {
                                        isDisabled = false
                                    }
                                }, 10f)
                            }
                        }
                    }
                    row()
                    label("Request handover coordination", "CoordinationPaneHeader").cell(pad = 30f)
                    row()
                    checkBox("   Altitude", "CoordinationCheckbox").cell(growX = true, pad = 20f).apply {
                        addChangeListener { _, _ ->
                            setAltitude = isChecked
                            altAtOrAboveButton.isDisabled = !isChecked
                            altAtButton.isDisabled = !isChecked
                            altAtOrBelowButton.isDisabled = !isChecked
                        }
                    }
                    row()
                    table {
                        // Altitude settings
                        table {
                            altAtOrAboveButton = textButton("At or above", "CoordinationPaneConstraintLight").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = false
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else {
                                        altitudeConstraint = AircraftHandoverCoordinationRequest.CONSTRAINT_GREATER_EQUAL
                                        uncheckOtherButtons(this)
                                    }
                                }
                            }.cell(growX = true, width = 200f, height = 75f)
                            row()
                            altAtButton = textButton("At", "CoordinationPaneConstraint").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = true
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else {
                                        altitudeConstraint = AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
                                        uncheckOtherButtons(this)
                                    }
                                }
                            }.cell(growX = true, width = 200f, height = 75f)
                            row()
                            altAtOrBelowButton = textButton("At or below", "CoordinationPaneConstraintLight").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = false
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else {
                                        altitudeConstraint = AircraftHandoverCoordinationRequest.CONSTRAINT_LESS_EQUAL
                                        uncheckOtherButtons(this)
                                    }
                                }
                            }.cell(growX = true, width = 200f, height = 75f)
                        }.cell(growX = true, padRight = 20f)
                        selectBox<String>("CoordinationPane").cell(growX = true)
                    }
                }
            }
            isVisible = false
        }
    }

    private fun uncheckOtherButtons(button: KTextButton) {
        if (button != altAtOrAboveButton) altAtOrAboveButton.isChecked = false
        if (button != altAtButton) altAtButton.isChecked = false
        if (button != altAtOrBelowButton) altAtOrBelowButton.isChecked = false
    }
}