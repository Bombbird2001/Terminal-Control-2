package com.bombbird.terminalcontrol2.networking.lanserver

import com.esotericsoftware.kryonet.ClientDiscoveryHandler
import ktx.collections.GdxArray
import java.net.DatagramPacket

class LANClientDiscoveryHandler: ClientDiscoveryHandler {
    var onDiscoveredHostDataMap: GdxArray<Pair<String, ByteArray>>? = null

    /**
     * Overrides [ClientDiscoveryHandler.onRequestNewDatagramPacket] to return a new datagram packet of 9 bytes, just
     * sufficient for the server host's onDiscoverHost data sent back
     */
    override fun onRequestNewDatagramPacket(): DatagramPacket {
        return DatagramPacket(ByteArray(9), 9)
    }

    /**
     * Overrides [ClientDiscoveryHandler.onDiscoveredHost] to perform actions on receiving data from the discovered
     * host server
     */
    override fun onDiscoveredHost(datagramPacket: DatagramPacket?) {
        val data = datagramPacket?.data ?: return
        onDiscoveredHostDataMap?.add(Pair(datagramPacket.address.hostAddress, data))
    }
}