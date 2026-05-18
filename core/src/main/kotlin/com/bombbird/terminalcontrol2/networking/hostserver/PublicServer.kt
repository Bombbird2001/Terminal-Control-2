package com.bombbird.terminalcontrol2.networking.hostserver

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDDataOld
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientData
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.transport.ProtoMessages
import com.bombbird.terminalcontrol2.networking.transport.TransportConnection
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.decodeBase64
import ktx.collections.GdxArrayMap
import tc2relay.Relay.TcpMessage
import java.lang.Exception
import java.net.UnknownHostException
import java.util.*
import javax.crypto.spec.SecretKeySpec

/**
 * Server for handling public multiplayer relay games.
 * Uses the new protobuf-based transport instead of KryoNet.
 */
class PublicServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit,
    private val mapName: String
) : NetworkServer(gameServer, onReceive, onConnect, onDisconnect) {
    val isConnected: Boolean
        get() = relayConn.isConnected

    override val serverKryo: Kryo
        get() = manualKryoInstance

    private val manualKryoInstance = Kryo().apply { registerClassesToKryo(this) }

    private var roomId: Short = Short.MAX_VALUE
    private var challengeCiphertext: ByteArray? = null

    private val relayConn = TransportConnection()

    private val uuidConnectionMap = GdxArrayMap<UUID, ConnectionMeta>(PLAYER_SIZE)

    override fun start(): Boolean {
        relayConn.onTcpReceived = { msg -> handleTcpMessage(msg) }
        relayConn.onDisconnected = {
            if (GAME.shownScreen is RadarScreen)
                GAME.quitCurrentGameWithDialog {
                    CustomDialog("Disconnected", "Lost connection to the relay server", "", "Ok")
                }
        }
        try {
            relayConn.connect(5000, Secrets.RELAY_ADDRESS, RELAY_TCP_PORT, RELAY_UDP_PORT)
        } catch (e: Exception) {
            FileLog.info("PublicServer", "Failed to connect to relay: ${e.message}")
            return false
        }

        // Send the challenge response immediately after connecting
        val ct = challengeCiphertext ?: return false
        relayConn.sendTCP(ProtoMessages.challengeResponse(ct))

        CLIENT_TCP_PORT_IN_USE = RELAY_TCP_PORT
        CLIENT_UDP_PORT_IN_USE = RELAY_UDP_PORT
        return true
    }

    override fun stop() {
        relayConn.disconnect()
    }

    override fun sendToAllTCP(data: Any) {
        val bytes = getSerialisedBytes(data) ?: return
        relayConn.sendTCP(ProtoMessages.forwardToAllClientsTcp(roomId.toInt(), bytes))
    }

    override fun sendToAllUDP(data: Any) {
        val bytes = getSerialisedBytes(data) ?: return
        relayConn.sendUDP(ProtoMessages.udpForwardUnencrypted(bytes))
    }

    override fun sendTCPToConnection(uuid: UUID, data: Any) {
        val bytes = getSerialisedBytes(data) ?: return
        relayConn.sendTCP(ProtoMessages.forwardToClient(roomId.toInt(), uuid.toString(), bytes))
    }

    override fun beforeStart(): Boolean {
        val roomCreation: HttpRequest.RoomCreationStatus?
        try {
            roomCreation = HttpRequest.sendCreateGameRequest()
        } catch (_: UnknownHostException) {
            GAME.quitCurrentGameWithDialog {
                CustomDialog("Failed to connect", "Please check your network connection and try again.", "", "Ok")
            }
            return false
        }
        if (roomCreation?.success != true) {
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect", "Room creation failed", "", "Ok") }
            return false
        }
        setRoomId(roomCreation.roomId)
        val roomKey = SecretKeySpec(decodeBase64(roomCreation.authResponse.roomKey), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        val hostKey = SecretKeySpec(decodeBase64(roomCreation.authResponse.clientKey), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        encryptor.setKey(hostKey)
        decrypter.setKey(roomKey)

        val iv = decodeBase64(roomCreation.authResponse.iv)
        val ciphertext = encryptor.encryptWithIV(iv, com.bombbird.terminalcontrol2.networking.relayserver.RelayNonce(roomCreation.authResponse.nonce))?.ciphertext
        if (ciphertext == null) {
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect", "Authorization challenge failed", "", "Ok") }
            return false
        }
        challengeCiphertext = ciphertext

        return true
    }

    private fun setRoomId(roomId: Short) {
        if (roomId == Short.MAX_VALUE) {
            return GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect", "Room creation failed", "", "Ok") }
        }
        if (this.roomId == Short.MAX_VALUE)
            this.roomId = roomId
    }

    override fun getRoomId(): Short {
        return roomId
    }

    override fun getConnectionStatus(): String {
        return if (relayConn.isConnected) "Connected to relay" else "Not connected"
    }

    override val connections: Collection<ConnectionMeta>
        get() {
            val conns = HashSet<ConnectionMeta>()
            for (i in 0 until uuidConnectionMap.size) {
                conns.add(uuidConnectionMap.getValueAt(i))
            }
            return conns
        }

    private fun handleTcpMessage(msg: TcpMessage) {
        try {
            when {
                msg.hasRequestRelayAction() -> {
                    val connId = msg.requestRelayAction.connId
                    relayConn.sendUDP(ProtoMessages.udpRegistration(roomId.toInt(), myUuid.toString(), connId))
                    requestGameCreation()
                }
                msg.hasPlayerConnected() -> {
                    val uuid = UUID.fromString(msg.playerConnected.uuid)
                    onConnect(uuid)
                }
                msg.hasPlayerDisconnected() -> {
                    val uuid = UUID.fromString(msg.playerDisconnected.uuid)
                    onDisconnect(uuid)
                }
                msg.hasForwardToHost() -> {
                    val fwd = msg.forwardToHost
                    val uuid = UUID.fromString(fwd.sendingUuid)
                    decodeRelayMessageObject(fwd.data.toByteArray(), uuid, fwd.clientToRelayRtt)
                }
                msg.hasRelayError() -> {
                    FileLog.info("PublicServer", "Relay error: ${msg.relayError.reason}")
                }
            }
        } catch (e: Exception) {
            FileLog.info("PublicServer", "Error handling TCP message: ${e.message}")
        }
    }

    fun onConnect(uuid: UUID) {
        val newConn = ConnectionMeta(uuid)
        uuidConnectionMap.put(uuid, newConn)
        sendTCPToConnection(uuid, RequestClientData())
    }

    fun onDisconnect(uuid: UUID) {
        val removedConn = uuidConnectionMap.removeKey(uuid) ?: run {
            FileLog.info("PublicServer", "Failed to remove $uuid from connection map")
            return
        }
        onDisconnect(removedConn)
    }

    fun requestGameCreation() {
        relayConn.sendTCP(
            ProtoMessages.newGameRequest(roomId.toInt(), gameServer.maxPlayersAllowed.toInt(), mapName, myUuid.toString())
        )
    }

    fun decodeRelayMessageObject(data: ByteArray, sendingUUID: UUID, clientToRelayRtt: Int = 0) {
        val sendingConnection = uuidConnectionMap[sendingUUID] ?: return
        val decoded = fromSerializedBytes(data)
        if (decoded is ClientUUIDDataOld) {
            sendTCPToConnection(sendingUUID, ConnectionError("Your game version is too old - please update to the latest build"))
            return
        }
        if (decoded is ClientData) {
            return checkClientData(sendingUUID, decoded)
        }
        sendingConnection.returnTripTime = clientToRelayRtt
        onReceive(sendingConnection, decoded)
    }

    private fun checkClientData(uuid: UUID, data: ClientData) {
        if (data.buildVersion != BUILD_VERSION) {
            sendTCPToConnection(uuid, ConnectionError("Your build version ${data.buildVersion} is not the same as host's build version $BUILD_VERSION"))
            return
        }
        onConnect(uuidConnectionMap[uuid] ?: return)
    }
}
