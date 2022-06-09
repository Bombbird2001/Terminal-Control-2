package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.ui.safeStage
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.scene2d.*
import kotlin.math.max

/** A basic screen extending [KtxScreen], implementing some lower level functionalities
 *
 * Should not be instantiated on its own, but rather extended from in other screen classes to implement their full functionality
 * */
abstract class BasicUIScreen: KtxScreen {
    val stage = safeStage(GAME.batch)
    lateinit var container: KContainer<Actor>

    init {
        stage.actors {
            // Background image
            if (BG_INDEX > 0) image(GAME.assetStorage.get<Texture>("Images/${BG_INDEX}.png")) {
                scaleBy(max(WORLD_WIDTH / width, WORLD_HEIGHT / height) - 1)
                x = WORLD_WIDTH / 2 - width * scaleX / 2
                y = WORLD_HEIGHT / 2 - height * scaleY / 2
            }
        }
    }

    /** Sets [Gdx.input]'s inputProcessors to [stage] of this screen */
    override fun show() {
        Gdx.input.inputProcessor = stage
    }

    /** Updates [stage] and draws it every render */
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.7f, 0.7f, 0.7f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0)
        stage.act(delta)
        stage.draw()
    }

    /** Clears and disposes of [stage] */
    override fun dispose() {
        stage.clear()
        stage.disposeSafely()
    }

    /** Updates various global constants, variables upon a screen resize, to ensure UI will fit to the new screen size
     *
     * Updates the [stage]'s viewport, and its camera's projectionMatrix, and resizes the root UI [container] to fit the new dimensions
     * */
    override fun resize(width: Int, height: Int) {
        ScreenSize.updateScreenSizeParameters(width, height)
        stage.viewport.update(width, height)
        stage.batch.projectionMatrix = stage.camera.combined
        container.apply {
            setSize(UI_WIDTH, UI_HEIGHT)
            setPosition(WORLD_WIDTH / 2 - UI_WIDTH / 2, WORLD_HEIGHT / 2 - UI_HEIGHT / 2)
        }
    }
}