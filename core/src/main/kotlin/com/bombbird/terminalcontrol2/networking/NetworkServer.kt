package com.bombbird.terminalcontrol2.networking

import com.bombbird.terminalcontrol2.networking.encryption.Decrypter
import com.bombbird.terminalcontrol2.networking.encryption.Encryptor
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.esotericsoftware.kryo.Kryo
import java.util.UUID
import javax.crypto.SecretKey

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
    abstract val encryptor: Encryptor
    abstract val decrypter: Decrypter
    abstract val kryo: Kryo

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

    /** Performs necessary actions for this server before connecting to the relay server */
    abstract fun beforeConnect()

    /**
     * Returns the room ID of the server (after it is allocated one by relay server); null for LAN servers
     * @return ID of the room if this is a public multiplayer game, else null
     */
    abstract fun getRoomId(): Short?

    abstract val connections: Collection<ConnectionMeta>

    /**
     * Performs encryption on the input data if needed using the server's encryptor, and returns the encrypted result
     *
     * If encryption not needed, returns the
     */
    protected fun encryptIfNeeded(data: Any): Any? {
        (data as? NeedsEncryption)?.let {
            return encryptor.encrypt(it)
        } ?: return data
    }
}