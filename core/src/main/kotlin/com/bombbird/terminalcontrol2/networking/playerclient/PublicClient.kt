package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientUUID
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.relayserver.*
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.bombbird.terminalcontrol2.utilities.FileLog
import org.apache.commons.codec.binary.Base64
import java.lang.Exception
import java.nio.channels.ClosedSelectorException
import javax.crypto.spec.SecretKeySpec

/**
 * Server for handling public multiplayer relay games
 *
 * Cannot be used for LAN multiplayer games, use [LANClient] for that
 */
class PublicClient: NetworkClient() {
    override val isConnected: Boolean
        get() = client.isConnected
    override val clientKryo: Kryo
        get() = client.kryo

    private var roomId: Short = Short.MAX_VALUE
    private lateinit var relayChallenge: RelayChallenge

    private val client = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                if (obj is NeedsEncryption) {
                    FileLog.info("PublicClient", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    decrypter.decrypt(obj)
                } else obj

                (decrypted as? RelayClientReceive)?.apply {
                    handleRelayClientReceive(this@PublicClient)
                } ?: onReceiveNonRelayData(decrypted)
            }

            override fun connected(connection: Connection) {
                connection.sendTCP(relayChallenge)
            }

            override fun disconnected(connection: Connection?) {
                if (GAME.shownScreen is RadarScreen)
                    GAME.quitCurrentGameWithDialog(CustomDialog("Disconnected", "You have been disconnected from the server - most likely the host quit the game", "", "Ok"))
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
        // Serialize, wrap in ClientToServer and encrypt
        val encrypted = encryptIfNeeded(ClientToServer(roomId, myUuid.toString(), getSerialisedBytes(data) ?: return)) ?: return
        client.sendTCP(encrypted)
    }

    override fun beforeConnect(roomId: Short?) {
        registerClassesToKryo(clientKryo)

        if (roomId == null) {
            GAME.quitCurrentGameWithDialog(CustomDialog("Failed to connect", "Missing room ID", "", "Ok"))
            return
        }

        // If somehow already connected, disconnect first
        stop()
        this.roomId = roomId

        // Send game join request to endpoint to retrieve symmetric key
        val authResponse = HttpRequest.sendGameAuthorizationRequest(roomId)
        if (authResponse?.success != true) {
            GAME.quitCurrentGameWithDialog(CustomDialog("Failed to connect", "Endpoint authorization failed", "", "Ok"))
            return
        }

        val key = SecretKeySpec(Base64.decodeBase64(authResponse.key), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        encryptor.setKey(key)
        decrypter.setKey(key)

        val iv = Base64.decodeBase64(authResponse.iv)
        val ciphertext = encryptor.encryptWithIV(iv, RelayNonce(authResponse.nonce))?.ciphertext ?: return
        relayChallenge = RelayChallenge(ciphertext)
    }

    override fun start() {
        client.start()
        client.updateThread.setUncaughtExceptionHandler { _, e ->
            // We can ignore this, it happens sometimes when the client is stopped
            if (e is ClosedSelectorException) return@setUncaughtExceptionHandler

            HttpRequest.sendCrashReport(Exception(e), "PublicClient", "Public multiplayer")
            GAME.quitCurrentGameWithDialog(CustomDialog("Error", "An error occurred", "", "Ok"))
        }
    }

    override fun stop() {
        client.stop()
    }

    override fun dispose() {
        client.dispose()
    }

    /**
     * De-serializes the byte array in relay object received by relay host, and performs actions on the received object
     *
     * Method is synchronized as Kryo is not thread-safe
     * @param data serialised bytes of object to decode
     */
    @Synchronized
    fun decodeRelayMessageObject(data: ByteArray) {
        val obj = fromSerializedBytes(data) ?: return
        onReceiveNonRelayData(obj)
    }

    /** Requests to join a game room */
    fun requestToJoinRoom() {
        if (roomId == Short.MAX_VALUE) return
        val encrypted = encryptIfNeeded(JoinGameRequest(roomId, myUuid.toString())) ?: return
        client.sendTCP(encrypted)
    }

    /**
     * Function to be called when receiving data that is not [ServerToClient]
     * @param obj the data received
     */
    private fun onReceiveNonRelayData(obj: Any?) {
        if (obj is ServerToClient) return
        (obj as? RequestClientUUID)?.apply {
            sendTCP(ClientUUIDData(myUuid.toString()))
        } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
    }
}