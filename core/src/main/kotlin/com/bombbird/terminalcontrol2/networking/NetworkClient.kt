package com.bombbird.terminalcontrol2.networking

import com.bombbird.terminalcontrol2.global.SERVER_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.NullPointerException

/**
 * Abstraction for handling network activities as a client
 */
abstract class NetworkClient {
    abstract val isConnected: Boolean

    protected val encryptor: Encryptor = AESGCMEncryptor(::getSerialisedBytes)
    protected val decrypter: Decrypter = AESGCMDecrypter(::fromSerializedBytes)
    abstract val clientKryo: Kryo
    private val manualKryo = Kryo().apply { registerClassesToKryo(this) }
    private val manualKryoLock = Any()

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
     * Actions to be performed before initiating connection
     * @param roomId The ID of the room to connect to, applicable only to public multiplayer games
     */
    abstract fun beforeConnect(roomId: Short?)

    /** Starts the client */
    abstract fun start()

    /** Stops the client, ending its connection to host if currently connected */
    abstract fun stop()

    /** Dispose of the client */
    abstract fun dispose()

    /**
     * Performs encryption on the input data if needed using the server's encryptor, and returns the encrypted result
     *
     * If encryption not needed, returns the object itself
     */
    protected fun encryptIfNeeded(data: Any): Any? {
        (data as? NeedsEncryption)?.let {
            return encryptor.encrypt(it)
        } ?: return data
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    @Synchronized
    protected fun getSerialisedBytes(data: Any): ByteArray? {
        var times = 0
        while (times < 3) {
            try {
                val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
                synchronized(manualKryoLock) {
                    manualKryo.writeClassAndObject(serialisationOutput, data)
                }
                return serialisationOutput.toBytes()
            } catch (e: NullPointerException) {
                times++
            }
        }

        return null
    }

    /**
     * De-serialises the input byte array with Kryo and returns the object
     * @param data the byte array to de-serialise
     * @return the de-serialised object
     */
    @Synchronized
    protected fun fromSerializedBytes(data: ByteArray): Any? {
        var times = 0
        while (times < 3) {
            try {
                synchronized(manualKryoLock) {
                    return manualKryo.readClassAndObject(Input(data))
                }
            } catch (e: NullPointerException) {
                times++
            }
        }

        return null
    }
}