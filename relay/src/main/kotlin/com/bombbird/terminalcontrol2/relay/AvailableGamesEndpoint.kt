package com.bombbird.terminalcontrol2.relay

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

object AvailableGamesEndpoint {
    private var relayServer: RelayServer? = null

    private object RequestHandler: HttpHandler {
        override fun handle(exchange: HttpExchange?) {
            if (exchange == null) return

            // Only accept GET requests
            if (exchange.requestMethod != "GET") return

            // Get non-full games from server
            var responseJson = "{}"
            relayServer?.apply {

            }

            val output = exchange.responseBody
            exchange.sendResponseHeaders(200, responseJson.length.toLong())
            output.write(responseJson.toByteArray())
            output.flush()
            output.close()
        }
    }

    fun launch(rs: RelayServer) {
        relayServer = rs
        val server = HttpServer.create(InetSocketAddress("localhost", 57774), 10)
        server.createContext("/games", RequestHandler)
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
    }
}