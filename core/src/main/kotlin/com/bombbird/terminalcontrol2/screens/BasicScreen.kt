package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.utilities.safeStage
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.scene2d.*

/** A basic screen extending [KtxScreen], implementing some lower level functionalities
 *
 * Should not be instantiated on its own, but rather extended from in other screen classes to implement their full functionality
 * */
abstract class BasicScreen(game: TerminalControl2): KtxScreen {
    val stage = safeStage(game.batch)
    lateinit var container: KContainer<Actor>

    /** Sets [Gdx.input]'s inputProcessors to [stage] of this screen */
    override fun show() {
        Gdx.input.inputProcessor = stage
    }

    /** Updates [stage] and draws it every render */
    override fun render(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0)
        stage.act(delta)
        stage.draw()
    }

    /** Clears and disposes of [stage] */
    override fun dispose() {
        stage.clear()
        stage.disposeSafely()
    }

    /** Updates various global [Constants] and [Variables] upon a screen resize, to ensure UI will fit to the new screen size
     *
     * Updates the [stage]'s viewport, and its camera's projectionMatrix, and resizes the root UI [container] to fit the new dimensions
     * */
    override fun resize(width: Int, height: Int) {
        ScreenSize.updateScreenSizeParameters(width, height)
        stage.viewport.apply {
            update(width, height)
        }
        stage.batch.projectionMatrix = stage.camera.combined
        container.apply {
            setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
            setPosition(Constants.WORLD_WIDTH / 2 - Variables.UI_WIDTH / 2, Constants.WORLD_HEIGHT / 2 - Variables.UI_HEIGHT / 2)
        }
    }
}