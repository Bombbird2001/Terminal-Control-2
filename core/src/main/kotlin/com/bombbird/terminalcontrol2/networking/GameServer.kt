package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.files.GameLoader
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToLong

/** Main game server class, responsible for handling all game logic, updates, sending required game data information to clients and handling incoming client inputs */
class GameServer {
    companion object {
        const val UPDATE_INTERVAL = 1000.0 / Constants.SERVER_UPDATE_RATE
        const val UPDATE_INTERVAL_LOW_FREQ = 1000.0 / Constants.UPDATE_RATE_LOW_FREQ
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_FAST = 1000.0 / Constants.SERVER_TO_CLIENT_UPDATE_RATE_FAST
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_SLOW = 1000.0 / Constants.SERVER_TO_CLIENT_UPDATE_RATE_SLOW
        const val SERVER_METAR_UPDATE_INTERVAL = Constants.SERVER_METAR_UPDATE_INTERVAL_MINS * 60 * 1000
    }

    private val loopRunning = AtomicBoolean(false)
    val gameRunning: Boolean
        get() = loopRunning.get()
    private val server = Server()
    val engine = Engine()

    val sectors = GdxArray<Sector>(Constants.SECTOR_SIZE)
    val aircraft = GdxArrayMap<String, Aircraft>(Constants.AIRCRAFT_SIZE)
    val minAltSectors = GdxArray<MinAltSector>()
    val shoreline = GdxArray<Shoreline>()

    /** Maps [AirportInfo.arptId] (instead of [AirportInfo.icaoCode]) to the airport for backwards compatibility (in case of airport ICAO code reassignments;
     * Old airport is still available as a separate entity even with the same ICAO code as the new airport)
     * */
    val airports = GdxArrayMap<Byte, Airport>(Constants.AIRPORT_SIZE)

    /** Maps [AirportInfo.icaoCode] to the most updated [AirportInfo.arptId];
     * The new airport with the ICAO code will be chosen instead of the old one after an ICAO code reassignment
     * */
    val updatedAirportMapping = GdxArrayMap<String, Byte>(Constants.AIRPORT_SIZE)

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
    val publishedHolds = GdxArrayMap<String, PublishedHold>(Constants.PUBLISHED_HOLD_SIZE)

    // var timeCounter = 0f
    // var frames = 0
    private var startTime = -1L

    /** Initialises game world */
    private fun loadGame() {
        GameLoader.loadWorldData("TCTP", this)

        // Add dummy aircraft
        val rwy = airports[0]?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(0)
        val rwyPos = rwy?.entity?.get(Position.mapper)
        aircraft.put("SHIBA2", Aircraft("SHIBA2", rwyPos?.x ?: 10f, rwyPos?.y ?: -10f, rwy?.entity?.get(Altitude.mapper)?.altitudeFt ?: 108f, FlightType.DEPARTURE, false).apply {
            entity[Direction.mapper]?.trackUnitVector?.rotateDeg((rwy?.entity?.get(Direction.mapper)?.trackUnitVector?.angleDeg() ?: 40.92f) - 90) // Runway 05L heading
            // Calculate headwind component for takeoff
            val headwind = entity[Altitude.mapper]?.let{ alt -> entity[Direction.mapper]?.let { dir -> entity[Position.mapper]?.let { pos ->
                val wind = getClosestAirportWindVector(pos.x, pos.y)
                calculateIASFromTAS(alt.altitudeFt, pxpsToKt(wind.dot(dir.trackUnitVector)))
            }}} ?: 0f
            entity += TakeoffRoll(calculateRequiredAcceleration(0, ((entity[AircraftInfo.mapper]?.aircraftPerf?.vR ?: 0) + headwind).toInt().toShort(), ((rwy?.entity?.get(RunwayInfo.mapper)?.lengthM ?: 3800) - 1000) * MathUtils.random(0.75f, 1f)))
            val climbOutSpeed = ((entity[AircraftInfo.mapper]?.aircraftPerf?.vR ?: 160) + MathUtils.random(15, 20)).toShort()
            entity[CommandTarget.mapper]?.apply {
                targetAltFt = 3000f
                targetIasKt = climbOutSpeed
                targetHdgDeg = 54f
            }
            entity += ClearanceAct(ClearanceState("HICAL1C", airports[0]?.entity?.get(SIDChildren.mapper)?.sidMap?.get("HICAL1C")?.getRandomSIDRouteForRunway("05L") ?: Route(), Route(),
                null, 3000, climbOutSpeed))
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
        server.bind(Variables.TCP_PORT, Variables.UDP_PORT)
        server.start()
        server.addListener(object: Listener {
            override fun received(connection: Connection?, `object`: Any?) {
                // TODO Handle receive requests
            }

            override fun connected(connection: Connection?) {
                // TODO Handle connections - send initial data and broadcast message if needed
                connection?.sendTCP(SerialisationRegistering.InitialAirspaceData(Variables.MAG_HDG_DEV, Variables.MIN_ALT, Variables.MAX_ALT, Variables.MIN_SEP, Variables.TRANS_ALT, Variables.TRANS_LVL))
                connection?.sendTCP(SerialisationRegistering.InitialSectorData(sectors.map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.InitialAircraftData(aircraft.values().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.AirportData(airports.values().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.WaypointData(waypoints.values.map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.PublishedHoldData(publishedHolds.values().map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.MinAltData(minAltSectors.map { it.getSerialisableObject() }.toTypedArray()))
                connection?.sendTCP(SerialisationRegistering.ShorelineData(shoreline.map { it.getSerialisableObject() }.toTypedArray()))
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
            val currFrame = (currMs - startTime) * Constants.SERVER_UPDATE_RATE / 1000
            val nextFrameTime = (UPDATE_INTERVAL - (currMs - startTime) % UPDATE_INTERVAL)

            // For cases where rounding errors result in a nextFrameTime of almost 0 - take the time needed to 2 frames later instead
            if (nextFrameTime < 0.1 * UPDATE_INTERVAL) {
                var newTime = (currFrame + 2) * 1000 / Constants.SERVER_UPDATE_RATE + startTime - currMs
                if (newTime > 24) newTime -= 1000 / Constants.SERVER_UPDATE_RATE
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
    }

    /** Update function that runs at a lower frequency, [Constants.UPDATE_RATE_LOW_FREQ] times a second */
    private fun lowFreqUpdate() {
        val systems = engine.systems
        for (i in 0 until systems.size()) (systems[i] as? LowFreqUpdate)?.lowFreqUpdate()
    }

    /** Send frequently updated data approximately [Constants.SERVER_TO_CLIENT_UPDATE_RATE_FAST] times a second
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

    /** Send not so frequently updated data approximately [Constants.SERVER_TO_CLIENT_UPDATE_RATE_SLOW] times a second
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
                                           vectorHdg: Short?, clearedAlt: Int, clearedIas: Short) {
        server.sendToAllTCP(SerialisationRegistering.AircraftControlStateUpdateData(callsign, primaryName, route.getSerialisedObject(), hiddenLegs.getSerialisedObject(), vectorHdg, clearedAlt, clearedIas))
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
}