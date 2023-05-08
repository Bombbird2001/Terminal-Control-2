package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.RELAY_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.dataclasses.ConnectionError
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.bombbird.terminalcontrol2.networking.relayserver.*
import com.bombbird.terminalcontrol2.networking.relayserver.RelayServer
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.kryonet.Server
import com.esotericsoftware.minlog.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [RelayServer] interface with Kryo server providing the underlying networking.
 *
 * When a disconnection occurs at the networking server level, the player is automatically removed from the room, and if
 * the player is the host, the room is also automatically closed and all connections to it closed
 */
object RelayServer: RelayServer {
    private val server = Server(RELAY_BUFFER_SIZE, RELAY_BUFFER_SIZE).apply {
        addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                (obj as? RelayServerReceive)?.apply {
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

    @JvmStatic
    fun main(args: Array<String>) {
        println("Hello world from RelayServer!")

        // Shut down server before terminating program
        Runtime.getRuntime().addShutdownHook(Thread {
            println("Server shutdown signal received")
            server.stop()
            AvailableGamesEndpoint.stop()
        })

        registerClassesToKryo(server.kryo)
        server.bind(57773, 57779)
        server.start()

        AvailableGamesEndpoint.launch(this)
    }

    override fun createNewRoom(
        newUUID: UUID,
        hostConnection: Connection,
        maxPlayers: Byte,
        mapName: String
    ): Short {
        if (hostUUIDs.contains(newUUID)) return Short.MAX_VALUE

        for (i in Short.MIN_VALUE until Short.MAX_VALUE) {
            if (!idToRoom.containsKey(i.toShort())) {
                connectionToRoomUUID[hostConnection] = Pair(i.toShort(), newUUID)
                idToRoom[i.toShort()] = Room(i.toShort(), maxPlayers, hostConnection, mapName)
                hostConnectionToRoomMap[hostConnection] = i.toShort()
                println("Room $i created - $mapName")
                return i.toShort()
            }
        }

        // If no rooms found (in the highly unlikely situation that all 65535 rooms are taken)
        return Short.MAX_VALUE
    }

    override fun sendCreateRoomResult(createdRoomId: Short, hostConnection: Connection) {
        hostConnection.sendTCP(RoomCreationStatus(createdRoomId))
    }

    override fun addPlayerToRoom(
        roomId: Short,
        newUUID: UUID,
        clientConnection: Connection
    ): Byte {
        val room = idToRoom[roomId] ?: return 1 // No such room
        if (room.isFull()) return 2 // Room is full
        if (uuidToRoom.containsKey(newUUID)) return 3 // UUID already in a room
        room.addPlayer(newUUID, clientConnection)
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
        // Verify connection is host
        if (hostConnectionToRoomMap[conn] != roomId) {
            Log.info("RelayServer", "Forward to client failed - Connection UUID is not host of room")
            return
        }

        val targetUUID = obj.uuid
        val roomObj = idToRoom[roomId] ?: run {
            Log.info("RelayServer", "Forward to client failed - Room ID $roomId not found")
            return
        }
        if (targetUUID == null) roomObj.forwardFromHostToAllClientConnections(obj, conn)
        else roomObj.forwardFromHostToAClient(obj, conn, UUID.fromString(targetUUID))
    }

    override fun forwardToServer(obj: ClientToServer, conn: Connection) {
        val roomId = obj.roomId
        // Verify roomId matches connection
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
     * Inner class encapsulating an instance of a game room on the relay server, containing a host and associated
     * client connections
     * @param id ID of the room
     * @param maxPlayers maximum number of players allowed in the room
     * @param hostConnection the connection to the host
     * @param mapName the name of the airport map being hosted
     */
    class Room(val id: Short, val maxPlayers: Byte, private val hostConnection: Connection?, val mapName: String) {
        private val connectedPlayers = ConcurrentHashMap<UUID, Connection>(maxPlayers.toInt())

        /**
         * Removes a player and their associated connection from the room
         * @param uuid UUID of the player who disconnected
         */
        fun removePlayer(uuid: UUID) {
            connectedPlayers.remove(uuid)
            hostConnection?.sendTCP(PlayerDisconnect(uuid.toString()))
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
        fun addPlayer(uuid: UUID, conn: Connection) {
            if (connectedPlayers.containsKey(uuid)) return
            connectedPlayers[uuid] = conn
            hostConnection?.sendTCP(PlayerConnect(uuid.toString()))
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
    }

    /**
     * Gets all available games that other players can join as of now
     * @return a list of all Room objects that are open and are not full yet
     */
    fun getAvailableGames(): List<Room> {
        return idToRoom.filter { entry -> !entry.value.isFull() }.map { entry -> entry.value }
    }
}