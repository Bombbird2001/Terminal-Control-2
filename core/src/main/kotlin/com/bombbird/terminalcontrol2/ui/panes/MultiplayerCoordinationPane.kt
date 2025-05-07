package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.ui.addChangeListener
import ktx.scene2d.KContainer
import ktx.scene2d.KWidget
import ktx.scene2d.Scene2dDsl
import ktx.scene2d.container
import ktx.scene2d.table
import ktx.scene2d.textButton

/** Helper object for UI pane's multiplayer coordination pane */
class MultiplayerCoordinationPane {
    companion object {
        const val POINT_OUT = "Point Out"
    }

    lateinit var parentPane: UIPane

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
                }
            }
        }
    }
}