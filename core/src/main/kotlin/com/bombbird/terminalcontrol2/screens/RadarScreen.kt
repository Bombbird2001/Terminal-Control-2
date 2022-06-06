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
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.networking.AircraftControlStateUpdateData
import com.bombbird.terminalcontrol2.ui.UIPane
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.handleIncomingRequest
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.bombbird.terminalcontrol2.systems.DataSystem
import com.bombbird.terminalcontrol2.systems.PhysicsSystemClient
import com.bombbird.terminalcontrol2.systems.RenderingSystem
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.safeStage
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.graphics.moveTo
import ktx.math.ImmutableVector2
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.min

/** Main class for the display of the in-game radar screen
 *
 * Contains the stage for the actors required in the radar screen
 *
 * Also contains the stage for drawing the UI overlay
 *
 * Implements [GestureListener] and [InputProcessor] to handle input/gesture events to it
 * */
class RadarScreen(connectionHost: String): KtxScreen, GestureListener, InputProcessor {
    private val clientEngine = getEngine(true)
    private val radarDisplayStage = safeStage(GAME.batch)
    private val constZoomStage = safeStage(GAME.batch)
    private val uiStage = safeStage(GAME.batch)
    val uiPane = UIPane(uiStage)
    private val shapeRenderer = ShapeRenderer(3000)

    private val gestureDetector = GestureDetector(40f, 0.2f, 1.1f, 0.15f, this)
    private val inputMultiplexer = InputMultiplexer()

    // Camera animation parameters
    private var cameraAnimating = false
    private var targetZoom: Float
    private var zoomRate = 0f
    private var targetCenter: Vector2
    private var panRate: ImmutableVector2

    // Airport map for access during TCP updates; see GameServer for more details
    val airports = GdxArrayMap<Byte, Airport>(AIRPORT_SIZE)
    val updatedAirportMapping = GdxArrayMap<String, Byte>(AIRPORT_SIZE)

    // Waypoint map for access; see GameServer for more details
    val waypoints = HashMap<Short, Waypoint>()
    val updatedWaypointMapping = HashMap<String, Short>()

    // Published hold map for access
    val publishedHolds = GdxArrayMap<String, PublishedHold>(PUBLISHED_HOLD_SIZE)

    // The primary TMA sector polygon without being split up into sub-sectors
    val primarySector = Polygon()

    // The current active configuration of polygons
    val sectors = GdxArray<Sector>()

    // Aircraft map for access during UDP updates
    val aircraft = GdxArrayMap<String, Aircraft>(AIRCRAFT_SIZE)

    // Selected aircraft:
    var selectedAircraft: Aircraft? = null

