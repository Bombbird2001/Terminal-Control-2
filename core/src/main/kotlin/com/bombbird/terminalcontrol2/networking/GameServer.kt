package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.files.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.networking.dataclasses.*
import com.bombbird.terminalcontrol2.networking.hostserver.LANServer
import com.bombbird.terminalcontrol2.networking.hostserver.PublicServer
import com.bombbird.terminalcontrol2.systems.*
import com.bombbird.terminalcontrol2.traffic.*
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.utilities.*
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Main game server class, responsible for handling all game logic, updates, sending required game data information to
 * clients and handling incoming client inputs
 * @param airportToHost name of the airport to host
 * @param saveId ID of the save file to load, if any
 * @param publicServer whether to host this game in the public server
 * @param maxPlayersSet the maximum numbers of player the host as selected
 * @param testMode whether to start this game server in test mode (i.e. will not initiate any network servers)
 */
class GameServer private constructor(airportToHost: String, saveId: Int?, val publicServer: Boolean, private val maxPlayersSet: Byte, testMode: Boolean = false) {
    companion object {
        const val UPDATE_INTERVAL = 1000.0 / SERVER_UPDATE_RATE
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_FAST = 1000.0 / SERVER_TO_CLIENT_UPDATE_RATE_FAST
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_SLOW = 1000.0 / SERVER_TO_CLIENT_UPDATE_RATE_SLOW
        const val SERVER_METAR_UPDATE_INTERVAL = SERVER_METAR_UPDATE_INTERVAL_MINS * 60 * 1000

        const val WEATHER_LIVE: Byte = 0
        const val WEATHER_RANDOM: Byte = 1
        const val WEATHER_STATIC: Byte = 2
        const val EMERGENCY_OFF: Byte = 3
        const val EMERGENCY_LOW: Byte = 4
        const val EMERGENCY_MEDIUM: Byte = 5
        const val EMERGENCY_HIGH: Byte = 6
        const val STORMS_OFF: Byte = 7
        const val STORMS_LOW: Byte = 8
        const val STORMS_MEDIUM: Byte = 9
        const val STORMS_HIGH: Byte = 10
        const val STORMS_NIGHTMARE: Byte = 11

        /**
         * Creates a new single-player mode game server object
         * @return GameServer in single-player mode
         */
        fun newSinglePlayerGameServer(airportToHost: String): GameServer {
            return GameServer(airportToHost, null, false, 1)
        }

        /**
         * Creates a new multiplayer LAN mode game server object
         * @return GameServer in LAN multiplayer mode
         */
        fun newLANMultiplayerGameServer(airportToHost: String): GameServer {
            return GameServer(airportToHost, null, false, 4) // TODO Let user choose max players
        }

        /**
         * Creates a new multiplayer public mode game server object
         * @return GameServer in public multiplayer mode
         */
        fun newPublicMultiplayerGameServer(airportToHost: String): GameServer {
            return GameServer(airportToHost, null, true, 4)
        }

        /**
         * Creates a new single-player mode game server object, loading an existing save
         * @param airportToHost name of airport to load
         * @param saveId ID of the save file to load
         * @return GameServer in single-player mode
         */
        fun loadSinglePlayerGameServer(airportToHost: String, saveId: Int): GameServer {
            return GameServer(airportToHost, saveId, false, 1)
        }

        /**
         * Creates a new multiplayer LAN mode game server object, loading an existing save
         * @param airportToHost name of airport to load
         * @param saveId ID of the save file to load
         * @return GameServer in LAN multiplayer mode
         */
        fun loadLANMultiplayerGameServer(airportToHost: String, saveId: Int): GameServer {
            return GameServer(airportToHost, saveId, false, 4)
        }

        /**
         * Creates a new multiplayer public mode game server object, loading an existing save
         * @param airportToHost name of airport to load
         * @param saveId ID of the save file to load
         * @return GameServer in public multiplayer mode
         */
        fun loadPublicMultiplayerGameServer(airportToHost: String, saveId: Int): GameServer {
            return GameServer(airportToHost, saveId, true, 4)
        }

        /**
         * Creates a new GameServer object for testing only; no network server will be initiated
         * @return GameServer in testing mode
         */
        fun testGameServer(): GameServer {
            return GameServer("", null, false, 4, true)
        }
    }

