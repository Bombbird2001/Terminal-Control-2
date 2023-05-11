package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.Exception

object RelayEndpoint {
    /** Moshi with RoomJSON adapter for JSON serialization */
    private val roomListType = Types.newParameterizedType(List::class.java, RelayServer.Room::class.java)
    private val moshi = Moshi.Builder().add(RoomJSONAdapter).build()
    private val moshiGamesAdapter = moshi.adapter<List<RelayServer.Room>>(roomListType)
    @OptIn(ExperimentalStdlibApi::class)
    private val moshiAuthRequestAdapter = moshi.adapter<HttpRequest.AuthorizationRequest>()
    @OptIn(ExperimentalStdlibApi::class)
    private val moshiAuthResponseAdapter = moshi.adapter<HttpRequest.AuthorizationResponse>()
    @OptIn(ExperimentalStdlibApi::class)
    private val moshiRoomCreationsStatusAdapter = moshi.adapter<HttpRequest.RoomCreationStatus>()
    private var relayServer: RelayServer? = null
    private lateinit var httpServer: HttpServer

    /** HttpHandler object to handle client requesting available games */
    private object GamesRequestHandler: HttpHandler {
        override fun handle(exchange: HttpExchange?) {
            try {
                if (exchange == null) return

                // Only accept POST requests
                if (exchange.requestMethod != "POST") return sendError(exchange, 405)

                // Get non-full games from server
                var responseJson = "{}"
                relayServer?.apply {
                    responseJson = moshiGamesAdapter.toJson(getAvailableGames())
                }

                val output = exchange.responseBody
                exchange.sendResponseHeaders(200, responseJson.length.toLong())
                output.write(responseJson.toByteArray())
                output.flush()
                output.close()
            } catch (e: Exception) {
                if (exchange != null) sendError(exchange, 500)
                e.printStackTrace()
            }
        }
    }

    /** HttpHandler object to handle client requesting authorization for joining a game */
    private object AuthorizeRequestHandler: HttpHandler {
        override fun handle(exchange: HttpExchange?) {
            try {
                if (exchange == null) return

                // Only accept POST requests
                if (exchange.requestMethod != "POST") return sendError(exchange, 405)

                val authReq: HttpRequest.AuthorizationRequest?
                try {
                    authReq = moshiAuthRequestAdapter.fromJson(String(exchange.requestBody.readBytes()))
                } catch (e: JsonDataException) {
                    return sendError(exchange, 400)
                }

                if (authReq == null) return sendError(exchange, 400)

                // Get symmetric key, nonce and output to JSON
                val res = relayServer?.authorizeUUIDToRoom(authReq.roomId, UUID.fromString(authReq.uuid))
                val authResponse = if (res == null) HttpRequest.AuthorizationResponse(false, "", "", "")
                else HttpRequest.AuthorizationResponse(true, res.first, res.second, res.third)
                val responseJson = moshiAuthResponseAdapter.toJson(authResponse)

                val output = exchange.responseBody
                exchange.sendResponseHeaders(200, responseJson.length.toLong())
                output.write(responseJson.toByteArray())
                output.flush()
                output.close()
            } catch (e: Exception) {
                if (exchange != null) sendError(exchange, 500)
                e.printStackTrace()
            }
        }
    }

    /** HttpHandler object to handle client requesting creation of new game */
    private object CreateGameHandler: HttpHandler {
        override fun handle(exchange: HttpExchange?) {
            try {
                if (exchange == null) return

                // Only accept POST requests
                if (exchange.requestMethod != "POST") return sendError(exchange, 405)

                // Get symmetric key and output to JSON
                val roomResponse = relayServer?.createPendingRoom() ?:
                HttpRequest.RoomCreationStatus(false, Short.MAX_VALUE, HttpRequest.AuthorizationResponse(false, "", "", ""))
                val responseJson = moshiRoomCreationsStatusAdapter.toJson(roomResponse)

                val output = exchange.responseBody
                exchange.sendResponseHeaders(200, responseJson.length.toLong())
                output.write(responseJson.toByteArray())
                output.flush()
                output.close()
            } catch (e: Exception) {
                if (exchange != null) sendError(exchange, 500)
                e.printStackTrace()
            }
        }
    }

    /**
     * Launches the server with the provided relayServer to handle logic
     * @param rs the [RelayServer]
     */
    fun launch(rs: RelayServer) {
        relayServer = rs
        httpServer = HttpServer.create(InetSocketAddress(Inet4Address.getByName(LOCALHOST), RELAY_ENDPOINT_PORT), 10)
        httpServer.createContext(RELAY_GAMES_PATH, GamesRequestHandler)
        httpServer.createContext(RELAY_GAME_AUTH_PATH, AuthorizeRequestHandler)
        httpServer.createContext(RELAY_GAME_CREATE_PATH, CreateGameHandler)
        httpServer.executor = Executors.newFixedThreadPool(2)
        httpServer.start()

        println("Endpoint launched")
    }

    /** Stops the server */
    fun stop() {
        httpServer.stop(1)
    }

    /**
     * Sends HTTP response error header to the connection
     * @param exchange the HTTP exchange to send to
     * @param errorCode the error code to send
     */
    private fun sendError(exchange: HttpExchange, errorCode: Int) {
        exchange.sendResponseHeaders(errorCode, -1)
        exchange.close()
    }
}