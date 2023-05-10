package com.bombbird.terminalcontrol2.networking.hostserver

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.relayserver.NewGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.RelayHostReceive
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToAllClientsUDP
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToClient
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.minlog.Log
import ktx.collections.GdxArrayMap
import java.util.*
import javax.crypto.SecretKey

/**
 * Server for handling public multiplayer relay games
 *
 * Cannot be used for LAN multiplayer games, use [LANServer] for that
 */
class PublicServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit,
    private val mapName: String
) : NetworkServer(gameServer, onReceive, onConnect, onDisconnect) {
    override val encryptor: Encryptor = AESGCMEncryptor(::getSerialisedBytes)
    override val decrypter: Decrypter = AESGCMDecrypter(::fromSerializedBytes)
    override val kryo: Kryo
        get() = relayServerConnector.kryo

    private var roomId: Short = Short.MAX_VALUE
    private var symmetricKey: SecretKey? = null
    private val relayServerConnector = Client(SERVER_WRITE_BUFFER_SIZE, SERVER_READ_BUFFER_SIZE).apply {
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                if (obj is NeedsEncryption) {
                    Log.info("PublicServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    decrypter.decrypt(obj)
                } else obj

                (decrypted as? RelayHostReceive)?.apply {
                    handleRelayHostReceive(this@PublicServer)
                }
            }
        })
    }

    /** Maps [UUID] to [ConnectionMeta] */
    private val uuidConnectionMap = GdxArrayMap<UUID, ConnectionMeta>(PLAYER_SIZE)

    override fun start(tcpPort: Int, udpPort: Int) {
        registerClassesToKryo(relayServerConnector.kryo)
        relayServerConnector.start()
        relayServerConnector.connect(5000, Secrets.RELAY_ADDRESS, TCP_PORT, UDP_PORT)
    }

    override fun stop() {
        relayServerConnector.stop()
    }

    override fun sendToAllTCP(data: Any) {
        val dataToSend = encryptIfNeeded(ServerToClient(roomId, null, getSerialisedBytes(data), true)) ?: return
        relayServerConnector.sendTCP(dataToSend)
    }

    override fun sendToAllUDP(data: Any) {
        // Will not be encrypted
        relayServerConnector.sendUDP(ServerToAllClientsUDP(getSerialisedBytes(data)))
    }

    override fun sendTCPToConnection(uuid: UUID, data: Any) {
        val dataToSend = encryptIfNeeded(ServerToClient(roomId, uuid.toString(), getSerialisedBytes(data), true))
        relayServerConnector.sendTCP(dataToSend)
    }

    override fun setRoomId(roomId: Short) {
        if (roomId == Short.MAX_VALUE) {
            // Failed to create room, stop server
            GAME.quitCurrentGame()
            return
        }

        if (this.roomId == Short.MAX_VALUE)
            this.roomId = roomId
    }


    override fun setSymmetricKey(key: SecretKey) {
        symmetricKey = key
    }

    override fun getRoomId(): Short {
        return roomId
    }

    override val connections: Collection<ConnectionMeta>
        get() {
            val conns = HashSet<ConnectionMeta>()
            for (conn in uuidConnectionMap.values()) {
                conns.add(conn)
            }
            return conns
        }

    /**
     * Function to be called when [PublicServer] receives message from relay server that player has connected
     * @param uuid [UUID] of the player joining
     */
    fun onConnect(uuid: UUID) {
        val newConn = ConnectionMeta(uuid)
        uuidConnectionMap.put(uuid, newConn)
        onConnect(newConn)
    }

    /**
     * Function to be called when [PublicServer] receives message from relay server that player has disconnected
     * @param uuid [UUID] of the player disconnecting
     */
    fun onDisconnect(uuid: UUID) {
        val removedConn = uuidConnectionMap.removeKey(uuid) ?: run {
            Log.info("PublicServer", "Failed to remove $uuid from connection map - it is not a key")
            return
        }
        onDisconnect(removedConn)
    }

    /** Requests for the relay server to create a game room */
    fun requestGameCreation() {
        relayServerConnector.sendTCP(NewGameRequest(roomId, gameServer.maxPlayers, mapName, myUuid.toString()))
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    @Synchronized
    private fun getSerialisedBytes(data: Any): ByteArray {
        val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
        relayServerConnector.kryo.writeClassAndObject(serialisationOutput, data)
        return serialisationOutput.toBytes()
    }

    /**
     * De-serialises the input byte array with Kryo and returns the object
     * @param data the byte array to de-serialise
     * @return the de-serialised object
     */
    @Synchronized
    private fun fromSerializedBytes(data: ByteArray): Any? {
        return relayServerConnector.kryo.readClassAndObject(Input(data))
    }

    /**
     * De-serializes the byte array in relay object received by relay host, and notifies [GameServer] of the received
     * object
     *
     * Method is synchronized as Kryo is not thread-safe
     * @param data serialised bytes of object to decode
     * @param sendingUUID the UUID of the sender
     */
    fun decodeRelayMessageObject(data: ByteArray, sendingUUID: UUID) {
        val sendingConnection = uuidConnectionMap[sendingUUID] ?: return
        onReceive(sendingConnection, fromSerializedBytes(data))
    }
}