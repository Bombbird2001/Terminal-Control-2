package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.RELAY_ENDPOINT_PORT
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

object AvailableGamesEndpoint {
    /** Moshi with RoomJSON adapter for JSON serialization */
    private val roomListType = Types.newParameterizedType(List::class.java, RelayServer.Room::class.java)
    private val moshiAdapter = Moshi.Builder().add(RoomJSONAdapter).build().adapter<List<RelayServer.Room>>(roomListType)
    private var relayServer: RelayServer? = null
    private lateinit var httpServer: HttpServer

    private object RequestHandler: HttpHandler {
        override fun handle(exchange: HttpExchange?) {
            if (exchange == null) return

            // Only accept GET requests
            if (exchange.requestMethod != "GET") return

            // Get non-full games from server
            var responseJson = "{}"
            relayServer?.apply {
                responseJson = moshiAdapter.toJson(getAvailableGames())
            }

            val output = exchange.responseBody
            exchange.sendResponseHeaders(200, responseJson.length.toLong())
            output.write(responseJson.toByteArray())
            output.flush()
            output.close()
        }
    }

    /**
     * Launches the server with the provided relayServer to handle logic
     * @param rs the [RelayServer]
     */
    fun launch(rs: RelayServer) {
        relayServer = rs
        httpServer = HttpServer.create(InetSocketAddress(RELAY_ENDPOINT_PORT), 10)
        httpServer.createContext("/games", RequestHandler)
        httpServer.executor = Executors.newFixedThreadPool(2)
        httpServer.start()

        println("Endpoint launched")
    }

    /** Stops the server */
    fun stop() {
        httpServer.stop(1)
    }
}