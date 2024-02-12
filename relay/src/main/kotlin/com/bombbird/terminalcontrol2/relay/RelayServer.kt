package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.RELAY_BUFFER_SIZE
import com.bombbird.terminalcontrol2.global.RELAY_TCP_PORT
import com.bombbird.terminalcontrol2.global.RELAY_UDP_PORT
import com.bombbird.terminalcontrol2.global.SERVER_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.encryption.EncryptedData
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.bombbird.terminalcontrol2.networking.relayserver.*
import com.bombbird.terminalcontrol2.networking.relayserver.RelayServer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.esotericsoftware.kryo.Kryo
import java.lang.NullPointerException
import java.util.Timer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator

/**
 * Implementation of [RelayServer] interface with Kryo server providing the underlying networking.
 *
 * When a disconnection occurs at the networking server level, the player is automatically removed from the room, and if
 * the player is the host, the room is also automatically closed and all connections to it closed
 */
object RelayServer: RelayServer, RelayAuthorization {
    private val server = Server(RELAY_BUFFER_SIZE, RELAY_BUFFER_SIZE).apply {
        addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                try {
                    if (obj is RelayChallenge) {
                        // Verify challenge
                        return RequestAuthenticator.handleRelayChallenge(connection, obj)
                    }

                    if (obj is NeedsEncryption) {
                        FileLog.info("RelayServer", "Received unencrypted data of class ${obj.javaClass.name}")
                        return
                    }

                    val decrypted = if (RequestAuthenticator.isPendingRoom(connection)) {
                        // If the connection is authorised but pending room connection, use AuthenticationChecker
                        if (obj is EncryptedData) {
                            RequestAuthenticator.decryptForPendingConn(connection, obj)
                        } else obj
                    } else {
                        // Decrypt using key belonging to the room the connection is in
                        val roomId = connectionToRoomUUID[connection]?.first ?: return
                        val connRoom = idToRoom[roomId] ?: return
                        if (obj is EncryptedData) {
                            connRoom.decryptForRoom(connection, obj)
                        } else obj
                    }

                    (decrypted as? RelayServerReceive)?.apply {
                        handleRelayServerReceive(this@RelayServer, connection)
                    }
                } catch (e: Exception) {
                    FileLog.error("RelayServer", "Error occurred while receiving data from client\n${e.stackTraceToString()}")
                    HttpRequest.sendCrashReport(e, "RelayServer", "NA")
                }
            }

