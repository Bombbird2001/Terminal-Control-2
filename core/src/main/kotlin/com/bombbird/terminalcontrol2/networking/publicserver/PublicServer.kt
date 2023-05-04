package com.bombbird.terminalcontrol2.networking.publicserver

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.Server
import com.bombbird.terminalcontrol2.networking.relayserver.NewGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.RelayHostReceive
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToClient
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import ktx.collections.GdxArrayMap
import java.util.*

class PublicServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit
) : Server(gameServer, onReceive, onConnect, onDisconnect) {
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
}