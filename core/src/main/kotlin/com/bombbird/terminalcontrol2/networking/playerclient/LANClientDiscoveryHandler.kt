package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.networking.hostserver.LANServerDiscoveryHandler
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.esotericsoftware.kryonet.ClientDiscoveryHandler
import ktx.collections.GdxArray
import java.net.DatagramPacket

class LANClientDiscoveryHandler: ClientDiscoveryHandler {
    var onDiscoveredHostDataMap: GdxArray<JoinGame.MultiplayerGameInfo>? = null

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
        onDiscoveredHostDataMap?.add(JoinGame.MultiplayerGameInfo(datagramPacket.address.hostAddress, decodedData.first,
            decodedData.second, decodedData.third, null))
    }

    /**
     * Decodes the byte array into player count and airport name data
     * @param byteArray the byte array received from the server
     * @return a triple, the first being a byte that represents the current number of players in game, the second being
     * the maximum number of players allowed in game, the third being a string that represents the current game world's
     * main airport; returns null if the byte array length does not match
     */
    private fun decodePacketData(byteArray: ByteArray): Triple<Byte, Byte, String>? {
        if (byteArray.size != LANServerDiscoveryHandler.DISCOVERY_PACKET_SIZE) return null
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
                else -> {
                    airport += Char(byteArray[pos] * 255 + byteArray[pos + 1])
                    pos += 2
                }
            }
        }

        return Triple(players, maxPlayers, airport)
    }
}