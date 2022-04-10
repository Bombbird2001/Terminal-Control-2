package com.bombbird.terminalcontrol2.graphics

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.Variables
import ktx.scene2d.*
import kotlin.math.max

/** The main UI panel display that will integrate the main information pane, and the lateral, altitude and speed panes for controlling of aircraft */
class UIPane(private val uiStage: Stage) {
    var uiContainer: KContainer<Actor>
    var paneImage: KImageButton
    val paneWidth: Float
        get() = max(Variables.UI_WIDTH * 0.28f, 400f)

    init {
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                // debugAll()
                setSize(paneWidth, Variables.UI_HEIGHT)
                setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
            }
            uiContainer = container {
                fill()
                setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
                table {
                    // debugAll()
                }.apply {
                    align(Align.left)
                }
                setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
            }
        }
    }

    /** Resize the pane and containers */
    fun resize(width: Int, height: Int) {
        uiStage.viewport.update(width, height)
        uiContainer.apply {
            setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
            setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
        }
        paneImage.apply {
            setSize(paneWidth, Variables.UI_HEIGHT)
            setPosition(-Variables.UI_WIDTH / 2, -Variables.UI_HEIGHT / 2)
        }
    }

    /** Gets the required x offset for radarDisplayStage's camera at a zoom level */
    fun getRadarCameraOffsetForZoom(zoom: Float): Float {
        return -paneWidth / 2 * zoom // TODO Change depending on pane position
    }
}