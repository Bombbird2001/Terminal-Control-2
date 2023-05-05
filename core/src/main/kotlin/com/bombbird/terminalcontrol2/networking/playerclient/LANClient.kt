package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.CLIENT_READ_BUFFER_SIZE
import com.bombbird.terminalcontrol2.global.CLIENT_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.myUuid
import com.bombbird.terminalcontrol2.networking.NetworkClient
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientUUID
import com.bombbird.terminalcontrol2.networking.handleIncomingRequestClient
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener

/**
 * Client for handling LAN multiplayer games
 *
 * Cannot be used for public multiplayer games, use [PublicClient] for that
 */
class LANClient(lanClientDiscoveryHandler: LANClientDiscoveryHandler): NetworkClient() {
    private val client = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        setDiscoveryHandler(lanClientDiscoveryHandler)
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                (obj as? RequestClientUUID)?.apply {
                    connection.sendTCP(ClientUUIDData(myUuid.toString()))
                } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
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
        // Not applicable for LANClient
        return
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

    fun discoverHosts(udpPort: Int) {
        client.discoverHosts(udpPort, 2000)
    }
}