package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
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
import tc2relay.Relay.TcpMessage
import java.lang.Exception
import javax.crypto.spec.SecretKeySpec

/**
 * Client for handling public multiplayer relay games.
 * Uses the new protobuf-based transport instead of KryoNet.
 */
class PublicClient : NetworkClient() {
    override val isConnected: Boolean
        get() = conn.isConnected
    override val clientKryo: Kryo
        get() = manualKryoInstance

    private val manualKryoInstance = Kryo().apply { registerClassesToKryo(this) }

    private var roomId: Short = Short.MAX_VALUE
    private var challengeCiphertext: ByteArray? = null

    private val conn = TransportConnection()

    override fun connect(timeout: Int, connectionHost: String, tcpPort: Int, udpPort: Int) {
        conn.onTcpReceived = { msg -> handleTcpMessage(msg) }
        conn.onUdpReceived = { msg, _ ->
            when {
                msg.hasForwardToAllClientsUdpUnencrypted() -> {
                    decodeRelayMessageObject(msg.forwardToAllClientsUdpUnencrypted.data.toByteArray())
                }
            }
        }
        conn.onDisconnected = {
            if (GAME.shownScreen is RadarScreen)
                GAME.quitCurrentGameWithDialog {
                    CustomDialog("Disconnected", "You have been disconnected from the server - either the host quit the game or your internet connection is unstable", "", "Ok")
                }
        }
        conn.connect(timeout, connectionHost, tcpPort, udpPort)

        // Send challenge response immediately
        val ct = challengeCiphertext ?: return
        conn.sendTCP(ProtoMessages.challengeResponse(ct))
    }

    override fun reconnect() {
        // Not easily supported with raw sockets; reconnect via connect()
    }

    override fun sendTCP(data: Any) {
        val bytes = getSerialisedBytes(data) ?: return
        conn.sendTCP(ProtoMessages.forwardToHost(roomId.toInt(), myUuid.toString(), bytes))
    }

    override fun beforeConnect(roomId: Short?) {
        if (roomId == null) {
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect", "Missing room ID", "", "Ok") }
            return
        }

        conn.disconnect()
        this.roomId = roomId

        val authResponse = HttpRequest.sendGameAuthorizationRequest(roomId)
        if (authResponse?.success != true) {
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect", "Endpoint authorization failed", "", "Ok") }
            return
        }

        val roomKey = SecretKeySpec(decodeBase64(authResponse.roomKey), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        val clientKey = SecretKeySpec(decodeBase64(authResponse.clientKey), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        encryptor.setKey(clientKey)
        decrypter.setKey(roomKey)

        val iv = decodeBase64(authResponse.iv)
        val ciphertext = encryptor.encryptWithIV(iv, com.bombbird.terminalcontrol2.networking.relayserver.RelayNonce(authResponse.nonce))?.ciphertext ?: return
        challengeCiphertext = ciphertext
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
        return if (conn.isConnected) "Connected to relay" else "Not connected"
    }

    private fun handleTcpMessage(msg: TcpMessage) {
        try {
            when {
                msg.hasRequestRelayAction() -> {
                    val connId = msg.requestRelayAction.connId
                    conn.sendUDP(ProtoMessages.udpRegistration(roomId.toInt(), myUuid.toString(), connId))
                    requestToJoinRoom()
                }
                msg.hasForwardToClient() -> {
                    decodeRelayMessageObject(msg.forwardToClient.data.toByteArray())
                }
                msg.hasForwardToAllClientsTcp() -> {
                    decodeRelayMessageObject(msg.forwardToAllClientsTcp.data.toByteArray())
                }
                msg.hasRelayError() -> {
                    val reason = msg.relayError.reason
                    FileLog.info("PublicClient", "Relay error: $reason")
                    handleIncomingRequestClient(
                        GAME.gameClientScreen ?: return,
                        ConnectionError(reason)
                    )
                }
            }
        } catch (e: Exception) {
            FileLog.info("PublicClient", "Error handling TCP message: ${e.message}")
        }
    }

    @Synchronized
    fun decodeRelayMessageObject(data: ByteArray) {
        val obj = fromSerializedBytes(data) ?: return
        onReceiveNonRelayData(obj)
    }

    fun requestToJoinRoom() {
        if (roomId == Short.MAX_VALUE) return
        conn.sendTCP(ProtoMessages.joinGameRequest(roomId.toInt(), myUuid.toString()))
    }

    private fun onReceiveNonRelayData(obj: Any?) {
        (obj as? RequestClientData)?.apply {
            sendTCP(ClientData(myUuid.toString(), BUILD_VERSION))
        } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
    }
}
