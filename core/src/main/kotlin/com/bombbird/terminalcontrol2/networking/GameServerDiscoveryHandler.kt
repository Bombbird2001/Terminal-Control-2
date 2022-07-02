package com.bombbird.terminalcontrol2.networking

import com.esotericsoftware.kryonet.ServerDiscoveryHandler
import java.net.InetSocketAddress
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class GameServerDiscoveryHandler(val gameServer: GameServer): ServerDiscoveryHandler {

    /**
     * Overrides [ServerDiscoveryHandler.onDiscoverHost] to send information about the server, such as the main airport
     * name as well as number of currently connected players
     */
    override fun onDiscoverHost(datagramChannel: DatagramChannel?, fromAddress: InetSocketAddress?): Boolean {
        val byteBuffer = ByteBuffer.allocate(9)
        if (byteBuffer.position() + 1 <= byteBuffer.capacity()) byteBuffer.put(gameServer.playerNo.get().toByte())
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