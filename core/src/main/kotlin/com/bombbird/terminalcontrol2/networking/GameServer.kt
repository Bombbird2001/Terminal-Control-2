package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.files.GameLoader
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.systems.AISystem
import com.bombbird.terminalcontrol2.systems.ControlStateSystem
import com.bombbird.terminalcontrol2.systems.LowFreqUpdate
import com.bombbird.terminalcontrol2.systems.PhysicsSystem
import com.bombbird.terminalcontrol2.utilities.*
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToLong

/** Main game server class, responsible for handling all game logic, updates, sending required game data information to clients and handling incoming client inputs */
class GameServer {
    companion object {
        const val UPDATE_INTERVAL = 1000.0 / SERVER_UPDATE_RATE
        const val UPDATE_INTERVAL_LOW_FREQ = 1000.0 / UPDATE_RATE_LOW_FREQ
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_FAST = 1000.0 / SERVER_TO_CLIENT_UPDATE_RATE_FAST
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_SLOW = 1000.0 / SERVER_TO_CLIENT_UPDATE_RATE_SLOW
        const val SERVER_METAR_UPDATE_INTERVAL = SERVER_METAR_UPDATE_INTERVAL_MINS * 60 * 1000
    }

    private val loopRunning = AtomicBoolean(false)
    val gameRunning: Boolean
        get() = loopRunning.get()
    private val server = Server()
    val engine = Engine()

    // Blocking queue to store aircraft clearance updates in a different thread as the main thread
    val pendingClearanceQueue = ConcurrentLinkedQueue<NetworkPendingClearanceObject>()

    val sectors = GdxArray<Sector>(SECTOR_SIZE)
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
    private fun loadGame() {
        GameLoader.loadWorldData("TCTP", this)

        // Add dummy aircraft
        val rwy = airports[0]?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(0)?.entity
        val rwyPos = rwy?.get(Position.mapper)
        val rwyDir = rwy?.get(Direction.mapper)
        aircraft.put("SHIBA2", Aircraft("SHIBA2", rwyPos?.x ?: 10f, rwyPos?.y ?: -10f, rwy?.get(Altitude.mapper)?.altitudeFt ?: 108f, FlightType.DEPARTURE, false).apply {
            entity[Direction.mapper]?.trackUnitVector?.rotateDeg((rwyDir?.trackUnitVector?.angleDeg() ?: 0f) - 90) // Runway heading
            // Calculate headwind component for takeoff
            val headwind = entity[Altitude.mapper]?.let { alt -> rwyDir?.let { dir -> entity[Position.mapper]?.let { pos ->
                val wind = getClosestAirportWindVector(pos.x, pos.y)
                calculateIASFromTAS(alt.altitudeFt, pxpsToKt(wind.dot(dir.trackUnitVector)))
            }}} ?: 0f
            entity[Speed.mapper]?.speedKts = -headwind
            val acPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: return@apply
            entity += TakeoffRoll(calculateRequiredAcceleration(0, (acPerf.vR + headwind).toInt().toShort(), ((rwy?.get(RunwayInfo.mapper)?.lengthM ?: 3800) - 1000) * MathUtils.random(0.75f, 1f)))
            val sid = airports[0]?.entity?.get(SIDChildren.mapper)?.sidMap?.get("HICAL1C") // TODO random selection from eligible SIDs
            val rwyName = rwy?.get(RunwayInfo.mapper)?.rwyName ?: ""
            val initClimb = sid?.rwyInitialClimbs?.get(rwyName) ?: 3000
            entity += ClearanceAct(ClearanceState.ActingClearance(ClearanceState(sid?.name ?: "", sid?.getRandomSIDRouteForRunway(rwyName) ?: Route(), Route(),
                null, initClimb, acPerf.climbOutSpeed)))
            entity[CommandTarget.mapper]?.apply {
                targetAltFt = initClimb
                targetIasKt = acPerf.climbOutSpeed
                targetHdgDeg = convertWorldAndRenderDeg(rwyDir?.trackUnitVector?.angleDeg() ?: 90f) + MAG_HDG_DEV
            }
        })

        engine.addSystem(PhysicsSystem())
        engine.addSystem(AISystem())
        engine.addSystem(ControlStateSystem())

        requestAllMetar()
    }

    /** Starts the game loop */
    fun initiateServer() {
        thread {
            loadGame()
            startNetworkingServer()
            startTime = -1L
            loopRunning.set(true)
            gameLoop()
            stopNetworkingServer()
        }
    }

    /** Stops the game loop and exits server */
    fun stopServer() {
        loopRunning.set(false)
        engine.removeAllEntities()
        engine.removeAllSystems()
    }

