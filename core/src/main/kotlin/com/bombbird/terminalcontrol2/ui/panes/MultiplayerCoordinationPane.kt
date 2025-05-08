package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.components.AircraftHandoverCoordinationRequest
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.INTERMEDIATE_ALTS
import com.bombbird.terminalcontrol2.global.MAX_ALT
import com.bombbird.terminalcontrol2.global.MIN_ALT
import com.bombbird.terminalcontrol2.global.TRANS_ALT
import com.bombbird.terminalcontrol2.global.TRANS_LVL
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.disallowDisabledClickThrough
import com.bombbird.terminalcontrol2.utilities.byte
import ktx.collections.GdxArray
import ktx.collections.gdxArrayOf
import ktx.scene2d.KCheckBox
import ktx.scene2d.KContainer
import ktx.scene2d.KSelectBox
import ktx.scene2d.KTextButton
import ktx.scene2d.KWidget
import ktx.scene2d.Scene2dDsl
import ktx.scene2d.checkBox
import ktx.scene2d.container
import ktx.scene2d.label
import ktx.scene2d.selectBoxOf
import ktx.scene2d.table
import ktx.scene2d.textButton

/** Helper object for UI pane's multiplayer coordination pane */
class MultiplayerCoordinationPane {
    companion object {
        const val POINT_OUT = "Point Out"
        const val SEND_REQUEST = "Send request"
    }

    lateinit var parentPane: UIPane

    lateinit var altCheckBox: KCheckBox
    lateinit var altSelectBox: KSelectBox<String>
    lateinit var altAtOrAboveButton: KTextButton
    lateinit var altAtButton: KTextButton
    lateinit var altAtOrBelowButton: KTextButton

    lateinit var hdgCheckBox: KCheckBox
    lateinit var hdgSelectBox1: KSelectBox<Byte>
    lateinit var hdgSelectBox2: KSelectBox<Byte>
    lateinit var hdgSelectBox3: KSelectBox<Byte>

    lateinit var spdCheckBox: KCheckBox
    lateinit var spdSelectBox: KSelectBox<Short>
    lateinit var spdAtOrAboveButton: KTextButton
    lateinit var spdAtButton: KTextButton
    lateinit var spdAtOrBelowButton: KTextButton

    lateinit var appCheckBox: KCheckBox
    lateinit var appSelectBox: KSelectBox<String>

