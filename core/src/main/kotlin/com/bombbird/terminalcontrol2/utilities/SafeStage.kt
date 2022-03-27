package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ScalingViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.bombbird.terminalcontrol2.global.Constants

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

/** Creates a new [ScalingViewport] set to [Scaling.fill], with world size of [Constants.WORLD_WIDTH] by [Constants.WORLD_HEIGHT] */
private fun getDefaultViewport() = ScalingViewport(
    Scaling.fill,
    Constants.WORLD_WIDTH,
    Constants.WORLD_HEIGHT,
    OrthographicCamera()
)