            /**
             * Called when a client disconnects
             * @param connection the disconnecting client
             */
            override fun disconnected(connection: Connection) {
                try {
                    val hostRoom = hostConnectionToRoomMap[connection]
                    if (hostRoom != null) {
                        // If connection is from a host
                        val room = idToRoom[hostRoom] ?: return
                        room.disconnectAllPlayers(connection)
                        idToRoom.remove(hostRoom)
                        hostConnectionToRoomMap.remove(connection)
                        hostUUIDs.remove(connectionToRoomUUID[connection]?.second)
                        FileLog.info("RelayServer", "Room $hostRoom closed")
                        BotUpdater.updateServers(moshiGamesAdapter.toJson(getAvailableGames()))
                    } else  {
                        // Connection is from non-host player
                        val uuidRoom = connectionToRoomUUID[connection] ?: return
                        val uuid = uuidRoom.second
                        val room = uuidRoom.first
                        idToRoom[room]?.removePlayer(uuid)
                        connectionToRoomUUID.remove(connection)
                        uuidToRoom.remove(uuid)
                    }
                } catch (e: Exception) {
                    FileLog.error("RelayServer", "Error occurred when client disconnected\n${e.stackTraceToString()}")
                    HttpRequest.sendCrashReport(e, "RelayServer", "NA")
                }
            }
        })
    }

    /** Map of room ID to map of UUID to connection, excluding the 2nd connection from a host */
    val idToRoom = ConcurrentHashMap<Short, Room>(32)

    /** Maps of connection to room ID and vice versa, only including the 2nd host connection */
    val hostConnectionToRoomMap = ConcurrentHashMap<Connection, Short>(32)

    /** Map of connection to room ID and UUID, INCLUDING the 2nd connection from a host */
    val connectionToRoomUUID = ConcurrentHashMap<Connection, Pair<Short, UUID>>(128)

    /** Map of UUID to the lobby room, excluding the 2nd connection from a host */
    val uuidToRoom = ConcurrentHashMap<UUID, Short>(128)

    /** Set of UUIDs that are hosts */
    val hostUUIDs = HashSet<UUID>(32)

    /** 128-bit AES key generator */
    val keyGenerator: KeyGenerator = KeyGenerator.getInstance("AES").apply { init(128) }

    private val manualKryo = Kryo().apply { registerClassesToKryo(this) }
    private val manualKryoLock = Any()

    /**
     * Symmetric key storage for pending room creations - will be removed after 20 seconds if no room creation
     * occurs by then
     */
    val pendingRooms = ConcurrentHashMap<Short, PendingRoom>(32)

    /** Timer responsible for closing pending rooms that are not connected to within 20 seconds */
    val timer = Timer()

    @JvmStatic
    fun main(args: Array<String>) {
        FileLog.info("RelayServer", "Hello world from RelayServer!")

        // Shut down server before terminating program
        Runtime.getRuntime().addShutdownHook(Thread {
            FileLog.info("RelayServer", "Server shutdown signal received")
            stop()
        })

        registerClassesToKryo(server.kryo)
        server.bind(RELAY_TCP_PORT, RELAY_UDP_PORT)
        server.start()

        val test = args.isNotEmpty() && args[0] == "test"

        RelayEndpoint.launch(this, !test)
    }

    fun stop() {
        FileLog.info("RelayServer", "Stopping relay")
        server.stop()
        RelayEndpoint.stop()
    }

    override fun createNewRoom(
        roomID: Short,
        newUUID: UUID,
        hostConnection: Connection,
        maxPlayers: Byte,
        mapName: String
    ): Boolean {
        if (hostUUIDs.contains(newUUID)) return false

        if (!idToRoom.containsKey(roomID)) {
            val pendingRoom = pendingRooms[roomID] ?: return false
            pendingRoom.roomCreated()
            connectionToRoomUUID[hostConnection] = Pair(roomID, newUUID)
            idToRoom[roomID] = Room(roomID, maxPlayers, hostConnection, mapName, pendingRoom.serverKey, pendingRoom.roomEncryptor, pendingRoom.hostDecrypter)
            hostConnectionToRoomMap[hostConnection] = roomID
            RequestAuthenticator.connAddedToRoom(hostConnection)
            FileLog.info("RelayServer", "Room $roomID created - $mapName")
            return true
        }

        return false
    }

    override fun addPlayerToRoom(
        roomId: Short,
        newUUID: UUID,
        clientConnection: Connection
    ): Byte {
        val room = idToRoom[roomId] ?: return 1 // No such room
        if (room.isFull()) return 2 // Room is full
        if (uuidToRoom.containsKey(newUUID)) return 3 // UUID already in a room
        if (!room.addPlayer(newUUID, clientConnection)) return 4 // UUID authorization failed
        connectionToRoomUUID[clientConnection] = Pair(roomId, newUUID)
        uuidToRoom[newUUID] = roomId
        RequestAuthenticator.connAddedToRoom(clientConnection)
        if (room.getConnectedPlayerCount().toInt() == 1) BotUpdater.updateServers(moshiGamesAdapter.toJson(getAvailableGames()))
        return 0
    }

    override fun sendAddPlayerResult(addResult: Byte, clientConnection: Connection) {
        if (addResult == 0.toByte()) return // All ok, nothing to send
        val failedReason = when (addResult.toInt()) {
            1 -> "Game room is no longer available"
            2 -> "Game room is full"
            3 -> "Player with same ID already exists in game"
            4 -> "Player authorization failed"
            else -> "Unknown reason"
        }
        val encrypted = RequestAuthenticator.encryptForPendingConn(clientConnection, ConnectionError(failedReason)) ?: return clientConnection.close()
        clientConnection.sendTCP(encrypted)
    }

    override fun forwardToClient(obj: ServerToClient, conn: Connection) {
        val roomId = obj.roomId
        // Verify roomId matches connection
        if (connectionToRoomUUID[conn]?.first != roomId) {
            FileLog.info("RelayServer", "Forward to client failed - Connection does not match room ID")
            return
        }

        val targetUUID = obj.uuid
        val roomObj = idToRoom[roomId] ?: run {
            FileLog.info("RelayServer", "Forward to client failed - Room ID $roomId not found")
            return
        }

        // Verify connection is host
        if (!roomObj.isHost(conn)) {
            FileLog.info("RelayServer", "Forward to client failed - Connection UUID is not host of room")
            return
        }

        if (targetUUID == null) roomObj.forwardFromHostToAllClientConnections(obj, conn)
        else roomObj.forwardFromHostToAClient(obj, conn, UUID.fromString(targetUUID))
    }

    override fun forwardToAllClientsUnencryptedUDP(obj: ServerToAllClientsUnencryptedUDP, conn: Connection) {
        val roomId = hostConnectionToRoomMap[conn] ?: return
        val roomObj = idToRoom[roomId]
        if (roomObj == null) {
            FileLog.info("RelayServer", "Forward to all clients failed - Connection does not belong to a room")
            return
        }
        if (!roomObj.isHost(conn)) {
            FileLog.info("RelayServer", "Forward to client failed - Connection UUID is not host of room")
            return
        }
        roomObj.forwardFromHostToAllClientConnectionsUnencryptedUDP(obj, conn)
    }

    override fun forwardToServer(obj: ClientToServer, conn: Connection) {
        val roomId = obj.roomId
        // Verify roomId matches connection - prevents malicious modification of roomID
        if (connectionToRoomUUID[conn]?.first != roomId) {
            FileLog.info("RelayServer", "Forward to host failed - Connection does not match room ID")
            return
        }

        val roomObj = idToRoom[roomId] ?: run {
            FileLog.info("RelayServer", "Forward to host failed - Room ID $roomId not found")
            return
        }
        roomObj.forwardFromClientToHost(obj, conn)
    }

    override fun authorizeUUIDToRoom(roomID: Short, uuid: UUID): RelayAuthorization.RoomAuthorizationResult? {
        return RequestAuthenticator.authorizeUUIDToRoom(roomID, uuid)
    }

    override fun createPendingRoom(): HttpRequest.RoomCreationStatus? {
        return RequestAuthenticator.createPendingRoom()
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    @Synchronized
    fun getSerialisedBytes(data: Any): ByteArray? {
        var times = 0
        while (times < 3) {
            try {
                val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
                synchronized(manualKryoLock) {
                    manualKryo.writeClassAndObject(serialisationOutput, data)
                }
                return serialisationOutput.toBytes()
            } catch (e: NullPointerException) {
                times++
            }
        }

        return null
    }

    /**
     * De-serialises the input byte array with Kryo and returns the object
     * @param data the byte array to de-serialise
     * @return the de-serialised object
     */
    @Synchronized
    fun fromSerializedBytes(data: ByteArray): Any? {
        var times = 0
        while (times < 3) {
            try {
                synchronized(manualKryoLock) {
                    return manualKryo.readClassAndObject(Input(data))
                }
            } catch (e: NullPointerException) {
                times++
            }
        }

        return null
    }

    /**
     * Gets all available games that other players can join as of now
     * @return a list of all Room objects that are open and are not full yet
     */
    fun getAvailableGames(): List<Room> {
        return idToRoom.filter { entry -> !entry.value.isFull() }.map { entry -> entry.value }
    }
}