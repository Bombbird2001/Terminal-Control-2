package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Polygon
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.files.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.systems.*
import com.bombbird.terminalcontrol2.traffic.*
import com.bombbird.terminalcontrol2.utilities.*
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
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
 * */
class GameServer {
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
    }

    private val loopRunning = AtomicBoolean(false)
    val gameRunning: Boolean
        get() = loopRunning.get()
    private val gamePaused = AtomicBoolean(false)
    private var lock = ReentrantLock()
    private val pauseCondition = lock.newCondition()
    val initialisingWeather = AtomicBoolean(true)
    private val initialWeatherCondition = lock.newCondition()
    var playerNo = AtomicInteger(0)
    val server = Server()
    val engine = Engine()
    var saveID: Int? = null

    // Blocking queue to store runnables to be run in the main thread after engine update
    private val pendingRunnablesQueue = ConcurrentLinkedQueue<Runnable>()
    var mainName = "----"
    val primarySector = Polygon() // The primary TMA sector polygon without being split up into sub-sectors
    val sectors =  GdxArrayMap<Byte, GdxArray<Sector>>(SECTOR_COUNT_SIZE) // Sector configuration for different player number
    val aircraft = GdxArrayMap<String, Aircraft>(AIRCRAFT_SIZE)
    val minAltSectors = GdxArray<MinAltSector>()
    val shoreline = GdxArray<Shoreline>()

    /**
     * Maps [AirportInfo.arptId] (instead of [AirportInfo.icaoCode]) to the airport for backwards compatibility (in case of airport ICAO code reassignments;
     * Old airport is still available as a separate entity even with the same ICAO code as the new airport)
     * */
    val airports = GdxArrayMap<Byte, Airport>(AIRPORT_SIZE)

    /**
     * Maps [AirportInfo.icaoCode] to the most updated [AirportInfo.arptId];
     * The new airport with the ICAO code will be chosen instead of the old one after an ICAO code reassignment
     * */
    val updatedAirportMapping = GdxArrayMap<String, Byte>(AIRPORT_SIZE)

    /**
     * Maps [WaypointInfo.wptId] (instead of [WaypointInfo.wptName]) to the waypoint for backwards compatibility (in case waypoint position is moved;
     * old waypoint is still available as a separate entity even with the same name as the new waypoint)
     * */
    val waypoints = HashMap<Short, Waypoint>()

    /**
     * Maps [WaypointInfo.wptName] to the most updated [WaypointInfo.wptId];
     * The new waypoint with the name will be chosen instead of the old one after the waypoint has "shifted"
     * */
    val updatedWaypointMapping = HashMap<String, Short>()

    /**
     * Maps [WaypointInfo.wptName] to the [PublishedHold]
     *
     * This map will map to the most updated published hold, since old holding legs are stored individually with the waypoint ID in the aircraft's [ClearanceState.route]
     * */
    val publishedHolds = GdxArrayMap<String, PublishedHold>(PUBLISHED_HOLD_SIZE)

    /** Maps [Connection] to [SectorInfo.sectorId] */
    val sectorMap = GdxArrayMap<Connection, Byte>(PLAYER_SIZE)

    /** Maps [Connection] to [UUID] */
    val connectionUUIDMap = GdxArrayMap<Connection, UUID>(PLAYER_SIZE)

    /** Maps [SectorInfo.sectorId] to [UUID] */
    val sectorUUIDMap = GdxArrayMap<Byte, UUID>(PLAYER_SIZE)

    /**
     * Keeps track of all sector swap requests sent
     *
     * First value in the pair is the sector that is being requested
     *
     * Second value in the pair is the sector requesting the swap
     * */
    val sectorSwapRequests = GdxArray<Pair<Byte, Byte>>(SECTOR_COUNT_SIZE * (SECTOR_COUNT_SIZE + 1) / 2)

    var arrivalSpawnTimerS = 0f
    var previousArrivalOffsetS = 0f
    var trafficValue = 6f
    var trafficMode = TrafficMode.NORMAL

    var score = 0
    var highScore = 0

    var landed = 0
    var departed = 0

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

    /**
     * Initialises game world
     * @param mainName the ICAO code of the main airport in the game world
     * */
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

        if (saveId != null) loadSave(this, saveId)
        loadWorldData(mainName, this)

        if (initialisingWeather.get()) lock.withLock {
            requestAllMetar()
            initialWeatherCondition.await()
        }

        if (saveId == null) appTestArrival(this)
    }

    /**
     * Starts the server processes
     * @param mainName the name of the main airport in the map
     * @param saveId the ID of the save file to load, or null if nothing to load
     * */
    fun initiateServer(mainName: String, saveId: Int?) {
        thread {
            saveID = saveId
            loadGame(mainName, saveId)
            startNetworkingServer()
            startTime = -1L
            Gdx.app.log("GameServer", "Starting game server")
            loopRunning.set(true)
            gameLoop()
            stopNetworkingServer()
            saveGame(this)
        }
    }

    /** Stops the game loop and exits server */
    fun stopServer() {
        Gdx.app.log("GameServer", "Stopping game server")
        loopRunning.set(false)
        engine.removeAllEntities()
        engine.removeAllSystems()
    }

    /** Initiates the KryoNet server for networking */
    private fun startNetworkingServer() {
        // Log.set(Log.LEVEL_DEBUG)
        registerClassesToKryo(server.kryo)
        server.setDiscoveryHandler(GameServerDiscoveryHandler(this))
        server.bind(TCP_PORT, UDP_PORT)
        server.start()
        server.addListener(object: Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                handleIncomingRequestServer(this@GameServer, connection, obj)
            }

            /**
             * Called when a client connects to the server
             * @param connection the incoming connection
             */
            override fun connected(connection: Connection) {
                connection.sendTCP(RequestClientUUID())
            }

            /**
             * Called when a client disconnects
             * @param connection the disconnecting client
             */
            override fun disconnected(connection: Connection?) {
                val newPlayerNo = playerNo.decrementAndGet().toByte()
                postRunnableAfterEngineUpdate {
                    // Remove entries only after this engine update to prevent threading issues
                    sectorUUIDMap.removeKey(sectorMap[connection])
                    sectorMap.removeKey(connection)
                    connectionUUIDMap.removeKey(connection)
                    if (newPlayerNo > 0) assignSectorsToPlayers(server.connections, sectorMap, connectionUUIDMap, sectorUUIDMap, newPlayerNo, sectors)
                    sectorSwapRequests.clear()
                }
            }
        })
    }

    /** Closes server and stops its thread */
    private fun stopNetworkingServer() {
        server.stop()
    }

    /** Main game loop */
    private fun gameLoop() {
        var prevMs = -1L
        var fastUpdateSlot = -1L
        var slowUpdateSlot = -1L
        var metarUpdateTime = 0
        while (loopRunning.get()) {
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

        engine.update(delta)

        // Process pending runnables
        while (true) { pendingRunnablesQueue.poll()?.run() ?: break }
    }

    /**
     * Handles received [GameRunningStatus] requests from clients
     * @param running the running status sent in the request
     */
    fun handleGameRunningRequest(running: Boolean) {
        if (running) {
            if (gamePaused.get()) lock.withLock { pauseCondition.signal() }
            gamePaused.set(false)
        }
        else if (playerNo.get() <= 1) gamePaused.set(true)
    }

    /**
     * Send frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_FAST] times a second
     *
     * Aircraft position
     *
     * (List not exhaustive)
     * */
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
            server.sendToAllUDP(FastUDPData(serialisedAircraftArray))
        }
    }

    /**
     * Send not so frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_SLOW] times a second
     *
     * Thunderstorm cells
     *
     * (List not exhaustive)
     * */
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
        server.sendToAllTCP(MetarData(airports.values().map { it.getSerialisedMetar() }.toTypedArray()))
    }

    /**
     * Sends aircraft control state sector updates
     * @param callsign the callsign of the aircraft to update
     * @param newSector the new sector that the aircraft is under control of
     * @param newUUID the UUID of the new player the aircraft is under control of, or null if tower/ACC
     * */
    fun sendAircraftSectorUpdateTCPToAll(callsign: String, newSector: Byte, newUUID: String?) {
        server.sendToAllTCP(AircraftSectorUpdateData(callsign, newSector, newUUID))
    }

    /**
     * Sends data to assign each player's individual sector
     * @param connection the specific connection to send to
     * @param newId the new sector ID assigned to this connection
     * @param newSectorArray the new sector configuration to use
     */
    fun sendIndividualSectorUpdateTCP(connection: Connection, newId: Byte, newSectorArray: Array<Sector.SerialisedSector>) {
        println("Individual sector send to $connection")
        connection.sendTCP(IndividualSectorData(newId, newSectorArray, primarySector.vertices ?: floatArrayOf()))
    }

    /**
     * Sends aircraft spawn data
     * @param aircraft the aircraft that spawned
     * */
    fun sendAircraftSpawn(aircraft: Aircraft) {
        server.sendToAllTCP(AircraftSpawnData(aircraft.getSerialisableObject()))
    }

    /**
     * Sends aircraft despawn data
     * @param callsign the callsign of the aircraft to despawn
     * */
    fun sendAircraftDespawn(callsign: String) {
        server.sendToAllTCP(AircraftDespawnData(callsign))
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
     * */
    fun sendAircraftClearanceStateUpdateToAll(callsign: String, primaryName: String = "", route: Route, hiddenLegs: Route,
                                           vectorHdg: Short?, vectorTurnDir: Byte?, clearedAlt: Int, expedite: Boolean,
                                              clearedIas: Short, minIas: Short, maxIas: Short, optimalIas: Short,
                                              clearedApp: String?, clearedTrans: String?) {
        server.sendToAllTCP(AircraftControlStateUpdateData(callsign, primaryName, route.getSerialisedObject(), hiddenLegs.getSerialisedObject(),
            vectorHdg, vectorTurnDir, clearedAlt, expedite, clearedIas, minIas, maxIas, optimalIas, clearedApp, clearedTrans, -5))
    }

    /**
     * Sends a new custom waypoint to be added by clients
     * @param waypoint the new custom waypoint
     * */
    fun sendCustomWaypointAdditionToAll(waypoint: Waypoint) {
        server.sendToAllTCP(CustomWaypointData(waypoint.getSerialisableObject()))
    }

    /**
     * Sends a message to clients to remove a custom waypoint
     * @param wptId the new ID of the custom waypoint to remove
     * */
    fun sendCustomWaypointRemovalToAll(wptId: Short) {
        server.sendToAllTCP(RemoveCustomWaypointData(wptId))
    }

    /**
     * Sends a message to clients to inform them of a pending runway configuration update
     * @param configId the new ID of the pending configuration, or null if the pending change is cancelled
     */
    fun sendPendingRunwayUpdateToAll(airportId: Byte, configId: Byte?) {
        server.sendToAllTCP(PendingRunwayUpdateData(airportId, configId))
    }

    /**
     * Sends a message to clients to inform them of the active runway configuration update
     * @param airportId the ID of the airport to change
     * @param configId the new ID of the active configuration
     */
    fun sendActiveRunwayUpdateToAll(airportId: Byte, configId: Byte) {
        server.sendToAllTCP(ActiveRunwayUpdateData(airportId, configId))
    }

    /** Sends a message to clients to inform them of a change in scores */
    fun sendScoreUpdate() {
        server.sendToAllTCP(ScoreData(score, highScore))
    }

    /**
     * Sends a message to clients to update them on the ongoing conflicts
     * @param conflicts the list of ongoing conflicts
     * @param potentialConflicts the list of potential conflicts
     * */
    fun sendConflicts(conflicts: GdxArray<ConflictManager.Conflict>, potentialConflicts: GdxArray<ConflictManager.PotentialConflict>) {
        server.sendToAllTCP(ConflictData(conflicts.toArray().map { it.getSerialisableObject() }.toTypedArray(),
            potentialConflicts.toArray().map { it.getSerialisableObject() }.toTypedArray()))
    }

    /** Sends a message to clients to inform them of the server's traffic settings */
    fun sendTrafficSettings() {
        server.sendToAllTCP(TrafficSettingsData(trafficMode, trafficValue,
            getArrivalClosedAirports(), getDepartureClosedAirports()))
    }

    /**
     * Adds a runnable to be run on the main server thread after the current engine update
     * @param runnable the runnable to add
     * */
    fun postRunnableAfterEngineUpdate(runnable: Runnable) {
        pendingRunnablesQueue.offer(runnable)
    }
}