    /** Initiates the KryoNet server for networking */
    private fun startNetworkingServer() {
        // Log.set(Log.LEVEL_DEBUG)
        SerialisationRegistering.registerAll(server.kryo)
        server.bind(TCP_PORT, UDP_PORT)
        server.start()
        server.addListener(object: Listener {
            override fun received(connection: Connection?, obj: Any?) {
                // TODO Handle receive requests
                (obj as? SerialisationRegistering.AircraftControlStateUpdateData)?.apply {
                    pendingClearanceQueue.offer(NetworkPendingClearanceObject(this, connection?.returnTripTime ?: 0))
                }
            }

            override fun connected(connection: Connection?) {
                // TODO Send broadcast message
                connection?.sendTCP(SerialisationRegistering.InitialAirspaceData(MAG_HDG_DEV, MIN_ALT, MAX_ALT, MIN_SEP, TRANS_ALT, TRANS_LVL))
                connection?.sendTCP(SerialisationRegistering.InitialSectorData(sectors.toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.InitialAircraftData(aircraft.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.AirportData(airports.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
                val wptArray = waypoints.values.toTypedArray()
                connection?.sendTCP(SerialisationRegistering.WaypointData(wptArray.map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.WaypointMappingData(wptArray.map { it.getMappingSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.PublishedHoldData(publishedHolds.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.MinAltData(minAltSectors.toArray().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.ShorelineData(shoreline.toArray().map { it.getSerialisableObject() }.toTypedArray()))
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
        var lowFreqUpdateSlot = -1L
        var fastUpdateSlot = -1L
        var slowUpdateSlot = -1L
        var metarUpdateTime = 0
        while (loopRunning.get()) {
            val currMs = System.currentTimeMillis()
            if (startTime == -1L) {
                // Skip this frame since server has just started up
                startTime = currMs
            } else {
                // Update client with seconds passed since last frame
                // println("Time diff: ${currMs - prevMs}")
                update((currMs - prevMs) / 1000f)
                val currLowFreqSlot = (currMs - startTime) / (UPDATE_INTERVAL_LOW_FREQ).toLong()
                if (currLowFreqSlot > lowFreqUpdateSlot) {
                    // Do low frequency update if this update is after the time slot for the next low frequency update
                    lowFreqUpdate()
                    lowFreqUpdateSlot = currLowFreqSlot
                }

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
        // println(engine.entities.size())

        // Process pending aircraft clearances from networking thread
        while (true) {
            val obj = pendingClearanceQueue.poll() ?: break
            aircraft[obj.aircraftControlStateUpdateData.callsign]?.entity?.let {
                addNewClearanceToPendingClearances(it, obj.aircraftControlStateUpdateData, obj.returnTripTime)
            }
        }
    }

    /** Update function that runs at a lower frequency, [UPDATE_RATE_LOW_FREQ] times a second */
    private fun lowFreqUpdate() {
        val systems = engine.systems
        for (i in 0 until systems.size()) (systems[i] as? LowFreqUpdate)?.lowFreqUpdate()
    }

    /** Send frequently updated data approximately [SERVER_TO_CLIENT_UPDATE_RATE_FAST] times a second
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
        server.sendToAllUDP(SerialisationRegistering.FastUDPData(aircraft.values().map { it.getSerialisableObjectUDP() }.toTypedArray()))
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
        server.sendToAllTCP(SerialisationRegistering.MetarData(airports.values().map { it.getSerialisedMetar() }.toTypedArray()))
    }

    /** Sends aircraft control state sector updates */
    fun sendAircraftSectorUpdateTCPToAll(callsign: String, newSector: Byte) {
        server.sendToAllTCP(SerialisationRegistering.AircraftSectorUpdateData(callsign, newSector))
    }

    /** Sends aircraft clearance state updates */
    fun sendAircraftClearanceStateUpdateToAll(callsign: String, primaryName: String = "", route: Route, hiddenLegs: Route,
                                           vectorHdg: Short?, clearedAlt: Int, clearedIas: Short, minIas: Short, maxIas: Short, optimalIas: Short) {
        server.sendToAllTCP(SerialisationRegistering.AircraftControlStateUpdateData(callsign, primaryName, route.getSerialisedObject(), hiddenLegs.getSerialisedObject(), vectorHdg, clearedAlt, clearedIas, minIas, maxIas, optimalIas))
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
     * Helper class for storage in an [ArrayBlockingQueue] for aircraft control state updates received by the server thread
     *
     * [aircraftControlStateUpdateData] is the actual control state update data
     *
     * [returnTripTime] is the TCP return trip time at the time of receipt
     * */
    class NetworkPendingClearanceObject(val aircraftControlStateUpdateData: SerialisationRegistering.AircraftControlStateUpdateData, val returnTripTime: Int)
}