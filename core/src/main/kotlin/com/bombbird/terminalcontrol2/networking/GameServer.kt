package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Engine
import com.bombbird.terminalcontrol2.components.Acceleration
import com.bombbird.terminalcontrol2.components.Direction
import com.bombbird.terminalcontrol2.components.FlightType
import com.bombbird.terminalcontrol2.components.RunwayLabel
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.systems.PhysicsSystem
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.collections.GdxArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToLong

/** Main game server class, responsible for handling all game logic, updates, sending required game data information to clients and handling incoming client inputs */
class GameServer {
    companion object {
        const val UPDATE_INTERVAL = 1000.0 / Constants.SERVER_UPDATE_RATE
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_FAST = 1000.0 / Constants.SERVER_TO_CLIENT_UPDATE_RATE_FAST
        const val SERVER_TO_CLIENT_UPDATE_INTERVAL_SLOW = 1000.0 / Constants.SERVER_TO_CLIENT_UPDATE_RATE_SLOW
    }

    private val loopRunning = AtomicBoolean(false)
    private val server = Server()
    val engine = Engine()

    val sectors = GdxArray<Sector>()
    val aircraft = HashMap<String, Aircraft>()
    val airports = HashMap<String, Airport>()

    // var timeCounter = 0f
    // var frames = 0
    private var startTime = -1L

    /** Initialises game world */
    private fun loadGame() {
        // Add dummy airport, runways
        airports["TCTP"] = Airport(0, "TCTP", "Haoyuan", 0f, 0f, 108f).apply {
            addRunway(0, "05L", -15f, 5f, 49.08f, 3660, 108f, RunwayLabel.LEFT)
            addRunway(1, "05R", 10f, -10f, 49.07f, 3800, 108f, RunwayLabel.RIGHT)
        }

        // Add dummy aircraft
        aircraft["SHIBA2"] = Aircraft("SHIBA2", 10f, -10f, 108f, FlightType.DEPARTURE).apply {
            entity[Acceleration.mapper]?.dSpeed = 1.5f // Dummy acceleration
            entity[Direction.mapper]?.dirUnitVector?.rotateDeg(-49.07f) // Runway 05R heading
        }

        // Add default 1 player sector
        sectors.add(
            Sector(0, "Bombbird", "125.1", floatArrayOf(461f, 983f, 967f, 969f, 1150f, 803f, 1326f, 509f, 1438f, 39f, 1360f, -551f, 1145f, -728f,
                955f, -861f, 671f, -992f, 365f, -1018f, -86f, -871f, -316f, -1071f, -620f, -1867f, -1856f, -1536f, -1109f, -421f, 461f, 983f))
        )

        Constants.SERVER_ENGINE.addSystem(PhysicsSystem())
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
    }

    /** Initiates the KryoNet server for networking */
    private fun startNetworkingServer() {
        Log.set(Log.LEVEL_DEBUG)
        SerialisationRegistering.registerAll(server.kryo)
        server.bind(Variables.TCP_PORT, Variables.UDP_PORT)
        server.start()
        server.addListener(object: Listener {
            override fun received(connection: Connection?, `object`: Any?) {
                // TODO Handle receive requests
            }

            override fun connected(connection: Connection?) {
                // TODO Handle connections - send initial data and broadcast message if needed
                connection?.sendTCP("This is a test message")
                println(connection?.sendTCP(SerialisationRegistering.InitialLoadData(sectors.map { it.getSerialisableObject() }.toTypedArray(), aircraft.values.map { it.getSerialisableObject() }.toTypedArray(), airports.values.map { it.getSerialisableObject() }.toTypedArray())))
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
        while (loopRunning.get()) {
            val currMs = System.currentTimeMillis()
            if (startTime == -1L) {
                // Skip this frame since server has just started up
                startTime = currMs
            } else {
                // Update client with seconds passed since last frame
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
            }
            prevMs = currMs
            Thread.sleep((UPDATE_INTERVAL - (currMs - startTime) % UPDATE_INTERVAL).roundToLong())
        }
    }

    /** Update function */
    private fun update(delta: Float) {
        // timeCounter += delta
        // frames++
        // println("Delta: $delta Average frame time: ${timeCounter / frames} Average FPS: ${frames / timeCounter}")

        Constants.SERVER_ENGINE.update(delta)
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
        server.sendToAllUDP(SerialisationRegistering.FastUDPData(aircraft.values.map { it.getSerialisableObjectUDP() }.toTypedArray()))
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

    /** Send non-frequent, event-updated and/or important data
     *
     * METAR updates
     *
     * Aircraft creation, deletion
     *
     * Thunderstorm creation, deletion
     *
     * Initial data load on client connection (sectors, airports, runway, etc.)
     *
     * (List not exhaustive)
     */
    private fun sendTCPToAll() {
        // TODO send data
    }
}