package com.bombbird.terminalcontrol2.networking

import com.bombbird.terminalcontrol2.networking.encryption.Decrypter
import com.bombbird.terminalcontrol2.networking.encryption.Encryptor
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.esotericsoftware.kryo.Kryo

/**
 * Abstraction for handling network activities as a client
 */
abstract class NetworkClient {
    abstract val isConnected: Boolean

    abstract val encryptor: Encryptor
    abstract val decrypter: Decrypter
    abstract val kryo: Kryo

    /**
     * Connects to the host with provided parameters
     * @param timeout max timeout to wait for connection
     * @param connectionHost address of the host
     * @param tcpPort TCP port to use
     * @param udpPort UDP port to use
     */
    abstract fun connect(timeout: Int, connectionHost: String, tcpPort: Int, udpPort: Int)

    /** Perform a reconnection if client is disconnected */
    abstract fun reconnect()

    /**
     * Sends an object to host over TCP
     * @param data the object to send
     */
    abstract fun sendTCP(data: Any)

    /**
     * Sets the ID of the game room, if required
     * @param roomId ID of game room
     */
    abstract fun setRoomId(roomId: Short)

    /** Starts the client */
    abstract fun start()

    /** Stops the client, ending its connection to host if currently connected */
    abstract fun stop()

    /** Dispose of the client */
    abstract fun dispose()

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