    private val loopRunning = AtomicBoolean(false)
    val gameRunning: Boolean
        get() = loopRunning.get()
    private val gamePaused = AtomicBoolean(false)
    private var lock = ReentrantLock()
    private val pauseCondition = lock.newCondition()
    val initialisingWeather = AtomicBoolean(true)
    private val initialWeatherCondition = lock.newCondition()
    private val playerNo = AtomicInteger(0)
    val playersInGame: Byte
        get() = playerNo.get().toByte()
    val maxPlayersAllowed: Byte
        get() = sectors.size.toByte().coerceAtMost(maxPlayersSet)
    lateinit var networkServer: NetworkServer
    val engine = Engine()
    var saveID: Int? = null

    // Blocking queue to store runnables to be run in the main thread after engine update
    private val pendingRunnablesQueue = ConcurrentLinkedQueue<Runnable>()
    var mainName = "----"
    val primarySector = Polygon() // The primary TMA sector polygon without being split up into sub-sectors
    val sectors =
        GdxArrayMap<Byte, GdxArray<Sector>>(SECTOR_COUNT_SIZE) // Sector configuration for different player number
    val aircraft = GdxArrayMap<String, Aircraft>(AIRCRAFT_SIZE)
    val minAltSectors = GdxArray<MinAltSector>()
    val shoreline = GdxArray<Shoreline>()

    val accSectors = GdxArray<ACCSector>(SECTOR_COUNT_SIZE) // ACC sectors

    /**
     * Maps [AirportInfo.arptId] (instead of [AirportInfo.icaoCode]) to the airport for backwards compatibility (in case of airport ICAO code reassignments;
     * Old airport is still available as a separate entity even with the same ICAO code as the new airport)
     */
    val airports = GdxArrayMap<Byte, Airport>(AIRPORT_SIZE)

    /**
     * Maps [AirportInfo.icaoCode] to the most updated [AirportInfo.arptId];
     * The new airport with the ICAO code will be chosen instead of the old one after an ICAO code reassignment
     */
    val updatedAirportMapping = GdxArrayMap<String, Byte>(AIRPORT_SIZE)

    /**
     * Maps [WaypointInfo.wptId] (instead of [WaypointInfo.wptName]) to the waypoint for backwards compatibility (in case waypoint position is moved;
     * old waypoint is still available as a separate entity even with the same name as the new waypoint)
     */
    val waypoints = HashMap<Short, Waypoint>()

    /**
     * Maps [WaypointInfo.wptName] to the most updated [WaypointInfo.wptId];
     * The new waypoint with the name will be chosen instead of the old one after the waypoint has "shifted"
     */
    val updatedWaypointMapping = HashMap<String, Short>()

    /**
     * Maps [WaypointInfo.wptName] to the [PublishedHold]
     *
     * This map will map to the most updated published hold, since old holding legs are stored individually with the waypoint ID in the aircraft's [ClearanceState.route]
     */
    val publishedHolds = GdxArrayMap<String, PublishedHold>(PUBLISHED_HOLD_SIZE)

    /** Maps [UUID] to [SectorInfo.sectorId] */
    val sectorMap = GdxArrayMap<UUID, Byte>(PLAYER_SIZE)

    /** Maps [SectorInfo.sectorId] to [UUID] */
    val sectorUUIDMap = GdxArrayMap<Byte, UUID>(PLAYER_SIZE)

    /**
     * Keeps track of all sector swap requests sent
     *
     * First value in the pair is the sector that is being requested
     *
     * Second value in the pair is the sector requesting the swap
     */
    val sectorSwapRequests = GdxArray<Pair<Byte, Byte>>(SECTOR_COUNT_SIZE * (SECTOR_COUNT_SIZE + 1) / 2)

    /** Flag for when a sector swap/player join has just occurred */
    var sectorJustSwapped = false

    var arrivalSpawnTimerS = 0f
    var previousArrivalOffsetS = 0f
    var trafficValue = 6f
    var trafficMode = TrafficMode.NORMAL

    var score = 0
    var highScore = 0

    var landed = 0
    var departed = 0

    var trailDotTimer = 0f

    /** Game specific settings */
    var weatherMode = WEATHER_LIVE
    var emergencyRate = EMERGENCY_LOW
    var stormsDensity = STORMS_OFF
    var gameSpeed = 1
    var nightModeStart = -1
    var nightModeEnd = -1
    var useRecat = true

    // var timeCounter = 0f
    // var frames = 0
    private var startTime = -1L

