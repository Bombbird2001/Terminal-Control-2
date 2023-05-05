package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientUUID
import com.bombbird.terminalcontrol2.networking.relayserver.ClientToServer
import com.bombbird.terminalcontrol2.networking.relayserver.JoinGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.RelayClientReceive
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToClient
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener

/**
 * Server for handling public multiplayer relay games
 *
 * Cannot be used for LAN multiplayer games, use [LANClient] for that
 */
class PublicClient: NetworkClient() {
    private var roomId: Short = Short.MAX_VALUE

    private val client = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                (obj as? RelayClientReceive)?.apply {
                    handleRelayClientReceive(this@PublicClient)
                } ?: onReceiveNonRelayData(obj)
            }
        })
    }

    override val isConnected: Boolean
        get() = client.isConnected

    override val kryo: Kryo
        get() = client.kryo

    override fun connect(timeout: Int, connectionHost: String, tcpPort: Int, udpPort: Int) {
        client.connect(timeout, connectionHost, tcpPort, udpPort)
    }

    override fun reconnect() {
        client.reconnect()
    }

    override fun sendTCP(data: Any) {
        // Serialize and wrap in ClientToServer
        client.sendTCP(ClientToServer(roomId, myUuid.toString(), getSerialisedBytes(data)))
    }

    override fun setRoomId(roomId: Short) {
        stop() // If somehow already connected, disconnect first
        this.roomId = roomId
    }

    override fun start() {
        registerClassesToKryo(kryo)
        client.start()
    }

    override fun stop() {
        client.stop()
    }

    override fun dispose() {
        client.dispose()
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    private fun getSerialisedBytes(data: Any): ByteArray {
        val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
        client.kryo.writeClassAndObject(serialisationOutput, data)
        return serialisationOutput.toBytes()
    }

    /**
     * De-serializes the byte array in relay object received by relay host, and performs actions on the received object
     *
     * Method is synchronized as Kryo is not thread-safe
     * @param data serialised bytes of object to decode
     */
    @Synchronized
    fun decodeRelayMessageObject(data: ByteArray) {
        val obj = client.kryo.readClassAndObject(Input(data))
        onReceiveNonRelayData(obj)
    }

    /** Requests to join a game room */
    fun requestToJoinRoom() {
        if (roomId == Short.MAX_VALUE) return
        client.sendTCP(JoinGameRequest(roomId, myUuid.toString()))
    }

    /**
     * Function to be called when receiving data that is not [ServerToClient]
     * @param obj the data received
     */
    private fun onReceiveNonRelayData(obj: Any?) {
        if (obj is ServerToClient) return
        (obj as? RequestClientUUID)?.apply {
            sendTCP(ClientUUIDData(myUuid.toString()))
        } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
    }
}