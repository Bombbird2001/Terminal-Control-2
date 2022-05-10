package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.utilities.removeMouseScrollListeners
import ktx.collections.toGdxArray
import ktx.scene2d.*

lateinit var routeEditTable: KTableWidget

@Scene2dDsl
fun <S> KWidget<S>.routeEditPane(paneWidth: Float, setToControlPane: () -> Unit): KContainer<Actor> {
    return container {
        fill()
        setSize(paneWidth, Variables.UI_HEIGHT)
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
            }.cell(growX = true, height = 0.1f * Variables.UI_HEIGHT)
            row()
            scrollPane("ControlPaneRoute") {
                routeEditTable = table {
                    // debugAll()
                    label("WPT01", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, height = 0.125f * Variables.UI_HEIGHT, padLeft = 10f, padRight = 10f)
                    textButton("9000B\n5000A", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                    textButton("250kts", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                    textButton("SKIP", "ControlPaneSelected").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                    row()
                    label("WPT02", "ControlPaneRoute").apply { setAlignment(Align.center) }.cell(growX = true, height = 0.125f * Variables.UI_HEIGHT, padLeft = 10f, padRight = 10f)
                    textButton("3000A", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                    textButton("230kts", "ControlPaneRestr").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                    textButton("SKIP", "ControlPaneSelected").cell(growX = true, preferredWidth = 0.25f * paneWidth, height = 0.125f * Variables.UI_HEIGHT)
                    align(Align.top)
                }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.8f * Variables.UI_HEIGHT - 40f, grow = true, padTop = 20f, padBottom = 20f, align = Align.top)
            row()
            table {
                textButton("Undo", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth)
                textButton("Confirm", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).addListener(object: ChangeListener() {
                    override fun changed(event: ChangeEvent?, actor: Actor?) {
                        setToControlPane()
                    }
                })
            }.cell(growX = true, height = 0.1f * Variables.UI_HEIGHT)
        }
        isVisible = false
        setSize(paneWidth, Variables.UI_HEIGHT)
    }
}