    // Loading screen callbacks
    var serverStartedCallback: (() -> Unit)? = null

    init {
        if (!testMode) initiateServer(airportToHost, saveId)
        else loadGameTest()
    }

    /** Initialises game world for testing purposes */
    private fun loadGameTest() {
        if (Gdx.files != null) {
            loadAircraftData()
            loadDisallowedCallsigns()
        }

        engine.addSystem(PhysicsSystem())
        engine.addSystem(PhysicsSystemInterval())
        engine.addSystem(AISystem())
        engine.addSystem(ControlStateSystem())
        engine.addSystem(ControlStateSystemInterval())
        engine.addSystem(TrafficSystemInterval())
        engine.addSystem(DataSystem())

        sectors.put(0, GdxArray())
        sectors.put(1, GdxArray())
        sectors.put(2, GdxArray())
        sectors.put(3, GdxArray())
    }

    /** Initialises game world where [mainName] is the ICAO code of the main airport */
    private fun loadGame(mainName: String, saveId: Int?) {
        this.mainName = mainName
        loadAircraftData()
        loadDisallowedCallsigns()

        engine.addSystem(PhysicsSystem())
        engine.addSystem(PhysicsSystemInterval())
        engine.addSystem(AISystem())
        engine.addSystem(ControlStateSystem())
        engine.addSystem(ControlStateSystemInterval())
        engine.addSystem(TrafficSystemInterval())
        engine.addSystem(DataSystem())

        if (saveId != null) loadSave(this, saveId)
        loadWorldData(mainName, this)

        if (initialisingWeather.get()) lock.withLock {
            requestAllMetar()
            // initialisingWeather may have already changed in the line above if static/random weather is used which
            // will immediately set initialisingWeather to false
            if (initialisingWeather.get()) initialWeatherCondition.await()
        }
    }

    /**
     * Starts the server processes
     * @param mainName the name of the main airport in the map
     * @param saveId the ID of the save file to load, or null if nothing to load
     */
    private fun initiateServer(mainName: String, saveId: Int?) {
        thread {
            try {
                Log.info("GameServer", "Starting game server")
                saveID = saveId
                loadGame(mainName, saveId)
                startNetworkingServer()
                startTime = -1L
                Log.info("GameServer", "Game server started")
                serverStartedCallback?.invoke()
                // Pause the game initially (until at least 1 player connects)
                handleGameRunningRequest(false)
                loopRunning.set(true)
                gameLoop()
                stopNetworkingServer()
                saveGame(this)
            } catch (e: Exception) {
                val multiplayerType = if (networkServer.getRoomId() != null) "Public multiplayer"
                else "LAN multiplayer/Singleplayer"
                HttpRequest.sendCrashReport(e, "GameServer", multiplayerType)
                GAME.quitCurrentGameWithDialog(CustomDialog("Error", "An error occurred", "", "Ok"))
            }
        }
    }

    /** Stops the game loop and exits server */
    fun stopServer() {
        setLoopingFalse()
        engine.removeAllEntities()
        engine.removeAllSystems()
        Log.info("GameServer", "Game server stopped")
    }

