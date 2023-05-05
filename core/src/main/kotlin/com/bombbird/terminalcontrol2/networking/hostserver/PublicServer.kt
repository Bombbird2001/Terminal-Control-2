package com.bombbird.terminalcontrol2.networking.hostserver

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.relayserver.NewGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.RelayHostReceive
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToClient
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.minlog.Log
import ktx.collections.GdxArrayMap
import java.util.*

/**
 * Server for handling public multiplayer relay games
 *
 * Cannot be used for LAN multiplayer games, use [LANServer] for that
 */
class PublicServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit
) : NetworkServer(gameServer, onReceive, onConnect, onDisconnect) {
    private var roomId: Short = Short.MAX_VALUE
    private val relayServerConnector = Client(SERVER_WRITE_BUFFER_SIZE, SERVER_READ_BUFFER_SIZE).apply {
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                (obj as? RelayHostReceive)?.apply {
                    handleRelayHostReceive(this@PublicServer)
                }
            }
        })
    }

    /** Maps [UUID] to [ConnectionMeta] */
    private val uuidConnectionMap = GdxArrayMap<UUID, ConnectionMeta>(PLAYER_SIZE)

    override fun start(tcpPort: Int, udpPort: Int) {
        registerClassesToKryo(relayServerConnector.kryo)
        registerRelayClassesToKryo(relayServerConnector.kryo)
        relayServerConnector.connect(5000, Secrets.RELAY_URL, TCP_PORT, UDP_PORT)
    }

    override fun stop() {
        relayServerConnector.stop()
    }

    override fun sendToAllTCP(data: Any) {
        relayServerConnector.sendTCP(ServerToClient(roomId, null, getSerialisedBytes(data), true))
    }

    override fun sendToAllUDP(data: Any) {
        relayServerConnector.sendUDP(ServerToClient(roomId, null, getSerialisedBytes(data), false))
    }

    override fun sendTCPToConnection(uuid: UUID, data: Any) {
        relayServerConnector.sendTCP(ServerToClient(roomId, uuid.toString(), getSerialisedBytes(data), true))
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
        relayServerConnector.sendTCP(NewGameRequest(4, myUuid.toString()))
    }

    /**
     * Updates the room ID of the game created, only if not set before
     * @param roomId the ID of the new room created
     */
    fun setRoomId(roomId: Short) {
        if (roomId == Short.MAX_VALUE) {
            // Failed to create room, stop server
            GAME.quitCurrentGame()
            return
        }

        if (this.roomId == Short.MAX_VALUE)
            this.roomId = roomId
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    private fun getSerialisedBytes(data: Any): ByteArray {
        val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
        relayServerConnector.kryo.writeClassAndObject(serialisationOutput, data)
        return serialisationOutput.toBytes()
    }

    /**
     * De-serializes the byte array in relay object received by relay host, and notifies [GameServer] of the received
     * object
     * @param data serialised bytes of object to decode
     * @param sendingUUID the UUID of the sender
     */
    fun decodeRelayMessageObject(data: ByteArray, sendingUUID: UUID) {
        val sendingConnection = uuidConnectionMap[sendingUUID] ?: return
        val obj = relayServerConnector.kryo.readClassAndObject(Input(data))
        onReceive(sendingConnection, obj)
    }
}