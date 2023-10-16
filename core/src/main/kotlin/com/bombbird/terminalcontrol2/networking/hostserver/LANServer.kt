package com.bombbird.terminalcontrol2.networking.hostserver

import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDDataOld
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientData
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
    private class DiffieHellmanPair(val serverDH: DiffieHellman, val clientDH: DiffieHellman)

    override val serverKryo: Kryo
        get() = server.kryo

    private val server = Server(SERVER_WRITE_BUFFER_SIZE, SERVER_READ_BUFFER_SIZE)

    /** Maps [Connection] to their respective Diffie-Hellman instances while the key has not been established */
    private val connectionDHMap = GdxArrayMap<Connection, DiffieHellmanPair>(PLAYER_SIZE)

    /** Maps [Connection] to their respective [Encryptor], [Decrypter] containing the secret key */
    private val connectionEncDecMap = GdxArrayMap<Connection, Pair<Encryptor, Decrypter>>(PLAYER_SIZE)

    /** Maps [Connection] to [ConnectionMeta] */
    private val connectionMetaMap = GdxArrayMap<Connection, ConnectionMeta>(PLAYER_SIZE)

    /** Maps [UUID] to [Connection] */
    private val uuidConnectionMap = GdxArrayMap<UUID, Connection>(PLAYER_SIZE)

    override fun start(): Boolean {
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
                    GAME.quitCurrentGameWithDialog {
                        CustomDialog("Error starting game", "If you see this error, consider restarting your " +
                                "device and try again.", "", "Ok" )
                    }
                    return false
                }
            }
        }
        server.start()
        Thread.sleep(1000)
        server.updateThread?.setUncaughtExceptionHandler { _, e ->
            // We can ignore this, it happens sometimes when the client is stopped
            if (e is ClosedSelectorException) return@setUncaughtExceptionHandler

            HttpRequest.sendCrashReport(Exception(e), "LANServer", gameServer.getMultiplayerType())
            GAME.quitCurrentGameWithDialog { CustomDialog("Error", "An error occurred", "", "Ok") }
        }
        server.addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                val encryptorDecrypter = connectionEncDecMap[connection]

                if (encryptorDecrypter == null && obj is DiffieHellmanValueOld) {
                    // Will prevent connection from a client with build version < 10; but such a client would not even
                    // have the new DH class registered to Kryo, so it would disconnect by itself anyway
                    // We can't send a ConnectionError since that has to be encrypted as well, so we have no choice but
                    // to close the connection directly
                    return connection.close()
                }

                if (encryptorDecrypter == null && obj is DiffieHellmanValues) {
                    val dhPair = connectionDHMap[connection] ?: return
                    val serverSecretKey = dhPair.serverDH.getAES128Key(obj.serverXy)
                    val clientSecretKey = dhPair.clientDH.getAES128Key(obj.clientXy)
                    val encryptor = AESGCMEncryptor(this@LANServer::getSerialisedBytes)
                    val decrypter = AESGCMDecrypter(this@LANServer::fromSerializedBytes)
                    encryptor.setKey(serverSecretKey)
                    decrypter.setKey(clientSecretKey)
                    connectionEncDecMap[connection] = Pair(encryptor, decrypter)
                    connectionDHMap.removeKey(connection)

                    // Key established
                    connection.sendTCP(encryptIfNeeded(RequestClientData(), encryptor))
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
                if (decrypted is ClientUUIDDataOld) {
                    getEncryptorForConnection(connection)?.let { connEncryptor ->
                        encryptIfNeeded(ConnectionError("Your game version is too old - please update to the " +
                                "latest build"), connEncryptor)?.let { connection.sendTCP(it) }
                    }
                }
                if (decrypted is ClientData) {
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
                val serverKeyDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                val serverToSend = serverKeyDH.getExchangeValue()
                val clientKeyDH = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                val clientToSend = clientKeyDH.getExchangeValue()
                connectionDHMap[connection] = DiffieHellmanPair(serverKeyDH, clientKeyDH)
                connection.sendTCP(DiffieHellmanValues(serverToSend, clientToSend))
            }

            /**
             * Called when a client disconnects
             * @param connection the disconnecting client
             */
            override fun disconnected(connection: Connection?) {
                connectionDHMap.removeKey(connection)
                connectionEncDecMap.removeKey(connection)
                gameServer.postRunnableAfterEngineUpdate {
                    // Remove entries only after this engine update to prevent threading issues
                    val conn = connectionMetaMap[connection] ?: return@postRunnableAfterEngineUpdate
                    connectionMetaMap.removeKey(connection)
                    onDisconnect(conn)
                }
            }
        })

        return true
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

    override fun getConnectionStatus(): String {
        return "Connected to ${server.connections.size} clients"
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
    private fun receiveClientData(connection: Connection, data: ClientData) {
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
        if (data.buildVersion != BUILD_VERSION) {
            encryptIfNeeded(ConnectionError("Your build version ${data.buildVersion} is not the same as host's " +
                    "build version $BUILD_VERSION"), encryptor)?.let { connection.sendTCP(it) }
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