    /** Initiates the host server for networking */
    private fun startNetworkingServer() {
        val onReceive = { conn: ConnectionMeta, data: Any? ->
            // Called on data receive
            handleIncomingRequestServer(this, conn, data)
        }

        val onConnect = { conn: ConnectionMeta ->
            val currPlayerNo = playerNo.incrementAndGet().toByte()
            val uuid = conn.uuid
            networkServer.sendToAllTCP(PlayerJoined())
            postRunnableAfterEngineUpdate {
                // Get data only after engine has completed this update to prevent threading issues
                networkServer.sendTCPToConnection(uuid, ClearAllClientData())
                networkServer.sendTCPToConnection(
                    uuid,
                    InitialACCSectorData(accSectors.toArray().map { it.getSerialisableObject() }.toTypedArray()))
                networkServer.sendTCPToConnection(
                    uuid,
                    InitialAirspaceData(MAG_HDG_DEV, MIN_ALT, MAX_ALT, MIN_SEP, TRANS_ALT, TRANS_LVL, INTERMEDIATE_ALTS.map { it }.toIntArray())
                )
                assignSectorsToPlayers(
                    networkServer.connections,
                    sectorMap,
                    sectorUUIDMap,
                    currPlayerNo,
                    sectors
                )
                sectorSwapRequests.clear()

                val aircraftArray = Entries(aircraft).map { it.value }.toTypedArray()
                var itemsRemaining = aircraftArray.size
                while (itemsRemaining > 0) {
                    val serialisedAircraftArray = Array(min(itemsRemaining, SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT)) {
                        aircraftArray[aircraftArray.size - itemsRemaining + it].getSerialisableObject()
                    }
                    itemsRemaining -= SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT
                    networkServer.sendTCPToConnection(uuid, InitialAircraftData(serialisedAircraftArray))
                }
                networkServer.sendTCPToConnection(
                    uuid,
                    AirportData(Entries(airports).map { it.value.getSerialisableObject() }.toTypedArray()))
                val wptArray = waypoints.values.toTypedArray()
                networkServer.sendTCPToConnection(
                    uuid,
                    WaypointData(wptArray.map { it.getSerialisableObject() }.toTypedArray())
                )
                networkServer.sendTCPToConnection(
                    uuid,
                    WaypointMappingData(wptArray.map { it.getMappingSerialisableObject() }.toTypedArray())
                )
                networkServer.sendTCPToConnection(
                    uuid,
                    PublishedHoldData(Entries(publishedHolds).map { it.value.getSerialisableObject() }.toTypedArray()))
                networkServer.sendTCPToConnection(
                    uuid,
                    MinAltData(minAltSectors.toArray().map { it.getSerialisableObject() }.toTypedArray())
                )
                val tmpShoreline = GdxArray<Shoreline>()
                val maxVertexCountPerSend = (SERVER_WRITE_BUFFER_SIZE / 8f - 2).toInt()
                var vertexCount = 0
                for (sentShoreline in 0 until shoreline.size) {
                    val shorelineArray = shoreline[sentShoreline].entity[GLineArray.mapper] ?: continue
                    if (vertexCount + shorelineArray.vertices.size / 2 > maxVertexCountPerSend) {
                        // Send existing data and restart
                        networkServer.sendTCPToConnection(
                            uuid,
                            ShorelineData(tmpShoreline.toArray().map { it.getSerialisableObject() }.toTypedArray())
                        )
                        tmpShoreline.clear()
                        vertexCount = 0
                    }
                    tmpShoreline.add(shoreline[sentShoreline])
                    vertexCount += shorelineArray.vertices.size / 2
                }
                networkServer.sendTCPToConnection(
                    uuid,
                    ShorelineData(tmpShoreline.toArray().map { it.getSerialisableObject() }.toTypedArray())
                )

                // Send current METAR
                networkServer.sendTCPToConnection(
                    uuid,
                    MetarData(Entries(airports).map { it.value.getSerialisedMetar() }.toTypedArray())
                )

                // Send current traffic settings
                networkServer.sendTCPToConnection(
                    uuid,
                    TrafficSettingsData(
                        trafficMode, trafficValue,
                        getArrivalClosedAirports(), getDepartureClosedAirports()
                    )
                )

                // Send runway configs
                Entries(airports).forEach {
                    val arpt = it.value
                    val arptId = arpt.entity[AirportInfo.mapper]?.arptId ?: return@forEach
                    networkServer.sendTCPToConnection(
                        uuid,
                        ActiveRunwayUpdateData(
                            arptId,
                            arpt.entity[ActiveRunwayConfig.mapper]?.configId ?: return@forEach
                        )
                    )
                    networkServer.sendTCPToConnection(
                        uuid,
                        PendingRunwayUpdateData(arptId, arpt.entity[PendingRunwayConfig.mapper]?.pendingId)
                    )
                }

                // Send score data
                networkServer.sendTCPToConnection(uuid, ScoreData(score, highScore))

                // Initial data sending complete
                networkServer.sendTCPToConnection(uuid, InitialDataSendComplete())
            }
            // Unpause the game if it is currently paused
            handleGameRunningRequest(true)
        }

        val onDisconnect = { conn: ConnectionMeta ->
            // Called on disconnect
            val newPlayerNo = playerNo.decrementAndGet().toByte()
            val sectorControlled = sectorMap[conn.uuid]
            if (sectorControlled != null) networkServer.sendToAllTCP(PlayerLeft(sectorControlled))
            postRunnableAfterEngineUpdate {
                // Remove entries only after this engine update to prevent threading issues
                sectorUUIDMap.removeKey(sectorControlled)
                sectorMap.removeKey(conn.uuid)
                if (newPlayerNo > 0) assignSectorsToPlayers(
                    networkServer.connections,
                    sectorMap,
                    sectorUUIDMap,
                    newPlayerNo,
                    sectors
                )
                sectorSwapRequests.clear()
            }
        }

        // Log.set(Log.LEVEL_DEBUG)
        networkServer = if (publicServer) PublicServer(this, onReceive, onConnect, onDisconnect, mainName)
        else LANServer(this, onReceive, onConnect, onDisconnect)
        networkServer.beforeConnect()
        networkServer.start(TCP_PORT, UDP_PORT)
    }

