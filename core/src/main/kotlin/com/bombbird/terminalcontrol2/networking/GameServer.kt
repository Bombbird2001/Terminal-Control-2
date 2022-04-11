package com.bombbird.terminalcontrol2.networking

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.global.Variables
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
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
    val server = Server()

    val sectors = GdxArray<Entity>()
    val aircraft = HashMap<String, Entity>()
    val airports = HashMap<String, Entity>()

    // var timeCounter = 0f
    // var frames = 0
    var startTime = -1L

    /** Starts the game loop */
    fun initiateServer() {
        thread {
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
        server.start()
        server.bind(Variables.TCP_PORT, Variables.UDP_PORT)
        server.addListener(object: Listener {
            override fun received(connection: Connection?, `object`: Any?) {
                // TODO Handle receive requests
            }
        })
        server.kryo.apply {
            // TODO Register all classes to be transmitted
        }
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
                    sendFastUDP()
                    fastUpdateSlot = currFastSlot
                }

                val currSlowSlot = (currMs - startTime) / (SERVER_TO_CLIENT_UPDATE_INTERVAL_SLOW).toLong()
                if (currSlowSlot > slowUpdateSlot) {
                    // Send slow UDP update if this update is after the time slot for the next slow UDP update
                    sendSlowUDP()
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

        // TODO Game logic update
    }

    /** Send frequently updated data
     *
     * Aircraft position
     *
     * Aircraft navigation
     *
     * (List not exhaustive)
     * */
    private fun sendFastUDP() {
        // TODO send data
        // println("Fast UDP sent, time passed since program start: ${(System.currentTimeMillis() - startTime) / 1000f}s")
    }

    /** Send not so frequently updated data
     *
     * Thunderstorm cells
     *
     * (List not exhaustive)
     * */
    private fun sendSlowUDP() {
        // TODO send data
        // println("Slow UDP sent, time passed since program start: ${(System.currentTimeMillis() - startTime) / 1000f}s")
    }

    /** Send event-updated data
     *
     * METAR updates
     *
     * Aircraft deletion
     *
     * Thunderstorm deletion
     *
     * (List not exhaustive)
     */
    private fun sendTCP() {

    }
}