package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.DepartureAircraft
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.systems.RenderingSystem
import com.bombbird.terminalcontrol2.utilities.MathTools
import com.bombbird.terminalcontrol2.utilities.safeStage
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.graphics.moveTo
import ktx.math.ImmutableVector2
import ktx.scene2d.actors
import kotlin.math.min

/** Main class for the display of the in-game radar screen
 *
 * Contains the stage for the actors required in the radar screen
 *
 * Also contains the stage for drawing the UI overlay
 *
 * Implements [GestureListener] and [InputProcessor] to handle input/gesture events to it
 * */
class RadarScreen: KtxScreen, GestureListener, InputProcessor {
    private val radarDisplayStage = safeStage(Constants.GAME.batch)
    private val uiStage = safeStage(Constants.GAME.batch)
    // var uiContainer: KContainer<Actor>
    private val shapeRenderer = ShapeRenderer()

    private val gestureDetector = GestureDetector(40f, 0.2f, 1.1f, 0.15f, this)
    private val inputMultiplexer = InputMultiplexer()

    // Camera animation parameters
    private var cameraAnimating = false
    private var targetZoom: Float
    private var zoomRate = 0f
    private var targetCenter: Vector2
    private var panRate: ImmutableVector2

    init {
        uiStage.actors {
            // uiContainer = container {  }
        }
        // Default zoom is set so that a full 100nm by 100nm square is visible (2500px x 2500px)
        (radarDisplayStage.camera as OrthographicCamera).apply {
            zoom = MathTools.nmToPx(Constants.DEFAULT_ZOOM_NM) / Variables.UI_HEIGHT
            targetZoom = zoom
            // Default camera position is set at (0, 0)
            targetCenter = Vector2()
            panRate = ImmutableVector2(0f, 0f)
            moveTo(targetCenter)
        }

        Constants.ENGINE.addSystem(RenderingSystem(shapeRenderer, radarDisplayStage))

        // Add dummy airport, runways
        val arpt = Airport("TCTP", "Haoyuan", 0f, 0f, 108f)
        arpt.addRunway("05L", -15f, 5f, 49.08f, 3660, 108f)
        arpt.addRunway("05R", 10f, -10f, 49.07f, 3800, 108f)

        // Add dummy aircraft
        val acft = DepartureAircraft("SHIBA1", 10f, -10f, 108f)
    }

