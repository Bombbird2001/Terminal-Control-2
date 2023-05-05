package com.bombbird.terminalcontrol2.networking

import java.util.UUID

/**
 * Abstraction for handling network activities as the server
 * @param gameServer the [GameServer] object for performing actions
 * @param onReceive the function to be called when an object is received
 * @param onConnect the function to be called when a connection is made
 * @param onDisconnect the function to be called when a connection ends
 */
abstract class NetworkServer(
    val gameServer: GameServer,
    protected val onReceive: (ConnectionMeta, Any?) -> Unit,
    protected val onConnect: (ConnectionMeta) -> Unit,
    protected val onDisconnect: (ConnectionMeta) -> Unit
) {
    /**
     * Starts the server
     * @param tcpPort port to accept TCP connections
     * @param udpPort port to accept UDP connections
     */
    abstract fun start(tcpPort: Int, udpPort: Int)

    /** Stops the server, ending all connections from clients if any */
    abstract fun stop()

    /**
     * Sends the object to all connected clients via TCP
     * @param data the object to send
     */
    abstract fun sendToAllTCP(data: Any)

    /**
     * Sends the object to all connected clients via UDP
     * @param data the object to send
     */
    abstract fun sendToAllUDP(data: Any)

    /**
     * Sends the object to the client with [uuid] via TCP
     * @param uuid UUID of the client to send to
     * @param data the object to send
     */
    abstract fun sendTCPToConnection(uuid: UUID, data: Any)

    abstract val connections: Collection<ConnectionMeta>
}