package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.CLIENT_READ_BUFFER_SIZE
import com.bombbird.terminalcontrol2.global.CLIENT_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.myUuid
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientUUID
import com.bombbird.terminalcontrol2.networking.relayserver.JoinGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToClient
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
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
                (obj as? ServerToClient)?.apply {
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
        client.sendTCP(data)
    }

    override fun setRoomId(roomId: Short) {
        stop() // If somehow already connected, disconnect first
        this.roomId = roomId
    }

    override fun start() {
        registerClassesToKryo(kryo)
        registerRelayClassesToKryo(kryo)
        client.start()
    }

    override fun stop() {
        client.stop()
    }

    override fun dispose() {
        client.dispose()
    }

    /**
     * De-serializes the byte array in relay object received by relay host, and performs actions on the received object
     * @param data serialised bytes of object to decode
     */
    fun decodeRelayMessageObject(data: ByteArray) {
        val obj = client.kryo.readClassAndObject(Input(data))
        onReceiveNonRelayData(obj)
    }

    /** Requests to join a game room */
    fun requestToJoinRoom() {
        if (roomId == Short.MAX_VALUE) return
        sendTCP(JoinGameRequest(roomId))
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