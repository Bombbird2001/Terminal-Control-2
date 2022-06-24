package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ScalingViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.bombbird.terminalcontrol2.global.*
import ktx.scene2d.*

/**
 * A thread safe implementation of [Stage]
 *
 * Creates a new [SpriteBatch] instance if not provided, but it is recommended to pass an existing instance as they are resource intensive.
 *
 * Uses the default [Viewport] created by [getDefaultViewport] if not provided
 */
fun safeStage(batch: Batch = SpriteBatch(), viewport: Viewport = getDefaultViewport()) = object: Stage(viewport, batch) {

    /** Overrides [Stage.addActor] method to ensure it is delegated to the main rendering thread if not already so */
    override fun addActor(actor: Actor) {
        if (isOnMainThread()) {
            super.addActor(actor)
        } else Gdx.app.postRunnable { super.addActor(actor) }
    }

    /** Overrides [Stage.draw] method to ensure it is delegated to the main rendering thread if not already so */
    override fun draw() {
        if (isOnMainThread()) {
            super.draw()
        } else Gdx.app.postRunnable { super.draw() }
    }

    /** Checks whether code is running on main rendering thread */
    private fun isOnMainThread(): Boolean {
        return Thread.currentThread().name == "main" || Thread.currentThread().name.contains("GLThread")
    }
}

/** Creates a new [ScalingViewport] set to [Scaling.fill], with world size of [WORLD_WIDTH] by [WORLD_HEIGHT] */
private fun getDefaultViewport() = ScalingViewport(
    Scaling.fill,
    WORLD_WIDTH,
    WORLD_HEIGHT,
    OrthographicCamera()
)

/** Removes the mouse scroll input listener from the [ScrollPane] to prevent nuisance pane scrolls when scrolling the mouse */
fun ScrollPane.removeMouseScrollListeners() {
    var listenerToRemove: InputListener? = null
    for (listener in listeners) if (listener is InputListener) {
        listenerToRemove = listener
        break
    }
    removeListener(listenerToRemove)
}

/** Adds a change listener to the [Actor], running the input [function] when the listener is notified */
inline fun Actor.addChangeListener(crossinline function: (ChangeEvent?, Actor?) -> Unit) {
    addListener(object: ChangeListener() {
        override fun changed(event: ChangeEvent?, actor: Actor?) {
            function(event, actor)
        }
    })
}

/**
 * Removes the default click listener, and adds a new click listener which stops the click event even if the select
 * box is disabled to prevent nuisance click through
 * */
fun <T> SelectBox<T>.disallowDisabledClickThrough() {
    removeListener(clickListener)
    addListener(object : ClickListener() {
        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (pointer == 0 && button != 0) return false
            if (isDisabled) return true
            if (scrollPane.hasParent()) hideScrollPane() else showScrollPane()
            return true
        }
    })
}

@Scene2dDsl
/**
 * Constructs and returns a default label for settings pages' options
 * @param text the text to display
 * */
fun KTableWidget.defaultSettingsLabel(text: String): Label {
    return label(text, "SettingsOption").cell(width = BUTTON_WIDTH_BIG / 2, height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 30f).apply {
        setAlignment(Align.right)
        wrap = true
    }
}

@Scene2dDsl
/** Constructs and returns a default select box for settings pages */
fun <T> KTableWidget.defaultSettingsSelectBox(): KSelectBox<T> {
    return selectBox<T>("Settings").apply {
        list.alignment = Align.center
        setAlignment(Align.center)
    }.cell(width = BUTTON_WIDTH_BIG / 2, height = BUTTON_HEIGHT_BIG / 1.5f)
}

@Scene2dDsl
/** Constructs and returns a default select box for time settings for night mode */
fun <T> KTableWidget.nightModeTimeSelectBox(): KSelectBox<T> {
    return selectBox<T>("Settings").apply {
        list.alignment = Align.center
        setAlignment(Align.center)
    }.cell(width = 100f, height = BUTTON_HEIGHT_BIG / 1.5f, padRight = 20f)
}

@Scene2dDsl
/** Creates a new row with default spacing for settings pane */
fun KTableWidget.newSettingsRow() {
    row().padTop(30f)
}
