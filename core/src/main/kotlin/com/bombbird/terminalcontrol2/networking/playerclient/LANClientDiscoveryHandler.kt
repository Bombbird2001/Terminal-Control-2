package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.networking.hostserver.LANServerDiscoveryHandler
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.esotericsoftware.kryonet.ClientDiscoveryHandler
import java.net.DatagramPacket

class LANClientDiscoveryHandler: ClientDiscoveryHandler {
    var onDiscoveredHostDataList: MutableList<JoinGame.MultiplayerGameInfo>? = null

    /** Data decoded from the datagram packet */
    private class DecodedData(val playerCount: Byte, val maxPlayers: Byte, val airport: String)

    /**
     * Overrides [ClientDiscoveryHandler.onRequestNewDatagramPacket] to return a new datagram packet of 10 bytes, just
     * sufficient for the server host's onDiscoverHost data sent back
     */
    override fun onRequestNewDatagramPacket(): DatagramPacket {
        return DatagramPacket(ByteArray(LANServerDiscoveryHandler.DISCOVERY_PACKET_SIZE),
            LANServerDiscoveryHandler.DISCOVERY_PACKET_SIZE)
    }

    /**
     * Overrides [ClientDiscoveryHandler.onDiscoveredHost] to perform actions on receiving data from the discovered
     * host server
     */
    override fun onDiscoveredHost(datagramPacket: DatagramPacket?) {
        val data = datagramPacket?.data ?: return
        val decodedData = decodePacketData(data) ?: return
        onDiscoveredHostDataList?.add(JoinGame.MultiplayerGameInfo(datagramPacket.address.hostAddress,
            datagramPacket.port, decodedData.playerCount, decodedData.maxPlayers, decodedData.airport, null))
    }

    /**
     * Decodes the byte array into player count and airport name data
     * @param byteArray the byte array received from the server
     * @return a [DecodedData] with the current number of players in game, the maximum number of players allowed in
     * game, and a string that represents the current game world's main airport; returns null if the byte array length
     * is less than required
     */
    private fun decodePacketData(byteArray: ByteArray): DecodedData? {
        if (byteArray.size < LANServerDiscoveryHandler.DISCOVERY_PACKET_SIZE) return null
        var players: Byte = -1
        var maxPlayers: Byte = -1
        var airport = ""
        var pos = 0
        while (pos < byteArray.size - 1) {
            when (pos) {
                0 -> {
                    players = byteArray[0]
                    pos++
                }
                1 -> {
                    maxPlayers = byteArray[1]
                    pos++
                }
                2, 4, 6, 8 -> {
                    airport += Char(byteArray[pos] * 255 + byteArray[pos + 1])
                    pos += 2
                }
            }
        }

        return DecodedData(players, maxPlayers, airport)
    }
}