    @Scene2dDsl
    fun multiplayerCoordinationPane(uiPane: UIPane, widget: KWidget<Actor>, paneWidth: Float): KContainer<Actor> {
        parentPane = uiPane
        return widget.container {
            fill()
            setSize(paneWidth, UI_HEIGHT)
            debugAll()
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
                    label("Handover coordination", "CoordinationPaneHeader").cell(pad = 20f)
                    row()
                    // Altitude settings
                    table {
                        altCheckBox = checkBox("   Altitude", "CoordinationCheckbox").cell(growX = true, pad = 20f).apply {
                            addChangeListener { _, _ ->
                                altAtOrAboveButton.isDisabled = !isChecked
                                altAtButton.isDisabled = !isChecked
                                altAtOrBelowButton.isDisabled = !isChecked
                                altSelectBox.isDisabled = !isChecked
                            }
                        }
                        altSelectBox = selectBoxOf(getAltBoxSelections(),"CoordinationPane").apply {
                            list.alignment = Align.center
                            setAlignment(Align.center)
                            isDisabled = true
                            disallowDisabledClickThrough()
                        }.cell(growX = true, height = 100f)
                    }.cell(growX = true, padLeft = 20f, padRight = 20f)
                    row()
                    table {
                        table {
                            altAtOrAboveButton = textButton("At or above", "CoordinationPaneConstraintLight").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = false
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else uncheckOtherAltConstraints(this)
                                }
                            }.cell(growX = true, preferredWidth = 175f, height = 75f, padRight = 10f)
                            altAtButton = textButton("At", "CoordinationPaneConstraint").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = true
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else uncheckOtherAltConstraints(this)
                                }
                            }.cell(growX = true, preferredWidth = 100f, height = 75f, padRight = 10f)
                            altAtOrBelowButton = textButton("At or below", "CoordinationPaneConstraintLight").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = false
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else uncheckOtherAltConstraints(this)
                                }
                            }.cell(preferredWidth = 175f, height = 75f)
                        }.cell(growX = true, padRight = 20f)
                    }.cell(growX = true, padLeft = 20f, padRight = 20f, padTop = 20f)
                    row()
                    // Heading settings
                    hdgCheckBox = checkBox("   Heading", "CoordinationCheckbox").cell(growX = true, pad = 20f).apply {
                        addChangeListener { _, _ ->
                            hdgSelectBox1.isDisabled = !isChecked
                            hdgSelectBox2.isDisabled = !isChecked
                            hdgSelectBox3.isDisabled = !isChecked
                        }
                    }
                    row()
                    table {
                        hdgSelectBox1 = selectBoxOf(gdxArrayOf(0.byte, 1, 2, 3), "CoordinationPane").apply {
                            selected = 3
                            list.alignment = Align.center
                            setAlignment(Align.center)
                            isDisabled = true
                            addChangeListener { _, _ ->
                                modulateWindHdgChoices()
                            }
                            disallowDisabledClickThrough()
                        }.cell(width = 100f, height = 100f, padRight = 10f)
                        hdgSelectBox2 = selectBoxOf(gdxArrayOf(0.byte, 1, 2, 3, 4, 5, 6), "CoordinationPane").apply {
                            selected = 6
                            list.alignment = Align.center
                            setAlignment(Align.center)
                            isDisabled = true
                            addChangeListener { _, _ ->
                                modulateWindHdgChoices()
                            }
                            disallowDisabledClickThrough()
                        }.cell(width = 100f, height = 100f, padRight = 10f)
                        hdgSelectBox3 = selectBoxOf(gdxArrayOf(0.byte, 5), "CoordinationPane").apply {
                            selected = 0
                            list.alignment = Align.center
                            setAlignment(Align.center)
                            isDisabled = true
                            disallowDisabledClickThrough()
                        }.cell(width = 100f, height = 100f)
                    }.cell(growX = true, padLeft = 20f, padRight = 20f)
                    row()
                    // Speed settings
                    table {
                        spdCheckBox = checkBox("   Speed", "CoordinationCheckbox").apply {
                            addChangeListener { _, _ ->
                                spdSelectBox.isDisabled = !isChecked
                                spdAtOrAboveButton.isDisabled = !isChecked
                                spdAtButton.isDisabled = !isChecked
                                spdAtOrBelowButton.isDisabled = !isChecked
                            }
                        }.cell(growX = true, padRight = 20f)
                        val spdChoices = GdxArray<Short>()
                        for (i in 16..28) spdChoices.add((i * 10).toShort())
                        spdSelectBox = selectBoxOf(spdChoices, "CoordinationPane").apply {
                            list.alignment = Align.center
                            setAlignment(Align.center)
                            isDisabled = true
                            disallowDisabledClickThrough()
                        }.cell(preferredWidth = 250f, height = 100f)
                    }.cell(growX = true, padLeft = 20f, padRight = 20f, padTop = 20f)
                    row()
                    table {
                        table {
                            spdAtOrAboveButton = textButton("At or above", "CoordinationPaneConstraintLight").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = false
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else uncheckOtherSpdConstraints(this)
                                }
                            }.cell(growX = true, preferredWidth = 175f, height = 75f, padRight = 10f)
                            spdAtButton = textButton("At", "CoordinationPaneConstraint").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = true
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else uncheckOtherSpdConstraints(this)
                                }
                            }.cell(growX = true, preferredWidth = 100f, height = 75f, padRight = 10f)
                            spdAtOrBelowButton = textButton("At or below", "CoordinationPaneConstraintLight").apply {
                                setProgrammaticChangeEvents(false)
                                isChecked = false
                                isDisabled = true
                                addChangeListener { _, _ ->
                                    if (!isChecked) isChecked = true
                                    else uncheckOtherSpdConstraints(this)
                                }
                            }.cell(preferredWidth = 175f, height = 75f)
                        }.cell(growX = true, padRight = 20f)
                    }.cell(growX = true, padLeft = 20f, padRight = 20f, padTop = 20f)
                    row()
                    // Approach settings
                    table {
                        appCheckBox = checkBox("   Approach", "CoordinationCheckbox").apply {
                            addChangeListener { _, _ ->
                                appSelectBox.isDisabled = !isChecked
                            }
                        }.cell(growX = true, padRight = 20f)
                        appSelectBox = selectBoxOf(gdxArrayOf("None"), "CoordinationPane").apply {
                            list.alignment = Align.center
                            setAlignment(Align.center)
                            isDisabled = true
                            disallowDisabledClickThrough()
                        }.cell(preferredWidth = 250f, height = 100f)
                    }.cell(growX = true, padLeft = 20f, padRight = 20f, padTop = 20f)
                    row()
                    textButton(SEND_REQUEST, "PointOutButton").apply {
                        addChangeListener { _, _ ->
                            sendCoordinationRequest()
                        }
                    }.cell(growX = true, padLeft = 20f, padRight = 20f, padTop = 20f, height = 100f)
                }.cell(preferredWidth = paneWidth, growX = true)
            }
            isVisible = false
        }
    }

    private fun sendCoordinationRequest() {
        parentPane.selAircraft?.let {
            val alt = if (altCheckBox.isChecked) {
                if (altSelectBox.selected.startsWith("FL")) {
                    altSelectBox.selected.substring(2).toInt() * 100
                } else {
                    altSelectBox.selected.toInt()
                }
            } else null
            val altConstraint = when {
                altAtOrAboveButton.isChecked -> AircraftHandoverCoordinationRequest.CONSTRAINT_GREATER_EQUAL
                altAtButton.isChecked -> AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
                altAtOrBelowButton.isChecked -> AircraftHandoverCoordinationRequest.CONSTRAINT_LESS_EQUAL
                else -> AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
            }

            val hdg = if (hdgCheckBox.isChecked) {
                (hdgSelectBox1.selected * 100 + hdgSelectBox2.selected * 10 + hdgSelectBox3.selected).toShort()
            } else null

            val spd = if (spdCheckBox.isChecked) {
                spdSelectBox.selected
            } else null
            val spdConstraint = when {
                spdAtOrAboveButton.isChecked -> AircraftHandoverCoordinationRequest.CONSTRAINT_GREATER_EQUAL
                spdAtButton.isChecked -> AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
                spdAtOrBelowButton.isChecked -> AircraftHandoverCoordinationRequest.CONSTRAINT_LESS_EQUAL
                else -> AircraftHandoverCoordinationRequest.CONSTRAINT_EQUAL
            }

            val app = if (appCheckBox.isChecked) {
                appSelectBox.selected
            } else null

            // TODO Cancelling requests
            if (alt != null || hdg != null || spd != null || app != null) {
                CLIENT_SCREEN?.sendHandoverCoordinationRequest(
                    it, alt, altConstraint,
                    hdg, spd, spdConstraint,
                    app, false
                )
            }
        }
    }

    private fun uncheckOtherAltConstraints(button: KTextButton) {
        if (button != altAtOrAboveButton) altAtOrAboveButton.isChecked = false
        if (button != altAtButton) altAtButton.isChecked = false
        if (button != altAtOrBelowButton) altAtOrBelowButton.isChecked = false
    }

    private fun getAltBoxSelections(): GdxArray<String> {
        val array = GdxArray<String>()
        val roundedMinAlt = (MIN_ALT / 1000 + 1) * 1000
        val roundedMaxAlt = MAX_ALT - MAX_ALT % 1000

        if (MIN_ALT % 1000 > 0) checkAltAndAddToArray(MIN_ALT, array)

        var intermediateQueueIndex = 0
        for (alt in roundedMinAlt .. roundedMaxAlt step 1000) {
            INTERMEDIATE_ALTS.also { while (intermediateQueueIndex < it.size) {
                it[intermediateQueueIndex]?.let { intermediateAlt -> if (intermediateAlt <= alt) {
                    if (intermediateAlt < alt && intermediateAlt in (MIN_ALT + 1) until MAX_ALT) checkAltAndAddToArray(intermediateAlt, array)
                    intermediateQueueIndex++
                } else return@also } ?: intermediateQueueIndex++
            }}
            checkAltAndAddToArray(alt, array)
        }
        if (MAX_ALT % 1000 > 0 && MAX_ALT > MIN_ALT) checkAltAndAddToArray(MAX_ALT, array)

        return array
    }

    private fun checkAltAndAddToArray(alt: Int, array: GdxArray<String>) {
        if (alt > TRANS_ALT && alt < TRANS_LVL * 100) return
        if (alt < TRANS_LVL * 100) array.add(alt.toString())
        else if (alt >= TRANS_LVL * 100) array.add("FL${alt / 100}")
    }

    /**
     * Modulates the choices for the 2nd and 3rd select boxes with the currently
     * selected values
     */
    private fun modulateWindHdgChoices() {
        if (hdgSelectBox1.selected == 3.byte) {
            hdgSelectBox2.setItems(0, 1, 2, 3, 4, 5, 6)
            if (hdgSelectBox2.selected == 6.byte) hdgSelectBox3.setItems(0)
            else hdgSelectBox3.setItems(0, 5)
        } else {
            hdgSelectBox2.setItems(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            hdgSelectBox3.setItems(0, 5)
        }
    }

    private fun uncheckOtherSpdConstraints(button: KTextButton) {
        if (button != spdAtOrAboveButton) spdAtOrAboveButton.isChecked = false
        if (button != spdAtButton) spdAtButton.isChecked = false
        if (button != spdAtOrBelowButton) spdAtOrBelowButton.isChecked = false
    }
}