    /** Ensures [radarDisplayStage]'s camera parameters are within limits, then updates the camera (and [shapeRenderer]) */
    private fun clampUpdateCamera() {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            zoom = MathUtils.clamp(zoom, MathTools.nmToPx(Constants.MIN_ZOOM_NM) / Variables.UI_HEIGHT, MathTools.nmToPx(Constants.MAX_ZOOM_NM) / Variables.UI_HEIGHT)
            update()

            // shapeRenderer will follow radarDisplayCamera
            shapeRenderer.projectionMatrix = combined
        }
    }

    /** Initiates animation of [radarDisplayStage]'s camera to the new position, as well as a new zoom depending on current zoom value */
    private fun initiateCameraAnimation(targetScreenX: Float, targetScreenY: Float) {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val worldCoord = unproject(Vector3(targetScreenX, targetScreenY, 0f))
            targetCenter.x = worldCoord.x
            targetCenter.y = worldCoord.y
            targetZoom = if (zoom > (MathTools.nmToPx(Constants.ZOOM_THRESHOLD_NM) / Variables.UI_HEIGHT)) MathTools.nmToPx(Constants.DEFAULT_ZOOM_IN_NM) / Variables.UI_HEIGHT
            else MathTools.nmToPx(Constants.DEFAULT_ZOOM_NM) / Variables.UI_HEIGHT
            zoomRate = (targetZoom - zoom) / Constants.CAM_ANIM_TIME
            panRate = ImmutableVector2((targetCenter.x - position.x), (targetCenter.y - position.y)).times(1 / Constants.CAM_ANIM_TIME)
            cameraAnimating = true
        }
    }

    /** Shifts [radarDisplayStage]'s camera by an amount depending on the time passed since last frame, and the zoom, pan rate calculated in [initiateCameraAnimation] */
    private fun runCameraAnimations(delta: Float) {
        if (!cameraAnimating) return
        (radarDisplayStage.camera as OrthographicCamera).apply {
            if ((zoomRate < 0 && zoom > targetZoom) || (zoomRate > 0 && zoom < targetZoom)) {
                val neededDelta = min((targetZoom - zoom) / zoomRate, delta)
                zoom += zoomRate * neededDelta
                val actlPanRate = panRate.times(neededDelta)
                translate(actlPanRate.x, actlPanRate.y)
                clampUpdateCamera()
            } else cameraAnimating = false
        }
    }

    /** Sets [Gdx.input]'s inputProcessors to [inputMultiplexer], which consists of [uiStage], [radarDisplayStage], [gestureDetector] and this [RadarScreen] */
    override fun show() {
        inputMultiplexer.addProcessor(uiStage)
        inputMultiplexer.addProcessor(radarDisplayStage)
        inputMultiplexer.addProcessor(gestureDetector)
        inputMultiplexer.addProcessor(this)
        Gdx.input.inputProcessor = inputMultiplexer
    }

    /** Main rendering function, updates engine, draws using [shapeRenderer] followed by [radarDisplayStage] and finally [uiStage], every loop */
    override fun render(delta: Float) {
        clearScreen(red = 0f, green = 0f, blue = 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0)

        runCameraAnimations(delta)
        Constants.ENGINE.update(delta)

        // TODO Draw radar display

        // Constants.GAME.batch.projectionMatrix = uiStage.camera.combined
        // TODO Draw UI
    }

    /** Clears and disposes of [radarDisplayStage], [uiStage] and [shapeRenderer] */
    override fun dispose() {
        radarDisplayStage.clear()
        uiStage.clear()

        radarDisplayStage.disposeSafely()
        uiStage.disposeSafely()
        shapeRenderer.disposeSafely()
    }

    /** Updates various global [Constants] and [Variables] upon a screen resize, to ensure UI will fit to the new screen size
     *
     * Updates the viewport and camera's projectionMatrix of [radarDisplayStage], [uiStage] and [shapeRenderer]
     * */
    override fun resize(width: Int, height: Int) {
        ScreenSize.updateScreenSizeParameters(width, height)
        radarDisplayStage.viewport.update(width, height)
        uiStage.viewport.update(width, height)
//        uiContainer.apply {
//            setSize(Variables.UI_WIDTH, Variables.UI_HEIGHT)
//            setPosition(Constants.WORLD_WIDTH / 2 - Variables.UI_WIDTH / 2, Constants.WORLD_HEIGHT / 2 - Variables.UI_HEIGHT / 2)
//        } TODO move uiContainer to separate UI class and call its resize function from here
        // shapeRenderer will follow radarDisplayCamera
        shapeRenderer.projectionMatrix = radarDisplayStage.camera.combined
    }

    /** Implements [GestureListener.touchDown], no action required */
    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        return false
    }

    /** Implements [GestureListener.tap], used to unselect an aircraft, and to test for double taps for zooming */
    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        // TODO Unselect aircraft, double tap zoom/animate
        if (count == 2 && !cameraAnimating) initiateCameraAnimation(x, y)
        return true
    }

    /** Implements [GestureListener.longPress], no action required */
    override fun longPress(x: Float, y: Float): Boolean {
        return false
    }

    /** Implements [GestureListener.fling], no action required */
    override fun fling(velocityX: Float, velocityY: Float, button: Int): Boolean {
        return false
    }

    /** Implements [GestureListener.pan], pans the camera on detecting pan gesture */
    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            translate(-deltaX * zoom * Variables.UI_WIDTH / Variables.WIDTH, deltaY * zoom * Variables.UI_HEIGHT / Variables.HEIGHT)
        }
        clampUpdateCamera()
        return true
    }

    /** Implements [GestureListener.panStop], no action required */
    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        return false
    }

    /** Implements [GestureListener.zoom], changes camera zoom on pinch (mobile) */
    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        // TODO Camera zoom
        return true
    }

    /** Implements [GestureListener.pinch], no action required */
    override fun pinch(
        initialPointer1: Vector2?,
        initialPointer2: Vector2?,
        pointer1: Vector2?,
        pointer2: Vector2?
    ): Boolean {
        return false
    }

    /** Implements [GestureListener.pinchStop], no action required */
    override fun pinchStop() {
        return
    }

    /** Implements [InputProcessor.keyDown], no action required */
    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    /** Implements [InputProcessor.keyUp], no action required */
    override fun keyUp(keycode: Int): Boolean {
        return false
    }

    /** Implements [InputProcessor.keyTyped], no action required */
    override fun keyTyped(character: Char): Boolean {
        return false
    }

    /** Implements [InputProcessor.touchDown], no action required */
    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    /** Implements [InputProcessor.touchUp], no action required */
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    /** Implements [InputProcessor.touchDragged], no action required */
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return false
    }

    /** Implements [InputProcessor.mouseMoved], no action required */
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    /** Implements [InputProcessor.scrolled], changes camera zoom on scroll (desktop) */
    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        (radarDisplayStage.camera as OrthographicCamera).zoom += amountY * 0.05f
        clampUpdateCamera()
        return true
    }
}