    // Networking client
    private val client = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE)

    // Blocking queue to store runnables to be run in the main thread after engine update
    private val pendingRunnablesQueue = ConcurrentLinkedQueue<Runnable>()

    init {
        if (true) GAME.gameServer = GameServer().apply { initiateServer() } // TODO True if single-player or host of multiplayer, false otherwise
        registerClassesToKryo(client.kryo)
        client.start()
        client.addListener(object: Listener {
            override fun received(connection: Connection?, obj: Any?) {
                handleIncomingRequest(this@RadarScreen, obj)
            }
        })
        while (true) {
            try {
                client.connect(5000, connectionHost, TCP_PORT, UDP_PORT)
                break
            } catch (_: IOException) {
                // Workaround for strange behaviour on some devices where the 5000ms timeout is ignored,
                // an IOException is thrown instantly as server has not started up
                Thread.sleep(1000)
            }
        }

        // Default zoom is set so that a full 100nm by 100nm square is visible (2500px x 2500px)
        (radarDisplayStage.camera as OrthographicCamera).apply {
            zoom = nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            targetZoom = zoom
            // Default camera position is set at (0, 0)
            targetCenter = Vector2()
            panRate = ImmutableVector2(0f, 0f)
            moveTo(targetCenter, uiPane.getRadarCameraOffsetForZoom(zoom))
        }
        constZoomStage.camera.moveTo(Vector2())

        clientEngine.addSystem(RenderingSystem(shapeRenderer, radarDisplayStage, constZoomStage, uiStage))
        clientEngine.addSystem(PhysicsSystemClient(1f))
        clientEngine.addSystem(DataSystem())
    }

    /** Adds an [Actor] to [constZoomStage] */
    fun addToConstZoomStage(actor: Actor) {
        constZoomStage.addActor(actor)
    }

    /**
     * Instructs [uiPane] to display the control pane for the supplied aircraft
     * @param aircraft the aircraft to display in the UI pane
     * */
    fun setUISelectedAircraft(aircraft: Aircraft) {
        uiPane.setSelectedAircraft(aircraft)
        selectedAircraft = aircraft
    }

    /** Deselects the currently selected aircraft in [uiPane] */
    fun deselectUISelectedAircraft() {
        uiPane.deselectAircraft()
        selectedAircraft = null
    }

    /** Ensures [radarDisplayStage]'s camera parameters are within limits, then updates the camera (and [shapeRenderer]) */
    private fun clampUpdateCamera(deltaZoom: Float) {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val oldZoom = zoom
            zoom += deltaZoom
            zoom = MathUtils.clamp(zoom, nmToPx(MIN_ZOOM_NM) / UI_HEIGHT, nmToPx(MAX_ZOOM_NM) / UI_HEIGHT)
            translate(uiPane.getRadarCameraOffsetForZoom(zoom - oldZoom), 0f)
            update()

            // shapeRenderer will follow radarDisplayCamera
            shapeRenderer.projectionMatrix = combined
        }
    }

    /** Initiates animation of [radarDisplayStage]'s camera to the new position, as well as a new zoom depending on current zoom value */
    private fun initiateCameraAnimation(targetScreenX: Float, targetScreenY: Float) {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            targetZoom = if (zoom > (nmToPx(ZOOM_THRESHOLD_NM) / UI_HEIGHT)) nmToPx(DEFAULT_ZOOM_IN_NM) / UI_HEIGHT
            else nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            val worldCoord = unprojectFromRadarCamera(targetScreenX, targetScreenY)
            targetCenter.x = worldCoord.x + uiPane.getRadarCameraOffsetForZoom(targetZoom)
            targetCenter.y = worldCoord.y
            zoomRate = (targetZoom - zoom) / CAM_ANIM_TIME
            panRate = ImmutableVector2((targetCenter.x - position.x), (targetCenter.y - position.y)).times(1 / CAM_ANIM_TIME)
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
                clampUpdateCamera(0f)
            } else cameraAnimating = false
        }
    }

    /** Helper function for unprojecting from screen coordinates to camera world coordinates, as unfortunately Camera's unproject function is not accurate in this case */
    private var unprojectFromRadarCamera = fun (screenX: Float, screenY: Float): Vector2 {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val scaleFactor = UI_HEIGHT / HEIGHT // 1px in screen distance = ?px in world distance (at zoom = 1)
            return Vector2((screenX - WIDTH / 2) * zoom * scaleFactor + position.x, (HEIGHT / 2 - screenY) * zoom * scaleFactor + position.y)
        }
    }

    /** Sets [Gdx.input]'s inputProcessors to [inputMultiplexer], which consists of [uiStage], [constZoomStage], [radarDisplayStage], [gestureDetector] and this [RadarScreen] */
    override fun show() {
        inputMultiplexer.addProcessor(uiStage)
        inputMultiplexer.addProcessor(constZoomStage)
        inputMultiplexer.addProcessor(radarDisplayStage)
        inputMultiplexer.addProcessor(gestureDetector)
        inputMultiplexer.addProcessor(this)
        Gdx.input.inputProcessor = inputMultiplexer
    }

    /** Main rendering function, updates engine which draws using [RenderingSystem] every loop */
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0)

        runCameraAnimations(delta)
        clientEngine.update(delta)

        // Process pending runnables
        while (true) { pendingRunnablesQueue.poll()?.run() ?: break }
    }

    /** Clears and disposes of [radarDisplayStage], [constZoomStage], [uiStage], [shapeRenderer], stops the [client] and [GameServer] if present */
    override fun dispose() {
        radarDisplayStage.clear()
        constZoomStage.clear()
        uiStage.clear()

        radarDisplayStage.disposeSafely()
        constZoomStage.disposeSafely()
        uiStage.disposeSafely()
        shapeRenderer.disposeSafely()

        clientEngine.removeAllEntities()
        clientEngine.removeAllSystems()

        thread {
            client.stop()
            GAME.gameServer?.stopServer()
        }
    }

    /** Updates various global constants, variables upon a screen resize, to ensure UI will fit to the new screen size
     *
     * Updates the viewport and camera's projectionMatrix of [radarDisplayStage], [constZoomStage], [uiStage] and [shapeRenderer]
     * */
    override fun resize(width: Int, height: Int) {
        ScreenSize.updateScreenSizeParameters(width, height)
        radarDisplayStage.viewport.update(width, height)
        constZoomStage.viewport.update(width, height)

        // Resize the UI pane
        uiPane.resize(width, height)

        // shapeRenderer will follow radarDisplayCamera
        shapeRenderer.projectionMatrix = radarDisplayStage.camera.combined
    }

    /** Implements [GestureListener.touchDown], no action required */
    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        return false
    }

    /** Implements [GestureListener.tap], used to unselect an aircraft, and to test for double taps for zooming */
    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (count == 2 && !cameraAnimating) initiateCameraAnimation(x, y)
        uiPane.deselectAircraft()
        selectedAircraft = null
        // toggleMinAltSectorsOnClick(x, y, unprojectFromRadarCamera, clientEngine)
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
            translate(-deltaX * zoom * UI_WIDTH / WIDTH, deltaY * zoom * UI_HEIGHT / HEIGHT)
        }
        clampUpdateCamera(0f)
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
        (radarDisplayStage.camera as OrthographicCamera).apply {
            clampUpdateCamera(amountY * 0.06f)
        }
        return true
    }

    /**
     * Adds a runnable to be run on the main client thread after the current engine update
     *
     * This is different from Gdx.app.postRunnable in that the runnable will only be run after
     * @param runnable the runnable to add
     * */
    fun postRunnableAfterEngineUpdate(runnable: Runnable) {
        pendingRunnablesQueue.offer(runnable)
    }

    /**
     * Sends a new player clearance for the aircraft
     * @param callsign the callsign of the aircraft to send the instructions to
     * @param newClearanceState the new clearance to send to the aircraft
     * */
    fun sendAircraftControlStateClearance(callsign: String, newClearanceState: ClearanceState) {
        client.sendTCP(
            AircraftControlStateUpdateData(callsign, newClearanceState.routePrimaryName,
            newClearanceState.route.getSerialisedObject(), newClearanceState.hiddenLegs.getSerialisedObject(),
            newClearanceState.vectorHdg, newClearanceState.vectorTurnDir, newClearanceState.clearedAlt, newClearanceState.clearedIas,
            newClearanceState.minIas, newClearanceState.maxIas, newClearanceState.optimalIas,
            newClearanceState.clearedApp, newClearanceState.clearedTrans)
        )
    }
}