    /** Closes server and stops its thread */
    private fun stopNetworkingServer() {
        networkServer.stop()
    }

    /** Main game loop */
    private fun gameLoop() {
        var prevMs = -1L
        var fastUpdateSlot = -1L
        var slowUpdateSlot = -1L
        var metarUpdateTime = 0
        var autosaveTime = 0
        while (loopRunning.get()) {
            // println("Looping")
            var currMs = System.currentTimeMillis()
            if (startTime == -1L) {
                // Skip this frame since server has just started up
                startTime = currMs
            } else {
                // Update client with seconds passed since last frame
                // println("Time diff: ${currMs - prevMs}")
                update((currMs - prevMs) / 1000f)

                val currFastSlot = (currMs - startTime) / (SERVER_TO_CLIENT_UPDATE_INTERVAL_FAST).toLong()
                if (currFastSlot > fastUpdateSlot) {
                    // Send fast UDP update if this update is after the time slot for the next fast UDP update
                    sendFastUDPToAll()
                    fastUpdateSlot = currFastSlot
                }

                val currSlowSlot = (currMs - startTime) / (SERVER_TO_CLIENT_UPDATE_INTERVAL_SLOW).toLong()
                if (currSlowSlot > slowUpdateSlot) {
                    // Send slow UDP update if this update is after the time slot for the next slow UDP update
                    sendSlowUDPToAll()
                    slowUpdateSlot = currSlowSlot
                }

                // Check if METAR update required, update timer
                metarUpdateTime += (currMs - prevMs).toInt()
                if (metarUpdateTime > SERVER_METAR_UPDATE_INTERVAL) {
                    requestAllMetar()
                    metarUpdateTime -= SERVER_METAR_UPDATE_INTERVAL
                }

                // Check if autosave time is up
                autosaveTime += (currMs - prevMs).toInt()
                if (autosaveTime > AUTOSAVE_INTERVAL_MIN * 60 * 1000) {
                    saveGame(this)
                    autosaveTime -= AUTOSAVE_INTERVAL_MIN * 60 * 1000
                }
            }

            if (gamePaused.get()) lock.withLock {
                pauseCondition.await()
                currMs = System.currentTimeMillis()
            }

            prevMs = currMs
            // println("$UPDATE_INTERVAL $currMs $startTime")
            val currFrame = (currMs - startTime) * SERVER_UPDATE_RATE / 1000
            val nextFrameTime = (UPDATE_INTERVAL - (currMs - startTime) % UPDATE_INTERVAL)

            // For cases where rounding errors result in a nextFrameTime of almost 0 - take the time needed to 2 frames later instead
            if (nextFrameTime < 0.1 * UPDATE_INTERVAL) {
                var newTime = (currFrame + 2) * 1000 / SERVER_UPDATE_RATE + startTime - currMs
                if (newTime > 24) newTime -= 1000 / SERVER_UPDATE_RATE
                Thread.sleep(newTime)
            } else Thread.sleep(nextFrameTime.roundToLong())
        }
    }

    /** Update function */
    private fun update(delta: Float) {
        // timeCounter += delta
        // frames++
        // println("Delta: $delta Average frame time: ${timeCounter / frames} Average FPS: ${frames / timeCounter}")
        // println(1 / delta)

        // Prevent lag spikes from causing huge deviations in simulation
        val cappedDelta = min(delta, 1f / 30)

        engine.update(cappedDelta)

        // Process pending runnables
        while (true) {
            pendingRunnablesQueue.poll()?.run() ?: break
        }
    }

    /**
     * Handles received [GameRunningStatus] requests from clients
     * @param running the running status sent in the request
     */
    fun handleGameRunningRequest(running: Boolean) {
        if (running) {
            if (gamePaused.get()) lock.withLock { pauseCondition.signal() }
            gamePaused.set(false)
        } else if (playerNo.get() <= 1) gamePaused.set(true)
    }

