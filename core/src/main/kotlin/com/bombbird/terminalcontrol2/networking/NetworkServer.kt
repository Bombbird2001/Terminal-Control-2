package com.bombbird.terminalcontrol2.networking

import com.bombbird.terminalcontrol2.global.SERVER_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.NullPointerException
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
    protected val encryptor: Encryptor = AESGCMEncryptor(::getSerialisedBytes)
    protected val decrypter: Decrypter = AESGCMDecrypter(::fromSerializedBytes)
    abstract val serverKryo: Kryo
    private val manualKryo = Kryo().apply { registerClassesToKryo(this) }
    private val manualKryoLock = Any()

    /** Starts the server, returns true if successful, else false */
    abstract fun start(): Boolean

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

    /** Gets the status of the connection, of both TCP and UDP */
    abstract fun getConnectionStatus(): String

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

    /**
     * Serialises the input object with Kryo and returns the byte array - retries up to 2 more times if error occurs
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object, or null if serialisation error occurs
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