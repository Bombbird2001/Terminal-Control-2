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
    private val server = Server(RELAY_BUFFER_SIZE, RELAY_BUFFER_SIZE).apply {
        addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                if (obj is RelayChallenge) {
                    // Verify challenge
                    if (!AuthenticationChecker.authenticateConnection(connection, obj)) return connection.close()
                    val encryptedRequest = AuthenticationChecker.encryptBasedOnConnectionPendingRoom(connection, RequestRelayAction()) ?: return
                    connection.sendTCP(encryptedRequest)
                }

                if (obj is NeedsEncryption) {
                    Log.info("RelayServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (AuthenticationChecker.isPendingRoom(connection)) {
                    // If the connection is authorised but pending room connection, use AuthenticationChecker
                    if (obj is EncryptedData) {
                        AuthenticationChecker.decryptForConn(connection, obj)
                    } else obj
                } else {
                    // Decrypt using key belonging to the room the connection is in
                    val connRoom = idToRoom[connectionToRoomUUID[connection]?.first] ?: return
                    if (obj is EncryptedData) {
                        connRoom.decryptForRoom(obj)
                    } else obj
                }

                (decrypted as? RelayServerReceive)?.apply {
                    handleRelayServerReceive(this@RelayServer, connection)
                }
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
                    Log.info("RelayServer", "Room $hostRoom closed")
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
        private val encryptor = AESGCMEncryptor(::getSerialisedBytes).apply { setKey(secretKey) }
        private val decrypter = AESGCMDecrypter(::fromSerializedBytes).apply { setKey(secretKey) }

        /** Function to be called when the associated room is actually created */
        fun roomCreated() {
            // Perform the removal now
            timerTask.cancel()
            timerTask.run()
        }

        /**
         * Performs encryption on the input data if needed using the server's encryptor, and returns the encrypted result
         *
         * If encryption not needed, returns the object itself
         */
        fun encryptIfNeeded(data: Any): Any? {
            (data as? NeedsEncryption)?.let {
                return encryptor.encrypt(it)
            } ?: return data
        }

        /**
         * Performs decryption on the received data using the room's key
         * @param data ciphertext to be decrypted
         * @return the decrypted object
         */
        fun decryptForRoom(data: EncryptedData): Any? {
            return decrypter.decrypt(data)
        }
    }

    /**
     * Symmetric key storage for pending room creations - will be removed after 20 seconds if no room creation
     * occurs by then
     */
    private val pendingRooms = ConcurrentHashMap<Short, PendingRoom>(32)

    private object AuthenticationChecker {
        private val encryptor = AESGCMEncryptor(::getSerialisedBytes)

        /**
         * Stores the random encrypted nonce mapped to room ID which is used to verify the challenge response sent by the
         * client upon encryption
         */
        private val randomNonceChallenge = ConcurrentHashMap<RelayChallenge, Short>(32)

        /**
         * Stores connections which have been authorised and pending relay action, mapping to the ID of the room for
         * subsequent decryption
         */
        private val authorisedConnectionsPendingAction = ConcurrentHashMap<Connection, Short>(32)

        /**
         * Attempts to authenticate the connection based on the sent challenge data
         * @param conn the connection to authenticate
         * @param relayChallenge the [RelayChallenge] object with challenge data
         * @return true if authentication succeeds, else false
         */
        fun authenticateConnection(conn: Connection, relayChallenge: RelayChallenge): Boolean {
            if (connectionToRoomUUID.containsKey(conn)) return false
            val roomId = randomNonceChallenge[relayChallenge] ?: return false
            authorisedConnectionsPendingAction[conn] = roomId
            return true
        }

        /**
         * Encrypts the object based on the authorised connection's room
         */
        fun encryptBasedOnConnectionPendingRoom(conn: Connection, obj: NeedsEncryption): Any? {
            // Try to find an open room first
            val id = authorisedConnectionsPendingAction[conn] ?: return null
            idToRoom[id]?.let {
                return it.encryptIfNeeded(obj)
            } ?: pendingRooms[id]?.let {
                // Otherwise, find the pending room
                return it.encryptIfNeeded(obj)
            }

            return null
        }

        /**
         * Creates and adds a new challenge nonce to validate new incoming connections
         * @param key the key used by the room for encryption
         * @param roomId the ID of the room the connection is authorized to
         * @return a pair consisting of the random nonce and IV (in Base64) to be encrypted and sent by the client
         */
        fun addChallengeNonce(key: SecretKey, roomId: Short): Pair<String, String>? {
            val uuid = UUID.randomUUID().toString()
            encryptor.setKey(key)
            val encrypted = encryptor.encrypt(RelayNonce(uuid)) ?: return null
            val ivBase64 = Base64.encodeBase64String(encrypted.iv)
            randomNonceChallenge[RelayChallenge(encrypted.ciphertext)] = roomId
            return Pair(uuid, ivBase64)
        }

        /**
         * Checks if the connection has been authorised but is still pending further action (joining/creating room)
         * @param conn the connection to check
         * @return true if pending, else false
         */
        fun isPendingRoom(conn: Connection): Boolean {
            return authorisedConnectionsPendingAction.containsKey(conn)
        }

        /**
         * Performs decryption on the received data using the pending connection's stored key
         * @param conn the connection to decrypt for
         * @param data ciphertext to be decrypted
         * @return the decrypted object
         */
        fun decryptForConn(conn: Connection, data: EncryptedData): Any? {
            val room = authorisedConnectionsPendingAction[conn]
            return idToRoom[room]?.decryptForRoom(data) ?: pendingRooms[room]?.decryptForRoom(data)
        }
    }

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
            val pendingRoom = pendingRooms[roomID] ?: return false
            pendingRoom.roomCreated()
            connectionToRoomUUID[hostConnection] = Pair(roomID, newUUID)
            idToRoom[roomID] = Room(roomID, maxPlayers, hostConnection, mapName, pendingRoom.secretKey)
            hostConnectionToRoomMap[hostConnection] = roomID
            Log.info("RelayServer", "Room $roomID created - $mapName")
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
            4 -> "Player authorization failed"
            else -> "Unknown reason"
        }
        val encrypted = AuthenticationChecker.encryptBasedOnConnectionPendingRoom(clientConnection, ConnectionError(failedReason)) ?: return
        clientConnection.sendTCP(encrypted)
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

    override fun forwardToAllClientsUnencryptedUDP(obj: ServerToAllClientsUnencryptedUDP, conn: Connection) {
        val roomObj = idToRoom[hostConnectionToRoomMap[conn]]
        if (roomObj == null) {
            Log.info("RelayServer", "Forward to all clients failed - Connection does not belong to a room")
            return
        }
        if (!roomObj.isHost(conn)) {
            Log.info("RelayServer", "Forward to client failed - Connection UUID is not host of room")
            return
        }
        roomObj.forwardFromHostToAllClientConnectionsUnencryptedUDP(obj, conn)
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
        private val authorizedUUIDs = ConcurrentHashMap<UUID, TimerTask>(maxPlayers.toInt())

        private val encryptor = AESGCMEncryptor(::getSerialisedBytes).apply { setKey(symmetricKey) }
        private val decrypter = AESGCMDecrypter(::fromSerializedBytes).apply { setKey(symmetricKey) }

        /**
         * Removes a player and their associated connection from the room
         * @param uuid UUID of the player who disconnected
         */
        fun removePlayer(uuid: UUID) {
            val encrypted = encryptIfNeeded(PlayerDisconnect(uuid.toString())) ?: return
            connectedPlayers.remove(uuid)
            authorizedUUIDs.remove(uuid)
            hostConnection?.sendTCP(encrypted)
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
            val pending = authorizedUUIDs[uuid] ?: return false
            pending.cancel()
            pending.run()
            if (connectedPlayers.containsKey(uuid)) return false
            connectedPlayers[uuid] = conn
            val encrypted = encryptIfNeeded(PlayerConnect(uuid.toString())) ?: return false
            hostConnection?.sendTCP(encrypted)
            return true
        }

        /**
         * Forwards the data to all client connections in this room
         * @param obj the [ServerToClient] to forward
         * @param senderConn the connection of the host (for verification)
         */
        fun forwardFromHostToAllClientConnections(obj: ServerToClient, senderConn: Connection) {
            if (senderConn != hostConnection) return
            val encrypted = encryptIfNeeded(obj)
            for (connectedPlayer in connectedPlayers.values) {
                if (senderConn == connectedPlayer) continue // Forward to all clients except host
                if (obj.tcp) connectedPlayer.sendTCP(encrypted)
                else connectedPlayer.sendUDP(encrypted)
            }
        }

        /**
         * Forwards the data to all client connections in this room using UDP without encryption
         * @param obj the [ServerToAllClientsUnencryptedUDP] to forward
         * @param senderConn the connection of the host (for verification)
         */
        fun forwardFromHostToAllClientConnectionsUnencryptedUDP(obj: ServerToAllClientsUnencryptedUDP, senderConn: Connection) {
            if (senderConn != hostConnection) return
            for (connectedPlayer in connectedPlayers.values) {
                if (senderConn == connectedPlayer) continue // Forward to all clients except host
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
            val encrypted = encryptIfNeeded(obj)
            if (obj.tcp) targetConnection.sendTCP(encrypted)
            else targetConnection.sendUDP(encrypted)
        }

        /**
         * Forwards the data from a client connection to the host
         * @param obj the [ClientToServer] to forward
         * @param senderConn the connection of the sender (for verification)
         */
        fun forwardFromClientToHost(obj: ClientToServer, senderConn: Connection) {
            if (senderConn == hostConnection) return // Host should not be sending to itself
            val encrypted = encryptIfNeeded(obj) ?: return
            hostConnection?.sendTCP(encrypted)
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
        fun authorizeUUID(uuid: UUID): SecretKey? {
            if (isFull()) return null
            val cancelTask = object : TimerTask() {
                override fun run() {
                    authorizedUUIDs.remove(uuid)
                    Log.info("RelayServer", "Pending player $uuid removed")
                }
            }
            timer.schedule(cancelTask, 10000)
            if (authorizedUUIDs.putIfAbsent(uuid, cancelTask) == null) {
                Log.info("RelayServer", "Pending player $uuid added")
                return symmetricKey
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
         * Performs encryption on the input data if needed using the server's encryptor, and returns the encrypted result
         *
         * If encryption not needed, returns the object itself
         */
        fun encryptIfNeeded(data: Any): Any? {
            (data as? NeedsEncryption)?.let {
                return encryptor.encrypt(it)
            } ?: return data
        }

        /**
         * Performs decryption on the received data using the room's key
         * @param data ciphertext to be decrypted
         * @return the decrypted object
         */
        fun decryptForRoom(data: EncryptedData): Any? {
            return decrypter.decrypt(data)
        }
    }

    /**
     * Gets all available games that other players can join as of now
     * @return a list of all Room objects that are open and are not full yet
     */
    fun getAvailableGames(): List<Room> {
        return idToRoom.filter { entry -> !entry.value.isFull() }.map { entry -> entry.value }
    }

    override fun authorizeUUIDToRoom(roomID: Short, uuid: UUID): Triple<String, String, String>? {
        idToRoom[roomID]?.let {
            val key = it.authorizeUUID(uuid) ?: return null
            val nonce = AuthenticationChecker.addChallengeNonce(key, roomID) ?: return null
            return Triple(Base64.encodeBase64String(key.encoded), nonce.first, nonce.second)
        } ?: run {
            Log.info("RelayServer", "Authorization failed - Room ID $roomID does not exist")
            return null
        }
    }

    override fun createPendingRoom(): HttpRequest.RoomCreationStatus? {
        for (i in Short.MIN_VALUE until Short.MAX_VALUE) {
            if (!idToRoom.containsKey(i.toShort()) && !pendingRooms.containsKey(i.toShort())) {
                val cancelTask = object : TimerTask() {
                    override fun run() {
                        pendingRooms.remove(i.toShort())
                        Log.info("RelayServer", "Pending room $i removed")
                    }
                }
                timer.schedule(cancelTask, 20000)

                val key = keyGenerator.generateKey()
                val newPendingRoom = PendingRoom(key, cancelTask)
                pendingRooms[i.toShort()] = newPendingRoom
                val nonce = AuthenticationChecker.addChallengeNonce(key, i.toShort()) ?: return null
                Log.info("RelayServer", "Pending room $i created")
                return HttpRequest.RoomCreationStatus(true, i.toShort(),
                    HttpRequest.AuthorizationResponse(true, Base64.encodeBase64String(key.encoded), nonce.first, nonce.second))
            }
        }

        // If no rooms found (in the highly unlikely situation that all 65535 rooms are taken)
        return null
    }
}