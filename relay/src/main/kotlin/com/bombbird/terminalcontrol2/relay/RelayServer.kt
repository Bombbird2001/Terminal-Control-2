package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.RELAY_BUFFER_SIZE
import com.bombbird.terminalcontrol2.global.SERVER_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMDecrypter
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMEncryptor
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
import com.esotericsoftware.minlog.Log
import org.apache.commons.codec.binary.Base64
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Implementation of [RelayServer] interface with Kryo server providing the underlying networking.
 *
 * When a disconnection occurs at the networking server level, the player is automatically removed from the room, and if
 * the player is the host, the room is also automatically closed and all connections to it closed
 */
object RelayServer: RelayServer, RelayAuthorization {
    private val encryptor = AESGCMEncryptor(::getSerialisedBytes)
    private val decrypter = AESGCMDecrypter(::fromSerializedBytes)

    private val server = Server(RELAY_BUFFER_SIZE, RELAY_BUFFER_SIZE).apply {
        addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                if (obj is NeedsEncryption) {
                    Log.info("RelayServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    decrypter.decrypt(obj)
                } else obj

                (decrypted as? RelayServerReceive)?.apply {
                    handleRelayServerReceive(this@RelayServer, connection)
                }
            }

            /**
             * Called when a client connects to the server
             * @param connection the incoming connection
             */
            override fun connected(connection: Connection) {
                connection.sendTCP(RequestRelayAction())
            }

            /**
             * Called when a client disconnects
             * @param connection the disconnecting client
             */
            override fun disconnected(connection: Connection) {
                val hostRoom = hostConnectionToRoomMap[connection]
                if (hostRoom != null) {
                    // If connection is from a host
                    val room = idToRoom[hostRoom] ?: return
                    room.disconnectAllPlayers(connection)
                    idToRoom.remove(hostRoom)
                    hostConnectionToRoomMap.remove(connection)
                    hostUUIDs.remove(connectionToRoomUUID[connection]?.second)
                    println("Room $hostRoom closed")
                } else  {
                    // Connection is from non-host player
                    val uuidRoom = connectionToRoomUUID[connection] ?: return
                    val uuid = uuidRoom.second
                    val room = uuidRoom.first
                    idToRoom[room]?.removePlayer(uuid)
                    connectionToRoomUUID.remove(connection)
                    uuidToRoom.remove(uuid)
                }
            }
        })
    }

    /** Map of room ID to map of UUID to connection, excluding the 2nd connection from a host */
    private val idToRoom = ConcurrentHashMap<Short, Room>(32)

    /** Maps of connection to room ID and vice versa, only including the 2nd host connection */
    private val hostConnectionToRoomMap = ConcurrentHashMap<Connection, Short>(32)

    /** Map of connection to room ID and UUID, INCLUDING the 2nd connection from a host */
    private val connectionToRoomUUID = ConcurrentHashMap<Connection, Pair<Short, UUID>>(128)

    /** Map of UUID to the lobby room, excluding the 2nd connection from a host */
    private val uuidToRoom = ConcurrentHashMap<UUID, Short>(128)

    /** Set of UUIDs that are hosts */
    private val hostUUIDs = HashSet<UUID>(32)

    /** 128-bit AES key generator */
    private val keyGenerator = KeyGenerator.getInstance("AES").apply { init(128) }

    /**
     * Inner class for encapsulating a pending room connection, initiated after a request is sent to the create room
     * endpoint
     * @param secretKey the secret key generated for the room
     * @param timerTask the timer task to remove the pending room if no room is created in time
     */
    private class PendingRoom(val secretKey: SecretKey, private val timerTask: TimerTask) {
        /** Function to be called when the associated room is actually created */
        fun roomCreated() {
            // Perform the removal now
            timerTask.cancel()
            timerTask.run()
        }
    }

    /**
     * Symmetric key storage for pending room creations - will be removed after 20 seconds if no room creation
     * occurs by then
     */
    private val pendingRoomKeys = ConcurrentHashMap<Short, PendingRoom>(32)

    /** Timer responsible for closing pending rooms that are not connected to within 20 seconds */
    private val timer = Timer()

    @JvmStatic
    fun main(args: Array<String>) {
        println("Hello world from RelayServer!")

        // Shut down server before terminating program
        Runtime.getRuntime().addShutdownHook(Thread {
            println("Server shutdown signal received")
            server.stop()
            RelayEndpoint.stop()
        })

        registerClassesToKryo(server.kryo)
        server.bind(57773, 57779)
        server.start()

        RelayEndpoint.launch(this)
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
            val pendingRoom = pendingRoomKeys[roomID] ?: return false
            pendingRoom.roomCreated()
            connectionToRoomUUID[hostConnection] = Pair(roomID, newUUID)
            idToRoom[roomID] = Room(roomID, maxPlayers, hostConnection, mapName, pendingRoom.secretKey)
            hostConnectionToRoomMap[hostConnection] = roomID
            println("Room $roomID created - $mapName")
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
        return 0
    }

    override fun sendAddPlayerResult(addResult: Byte, clientConnection: Connection) {
        if (addResult == 0.toByte()) return // All ok, nothing to send
        val failedReason = when (addResult.toInt()) {
            1 -> "Game room is no longer available"
            2 -> "Game room is full"
            3 -> "Player with same ID already exists in game"
            4 -> "PLayer authorization failed"
            else -> "Unknown reason"
        }
        clientConnection.sendTCP(ConnectionError(failedReason))
    }

    override fun forwardToClient(obj: ServerToClient, conn: Connection) {
        val roomId = obj.roomId
        // Verify roomId matches connection
        if (connectionToRoomUUID[conn]?.first != roomId) {
            Log.info("RelayServer", "Forward to client failed - Connection does not match room ID")
            return
        }

        val targetUUID = obj.uuid
        val roomObj = idToRoom[roomId] ?: run {
            Log.info("RelayServer", "Forward to client failed - Room ID $roomId not found")
            return
        }

        // Verify connection is host
        if (!roomObj.isHost(conn)) {
            Log.info("RelayServer", "Forward to client failed - Connection UUID is not host of room")
            return
        }

        if (targetUUID == null) roomObj.forwardFromHostToAllClientConnections(obj, conn)
        else roomObj.forwardFromHostToAClient(obj, conn, UUID.fromString(targetUUID))
    }

    override fun forwardToAllClientsUDP(obj: ServerToAllClientsUDP, conn: Connection) {
        val roomObj = idToRoom[hostConnectionToRoomMap[conn]]
        if (roomObj == null) {
            Log.info("RelayServer", "Forward to all clients failed - Connection does not belong to a room")
            return
        }
        if (!roomObj.isHost(conn)) {
            Log.info("RelayServer", "Forward to client failed - Connection UUID is not host of room")
            return
        }
    }

    override fun forwardToServer(obj: ClientToServer, conn: Connection) {
        val roomId = obj.roomId
        // Verify roomId matches connection - prevents malicious modification of roomID
        if (connectionToRoomUUID[conn]?.first != roomId) {
            Log.info("RelayServer", "Forward to host failed - Connection does not match room ID")
            return
        }

        val roomObj = idToRoom[roomId] ?: run {
            Log.info("RelayServer", "Forward to host failed - Room ID $roomId not found")
            return
        }
        roomObj.forwardFromClientToHost(obj, conn)
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    @Synchronized
    private fun getSerialisedBytes(data: Any): ByteArray {
        val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
        server.kryo.writeClassAndObject(serialisationOutput, data)
        return serialisationOutput.toBytes()
    }

    /**
     * De-serialises the input byte array with Kryo and returns the object
     * @param data the byte array to de-serialise
     * @return the de-serialised object
     */
    @Synchronized
    private fun fromSerializedBytes(data: ByteArray): Any? {
        return server.kryo.readClassAndObject(Input(data))
    }

    /**
     * Inner class encapsulating an instance of a game room on the relay server, containing a host and associated
     * client connections
     * @param id ID of the room
     * @param maxPlayers maximum number of players allowed in the room
     * @param hostConnection the connection to the host
     * @param mapName the name of the airport map being hosted
     */
    class Room(val id: Short, val maxPlayers: Byte, private val hostConnection: Connection?, val mapName: String,
               private val symmetricKey: SecretKey) {
        private val connectedPlayers = ConcurrentHashMap<UUID, Connection>(maxPlayers.toInt())
        private val authorizedUUIDs = ConcurrentHashMap<UUID, Boolean>(maxPlayers.toInt())

        /**
         * Removes a player and their associated connection from the room
         * @param uuid UUID of the player who disconnected
         */
        fun removePlayer(uuid: UUID) {
            connectedPlayers.remove(uuid)
            hostConnection?.sendTCP(PlayerDisconnect(uuid.toString()))
            authorizedUUIDs.remove(uuid)
        }

        /**
         * Forces all connections in this room to close after the host disconnects
         * @param hostConn connection belonging to the disconnecting host
         */
        fun disconnectAllPlayers(hostConn: Connection) {
            for (conn in connectedPlayers) {
                if (conn.value != hostConn) conn.value.close()
            }
        }

        /**
         * Checks if the room has reached the max number of players
         * @return true if full, else false
         */
        fun isFull(): Boolean {
            return connectedPlayers.size >= maxPlayers
        }

        /**
         * Adds the provided player UUID to the room
         * @param uuid UUID of the player who wants to join
         * @param conn connection of the joining player
         */
        fun addPlayer(uuid: UUID, conn: Connection): Boolean {
            if (!authorizedUUIDs.containsKey(uuid)) return false
            if (connectedPlayers.containsKey(uuid)) return false
            connectedPlayers[uuid] = conn
            hostConnection?.sendTCP(PlayerConnect(uuid.toString()))
            return true
        }

        /**
         * Forwards the data to all client connections in this room
         * @param obj the [ServerToClient] to forward
         * @param senderConn the connection of the host (for verification)
         */
        fun forwardFromHostToAllClientConnections(obj: ServerToClient, senderConn: Connection) {
            if (senderConn != hostConnection) return
            for (connectedPlayer in connectedPlayers.values) {
                if (senderConn == connectedPlayer) continue // Forward to all clients except host
                if (obj.tcp) connectedPlayer.sendTCP(obj)
                else connectedPlayer.sendUDP(obj)
            }
        }

        /**
         * Forwards the data to a client connection in this room
         * @param obj the [ServerToClient] to forward
         * @param senderConn the connection of the host (for verification)
         * @param targetUUID UUID of the player to send to
         */
        fun forwardFromHostToAClient(obj: ServerToClient, senderConn: Connection, targetUUID: UUID) {
            if (senderConn != hostConnection) return
            // Forward to only 1 client
            val targetConnection = connectedPlayers[targetUUID] ?: return
            if (senderConn == targetConnection) return // If somehow sent to host, ignore it
            if (obj.tcp) targetConnection.sendTCP(obj)
            else targetConnection.sendUDP(obj)
        }

        /**
         * Forwards the data from a client connection to the host
         * @param obj the [ClientToServer] to forward
         * @param senderConn the connection of the sender (for verification)
         */
        fun forwardFromClientToHost(obj: ClientToServer, senderConn: Connection) {
            if (senderConn == hostConnection) return // Host should not be sending to itself
            hostConnection?.sendTCP(obj)
        }

        /**
         * Gets the number of players connected in the game
         * @return number of connected players
         */
        fun getConnectedPlayerCount(): Byte {
            return connectedPlayers.size.toByte()
        }

        /**
         * Adds the UUID to the list of authorized players to join the room
         * @return the secret symmetric key if the UUID was successfully added, else null (if UUID already exists, or
         * room is full)
         */
        fun authorizeUUID(uuid: UUID): String? {
            if (isFull()) return null
            if (authorizedUUIDs.putIfAbsent(uuid, true) == true) {
                return getSymmetricKeyBase64()
            }
            return null
        }

        /**
         * Checks whether the provided connection is the host of this room
         * @param conn the connection to check
         */
        fun isHost(conn: Connection): Boolean {
            return conn == hostConnection
        }

        /**
         * Returns the Base64 encoded string of the symmetric key of the room
         * @return Base64 encoded key
         */
        private fun getSymmetricKeyBase64(): String {
            return Base64.encodeBase64String(symmetricKey.encoded)
        }
    }

    /**
     * Gets all available games that other players can join as of now
     * @return a list of all Room objects that are open and are not full yet
     */
    fun getAvailableGames(): List<Room> {
        return idToRoom.filter { entry -> !entry.value.isFull() }.map { entry -> entry.value }
    }

    override fun authorizeUUIDToRoom(roomID: Short, uuid: UUID): String? {
        idToRoom[roomID]?.let {
            return it.authorizeUUID(uuid)
        } ?: run {
            Log.info("RelayServer", "Authorization failed - Room ID $roomID does not exist")
            return null
        }
    }

    override fun createPendingRoom(): HttpRequest.RoomCreationStatus {
        for (i in Short.MIN_VALUE until Short.MAX_VALUE) {
            if (!idToRoom.containsKey(i.toShort()) && !pendingRoomKeys.containsKey(i.toShort())) {
                val cancelTask = object : TimerTask() {
                    override fun run() {
                        pendingRoomKeys.remove(i.toShort())
                    }
                }
                timer.schedule(cancelTask, 20000)
                val newPendingRoom = PendingRoom(keyGenerator.generateKey(), cancelTask)
                pendingRoomKeys[i.toShort()] = newPendingRoom
                println("Pending room $i created")
                return HttpRequest.RoomCreationStatus(true, i.toShort(), Base64.encodeBase64String(newPendingRoom.secretKey.encoded))
            }
        }

        // If no rooms found (in the highly unlikely situation that all 65535 rooms are taken)
        return HttpRequest.RoomCreationStatus(false, Short.MAX_VALUE, "")
    }
}