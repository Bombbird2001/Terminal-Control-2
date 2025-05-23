package com.bombbird.terminalcontrol2.screens

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import com.bombbird.terminalcontrol2.components.AircraftInfo
import com.bombbird.terminalcontrol2.components.Controllable
import com.bombbird.terminalcontrol2.components.Datatag
import com.bombbird.terminalcontrol2.components.FlightType
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.files.loadAircraftData
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.*
import com.bombbird.terminalcontrol2.networking.NetworkClient
import com.bombbird.terminalcontrol2.systems.*
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.traffic.conflict.Conflict
import com.bombbird.terminalcontrol2.traffic.conflict.PotentialConflict
import com.bombbird.terminalcontrol2.traffic.conflict.PredictedConflict
import com.bombbird.terminalcontrol2.ui.*
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagLineSpacing
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagStyle
import com.bombbird.terminalcontrol2.ui.panes.UIPane
import com.bombbird.terminalcontrol2.utilities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ktx.app.KtxScreen
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.assets.disposeSafely
import ktx.async.KtxAsync
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.graphics.moveTo
import ktx.math.ImmutableVector2
import java.io.IOException
import java.nio.channels.ClosedSelectorException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/**
 * Main class for the display of the in-game radar screen
 *
 * Contains the stage for the actors required in the radar screen
 *
 * Also contains the stage for drawing the UI overlay
 *
 * Implements [GestureListener] and [InputProcessor] to handle input/gesture events to it
 * @param connectionHost the address of the host server to connect to; if null, no connection will be initiated
 * @param connectionTcpPort the TCP port of the host server to connect to; if null, CLIENT_TCP_PORT_IN_USE will be used
 * @param connectionUdpPort the UDP port of the host server to connect to; if null, CLIENT_UDP_PORT_IN_USE will be used
 * @param roomId the ID of the room to join (public multiplayer)
 */
