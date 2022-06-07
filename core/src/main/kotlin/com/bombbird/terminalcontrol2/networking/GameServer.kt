package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Polygon
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.files.loadAircraftData
import com.bombbird.terminalcontrol2.files.loadWorldData
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.systems.AISystem
import com.bombbird.terminalcontrol2.systems.ControlStateSystem
import com.bombbird.terminalcontrol2.systems.PhysicsSystem
import com.bombbird.terminalcontrol2.utilities.*
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
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
    }

    private val loopRunning = AtomicBoolean(false)
    val gameRunning: Boolean
        get() = loopRunning.get()
    private val gamePaused = AtomicBoolean(false)
    private var lock = ReentrantLock()
    private val condition = lock.newCondition()
    val playerNo = 1.byte // TODO Change depending on current number of connected players
    private val server = Server()
    val engine = Engine()

    // Blocking queue to store runnables to be run in the main thread after engine update
    private val pendingRunnablesQueue = ConcurrentLinkedQueue<Runnable>()
    val primarySector = Polygon() // The primary TMA sector polygon without being split up into sub-sectors
    val sectors =  GdxArrayMap<Byte, GdxArray<Sector>>(SECTOR_COUNT_SIZE) // Sector configuration for different player number
    val aircraft = GdxArrayMap<String, Aircraft>(AIRCRAFT_SIZE)
    val minAltSectors = GdxArray<MinAltSector>()
    val shoreline = GdxArray<Shoreline>()

    /** Maps [AirportInfo.arptId] (instead of [AirportInfo.icaoCode]) to the airport for backwards compatibility (in case of airport ICAO code reassignments;
     * Old airport is still available as a separate entity even with the same ICAO code as the new airport)
     * */
    val airports = GdxArrayMap<Byte, Airport>(AIRPORT_SIZE)

    /** Maps [AirportInfo.icaoCode] to the most updated [AirportInfo.arptId];
     * The new airport with the ICAO code will be chosen instead of the old one after an ICAO code reassignment
     * */
    val updatedAirportMapping = GdxArrayMap<String, Byte>(AIRPORT_SIZE)

    /** Maps [WaypointInfo.wptId] (instead of [WaypointInfo.wptName]) to the waypoint for backwards compatibility (in case waypoint position is moved;
     * old waypoint is still available as a separate entity even with the same name as the new waypoint)
     * */
    val waypoints = HashMap<Short, Waypoint>()

    /** Maps [WaypointInfo.wptName] to the most updated [WaypointInfo.wptId];
     * The new waypoint with the name will be chosen instead of the old one after the waypoint has "shifted"
     * */
    val updatedWaypointMapping = HashMap<String, Short>()

    /** Maps [WaypointInfo.wptName] to the [PublishedHold]
     *
     * This map will map to the most updated published hold, since old holding legs are stored individually with the waypoint ID in the aircraft's [ClearanceState.route]
     * */
    val publishedHolds = GdxArrayMap<String, PublishedHold>(PUBLISHED_HOLD_SIZE)

    // var timeCounter = 0f
    // var frames = 0
    private var startTime = -1L

    /** Initialises game world */
    private fun loadGame(mainName: String) {
        loadAircraftData()
        loadWorldData(mainName, this)

        // Set 05L, 05R as active for development
        airports[0]?.entity?.get(RunwayChildren.mapper)?.rwyMap?.apply {
            get(0).entity += ActiveLanding()
            get(0).entity += ActiveTakeoff()
            get(1).entity += ActiveLanding()
            get(1).entity += ActiveTakeoff()
        }

        // Set 28 as active for development
        airports[1]?.entity?.get(RunwayChildren.mapper)?.rwyMap?.apply {
            get(1).entity += ActiveLanding()
            get(1).entity += ActiveTakeoff()
        }

        // Add dummy aircraft
        airports[0]?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(0)?.entity?.let { rwy -> createDeparture(rwy, this) }
        airports[0]?.entity?.let { arpt -> createArrival(arpt, this) }
        appTestArrival(this)

        engine.addSystem(PhysicsSystem(1f))
        engine.addSystem(AISystem())
        engine.addSystem(ControlStateSystem(1f))

        requestAllMetar()
    }

    /** Starts the game loop */
    fun initiateServer(mainName: String) {
        thread {
            loadGame(mainName)
            startNetworkingServer()
            startTime = -1L
            Gdx.app.log("GameServer", "Starting game server")
            loopRunning.set(true)
            gameLoop()
            stopNetworkingServer()
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
        server.bind(TCP_PORT, UDP_PORT)
        server.start()
        server.addListener(object: Listener {
            override fun received(connection: Connection?, obj: Any?) {
                // TODO Handle receive requests
                (obj as? AircraftControlStateUpdateData)?.apply {
                    postRunnableAfterEngineUpdate {
                        aircraft[obj.callsign]?.entity?.let {
                            if (it[Controllable.mapper]?.sectorId != obj.sendingSector) return@postRunnableAfterEngineUpdate
                            addNewClearanceToPendingClearances(it, obj, connection?.returnTripTime ?: 0)
                        }
                    }
                } ?: (obj as? GameRunningStatus)?.apply {
                    if (obj.running) {
                        if (gamePaused.get()) lock.withLock { condition.signal() }
                        gamePaused.set(false)
                    }
                    else if (playerNo <= 1) gamePaused.set(true)
                }
            }

            override fun connected(connection: Connection?) {
                // TODO Send broadcast message
                connection?.sendTCP(ClearAllClientData())
                connection?.sendTCP(InitialAirspaceData(MAG_HDG_DEV, MIN_ALT, MAX_ALT, MIN_SEP, TRANS_ALT, TRANS_LVL))
                connection?.sendTCP(
                    InitialIndividualSectorData(playerNo, sectors[playerNo].toArray().map { it.getSerialisableObject() }.toTypedArray(),
                    primarySector.vertices ?: floatArrayOf()
                ))
                connection?.sendTCP(InitialAircraftData(aircraft.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(AirportData(airports.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
                val wptArray = waypoints.values.toTypedArray()
                connection?.sendTCP(WaypointData(wptArray.map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(WaypointMappingData(wptArray.map { it.getMappingSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(PublishedHoldData(publishedHolds.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(MinAltData(minAltSectors.toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(ShorelineData(shoreline.toArray().map { it.getSerialisableObject() }.toTypedArray()))

                // Send current METAR
                connection?.sendTCP(MetarData(airports.values().map { it.getSerialisedMetar() }.toTypedArray()))
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
                condition.await()
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
     * Send frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_FAST] times a second
     *
     * Aircraft position
     *
     * Aircraft navigation
     *
     * (List not exhaustive)
     * */
    private fun sendFastUDPToAll() {
        // TODO send data
        // println("Fast UDP sent, time passed since program start: ${(System.currentTimeMillis() - startTime) / 1000f}s")
        server.sendToAllUDP(FastUDPData(aircraft.values().map { it.getSerialisableObjectUDP() }.toTypedArray()))
    }

    /** Send not so frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_SLOW] times a second
     *
     * Thunderstorm cells
     *
     * (List not exhaustive)
     * */
    private fun sendSlowUDPToAll() {
        // TODO send data
        // println("Slow UDP sent, time passed since program start: ${(System.currentTimeMillis() - startTime) / 1000f}s")
    }

    /** Send non-frequent METAR updates */
    fun sendMetarTCPToAll() {
        server.sendToAllTCP(MetarData(airports.values().map { it.getSerialisedMetar() }.toTypedArray()))
    }

    /**
     * Sends aircraft control state sector updates
     * @param callsign the callsign of the aircraft to update
     * @param newSector the new sector that the aircraft is under control of
     * */
    fun sendAircraftSectorUpdateTCPToAll(callsign: String, newSector: Byte) {
        server.sendToAllTCP(AircraftSectorUpdateData(callsign, newSector))
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
                                           vectorHdg: Short?, vectorTurnDir: Byte?, clearedAlt: Int,
                                              clearedIas: Short, minIas: Short, maxIas: Short, optimalIas: Short,
                                              clearedApp: String?, clearedTrans: String?) {
        server.sendToAllTCP(AircraftControlStateUpdateData(callsign, primaryName, route.getSerialisedObject(), hiddenLegs.getSerialisedObject(),
            vectorHdg, vectorTurnDir, clearedAlt, clearedIas, minIas, maxIas, optimalIas, clearedApp, clearedTrans, -5))
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

    /** Send non-frequent, event-updated and/or important data
     *
     * METAR updates
     *
     * Aircraft creation, deletion
     *
     * Thunderstorm creation, deletion
     *
     * (List not exhaustive)
     */
    private fun sendTCPToAll() {
        // TODO send data
    }

    /**
     * Adds a runnable to be run on the main server thread after the current engine update
     * @param runnable the runnable to add
     * */
    fun postRunnableAfterEngineUpdate(runnable: Runnable) {
        pendingRunnablesQueue.offer(runnable)
    }
}