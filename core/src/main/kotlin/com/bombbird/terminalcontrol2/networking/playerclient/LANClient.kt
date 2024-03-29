package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.NetworkClient
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientData
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.handleIncomingRequestClient
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.bombbird.terminalcontrol2.utilities.FileLog
import java.lang.Exception
import java.nio.channels.ClosedSelectorException

/**
 * Client for handling LAN multiplayer games
 *
 * Cannot be used for public multiplayer games, use [PublicClient] for that
 */
class LANClient(lanClientDiscoveryHandler: LANClientDiscoveryHandler): NetworkClient() {
    override val isConnected: Boolean
        get() = client.isConnected
    override val clientKryo: Kryo
        get() = client.kryo

    private var secretKeyCalculated = false

    private val client = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        setDiscoveryHandler(lanClientDiscoveryHandler)
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                if (!secretKeyCalculated && obj is DiffieHellmanValueOld) {
                    // Will prevent connection to a host with build version < 10
                    return connection.close()
                }

                if (!secretKeyCalculated && obj is DiffieHellmanValues) {
                    // Calculate DH values
                    val serverDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                    val clientDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                    val serverToSend = serverDH.getExchangeValue()
                    val clientToSend = clientDH.getExchangeValue()
                    val serverSecretKey = serverDH.getAES128Key(obj.serverXy)
                    val clientSecretKey = clientDH.getAES128Key(obj.clientXy)
                    encryptor.setKey(clientSecretKey)
                    decrypter.setKey(serverSecretKey)

                    // Key established
                    secretKeyCalculated = true
                    connection.sendTCP(DiffieHellmanValues(serverToSend, clientToSend))
                    return
                }

                if (!secretKeyCalculated) return

                if (obj is NeedsEncryption) {
                    FileLog.info("RelayServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    decrypter.decrypt(obj)
                } else obj

                (decrypted as? RequestClientData)?.apply {
                    this@LANClient.sendTCP(ClientData(myUuid.toString(), BUILD_VERSION))
                } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, decrypted)
            }

            override fun disconnected(connection: Connection?) {
                if (GAME.shownScreen is RadarScreen && GAME.gameServer == null)
                    GAME.quitCurrentGameWithDialog { CustomDialog("Disconnected", "You have been disconnected" +
                            " from the server - either the host quit the game or your internet connection is unstable", "", "Ok") }
            }
        })
    }

    override fun connect(timeout: Int, connectionHost: String, tcpPort: Int, udpPort: Int) {
        client.connect(timeout, connectionHost, tcpPort, udpPort)
    }

    override fun reconnect() {
        client.reconnect()
    }

    override fun sendTCP(data: Any) {
        val encrypted = encryptIfNeeded(data) ?: return
        client.sendTCP(encrypted)
    }

    override fun beforeConnect(roomId: Short?) {
        registerClassesToKryo(clientKryo)
        secretKeyCalculated = false
    }

    override fun start() {
        client.start()
        client.updateThread.setUncaughtExceptionHandler { _, e ->
            // We can ignore this, it happens sometimes when the client is stopped
            if (e is ClosedSelectorException) return@setUncaughtExceptionHandler

            HttpRequest.sendCrashReport(Exception(e), "LANClient", MULTIPLAYER_SINGLEPLAYER_LAN_CLIENT)
            GAME.quitCurrentGameWithDialog { CustomDialog("Error", "An error occurred", "", "Ok") }
        }
    }

    override fun stop() {
        client.stop()
    }

    override fun dispose() {
        client.dispose()
    }

    override fun getConnectionStatus(): String {
        val udp = try {
            client.remoteAddressUDP?.let { "UDP connected to $it" } ?: "UDP not connected"
        } catch (e: NullPointerException) {
            "UDP not initialized"
        }
        return "Connection ${client.id}: ${client.remoteAddressTCP?.let { "TCP connected to $it" } ?: "TCP not connected"}, $udp"
    }

    fun discoverHosts(udpPort: Int) {
        client.discoverHosts(udpPort, 5000)
    }
}