    /**
     * Send frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_FAST] times a second
     *
     * Aircraft position
     *
     * (List not exhaustive)
     */
    private fun sendFastUDPToAll() {
        // println("Fast UDP sent, time passed since program start: ${(System.currentTimeMillis() - startTime) / 1000f}s")
        // Split aircraft values into blocks of 25 aircraft max, and send a UDP packet for each, to prevent buffer size
        // limitations at high aircraft counts
        val aircraftArray = aircraft.values().toArray()
        var itemsRemaining = aircraftArray.size
        while (itemsRemaining > 0) {
            val serialisedAircraftArray = Array(min(itemsRemaining, SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT)) {
                aircraftArray[aircraftArray.size - itemsRemaining + it].getSerialisableObjectUDP()
            }
            itemsRemaining -= SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT
            networkServer.sendToAllUDP(FastUDPData(serialisedAircraftArray))
        }
    }

    /**
     * Send not so frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_SLOW] times a second
     *
     * Thunderstorm cells
     *
     * (List not exhaustive)
     */
    private fun sendSlowUDPToAll() {
        // println("Slow UDP sent, time passed since program start: ${(System.currentTimeMillis() - startTime) / 1000f}s")
    }

    /** Notifies the main server thread that the initial METAR has been loaded and to proceed with starting the server */
    fun notifyWeatherLoaded() {
        if (initialisingWeather.get()) lock.withLock {
            initialisingWeather.set(false)
            initialWeatherCondition.signal()
        }
    }

    /** Send non-frequent METAR updates */
    fun sendMetarTCPToAll() {
        if (gameRunning)
            networkServer.sendToAllTCP(MetarData(Entries(airports).map { it.value.getSerialisedMetar() }.toTypedArray()))
    }

    /**
     * Sends aircraft control state sector updates
     * @param callsign the callsign of the aircraft to update
     * @param newSector the new sector that the aircraft is under control of
     * @param newUUID the UUID of the new player the aircraft is under control of, or null if tower/ACC
     */
    fun sendAircraftSectorUpdateTCPToAll(callsign: String, newSector: Byte, newUUID: String?) {
        val tagFlashing = aircraft[callsign]?.entity?.get(InitialClientDatatagPosition.mapper)?.flashing == true
        networkServer.sendToAllTCP(AircraftSectorUpdateData(callsign, newSector, newUUID, sectorJustSwapped, tagFlashing))
    }

    /**
     * Sends data to assign each player's individual sector
     * @param connUuid the UUID of player to send to
     * @param newId the new sector ID assigned to this connection
     * @param newSectorArray the new sector configuration to use
     */
    fun sendIndividualSectorUpdateTCP(
        connUuid: UUID,
        newId: Byte,
        newSectorArray: Array<Sector.SerialisedSector>
    ) {
        println("Individual sector send to $connUuid")
        networkServer.sendTCPToConnection(
            connUuid,
            IndividualSectorData(newId, newSectorArray, primarySector.vertices ?: floatArrayOf())
        )
    }

    /**
     * Sends aircraft spawn data
     * @param aircraft the aircraft that spawned
     */
    fun sendAircraftSpawn(aircraft: Aircraft) {
        networkServer.sendToAllTCP(AircraftSpawnData(aircraft.getSerialisableObject()))
    }

    /**
     * Sends aircraft despawn data
     * @param callsign the callsign of the aircraft to despawn
     */
    fun sendAircraftDespawn(callsign: String) {
        networkServer.sendToAllTCP(AircraftDespawnData(callsign))
    }

    /**
     * Sends aircraft clearance state updates
     * @param callsign the callsign of the aircraft to update
     * @param primaryName the updated primary name of the route
     * @param route the updated route
     * @param hiddenLegs the updated hidden legs
     * @param vectorHdg the updated cleared vector heading
     * @param vectorTurnDir the updated vector turn direction
     * @param clearedAlt the updated cleared altitude
     * @param clearedIas the updated cleared IAS
     * @param minIas the updated minimum IAS that can be cleared
     * @param maxIas the updated maximum IAS that can be cleared
     * @param optimalIas the updated optimal IAS that aircraft will target
     */
    fun sendAircraftClearanceStateUpdateToAll(
        callsign: String, primaryName: String = "", route: Route, hiddenLegs: Route,
        vectorHdg: Short?, vectorTurnDir: Byte?, clearedAlt: Int, expedite: Boolean,
        clearedIas: Short, minIas: Short, maxIas: Short, optimalIas: Short,
        clearedApp: String?, clearedTrans: String?
    ) {
        networkServer.sendToAllTCP(
            AircraftControlStateUpdateData(
                callsign,
                primaryName,
                route.getSerialisedObject(),
                hiddenLegs.getSerialisedObject(),
                vectorHdg,
                vectorTurnDir,
                clearedAlt,
                expedite,
                clearedIas,
                minIas,
                maxIas,
                optimalIas,
                clearedApp,
                clearedTrans,
                -5
            )
        )
    }

