package com.bombbird.terminalcontrol2.networking.hostserver

import com.bombbird.terminalcontrol2.networking.GameServer
import com.esotericsoftware.kryonet.ServerDiscoveryHandler
import java.net.InetSocketAddress
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class LANServerDiscoveryHandler(val gameServer: GameServer): ServerDiscoveryHandler {
    companion object {
        const val DISCOVERY_PACKET_SIZE = 10
    }

    /**
     * Overrides [ServerDiscoveryHandler.onDiscoverHost] to send information about the server, such as the main airport
     * name, number of currently connected players and maximum number of players
     */
    override fun onDiscoverHost(datagramChannel: DatagramChannel?, fromAddress: InetSocketAddress?): Boolean {
        val byteBuffer = ByteBuffer.allocate(DISCOVERY_PACKET_SIZE)
        if (byteBuffer.position() + 1 <= byteBuffer.capacity()) byteBuffer.put(gameServer.playersInGame)
        if (byteBuffer.position() + 1 <= byteBuffer.capacity()) byteBuffer.put(gameServer.maxPlayers)
        for (c in gameServer.mainName) {
            if (byteBuffer.position() + 2 > byteBuffer.capacity()) break
            byteBuffer.putChar(c)
        }
        // Cast to Buffer to prevent any NoSuchMethodError due to difference in compile and runtime methods in ByteBuffer
        (byteBuffer as? Buffer)?.position(0)
        datagramChannel?.send(byteBuffer, fromAddress)
        return true
    }
}