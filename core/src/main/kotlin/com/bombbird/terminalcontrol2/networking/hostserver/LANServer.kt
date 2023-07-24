package com.bombbird.terminalcontrol2.networking.hostserver

import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientUUID
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.collections.GdxArrayMap
import ktx.collections.set
import java.lang.Exception
import java.net.BindException
import java.nio.channels.ClosedSelectorException
import java.util.*
import kotlin.collections.HashSet

/**
 * Server for handling LAN multiplayer games
 *
 * Cannot be used for public multiplayer games, use [PublicServer] for that
 */
class LANServer(
    gameServer: GameServer,
    onReceive: (ConnectionMeta, Any?) -> Unit,
    onConnect: (ConnectionMeta) -> Unit,
    onDisconnect: (ConnectionMeta) -> Unit
) : NetworkServer(gameServer, onReceive, onConnect, onDisconnect) {
    override val serverKryo: Kryo
        get() = server.kryo

    private val server = Server(SERVER_WRITE_BUFFER_SIZE, SERVER_READ_BUFFER_SIZE)

    /** Maps [Connection] to their respective Diffie-Hellman instances while the key has not been established */
    private val connectionDHMap = GdxArrayMap<Connection, DiffieHellman>(PLAYER_SIZE)

    /** Maps [Connection] to their respective [Encryptor], [Decrypter] containing the secret key */
    private val connectionEncDecMap = GdxArrayMap<Connection, Pair<Encryptor, Decrypter>>(PLAYER_SIZE)

    /** Maps [Connection] to [ConnectionMeta] */
    private val connectionMetaMap = GdxArrayMap<Connection, ConnectionMeta>(PLAYER_SIZE)

    /** Maps [UUID] to [Connection] */
    private val uuidConnectionMap = GdxArrayMap<UUID, Connection>(PLAYER_SIZE)

    override fun start() {
        server.setDiscoveryHandler(LANServerDiscoveryHandler(gameServer))
        // Try all 10 available port combinations
        for (entry in LAN_TCP_PORTS.withIndex()) {
            try {
                val tcpPort = entry.value
                val udpPort = LAN_UDP_PORTS[entry.index]
                CLIENT_TCP_PORT_IN_USE = tcpPort
                CLIENT_UDP_PORT_IN_USE = udpPort
                server.bind(tcpPort, udpPort)
                break
            } catch (e: BindException) {
                // If reached last available combination, all combinations taken, exit with error
                if (entry.index == LAN_TCP_PORTS.size - 1) {
                    return GAME.quitCurrentGameWithDialog(
                        CustomDialog("Error starting game",
                            "If you see this error, consider restarting your device and try again.", "", "Ok")
                    )
                }
            }
        }
        server.start()
        Thread.sleep(1000)
        server.updateThread?.setUncaughtExceptionHandler { _, e ->
            // We can ignore this, it happens sometimes when the client is stopped
            if (e is ClosedSelectorException) return@setUncaughtExceptionHandler

            val multiplayerType = if (gameServer.maxPlayersAllowed > 1) "LAN multiplayer" else "Singleplayer"
            HttpRequest.sendCrashReport(Exception(e), "LANServer", multiplayerType)
            GAME.quitCurrentGameWithDialog(CustomDialog("Error", "An error occurred", "", "Ok"))
        }
        server.addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                val encryptorDecrypter = connectionEncDecMap[connection]

                if (encryptorDecrypter == null && obj is DiffieHellmanValue) {
                    val dh = connectionDHMap[connection] ?: return
                    val secretKey = dh.getAES128Key(obj.xy)
                    val encryptor = AESGCMEncryptor(this@LANServer::getSerialisedBytes)
                    val decrypter = AESGCMDecrypter(this@LANServer::fromSerializedBytes)
                    encryptor.setKey(secretKey)
                    decrypter.setKey(secretKey)
                    connectionEncDecMap[connection] = Pair(encryptor, decrypter)
                    connectionDHMap.removeKey(connection)

                    // Key established
                    connection.sendTCP(encryptIfNeeded(RequestClientUUID(), encryptor))
                    return
                }

                if (encryptorDecrypter == null) return

                if (obj is NeedsEncryption) {
                    FileLog.info("LANServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    encryptorDecrypter.second.decrypt(obj)
                } else obj

                // Check if is initial connection response
                if (decrypted is ClientUUIDData) {
                    receiveClientData(connection, decrypted)
                }

                val conn = connectionMetaMap[connection] ?: return
                conn.returnTripTime = connection.returnTripTime
                onReceive(conn, decrypted)
            }

            /**
             * Called when a client connects to the server
             * @param connection the incoming connection
             */
            override fun connected(connection: Connection) {
                val dh = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                val toSend = dh.getExchangeValue()
                connectionDHMap[connection] = dh
                connection.sendTCP(DiffieHellmanValue(toSend))
            }

            /**
             * Called when a client disconnects
             * @param connection the disconnecting client
             */
            override fun disconnected(connection: Connection?) {
                gameServer.postRunnableAfterEngineUpdate {
                    // Remove entries only after this engine update to prevent threading issues
                    val conn = connectionMetaMap[connection] ?: return@postRunnableAfterEngineUpdate
                    connectionMetaMap.removeKey(connection)
                    onDisconnect(conn)
                }
            }
        })
    }

    override fun stop() {
        server.stop()
    }

    override fun sendToAllTCP(data: Any) {
        for (i in 0 until connectionEncDecMap.size) {
            val conn = connectionEncDecMap.getKeyAt(i) ?: continue
            val encrypted = encryptIfNeeded(data, getEncryptorForConnection(conn) ?: continue) ?: continue
            conn.sendTCP(encrypted)
        }
    }

    override fun sendToAllUDP(data: Any) {
        // Will not be encrypted
        server.sendToAllUDP(data)
    }

    override fun sendTCPToConnection(uuid: UUID, data: Any) {
        val conn = uuidConnectionMap[uuid] ?: return
        val encrypted = encryptIfNeeded(data, getEncryptorForConnection(conn) ?: return) ?: return
        conn.sendTCP(encrypted)
    }

    override fun beforeStart(): Boolean {
        registerClassesToKryo(serverKryo)
        return true
    }

    override fun getRoomId(): Short? {
        return null
    }

    override val connections: Collection<ConnectionMeta>
        get() {
            val conns = HashSet<ConnectionMeta>()
            for (conn in Entries(connectionMetaMap)) {
                conns.add(conn.value)
            }
            return conns
        }

    /**
     * Custom handler for LAN server on receiving client UUID data
     * @param connection the connection sending the data
     * @param data client data received
     */
    private fun receiveClientData(connection: Connection, data: ClientUUIDData) {
        // If the UUID is null or the map already contains the UUID, do not send the data
        val encryptor = getEncryptorForConnection(connection) ?: return
        if (data.uuid == null) {
            encryptIfNeeded(ConnectionError("Missing player ID"), encryptor)?.let { connection.sendTCP(it) }
            return
        }
        if (gameServer.playersInGame == gameServer.maxPlayersAllowed) {
            encryptIfNeeded(ConnectionError("Game is full"), encryptor)?.let { connection.sendTCP(it) }
            return
        }
        val connUuid = UUID.fromString(data.uuid)
        if (gameServer.sectorMap.containsKey(connUuid)) {
            FileLog.info("NetworkingTools", "UUID $connUuid is already in game")
            encryptIfNeeded(ConnectionError("Player with same ID already in server"), encryptor)?.let { connection.sendTCP(it) }
            return
        }
        val connMeta = ConnectionMeta(connUuid, 0)
        connectionMetaMap.put(connection, connMeta)
        uuidConnectionMap.put(connUuid, connection)
        onConnect(connMeta)
    }

    /**
     * Returns the [Encryptor] object for the input connection
     * @param conn the connection to get the Encryptor for
     * @return the Encryptor
     */
    private fun getEncryptorForConnection(conn: Connection): Encryptor? {
        return connectionEncDecMap[conn]?.first
    }
}