class RadarScreen private constructor(private val connectionHost: String, private val connectionTcpPort: Int?,
                                      private val connectionUdpPort: Int?, private var roomId: Short?): KtxScreen, GestureListener, InputProcessor, ShowsDialog {
    private val clientEngine = getEngine(true)
    private val radarDisplayStage = safeStage(GAME.batch)
    private val constZoomStage = safeStage(GAME.batch)
    private val uiStage = safeStage(GAME.batch)
    val uiPane = UIPane(uiStage)
    private val shapeRenderer = ShapeRendererBoundingBox(3000)

    private val gestureDetector = GestureDetector(40f, 0.2f, 1.1f, 0.15f, this)
    private val inputMultiplexer = InputMultiplexer()

    // Camera animation parameters
    private var cameraAnimating = false
    private var targetZoom: Float
    private var zoomRate = 0f
    private var targetCenter: Vector2
    private var panRate: ImmutableVector2
    private var prevInitDist = 0f
    private var prevZoom = 1f

    // Game running status
    @Volatile
    private var running = false
    @Volatile
    private var initialDataReceived = false

    // Game meta-data
    var mainName = ""
    var maxPlayers = 0

    // Airport map for access during TCP updates; see GameServer for more details
    val airports = GdxArrayMap<Byte, Airport>(AIRPORT_SIZE)

    // Waypoint map for access; see GameServer for more details
    val waypoints = HashMap<Short, Waypoint>()
    val updatedWaypointMapping = HashMap<String, Short>()

    // Published hold map for access
    val publishedHolds = GdxArrayMap<String, PublishedHold>(PUBLISHED_HOLD_SIZE)

    // Array for min alt sectors
    val minAltSectors = GdxArray<MinAltSector>()

    // The primary TMA sector polygon without being split up into sub-sectors
    val primarySector = Polygon()
    val primarySectorBound = Rectangle()

    // Range ring entities
    private val rangeRings = GdxArray<RangeRing>()

    // The current active configuration of polygons
    val sectors = GdxArray<Sector>(SECTOR_COUNT_SIZE)
    var playerSector: Byte = 0
    var swapSectorRequest: Byte? = null
    var incomingSwapRequests = GdxArray<Byte>(SECTOR_COUNT_SIZE)

    // ACC sectors
    val accSectors = GdxArray<ACCSector>(SECTOR_COUNT_SIZE)

    // Aircraft map for access during UDP updates
    val aircraft = GdxArrayMap<String, Aircraft>(AIRCRAFT_SIZE)

    // List of ongoing and potential conflicts to be rendered
    val conflicts = GdxArray<Conflict>(CONFLICT_SIZE)
    val potentialConflicts = GdxArray<PotentialConflict>(CONFLICT_SIZE)
    val predictedConflicts = GdxArray<PredictedConflict>(CONFLICT_SIZE)

    // All current thunderstorms
    val storms = GdxArray<ThunderStorm>(MAX_THUNDERSTORM_COUNT)

    // Server values - used in this case only for displaying traffic info on the status pane and serves no other purposes
    var serverTrafficMode = TrafficMode.NORMAL
    var serverTrafficValue = 6f

    // Server values - night mode, RECAT
    var isNight = false
    var useRecat = true

    // Selected aircraft
    var selectedAircraft: Aircraft? = null

    // Networking client, and flag for stopping connection attempts if game quits before connection is established
    val networkClient: NetworkClient
        get() = if (!isPublicMultiplayer()) GAME.lanClient else GAME.publicClient
    private var attemptConnection = true

    // Blocking queue to store runnables to be run in the main thread after engine update
    private val pendingRunnablesQueue = ConcurrentLinkedQueue<Runnable>()

    // Loading screen callbacks
    var dataLoadedCallback: (() -> Unit)? = null
    var connectedToHostCallback: (() -> Unit)? = null

    // Signal booleans for if host server start failed
    var hostServerStartFailed = false

    // Custom input variables
    private var isDistMeasureDragging = false
    private var isZoomPinching = false
    private var waitForZoomPinchTimer = 0f
    var distMeasurePoint1 = Vector2()
    var distMeasurePoint2 = Vector2()

    companion object {
        // Datatag family for updating styles
        private val datatagFamily = allOf(Datatag::class, FlightType::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)

        /**
         * Returns a new instance of single player RadarScreen
         * @return RadarScreen object in single player mode
         */
        fun newSinglePlayerRadarScreen(): RadarScreen {
            return RadarScreen(LOCALHOST, null, null, null)
        }

        /**
         * Returns a new instance of LAN multiplayer RadarScreen
         * @param lanAddress address of the LAN server to connect to
         * @return RadarScreen object in LAN multiplayer mode
         */
        fun newLANMultiplayerRadarScreen(lanAddress: String): RadarScreen {
            return RadarScreen(lanAddress, null, null, null)
        }

        /**
         * Returns a new instance of LAN multiplayer RadarScreen
         * @param lanAddress address of the LAN server to connect to
         * @param tcpPort TCP port of the LAN server to connect to
         * @param udpPort UDP port of the LAN server to connect to
         * @return RadarScreen object in LAN multiplayer mode
         */
        fun joinLANMultiplayerRadarScreen(lanAddress: String, tcpPort: Int, udpPort: Int): RadarScreen {
            return RadarScreen(lanAddress, tcpPort, udpPort, null)
        }

        /**
         * Returns a new instance of public multiplayer RadarScreen
         * @return RadarScreen object in public multiplayer mode
         */
        fun newPublicMultiplayerRadarScreen(): RadarScreen {
            return RadarScreen(Secrets.RELAY_ADDRESS, RELAY_TCP_PORT, RELAY_UDP_PORT, null)
        }

        /**
         * Returns a new instance of public multiplayer RadarScreen to join a public multiplayer game
         * @param roomId ID of the room to join
         * @return RadarScreen object in public multiplayer mode
         */
        fun joinPublicMultiplayerRadarScreen(roomId: Short): RadarScreen {
            return RadarScreen(Secrets.RELAY_ADDRESS, RELAY_TCP_PORT, RELAY_UDP_PORT, roomId)
        }
    }

    init {
        KtxAsync.launch(Dispatchers.IO) {
            // Aircraft data must be loaded on client side as well
            loadAircraftData()
            dataLoadedCallback?.invoke()
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

        clientEngine.addSystem(RenderingSystemClient(shapeRenderer, radarDisplayStage, constZoomStage, uiStage, uiPane))
        clientEngine.addSystem(PhysicsSystemClient())
        clientEngine.addSystem(PhysicsSystemIntervalClient())
        clientEngine.addSystem(DataSystemClient())
        clientEngine.addSystem(DataSystemIntervalClient())
        clientEngine.addSystem(ControlStateSystemIntervalClient())
        clientEngine.addSystem(TrafficSystemIntervalClient())

        KtxAsync.launch(Dispatchers.IO) {
            try {
                running = attemptConnectionToServer()
                FileLog.info("RadarScreen", networkClient.getConnectionStatus())
            } catch (e: Exception) {
                HttpRequest.sendCrashReport(e, "RadarScreen", getMultiplayerType())
                GAME.quitCurrentGameWithDialog { CustomDialog("Error", "An error occurred", "", "Ok") }
            }
        }
    }

    /**
     * Adds an actor to the constant zoom stage
     * @param actor the [Actor] to add to [constZoomStage]
     */
    fun addToConstZoomStage(actor: Actor) {
        constZoomStage.addActor(actor)
    }

    /**
     * Instructs [uiPane] to display the control pane for the supplied aircraft
     * @param aircraft the aircraft to display in the UI pane
     */
    fun setUISelectedAircraft(aircraft: Aircraft) {
        if (selectedAircraft != null) deselectUISelectedAircraft()
        Gdx.app.postRunnable {
            uiPane.setSelectedAircraft(aircraft)
            selectedAircraft = aircraft
            aircraft.entity.apply {
                val datatag = get(Datatag.mapper) ?: return@apply
                val flightType = get(FlightType.mapper) ?: return@apply
                updateDatatagStyle(datatag, flightType.type, true)
            }
            updateDistToGo()
            updateWaypointRestr()
        }
    }

    /** Deselects the currently selected aircraft in [uiPane] */
    private fun deselectUISelectedAircraft() {
        Gdx.app.postRunnable {
            uiPane.deselectAircraft()
            selectedAircraft?.entity?.apply {
                val datatag = get(Datatag.mapper) ?: return@apply
                val flightType = get(FlightType.mapper) ?: return@apply
                updateDatatagStyle(datatag, flightType.type, false)
            }
            selectedAircraft = null
            updateDistToGo()
            updateWaypointRestr()
        }
    }

    /** Ensures [radarDisplayStage]'s camera parameters are within limits, then updates the camera (and [shapeRenderer]) */
    private fun clampUpdateCamera(deltaZoom: Float) {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val oldZoom = zoom
            zoom += deltaZoom
            zoom = MathUtils.clamp(zoom, nmToPx(MIN_ZOOM_NM) / UI_HEIGHT, nmToPx(MAX_ZOOM_NM) / UI_HEIGHT)
            translate(uiPane.getRadarCameraOffsetForZoom(zoom - oldZoom), 0f)
            position.x = MathUtils.clamp(position.x, primarySectorBound.x - nmToPx(20f), primarySectorBound.x + primarySectorBound.width + nmToPx(20f))
            position.y = MathUtils.clamp(position.y, primarySectorBound.y - nmToPx(20f), primarySectorBound.y + primarySectorBound.height + nmToPx(20f))
            update()

            // shapeRenderer will follow radarDisplayCamera
            shapeRenderer.projectionMatrix = combined
        }
    }

    /**
     * Initiates animation of [radarDisplayStage]'s camera to the new position, as well as a new zoom depending on current
     * zoom value
     */
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

    /**
     * Shifts [radarDisplayStage]'s camera by an amount depending on the time passed since last frame, and the zoom, pan
     * rate calculated in [initiateCameraAnimation]
     */
    private fun runCameraAnimations(delta: Float) {
        if (!cameraAnimating) return
        (radarDisplayStage.camera as OrthographicCamera).apply {
            if ((zoomRate < 0 && zoom > targetZoom) || (zoomRate > 0 && zoom < targetZoom)) {
                val neededDelta = min((targetZoom - zoom) / zoomRate, delta)
                zoom += zoomRate * neededDelta
                val actualPanRate = panRate.times(neededDelta)
                translate(actualPanRate.x, actualPanRate.y)
                clampUpdateCamera(0f)
            } else cameraAnimating = false
        }
    }

    /**
     * Handles custom inputs for distance measuring using right mouse (on desktop) or delayed pinching on Android
     * @param delta time passed since last frame
     */
    private fun processDistanceMeasurerInputs(delta: Float) {
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && Gdx.app.type == Application.ApplicationType.Desktop) {
            if (!isDistMeasureDragging) {
                distMeasurePoint1 = unprojectFromRadarCamera(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            }
            distMeasurePoint2 = unprojectFromRadarCamera(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            isDistMeasureDragging = true
        } else if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1) && isMobile()) {
            // We wait for 0.5 second before enabling distance measuring - isZoomPinching must remain false for this 0.5 second
            if (waitForZoomPinchTimer < 0.5f) {
                waitForZoomPinchTimer += delta
                if (isZoomPinching) waitForZoomPinchTimer = 0f
                isZoomPinching = false
                return
            }

            isDistMeasureDragging = true
            distMeasurePoint1 = unprojectFromRadarCamera(Gdx.input.getX(0).toFloat(), Gdx.input.getY(0).toFloat())
            distMeasurePoint2 = unprojectFromRadarCamera(Gdx.input.getX(1).toFloat(), Gdx.input.getY(1).toFloat())
        } else {
            isDistMeasureDragging = false
            waitForZoomPinchTimer = 0f
            distMeasurePoint1.setZero()
            distMeasurePoint2.setZero()
        }
    }

    /**
     * Helper function for unprojecting from screen coordinates to camera world coordinates, as unfortunately Camera's
     * unproject function is not accurate in this case
     */
    private fun unprojectFromRadarCamera(screenX: Float, screenY: Float): Vector2 {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val scaleFactor = UI_HEIGHT / HEIGHT // 1px in screen distance = ?px in world distance (at zoom = 1)
            return Vector2((screenX - WIDTH / 2) * zoom * scaleFactor + position.x, (HEIGHT / 2 - screenY) * zoom * scaleFactor + position.y)
        }
    }

    /** Updates all the datatag styles to match the current datatag settings; call after datatag settings has been changed */
    fun updateAllDatatagStyles() {
        val datatags = clientEngine.getEntitiesFor(datatagFamily)
        for (i in 0 until datatags.size()) {
            datatags[i]?.apply {
                val datatag = get(Datatag.mapper) ?: return@apply
                val flightType = get(FlightType.mapper) ?: return@apply
                updateDatatagStyle(datatag, flightType.type, false)
                updateDatatagLineSpacing(datatag)
            }
        }
    }

    /** Object initialization ot be called after the client received a ClearAllData request from server */
    fun afterClearData() {
        updateRangeRings()
    }

    /** Updates the range rings for the selected range ring interval; call after range ring interval is changed */
    fun updateRangeRings() {
        for (i in 0 until rangeRings.size) rangeRings[i].removeRing()
        rangeRings.clear()
        if (RANGE_RING_INTERVAL_NM == 0) return
        for (i in 0..50 step RANGE_RING_INTERVAL_NM) {
            if (i == 0) continue
            rangeRings.add(RangeRing(i))
        }
    }

    /** Updates the labels for the MVAs; call after MVA altitude display is changed */
    fun updateMVADisplay() {
        for (i in 0 until minAltSectors.size) {
            minAltSectors[i].setMVALabelVisibility(SHOW_MVA_ALTITUDE)
        }
    }

    /**
     * Sets [Gdx.input]'s inputProcessors to [inputMultiplexer], which consists of [uiStage], [constZoomStage], [radarDisplayStage],
     * [gestureDetector] and this [RadarScreen]
     */
    override fun show() {
        inputMultiplexer.addProcessor(uiStage)
        inputMultiplexer.addProcessor(constZoomStage)
        inputMultiplexer.addProcessor(radarDisplayStage)
        inputMultiplexer.addProcessor(gestureDetector)
        inputMultiplexer.addProcessor(this)
        Gdx.input.inputProcessor = inputMultiplexer
    }

    /** Main rendering function, updates engine which draws using [RenderingSystemClient] every loop */
    override fun render(delta: Float) {
        try {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT or if (Gdx.graphics.bufferFormat.coverageSampling) GL20.GL_COVERAGE_BUFFER_BIT_NV else 0)

            // Default to simulation rate of 1 if player is not host
            val gameSpeed = GAME.gameServer?.gameSpeed ?: 1
            // Prevent lag spikes from causing huge deviations in simulation
            val cappedDelta = min(delta * gameSpeed, 1f / 10)

            if (running) {
                runCameraAnimations(cappedDelta)
                clientEngine.update(cappedDelta)
                processDistanceMeasurerInputs(cappedDelta)
            }

            // Process pending runnables
            while (true) { pendingRunnablesQueue.poll()?.run() ?: break }
        } catch (e: Exception) {
            HttpRequest.sendCrashReport(e, "RadarScreen", getMultiplayerType())
            GAME.quitCurrentGameWithDialog { CustomDialog("Error", "An error occurred", "", "Ok") }
        }
    }

    /**
     * Clears and disposes of [radarDisplayStage], [constZoomStage], [uiStage], [shapeRenderer], stops the [networkClient] and
     * [GameServer] if present
     */
    override fun dispose() {
        radarDisplayStage.clear()
        constZoomStage.clear()
        uiStage.clear()

        radarDisplayStage.disposeSafely()
        constZoomStage.disposeSafely()
        uiStage.disposeSafely()
        shapeRenderer.disposeSafely()

        GAME.soundManager.stop()

        FamilyWithListener.clearAllClientFamilyEntityListeners(clientEngine)
        clientEngine.removeAllEntitiesOnMainThread(true)
        clientEngine.removeAllSystemsOnMainThread(true)

        GAME.gameServer?.stopServer()
        GAME.discordHandler.updateInMenu()

        attemptConnection = false
        KtxAsync.launch(Dispatchers.IO) {
            try {
                networkClient.stop()
            } catch (e: ClosedSelectorException) {
                FileLog.info("RadarScreen", "Client channel selector already closed before disposal")
            }
        }
    }

    /**
     * Updates various global constants, variables upon a screen resize, to ensure UI will fit to the new screen size
     *
     * Updates the viewport and camera's projectionMatrix of [radarDisplayStage], [constZoomStage], [uiStage] and [shapeRenderer]
     */
    override fun resize(width: Int, height: Int) {
        ScreenSize.updateScreenSizeParameters(width, height)
        radarDisplayStage.viewport.setWorldSize(UI_WIDTH, UI_HEIGHT)
        radarDisplayStage.viewport.update(width, height)
        radarDisplayStage.camera.update()
        constZoomStage.viewport.setWorldSize(UI_WIDTH, UI_HEIGHT)
        constZoomStage.viewport.update(width, height)
        constZoomStage.camera.update()

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
        deselectUISelectedAircraft()
        // toggleMinAltSectorsOnClick(x, y, ::unprojectFromRadarCamera, clientEngine)
        // println("Clicked on: ${unprojectFromRadarCamera(x, y).scl(1 / 25f)}")
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
        if (isDistMeasureDragging) return true
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
        if (isDistMeasureDragging) return true
        if (initialDistance != prevInitDist) {
            prevInitDist = initialDistance
            prevZoom = (radarDisplayStage.camera as OrthographicCamera).zoom
        }
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val ratio = initialDistance / distance
            if (ratio < 0.95 || ratio > 1.05) {
                isZoomPinching = true
                clampUpdateCamera(prevZoom * ratio - zoom)
            }
        }
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

    /** Implements [InputProcessor.touchCancelled], no action required */
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
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
     * This is different from Gdx.app.postRunnable in that the runnable will run right after the engine has updated,
     * instead of after the whole render loop has been finished
     * @param runnable the runnable to add
     */
    fun postRunnableAfterEngineUpdate(print: Boolean, runnable: Runnable) {
        if (print) FileLog.info("RadarScreen", "Runnable added")
        pendingRunnablesQueue.offer(runnable)
    }

    /**
     * Attempt to connect to the server, retrying until success. Returns true if connection to server was successful or
     * client is already connected, else false
     */
    private fun attemptConnectionToServer(): Boolean {
        if (networkClient.isConnected) {
            networkClient.stop()
            return attemptConnectionToServer()
        }
        var times = 0
        while (true) {
            if (hostServerStartFailed) {
                hostServerStartFailed = false
                return false
            }
            val gs = GAME.gameServer
            // If the game server is not yet running, or if the game server is a public server and has yet to receive
            // its room ID
            if (gs != null && (!gs.gameRunning || (gs.publicServer && gs.getRoomId() == null))) {
                Thread.sleep(2000)
                times++
                if (times >= 15) {
                    FileLog.info("RadarScreen", "Game server not running - timeout")
                    GAME.quitCurrentGameWithDialog { CustomDialog("Error", "Timeout when connecting to server", "", "Ok") }
                    return false
                }
                continue
            }
            try {
                // Check if game server is public server, if it is, set to its room ID
                if (!attemptConnection) return false
                if (gs != null && gs.publicServer && gs.getRoomId() != null) roomId = gs.getRoomId()
                Thread.sleep(1000)
                networkClient.beforeConnect(roomId)
                networkClient.start()
                networkClient.connect(CONNECTION_TIMEOUT, connectionHost, connectionTcpPort ?: CLIENT_TCP_PORT_IN_USE, connectionUdpPort ?: CLIENT_UDP_PORT_IN_USE)
                return true
            } catch (_: IOException) {
                // Workaround for strange behaviour on some devices where the connection timeout is ignored,
                // an IOException is thrown instantly as server has not started up
                Thread.sleep(CONNECTION_TIMEOUT.toLong())
            }
        }
    }

    /**
     * Pauses the game
     *
     * A pause request is sent to the server which will check if the number of players is not more than 1 and will pause
     * the game on the server-side as well if that is the case, otherwise it will continue running the game if more than
     * 1 player is present
     */
    fun pauseGame() {
        networkClient.sendTCP(GameRunningStatus(false))
        GAME.soundManager.pause()
        GAME.getScreen<PauseScreen>().radarScreen = this
        GAME.setScreen<PauseScreen>()
    }

    /** Resumes the game, and sends a resume game signal to the server */
    fun resumeGame(reconnect: Boolean = true) {
        if (networkClient.isConnected) networkClient.sendTCP(GameRunningStatus(true))
        GAME.soundManager.resume()
        if (!networkClient.isConnected && reconnect) {
            networkClient.beforeConnect(roomId)
            try {
                networkClient.reconnect()
            } catch (e: IOException) {
                FileLog.warn("RadarScreen", "Failed to reconnect to server")
                GAME.quitCurrentGameWithDialog { CustomDialog("Disconnected", "You have been disconnected from the server - most likely the host quit the game",
                    "", "Ok") }
            }
        }
    }

    /**
     * Quits the game, which will resume the game (from paused screen) and set the running flag to false if the player
     * is also the host
     */
    fun quitGame() {
        GAME.gameServer?.setLoopingFalse()
        resumeGame(false)
    }

    /**
     * Sends a new player clearance for the aircraft
     * @param callsign the callsign of the aircraft to send the instructions to
     * @param newClearanceState the new clearance to send to the aircraft
     */
    fun sendAircraftControlStateClearance(callsign: String, newClearanceState: ClearanceState) {
        networkClient.sendTCP(
            AircraftControlStateUpdateData(callsign, newClearanceState.routePrimaryName,
            newClearanceState.route.getSerialisedObject(), newClearanceState.hiddenLegs.getSerialisedObject(),
            newClearanceState.vectorHdg, newClearanceState.vectorTurnDir, newClearanceState.clearedAlt, newClearanceState.expedite, newClearanceState.clearedIas,
            newClearanceState.minIas, newClearanceState.maxIas, newClearanceState.optimalIas,
            newClearanceState.clearedApp, newClearanceState.clearedTrans, null,
            newClearanceState.cancelLastMaxSpd, newClearanceState.initiateGoAround, playerSector)
        )
    }

    /**
     * Sends an aircraft handover request to the server
     * @param callsign the callsign of the aircraft to hand over
     * @param newSector the ID of the new sector to hand over to
     */
    fun sendAircraftHandOverRequest(callsign: String, newSector: Byte) {
        networkClient.sendTCP(HandoverRequest(callsign, newSector, playerSector))
    }

    /**
     * Sends a sector swap request to the server
     * @param requestedSector the ID of the sector the user wishes to swap with
     */
    fun sendSectorSwapRequest(requestedSector: Byte) {
        networkClient.sendTCP(SectorSwapRequest(requestedSector, playerSector))
    }

    /** Sends a request to cancel the current pending sector swap request to the server */
    fun cancelSectorSwapRequest() {
        networkClient.sendTCP(SectorSwapRequest(null, playerSector))
    }

    /**
     * Sends a request to decline an incoming swap request
     * @param requestingSector the ID of the sector being declined
     */
    fun declineSectorSwapRequest(requestingSector: Byte) {
        networkClient.sendTCP(DeclineSwapRequest(requestingSector, playerSector))
    }

    /**
     * Sends the datatag position of the aircraft on client to the server, only if the aircraft is under this player's control
     * @param aircraft the aircraft entity whose datatag position is being sent
     * @param xOffset the x position offset of the datatag from the aircraft radar blip
     * @param yOffset the y position offset of the datatag from the aircraft radar blip
     * @param minimised whether the datatag has been minimized
     */
    fun sendAircraftDatatagPositionUpdateIfControlled(aircraft: Entity, xOffset: Float, yOffset: Float, minimised: Boolean, flashing: Boolean) {
        val controllable = aircraft[Controllable.mapper] ?: return
        // We only send this if the aircraft's sector ID is the same as the player's sector, or, this aircraft is under
        // tower or ACC control and the player is the host
        if (controllable.sectorId != playerSector && (controllable.sectorId >= 0 || GAME.gameServer == null)) return

        val callsign = aircraft[AircraftInfo.mapper]?.icaoCallsign ?: return FileLog.info("RadarScreen", "Missing AircraftInfo component")
        networkClient.sendTCP(AircraftDatatagPositionUpdateData(callsign, xOffset, yOffset, minimised, flashing))
    }

    /**
     * Checks if the client has received all initial data required
     * @return false if client has not or is in the process of receiving initial data, else true
     */
    fun isInitialDataReceived(): Boolean {
        return initialDataReceived
    }

    /** Sets the initial data received flag to true, allowing the client to handle other incoming data */
    fun notifyInitialDataSendComplete() {
        initialDataReceived = true
        connectedToHostCallback?.invoke()
    }

    /** Returns true if client is in public multiplayer game, else false */
    fun isPublicMultiplayer(): Boolean {
        return roomId != null
    }

    /** Returns the type of multiplayer the client is in */
    fun getMultiplayerType(): String {
        return if (isPublicMultiplayer()) MULTIPLAYER_PUBLIC_CLIENT else MULTIPLAYER_SINGLEPLAYER_LAN_CLIENT
    }

    /**
     * Overrides [ShowsDialog.showDialog] to show the dialog on [uiStage]
     * @param dialog the dialog to show
     */
    override fun showDialog(dialog: Dialog) {
        dialog.show(uiStage)
    }
}