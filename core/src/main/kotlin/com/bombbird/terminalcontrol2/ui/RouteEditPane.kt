package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.utilities.removeMouseScrollListeners
import ktx.collections.toGdxArray
import ktx.scene2d.*

lateinit var routeEditTable: KTableWidget

/**
 * @param paneWidth will be used as the reference width of the UI pane when initialising the container
 * @return a [KContainer] used to contain a table with the elements of the route edit pane, which has been added to the [KWidget]
 * */
@Scene2dDsl
fun <S> KWidget<S>.routeEditPane(paneWidth: Float, setToControlPane: () -> Unit): KContainer<Actor> {
    return container {
        fill()
        setSize(paneWidth, UI_HEIGHT)
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
            }.cell(growX = true, height = 0.1f * UI_HEIGHT)
            row()
            scrollPane("ControlPaneRoute") {
                routeEditTable = table {
                    // debugAll()
                    align(Align.top)
                }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.8f * UI_HEIGHT - 40f, grow = true, padTop = 20f, padBottom = 20f, align = Align.top)
            row()
            table {
                textButton("Undo", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth)
                textButton("Confirm", "ControlPaneButton").cell(grow = true, preferredWidth = 0.5f * paneWidth).addListener(object: ChangeListener() {
                    override fun changed(event: ChangeEvent?, actor: Actor?) {
                        setToControlPane()
                    }
                })
            }.cell(growX = true, height = 0.1f * UI_HEIGHT)
        }
        isVisible = false
        setSize(paneWidth, UI_HEIGHT)
    }
}