    /**
     * Sends a new custom waypoint to be added by clients
     * @param waypoint the new custom waypoint
     */
    fun sendCustomWaypointAdditionToAll(waypoint: Waypoint) {
        networkServer.sendToAllTCP(CustomWaypointData(waypoint.getSerialisableObject()))
    }

    /**
     * Sends a message to clients to remove a custom waypoint
     * @param wptId the new ID of the custom waypoint to remove
     */
    fun sendCustomWaypointRemovalToAll(wptId: Short) {
        networkServer.sendToAllTCP(RemoveCustomWaypointData(wptId))
    }

    /**
     * Sends a message to clients to inform them of a pending runway configuration update
     * @param configId the new ID of the pending configuration, or null if the pending change is cancelled
     */
    fun sendPendingRunwayUpdateToAll(airportId: Byte, configId: Byte?) {
        if (gameRunning)
            networkServer.sendToAllTCP(PendingRunwayUpdateData(airportId, configId))
    }

    /**
     * Sends a message to clients to inform them of the active runway configuration update
     * @param airportId the ID of the airport to change
     * @param configId the new ID of the active configuration
     */
    fun sendActiveRunwayUpdateToAll(airportId: Byte, configId: Byte) {
        if (gameRunning)
            networkServer.sendToAllTCP(ActiveRunwayUpdateData(airportId, configId))
    }

    fun incrementScoreBy(inc: Int, flightType: Byte) {
        score += inc
        if (flightType == FlightType.DEPARTURE) departed++
        else if (flightType == FlightType.ARRIVAL) landed++
        if (score > highScore) highScore = score
        sendScoreUpdate()
    }

    /** Sends a message to clients to inform them of a change in scores */
    fun sendScoreUpdate() {
        networkServer.sendToAllTCP(ScoreData(score, highScore))
    }

    /**
     * Sends a message to clients to update them on the ongoing conflicts
     * @param conflicts the list of ongoing conflicts
     * @param potentialConflicts the list of potential conflicts
     */
    fun sendConflicts(
        conflicts: GdxArray<ConflictManager.Conflict>,
        potentialConflicts: GdxArray<ConflictManager.PotentialConflict>
    ) {
        networkServer.sendToAllTCP(
            ConflictData(
                conflicts.toArray().map { it.getSerialisableObject() }.toTypedArray(),
                potentialConflicts.toArray().map { it.getSerialisableObject() }.toTypedArray()
            )
        )
    }

    /** Sends a message to clients to inform them of the server's traffic settings */
    fun sendTrafficSettings() {
        networkServer.sendToAllTCP(
            TrafficSettingsData(
                trafficMode, trafficValue,
                getArrivalClosedAirports(), getDepartureClosedAirports()
            )
        )
    }

    /**
     * Sends the trail dot updates for each aircraft
     * @param trails the array containing trail position data for each aircraft
     */
    fun sendAircraftTrailDotUpdate(trails: GdxArray<Pair<String, Position>>) {
        val trailArray = trails.toArray().map { TrailDotData(it.first, it.second.x, it.second.y) }.toTypedArray()
        networkServer.sendToAllTCP(AllTrailDotData(trailArray))
    }

    /**
     * Adds a runnable to be run on the main server thread after the current engine update
     * @param runnable the runnable to add
     */
    fun postRunnableAfterEngineUpdate(runnable: Runnable) {
        pendingRunnablesQueue.offer(runnable)
    }

    /** Sets the looping flag to false */
    fun setLoopingFalse() {
        loopRunning.set(false)
    }

    /**
     * Returns the room ID of the underlying multiplayer server; if is LAN server, null is returned
     * @return ID of room
     */
    fun getRoomId(): Short? {
        return networkServer.getRoomId()
    }
}