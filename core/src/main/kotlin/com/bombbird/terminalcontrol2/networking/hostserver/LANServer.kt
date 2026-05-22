package com.bombbird.terminalcontrol2.networking.hostserver

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDDataOld
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientData
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.transport.ProtoMessages
import com.bombbird.terminalcontrol2.networking.transport.TransportServer
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.collections.GdxArrayMap
import ktx.collections.set
import tc2relay.Relay.TcpMessage
import java.lang.Exception
import java.math.BigInteger
import java.net.BindException
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Server for handling LAN multiplayer games.
 * Uses the new protobuf-based transport instead of KryoNet.
 */
class LANServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit
) : NetworkServer(gameServer, onReceive, onConnect, onDisconnect) {
    private class DiffieHellmanPair(val serverDH: DiffieHellman, val clientDH: DiffieHellman)

    override val serverKryo: Kryo
        get() = manualKryoInstance

    private val manualKryoInstance = Kryo().apply { registerClassesToKryo(this) }

    private val server = TransportServer()

    private val connectionDHMap = GdxArrayMap<Int, DiffieHellmanPair>(PLAYER_SIZE)
    private val connectionEncDecMap = GdxArrayMap<Int, Pair<Encryptor, Decrypter>>(PLAYER_SIZE)
    private val connectionMetaMap = GdxArrayMap<Int, ConnectionMeta>(PLAYER_SIZE)
    private val uuidConnectionMap = GdxArrayMap<UUID, Int>(PLAYER_SIZE)

    /** UDP address tracking per connection for sending UDP */
    private val connUdpAddrs = ConcurrentHashMap<Int, InetSocketAddress>()

    private var discoverySocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null

    override fun start(): Boolean {
        for (entry in LAN_TCP_PORTS.withIndex()) {
            try {
                val tcpPort = entry.value
                val udpPort = LAN_UDP_PORTS[entry.index]
                server.bind(tcpPort, udpPort)
                break
            } catch (_: BindException) {
                if (entry.index == LAN_TCP_PORTS.size - 1) {
                    GAME.quitCurrentGameWithDialog {
                        CustomDialog("Error starting game", "If you see this error, consider restarting your device and try again.", "", "Ok")
                    }
                    return false
                }
            }
        }

        server.onClientConnected = { connId, _ ->
            val serverKeyDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
            val serverToSend = serverKeyDH.getExchangeValue()
            val clientKeyDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
            val clientToSend = clientKeyDH.getExchangeValue()
            connectionDHMap[connId] = DiffieHellmanPair(serverKeyDH, clientKeyDH)
            server.sendTCPTo(connId, ProtoMessages.lanKeyExchange(serverToSend.toByteArray(), clientToSend.toByteArray()))
        }

        server.onClientDisconnected = { connId ->
            connectionDHMap.removeKey(connId)
            connectionEncDecMap.removeKey(connId)
            connUdpAddrs.remove(connId)
            gameServer.postRunnableAfterEngineUpdate {
                val conn = connectionMetaMap[connId] ?: return@postRunnableAfterEngineUpdate
                connectionMetaMap.removeKey(connId)
                onDisconnect(conn)
            }
        }

        server.onTcpReceived = { connId, msg -> handleTcpMessage(connId, msg) }

        server.onUdpReceived = { msg, sender ->
            when {
                msg.hasUdpRegistration() -> {
                    connUdpAddrs[msg.udpRegistration.connId.toInt()] = sender
                }
                msg.hasLanDiscoverRequest() -> {
                    val response = ProtoMessages.lanDiscoverResponse(
                        gameServer.playersInGame.toInt(),
                        gameServer.maxPlayersAllowed.toInt(),
                        gameServer.mainName,
                        server.tcpPort,
                        server.udpPort
                    )
                    server.sendUDPTo(response, sender)
                }
            }
        }

        server.start()

        Thread.sleep(1000)
        return true
    }

    override fun stop() {
        server.stop()
        discoverySocket?.close()
        discoveryThread?.interrupt()
    }

    override fun sendToAllTCP(data: Any) {
        for (i in 0 until connectionEncDecMap.size) {
            val connId = connectionEncDecMap.getKeyAt(i) ?: continue
            val encDec = connectionEncDecMap.getValueAt(i) ?: continue
            val encrypted = encryptIfNeeded(data, encDec.first) ?: continue
            val bytes = getSerialisedBytes(encrypted) ?: continue
            server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
        }
    }

    override fun sendToAllUDP(data: Any) {
        val bytes = getSerialisedBytes(data) ?: return
        val msg = ProtoMessages.udpForwardUnencrypted(bytes)
        for ((_, addr) in connUdpAddrs) {
            server.sendUDPTo(msg, addr)
        }
    }

    override fun sendTCPToConnection(uuid: UUID, data: Any) {
        val connId = uuidConnectionMap[uuid] ?: return
        val encDec = connectionEncDecMap[connId] ?: return
        val encrypted = encryptIfNeeded(data, encDec.first) ?: return
        val bytes = getSerialisedBytes(encrypted) ?: return
        server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
    }

    override fun beforeStart(): Boolean {
        return true
    }

    override fun getRoomConnectionInfo(): RoomConnectionInfo {
        return RoomConnectionInfo(null, server.tcpPort, server.udpPort)
    }

    override fun getConnectionStatus(): String {
        return "Connected to ${server.connections.size} clients"
    }

    override val connections: Collection<ConnectionMeta>
        get() {
            val conns = HashSet<ConnectionMeta>()
            for (i in 0 until connectionMetaMap.size) {
                conns.add(connectionMetaMap.getValueAt(i))
            }
            return conns
        }

    private fun handleTcpMessage(connId: Int, msg: TcpMessage) {
        try {
            val encDec = connectionEncDecMap[connId]

            if (encDec == null && msg.hasLanKeyExchange()) {
                val kex = msg.lanKeyExchange
                val dhPair = connectionDHMap[connId] ?: return
                val serverSecretKey = dhPair.serverDH.getAES128Key(BigInteger(kex.serverXy.toByteArray()))
                val clientSecretKey = dhPair.clientDH.getAES128Key(BigInteger(kex.clientXy.toByteArray()))
                val enc = AESGCMEncryptor(this::getSerialisedBytes)
                val dec = AESGCMDecrypter(this::fromSerializedBytes)
                enc.setKey(serverSecretKey)
                dec.setKey(clientSecretKey)
                connectionEncDecMap[connId] = Pair(enc, dec)
                connectionDHMap.removeKey(connId)

                // Send conn ID for use in UDP connection registering
                val connIdBytes = getSerialisedBytes(encryptIfNeeded(ConnectionIDData(connId), enc) ?: return) ?: return
                server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), connIdBytes))
                val requestBytes = getSerialisedBytes(encryptIfNeeded(RequestClientData(), enc) ?: return) ?: return
                server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), requestBytes))
                return
            }

            if (encDec == null) return

            if (msg.hasEncryptedFrame()) {
                val ef = msg.encryptedFrame
                val decryptedObj = fromSerializedBytes(ef.ciphertext.toByteArray()) ?: return
                val innerDecrypted = if (decryptedObj is EncryptedData) {
                    encDec.second.decrypt(decryptedObj)
                } else decryptedObj

                if (innerDecrypted is ClientUUIDDataOld) {
                    val enc = encDec.first
                    val errorBytes = getSerialisedBytes(encryptIfNeeded(
                        ConnectionError("Your game version is too old - please update to the latest build"), enc) ?: return) ?: return
                    server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), errorBytes))
                    return
                }
                if (innerDecrypted is ClientData) {
                    receiveClientData(connId, innerDecrypted)
                    return
                }

                val conn = connectionMetaMap[connId] ?: return
                onReceive(conn, innerDecrypted)
            }
        } catch (e: Exception) {
            FileLog.info("LANServer", "Error handling TCP message: ${e.message}")
        }
    }

    private fun receiveClientData(connId: Int, data: ClientData) {
        val encDec = connectionEncDecMap[connId] ?: return
        val enc = encDec.first
        if (data.uuid == null) {
            val bytes = getSerialisedBytes(encryptIfNeeded(ConnectionError("Missing player ID"), enc) ?: return) ?: return
            server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
            return
        }
        if (gameServer.playersInGame == gameServer.maxPlayersAllowed) {
            val bytes = getSerialisedBytes(encryptIfNeeded(ConnectionError("Game is full"), enc) ?: return) ?: return
            server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
            return
        }
        if (data.buildVersion != BUILD_VERSION) {
            val bytes = getSerialisedBytes(encryptIfNeeded(
                ConnectionError("Your build version ${data.buildVersion} is not the same as host's build version $BUILD_VERSION"), enc) ?: return) ?: return
            server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
            return
        }
        val connUuid = UUID.fromString(data.uuid)
        if (gameServer.sectorMap.containsKey(connUuid)) {
            FileLog.info("LANServer", "UUID $connUuid is already in game")
            val bytes = getSerialisedBytes(encryptIfNeeded(ConnectionError("Player with same ID already in server"), enc) ?: return) ?: return
            server.sendTCPTo(connId, ProtoMessages.encryptedFrame(byteArrayOf(), bytes))
            return
        }
        val connMeta = ConnectionMeta(connUuid, 0)
        connectionMetaMap.put(connId, connMeta)
        uuidConnectionMap.put(connUuid, connId)
        onConnect(connMeta)
    }
}
