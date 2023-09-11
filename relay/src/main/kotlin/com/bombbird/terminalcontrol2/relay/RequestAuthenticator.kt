package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMEncryptor
import com.bombbird.terminalcontrol2.networking.encryption.EncryptedData
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.networking.relayserver.RelayAuthorization
import com.bombbird.terminalcontrol2.networking.relayserver.RelayChallenge
import com.bombbird.terminalcontrol2.networking.relayserver.RelayNonce
import com.bombbird.terminalcontrol2.networking.relayserver.RequestRelayAction
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.esotericsoftware.kryonet.Connection
import org.apache.commons.codec.binary.Base64
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

/**
 * Object responsible for handling the initial HTTPS request for creating or joining a game, as well as the initial
 * connection requests made to the relay server
 */
object RequestAuthenticator {
    private val encryptor = AESGCMEncryptor(RelayServer::getSerialisedBytes)

    /**
     * Stores the random encrypted nonce mapped to room ID and UUID which is used to verify the challenge response
     * sent by the client upon encryption
     */
    private val randomNonceChallenge = ConcurrentHashMap<RelayChallenge, Pair<Short, UUID?>>(32)

    /**
     * Stores connections which have been authorised and pending relay action, mapping to the ID of the room for
     * subsequent decryption
     */
    private val authorisedConnectionsPendingAction = ConcurrentHashMap<Connection, Pair<Short, UUID?>>(32)

    /**
     * Handles the relay challenge received from a connecting client
     * @param connection the connection responding to the challenge
     * @param challenge the challenge response object
     */
    fun handleRelayChallenge(connection: Connection, challenge: RelayChallenge) {
        if (!authenticateConnection(connection, challenge)) return connection.close()
        val encryptedRequest = encryptForPendingConn(connection, RequestRelayAction()) ?: return
        connection.sendTCP(encryptedRequest)
    }

    /**
     * Attempts to authenticate the connection based on the sent challenge data
     * @param conn the connection to authenticate
     * @param relayChallenge the [RelayChallenge] object with challenge data
     * @return true if authentication succeeds, else false
     */
    private fun authenticateConnection(conn: Connection, relayChallenge: RelayChallenge): Boolean {
        if (RelayServer.connectionToRoomUUID.containsKey(conn)) return false
        val roomId = randomNonceChallenge[relayChallenge] ?: return false
        authorisedConnectionsPendingAction[conn] = roomId
        return true
    }

    /**
     * Encrypts the object based on the authorised connection's room, or pending room if the room is not yet open,
     * returning the object encrypted with the room key
     * @param conn the connection to encrypt for
     * @param obj the object to encrypt
     */
    fun encryptForPendingConn(conn: Connection, obj: NeedsEncryption): Any? {
        // Try to find an open room first
        val id = authorisedConnectionsPendingAction[conn] ?: return null
        RelayServer.idToRoom[id.first]?.let {
            return it.encryptIfNeeded(obj)
        } ?: RelayServer.pendingRooms[id.first]?.let {
            // Otherwise, find the pending room
            return it.encryptIfNeeded(obj)
        }

        return null
    }

    /**
     * Performs decryption on the received data using the pending connection's stored key
     * @param conn the connection to decrypt for
     * @param data ciphertext to be decrypted
     * @return the decrypted object
     */
    fun decryptForPendingConn(conn: Connection, data: EncryptedData): Any? {
        val roomAndUUID = authorisedConnectionsPendingAction[conn] ?: return null
        val existingRoom = RelayServer.idToRoom[roomAndUUID.first]
        val uuid = roomAndUUID.second
        // First check if the room already exists and UUID exists for the joining player
        if (existingRoom != null && uuid != null) {
            return existingRoom.decryptForPendingUUID(uuid, data)
        }
        // Otherwise, we assume it's a room creation request
        return RelayServer.pendingRooms[roomAndUUID.first]?.decryptFromHost(data)
    }

    /**
     * Creates and adds a new challenge nonce to validate new incoming connections
     * @param clientKey the key used by the joining client for encryption
     * @param roomId the ID of the room the connection is authorized to
     * @param playerUUID the UUID of the player who wants to connect - null if the player is creating a room
     * @return a pair consisting of the random nonce and IV (in Base64) to be encrypted and sent by the client
     */
    private fun addChallengeNonce(clientKey: SecretKey, roomId: Short, playerUUID: UUID?): Pair<String, String>? {
        val nonceString = UUID.randomUUID().toString()
        encryptor.setKey(clientKey)
        val encrypted = encryptor.encrypt(RelayNonce(nonceString)) ?: return null
        val ivBase64 = Base64.encodeBase64String(encrypted.iv)
        randomNonceChallenge[RelayChallenge(encrypted.ciphertext)] = Pair(roomId, playerUUID)
        return Pair(nonceString, ivBase64)
    }

    /**
     * Removes the connection from the pending action map when the connection has been added to the room
     * @param conn the connection to remove
     */
    fun connAddedToRoom(conn: Connection) {
        authorisedConnectionsPendingAction.remove(conn)
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
     * Attempts to authorize the UUID to the room, and returns the challenge parameters to be sent on initial
     * connection
     */
    fun authorizeUUIDToRoom(roomID: Short, uuid: UUID): RelayAuthorization.RoomAuthorizationResult? {
        RelayServer.idToRoom[roomID]?.let {
            val key = it.authorizeUUID(uuid) ?: return null
            val nonce = addChallengeNonce(key.second, roomID, uuid) ?: return null
            return RelayAuthorization.RoomAuthorizationResult(Base64.encodeBase64String(key.first.encoded),
                Base64.encodeBase64String(key.second.encoded), nonce.first, nonce.second)
        } ?: run {
            FileLog.info("RequestAuthenticator", "Authorization failed - Room ID $roomID does not exist")
            return null
        }
    }

    /** Attempts to create a pending room, and returns the  */
    fun createPendingRoom(): HttpRequest.RoomCreationStatus? {
        for (i in Short.MIN_VALUE until Short.MAX_VALUE) {
            if (!RelayServer.idToRoom.containsKey(i.toShort()) && !RelayServer.pendingRooms.containsKey(i.toShort())) {
                val cancelTask = object : TimerTask() {
                    override fun run() {
                        RelayServer.pendingRooms.remove(i.toShort())
                        FileLog.info("RequestAuthenticator", "Pending room $i removed")
                    }
                }
                RelayServer.timer.schedule(cancelTask, 20000)

                val roomKey = RelayServer.keyGenerator.generateKey()
                val hostKey = RelayServer.keyGenerator.generateKey()
                val newPendingRoom = PendingRoom(roomKey, hostKey, cancelTask)
                RelayServer.pendingRooms[i.toShort()] = newPendingRoom
                val nonce = addChallengeNonce(hostKey, i.toShort(), null) ?: return null
                FileLog.info("RequestAuthenticator", "Pending room $i created")
                return HttpRequest.RoomCreationStatus(true, i.toShort(),
                    HttpRequest.AuthorizationResponse(true, Base64.encodeBase64String(roomKey.encoded),
                        Base64.encodeBase64String(hostKey.encoded), nonce.first, nonce.second))
            }
        }

        // If no rooms found (in the highly unlikely situation that all 65535 rooms are taken)
        return null
    }
}