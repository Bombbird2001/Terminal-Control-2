package com.bombbird.terminalcontrol2.networking

import com.bombbird.terminalcontrol2.networking.encryption.Encryptor
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.esotericsoftware.kryo.Kryo
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
    abstract val kryo: Kryo

    /** Starts the server */
    abstract fun start()

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

    /**
     * Performs necessary actions for this server before connecting to the relay server. Returns a boolean to denote
     * whether pre-start actions have succeeded - if this is false, the server should not attempt to call [start]
     */
    abstract fun beforeStart(): Boolean

    /**
     * Returns the room ID of the server (after it is allocated one by relay server); null for LAN servers
     * @return ID of the room if this is a public multiplayer game, else null
     */
    abstract fun getRoomId(): Short?

    abstract val connections: Collection<ConnectionMeta>

    /**
     * Performs encryption on the input data if needed using the server's encryptor, and returns the encrypted result
     *
     * If encryption not needed, returns the object itself
     * @param data the object to encrypt
     * @param encryptor the [Encryptor] to use (depends on the connection for LAN Server, but is the same for public
     * server)
     */
    protected fun encryptIfNeeded(data: Any, encryptor: Encryptor): Any? {
        (data as? NeedsEncryption)?.let {
            return encryptor.encrypt(it)
        } ?: return data
    }
}