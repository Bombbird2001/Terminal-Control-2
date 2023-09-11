package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.networking.encryption.AESGCMDecrypter
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMEncryptor
import com.bombbird.terminalcontrol2.networking.encryption.EncryptedData
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.networking.relayserver.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.esotericsoftware.kryonet.Connection
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

/**
 * Class encapsulating an instance of a game room on the relay server, containing a host and associated
 * client connections
 * @param id ID of the room
 * @param maxPlayers maximum number of players allowed in the room
 * @param hostConnection the connection to the host
 * @param mapName the name of the airport map being hosted
 * @param roomKey the symmetric key used to encrypt data sent by the room
 * @param encryptor the encryptor used to encrypt data sent by the room, must be the same instance passed from the
 * pending room
 * @param hostDecrypter the decrypter used to decrypt data sent by the host, must be the same instance passed from
 * the pending room
 */
class Room(val id: Short, val maxPlayers: Byte, private val hostConnection: Connection?, val mapName: String,
           private val roomKey: SecretKey, private val encryptor: AESGCMEncryptor,
           private val hostDecrypter: AESGCMDecrypter) {
    private val pendingUUIDToDecrypter = ConcurrentHashMap<UUID, AESGCMDecrypter>(maxPlayers.toInt())
    private val connectionToDecrypter = ConcurrentHashMap<Connection, AESGCMDecrypter>(maxPlayers.toInt())
    private val connectedPlayers = ConcurrentHashMap<UUID, Connection>(maxPlayers.toInt())
    private val authorizedUUIDs = ConcurrentHashMap<UUID, TimerTask>(maxPlayers.toInt())

    init {
        encryptor.setKey(roomKey)
        hostConnection?.let {
            connectionToDecrypter[it] = hostDecrypter
        }
    }

    /**
     * Removes a player and their associated connection from the room
     * @param uuid UUID of the player who disconnected
     */
    fun removePlayer(uuid: UUID) {
        val encrypted = encryptIfNeeded(PlayerDisconnect(uuid.toString())) ?: return
        pendingUUIDToDecrypter.remove(uuid)
        connectedPlayers.remove(uuid)?.let { connectionToDecrypter.remove(it) }
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
        val decrypter = pendingUUIDToDecrypter.remove(uuid) ?: return false
        connectedPlayers[uuid] = conn
        connectionToDecrypter[conn] = decrypter
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
     * @return a Pair, first being the secret symmetric key used by the room to encrypt traffic, second being the
     * secret symmetric used by the client to encrypt traffic if the UUID was successfully added, else null (if UUID
     * already exists, or room is full)
     */
    fun authorizeUUID(uuid: UUID): Pair<SecretKey, SecretKey>? {
        if (isFull()) {
            FileLog.info("Room", "Room $id is full")
            return null
        }
        // Player cannot be in multiple public games at once
        if (RelayServer.uuidToRoom.containsKey(uuid)) {
            FileLog.info("Room", "Player $uuid is already in a room")
            return null
        }
        val cancelTask = object : TimerTask() {
            override fun run() {
                authorizedUUIDs.remove(uuid)
                FileLog.info("Room", "Pending player $uuid removed")
            }
        }
        RelayServer.timer.schedule(cancelTask, 10000)
        if (authorizedUUIDs.putIfAbsent(uuid, cancelTask) == null) {
            val clientKey = RelayServer.keyGenerator.generateKey()
            pendingUUIDToDecrypter[uuid] = AESGCMDecrypter(RelayServer::fromSerializedBytes).apply { setKey(clientKey) }
            FileLog.info("Room", "Pending player $uuid added")
            return Pair(roomKey, clientKey)
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
     * Performs decryption on the received data using the connection's key
     * @param data ciphertext to be decrypted
     * @return the decrypted object
     */
    fun decryptForRoom(connection: Connection, data: EncryptedData): Any? {
        return connectionToDecrypter[connection]?.decrypt(data)
    }

    /**
     * Performs decryption on the received data using the pending UUID's key
     * @param data ciphertext to be decrypted
     * @return the decrypted object
     */
    fun decryptForPendingUUID(uuid: UUID, data: EncryptedData): Any? {
        return pendingUUIDToDecrypter[uuid]?.decrypt(data)
    }
}