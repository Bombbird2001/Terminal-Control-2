package com.bombbird.terminalcontrol2.networking.hostserver

import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDDataOld
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientData
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.relayserver.*
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.collections.GdxArrayMap
import org.apache.commons.codec.binary.Base64
import java.lang.Exception
import java.net.UnknownHostException
import java.nio.channels.ClosedSelectorException
import java.util.*
import javax.crypto.spec.SecretKeySpec

/**
 * Server for handling public multiplayer relay games
 *
 * Cannot be used for LAN multiplayer games, use [LANServer] for that
 */
class PublicServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit,
    private val mapName: String
) : NetworkServer(gameServer, onReceive, onConnect, onDisconnect) {
    val isConnected: Boolean
        get() = relayServerConnector.isConnected

    override val serverKryo: Kryo
        get() = relayServerConnector.kryo

    private var roomId: Short = Short.MAX_VALUE
    private lateinit var relayChallenge: RelayChallenge

    private val relayServerConnector = Client(SERVER_WRITE_BUFFER_SIZE, SERVER_READ_BUFFER_SIZE).apply {
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                if (obj is NeedsEncryption) {
                    FileLog.info("PublicServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    decrypter.decrypt(obj)
                } else obj

                (decrypted as? RelayHostReceive)?.apply {
                    handleRelayHostReceive(this@PublicServer, connection)
                }
            }

            override fun connected(connection: Connection) {
                connection.sendTCP(relayChallenge)
            }
        })
    }

    /** Maps [UUID] to [ConnectionMeta] */
    private val uuidConnectionMap = GdxArrayMap<UUID, ConnectionMeta>(PLAYER_SIZE)

    override fun start(): Boolean {
        relayServerConnector.start()
        relayServerConnector.updateThread.setUncaughtExceptionHandler { _, e ->
            // We can ignore this, it happens sometimes when the client is stopped
            if (e is ClosedSelectorException) return@setUncaughtExceptionHandler

            HttpRequest.sendCrashReport(Exception(e), "PublicServer", "Public multiplayer")
            GAME.quitCurrentGameWithDialog { CustomDialog("Error", "An error occurred", "", "Ok") }
        }
        CLIENT_TCP_PORT_IN_USE = RELAY_TCP_PORT
        CLIENT_UDP_PORT_IN_USE = RELAY_UDP_PORT
        relayServerConnector.connect(5000, Secrets.RELAY_ADDRESS, RELAY_TCP_PORT, RELAY_UDP_PORT)

        return true
    }

    override fun stop() {
        relayServerConnector.stop()
    }

    override fun sendToAllTCP(data: Any) {
        val dataToSend = encryptIfNeeded(ServerToClient(roomId, null, getSerialisedBytes(data) ?: return, true), encryptor) ?: return
        relayServerConnector.sendTCP(dataToSend)
    }

    override fun sendToAllUDP(data: Any) {
        // Will not be encrypted
        relayServerConnector.sendUDP(ServerToAllClientsUnencryptedUDP(getSerialisedBytes(data) ?: return))
    }

    override fun sendTCPToConnection(uuid: UUID, data: Any) {
        val dataToSend = encryptIfNeeded(ServerToClient(roomId, uuid.toString(), getSerialisedBytes(data) ?: return, true), encryptor) ?: return
        relayServerConnector.sendTCP(dataToSend)
    }

    override fun beforeStart(): Boolean {
        registerClassesToKryo(serverKryo)

        val roomCreation: HttpRequest.RoomCreationStatus?
        try {
            roomCreation = HttpRequest.sendCreateGameRequest()
        } catch (e: UnknownHostException) {
            // Could not resolve host from DNS, most likely network error
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect",
                "Please check your network connection and try again.", "", "Ok") }
            return false
        }
        if (roomCreation?.success != true) {
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect",
                "Room creation failed", "", "Ok") }
            return false
        }
        setRoomId(roomCreation.roomId)
        val roomKey = SecretKeySpec(Base64.decodeBase64(roomCreation.authResponse.roomKey), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        val hostKey = SecretKeySpec(Base64.decodeBase64(roomCreation.authResponse.clientKey), 0, AESGCMEncryptor.AES_KEY_LENGTH_BYTES, "AES")
        encryptor.setKey(hostKey)
        decrypter.setKey(roomKey)

        val iv = Base64.decodeBase64(roomCreation.authResponse.iv)
        val ciphertext = encryptor.encryptWithIV(iv, RelayNonce(roomCreation.authResponse.nonce))?.ciphertext
        if (ciphertext == null) {
            GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect",
                "Authorization challenge failed", "", "Ok") }
            return false
        }
        relayChallenge = RelayChallenge(ciphertext)

        return true
    }

    private fun setRoomId(roomId: Short) {
        if (roomId == Short.MAX_VALUE) {
            // Failed to create room, stop server
            return GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect",
                "Room creation failed", "", "Ok") }
        }

        if (this.roomId == Short.MAX_VALUE)
            this.roomId = roomId
    }

    override fun getRoomId(): Short {
        return roomId
    }

    override val connections: Collection<ConnectionMeta>
        get() {
            val conns = HashSet<ConnectionMeta>()
            for (conn in Entries(uuidConnectionMap)) {
                conns.add(conn.value)
            }
            return conns
        }

    /**
     * Function to be called when [PublicServer] receives message from relay server that player has connected
     * @param uuid [UUID] of the player joining
     */
    fun onConnect(uuid: UUID) {
        val newConn = ConnectionMeta(uuid)
        uuidConnectionMap.put(uuid, newConn)
        sendTCPToConnection(uuid, RequestClientData())
    }

    /**
     * Function to be called when [PublicServer] receives message from relay server that player has disconnected
     * @param uuid [UUID] of the player disconnecting
     */
    fun onDisconnect(uuid: UUID) {
        val removedConn = uuidConnectionMap.removeKey(uuid) ?: run {
            FileLog.info("PublicServer", "Failed to remove $uuid from connection map - it is not a key")
            return
        }
        onDisconnect(removedConn)
    }

    /** Requests for the relay server to create a game room */
    fun requestGameCreation() {
        val encrypted = encryptIfNeeded(NewGameRequest(roomId, gameServer.maxPlayersAllowed, mapName, myUuid.toString()), encryptor) ?: run {
            FileLog.info("PublicServer", "Room creation failed - encryption failed")
            return
        }
        relayServerConnector.sendTCP(encrypted)
    }

    /**
     * De-serializes the byte array in relay object received by relay host, and notifies [GameServer] of the received
     * object
     *
     * Method is synchronized as Kryo is not thread-safe
     * @param data serialised bytes of object to decode
     * @param sendingUUID the UUID of the sender
     */
    fun decodeRelayMessageObject(data: ByteArray, sendingUUID: UUID) {
        val sendingConnection = uuidConnectionMap[sendingUUID] ?: return
        val decoded = fromSerializedBytes(data)
        if (decoded is ClientToServer) sendingConnection.returnTripTime = decoded.clientToRelayReturnTripTime
        if (decoded is ClientUUIDDataOld) {
            sendTCPToConnection(sendingUUID, ConnectionError("Your game version is too old - please update to " +
                    "the latest build"))
            return
        }
        if (decoded is ClientData) {
            return checkClientData(sendingUUID, decoded)
        }
        onReceive(sendingConnection, decoded)
    }

    private fun checkClientData(uuid: UUID, data: ClientData) {
        // Check that build version is correct
        if (data.buildVersion != BUILD_VERSION) {
            sendTCPToConnection(uuid, ConnectionError("Your build version ${data.buildVersion} is " +
                    "not the same as host's build version $BUILD_VERSION"))
            return
        }
        onConnect(uuidConnectionMap[uuid] ?: return)
    }
}