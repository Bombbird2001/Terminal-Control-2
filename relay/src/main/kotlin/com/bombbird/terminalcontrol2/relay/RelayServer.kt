package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.networking.registerRelayClassesToRelayServerKryo
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
    private val server = Server().apply {
        addListener(object : Listener {
            /**
             * Called when the server receives a TCP/UDP request from a client
             * @param connection the incoming connection
             * @param obj the serialised network object
             */
            override fun received(connection: Connection, obj: Any?) {
                // Check if is initial connection response
                if (obj is RelayServerReceive) {
                    obj.handleRelayServerReceive(this@RelayServer, connection)
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
                // If connection is from a host
                val hostRoom = hostConnectionToRoomMap[connection]
                if (hostRoom != null) {
                    val connections = roomToConnectionsMap[hostRoom]?.first ?: return
                    for (conn in connections.values) {
                        if (conn != connection) conn.close()
                    }
                    roomToConnectionsMap.remove(hostRoom)
                    hostConnectionToRoomMap.remove(connection)
                    roomToHostConnectionMap.remove(hostRoom)
                    hostUUIDs.remove(connectionToRoomUUID[connection]?.second)
                } else  {
                    val uuidRoom = connectionToRoomUUID[connection] ?: return
                    val uuid = uuidRoom.second
                    val room = uuidRoom.first
                    roomToConnectionsMap[room]?.first?.remove(uuid)
                    connectionToRoomUUID.remove(connection)
                    uuidToRoom.remove(uuid)
                }
            }
        })
    }

    /** Map of room ID to map of UUID to connection, excluding the 2nd connection from a host */
    private val roomToConnectionsMap = ConcurrentHashMap<Short, Pair<ConcurrentHashMap<UUID, Connection>, Byte>>(32)

    /** Maps of connection to room ID and vice versa, only including the 2nd host connection */
    private val hostConnectionToRoomMap = ConcurrentHashMap<Connection, Short>(32)
    private val roomToHostConnectionMap = ConcurrentHashMap<Short, Connection>(32)

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
        })

        registerRelayClassesToRelayServerKryo(server.kryo)
        server.bind(57773, 57779)
        server.start()
    }

    override fun createNewRoom(
        newUUID: UUID,
        hostConnection: Connection,
        maxPlayers: Byte
    ): Short {
        if (hostUUIDs.contains(newUUID)) return Short.MAX_VALUE

        for (i in Short.MIN_VALUE until Short.MAX_VALUE) {
            if (!roomToConnectionsMap.containsKey(i.toShort())) {
                connectionToRoomUUID[hostConnection] = Pair(i.toShort(), newUUID)
                roomToConnectionsMap[i.toShort()] = Pair(ConcurrentHashMap(maxPlayers.toInt()), maxPlayers)
                hostConnectionToRoomMap[hostConnection] = i.toShort()
                roomToHostConnectionMap[i.toShort()] = hostConnection
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
        val room = roomToConnectionsMap[roomId] ?: return 1 // No such room
        if (room.second <= room.first.size) return 2 // Room is full
        if (uuidToRoom.containsKey(newUUID)) return 3 // UUID already in a room
        room.first[newUUID] = clientConnection
        connectionToRoomUUID[clientConnection] = Pair(roomId, newUUID)
        uuidToRoom[newUUID] = roomId
        roomToHostConnectionMap[roomId]?.sendTCP(PlayerConnect(newUUID.toString()))
        return 0
    }

    override fun sendAddPlayerResult(addResult: Byte, clientConnection: Connection) {
        clientConnection.sendTCP(PlayerConnectStatus(addResult))
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
        if (targetUUID == null) {
            for (connectedPlayer in roomToConnectionsMap[roomId]?.first?.values ?: return) {
                if (conn == connectedPlayer) continue // Forward to all clients except host
                if (obj.tcp) connectedPlayer.sendTCP(obj)
                else connectedPlayer.sendUDP(obj)
            }
        } else {
            // Forward to only 1 client
            val targetConnection = roomToConnectionsMap[roomId]?.first?.get(UUID.fromString(targetUUID)) ?: return
            if (conn == targetConnection) return // If somehow sent to host, ignore it
            if (obj.tcp) targetConnection.sendTCP(obj)
            else targetConnection.sendUDP(obj)
        }
    }

    override fun forwardToServer(obj: ClientToServer, conn: Connection) {
        val roomId = obj.roomId
        // Verify roomId matches connection
        if (connectionToRoomUUID[conn]?.first != roomId) {
            Log.info("RelayServer", "Forward to server failed - Connection does not match room ID")
            return
        }

        val hostConn = roomToHostConnectionMap[roomId] ?: return
        if (conn == hostConn) return // Host should not be sending to itself
        hostConn.sendTCP(obj)
    }
}