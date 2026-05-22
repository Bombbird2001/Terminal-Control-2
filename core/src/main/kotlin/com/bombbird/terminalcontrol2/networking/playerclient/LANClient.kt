package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientData
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.transport.ProtoMessages
import com.bombbird.terminalcontrol2.networking.transport.TransportConnection
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.bombbird.terminalcontrol2.utilities.FileLog
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import ktx.async.KtxAsync
import tc2relay.Relay.TcpMessage
import tc2relay.Relay.UdpMessage
import java.lang.Exception
import java.math.BigInteger
import java.net.*

/**
 * Client for handling LAN multiplayer games.
 * Uses the new protobuf-based transport instead of KryoNet.
 */
class LANClient : NetworkClient() {
    override val isConnected: Boolean
        get() = conn.isConnected
    override val clientKryo: Kryo
        get() = manualKryoInstance

    private val manualKryoInstance = Kryo().apply { registerClassesToKryo(this) }

    private val conn = TransportConnection()

    private var serverDH: DiffieHellman? = null
    private var clientDH: DiffieHellman? = null

    override fun connect(timeout: Int, connectionHost: String, tcpPort: Int, udpPort: Int) {
        conn.onTcpReceived = { msg -> handleTcpMessage(msg) }
        conn.onUdpReceived = { msg, _ ->
            handleUdpMessage(msg)
        }
        conn.onDisconnected = {
            if (GAME.shownScreen is RadarScreen)
                GAME.quitCurrentGameWithDialog {
                    CustomDialog("Disconnected", "You have been disconnected from the host.", "", "Ok")
                }
        }
        conn.connect(timeout, connectionHost, tcpPort, udpPort)

        println("Client socket: ${conn.localTcpPort}/${conn.localUdpPort}")
    }

    override fun reconnect() {
        // Not easily supported; full reconnect needed
    }

    override fun sendTCP(data: Any) {
        val encryptedData = encryptIfNeeded(data) ?: return
        val bytes = getSerialisedBytes(encryptedData) ?: return
        conn.sendTCP(ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
    }

    override fun beforeConnect(roomId: Short?) {
        conn.disconnect()
        serverDH = null
        clientDH = null
    }

    override fun start() {
        // Connection is started in connect()
    }

    override fun stop() {
        conn.disconnect()
    }

    override fun dispose() {
        conn.disconnect()
    }

    override fun getConnectionStatus(): String {
        return if (conn.isConnected) "Connected to LAN host" else "Not connected"
    }

    private fun handleTcpMessage(msg: TcpMessage) {
        try {
            when {
                msg.hasLanKeyExchange() -> {
                    val kex = msg.lanKeyExchange
                    val srvDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                    val cliDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                    serverDH = srvDH
                    clientDH = cliDH

                    val serverResponseXy = srvDH.getExchangeValue().toByteArray()
                    val clientResponseXy = cliDH.getExchangeValue().toByteArray()

                    val serverKey = srvDH.getAES128Key(BigInteger(kex.serverXy.toByteArray()))
                    val clientKey = cliDH.getAES128Key(BigInteger(kex.clientXy.toByteArray()))
                    decrypter.setKey(serverKey)
                    encryptor.setKey(clientKey)
                    conn.sendTCP(ProtoMessages.lanKeyExchange(serverResponseXy, clientResponseXy))
                }
                msg.hasEncryptedFrame() -> {
                    val ef = msg.encryptedFrame
                    val decryptedObj = fromSerializedBytes(ef.ciphertext.toByteArray()) ?: return
                    val innerDecrypted = if (decryptedObj is EncryptedData) {
                        decrypter.decrypt(decryptedObj)
                    } else decryptedObj
                    onReceiveNonRelayData(innerDecrypted)
                }
            }
        } catch (e: Exception) {
            FileLog.info("LANClient", "Error handling TCP message: ${e.message}")
        }
    }

    private fun handleUdpMessage(msg: UdpMessage) {
        try {
            when {
                msg.hasForwardToAllClientsUdpUnencrypted() -> {
                    val obj = fromSerializedBytes(msg.forwardToAllClientsUdpUnencrypted.data.toByteArray()) ?: return
                    handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
                }
            }
        } catch (e: Exception) {
            FileLog.info("LANClient", "Error handling UDP message: ${e.message}")
        }
    }

    private fun onReceiveNonRelayData(obj: Any?) {
        (obj as? RequestClientData)?.apply {
            sendTCP(ClientData(myUuid.toString(), BUILD_VERSION))
        } ?: (obj as? ConnectionIDData)?.apply {
            conn.sendUDP(ProtoMessages.udpRegistration(-1, myUuid.toString(), connId.toLong()))
        } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
    }

    /**
     * Discover LAN servers by sending UDP broadcast probes to known ports.
     * Returns a map of address:port -> discovery response info.
     */
    data class DiscoveredHost(
        val address: String,
        val tcpPort: Int,
        val udpPort: Int,
        val playerCount: Int,
        val maxPlayers: Int,
        val mapName: String
    )

    companion object {
        suspend fun discoverLANHosts(timeoutMs: Int = 3000): List<DiscoveredHost> {
            val probe = ProtoMessages.lanDiscoverRequest().toByteArray()
            val discovered = ArrayList<Deferred<DiscoveredHost?>>()

            for (udpPort in LAN_UDP_PORTS) {
                discovered.add(KtxAsync.async(Dispatchers.IO) {
                    try {
                        val socket = DatagramSocket()
                        socket.broadcast = true
                        socket.soTimeout = timeoutMs

                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        socket.send(DatagramPacket(probe, probe.size, broadcastAddr, udpPort))

                        val buf = ByteArray(2048)
                        try {
                            while (true) {
                                val resp = DatagramPacket(buf, buf.size)
                                socket.receive(resp)
                                val msg = UdpMessage.parseFrom(resp.data.copyOf(resp.length))
                                if (msg.hasLanDiscoverResponse()) {
                                    val dr = msg.lanDiscoverResponse
                                    return@async DiscoveredHost(
                                        resp.address.hostAddress,
                                        dr.tcpPort,
                                        dr.udpPort,
                                        dr.playerCount,
                                        dr.maxPlayers,
                                        dr.mapName
                                    )
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            // Finished receiving
                        }
                        socket.close()
                    } catch (e: Exception) {
                        FileLog.info("LANClient", "Discovery error on port $udpPort: ${e.message}")
                    }

                    return@async null
                })
            }

            return discovered.awaitAll().filterNotNull()
        }
    }
}
