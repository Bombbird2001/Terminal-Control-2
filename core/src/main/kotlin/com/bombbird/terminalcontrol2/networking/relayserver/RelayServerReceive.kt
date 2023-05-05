package com.bombbird.terminalcontrol2.networking.relayserver

import com.bombbird.terminalcontrol2.networking.hostserver.PublicServer
import com.bombbird.terminalcontrol2.networking.playerclient.PublicClient
import com.esotericsoftware.kryonet.Connection
import java.util.UUID

/**
 * Interface for the intermediate relay server
 *
 * Supports creation of new room, adding player to room, forwarding of TCP/UDP data from server to client(s), and TCP
 * data from client to server
 */
interface RelayServer {
    /**
     * Method to be called to instruct the server to create a new relay room
     * @param newUUID UUID of the requesting player
     * @param hostConnection connection of the requesting player
     * @param maxPlayers maximum number of players to allow in the room
     * @return ID of the created room, or [Short.MAX_VALUE] if no room can be added or UUID already in an existing room
     */
    fun createNewRoom(newUUID: UUID, hostConnection: Connection, maxPlayers: Byte): Short

    /**
     * Method to be called after [createNewRoom] returns to inform the requesting host of the status of room creation
     * @param createdRoomId the ID of the room created
     * @param hostConnection the [Connection] of the requesting host
     */
    fun sendCreateRoomResult(createdRoomId: Short, hostConnection: Connection)

    /**
     * Method to be called to instruct the server to add the player to the relay room, and notifies the host of the
     * connection
     * @param roomId the ID of the room the player wants to join
     * @param newUUID UUID of the requesting player
     * @param clientConnection connection of the requesting player
     * @return 0 if success, 1 if no room with specified ID exists, 2 if room is full, 3 if UUID already in a room
     */
    fun addPlayerToRoom(roomId: Short, newUUID: UUID, clientConnection: Connection): Byte

    /**
     * Method to be called after [addPlayerToRoom] returns to inform the requesting client of the status of joining
     * @param addResult the result of the join
     * @param clientConnection the [Connection] of the requesting client
     */
    fun sendAddPlayerResult(addResult: Byte, clientConnection: Connection)

    /**
     * Forwards the TCP/UDP object to the client using data in the [ServerToClient] object received
     * @param obj the [ServerToClient] object containing routing and application data
     * @param conn the [Connection] that sent this object (which should be the connection from host "client"
     */
    fun forwardToClient(obj: ServerToClient, conn: Connection)

    /**
     * Forwards the TCP object to the server using data in the [ClientToServer] object received
     * @param obj the [ClientToServer] object containing routing and application data
     * @param conn the [Connection] that sent this object
     */
    fun forwardToServer(obj: ClientToServer, conn: Connection)
}

interface RelayServerReceive {
    /**
     * Handles this data received on the relay server
     * @param rs the [RelayServer] object to handle the request
     * @param conn the [Connection] that sent this object
     */
    fun handleRelayServerReceive(rs: RelayServer, conn: Connection)
}

interface RelayClientReceive {
    /**
     * Handles this data received on the relay client
     * @param client the [PublicClient] object to handle the request
     */
    fun handleRelayClientReceive(client: PublicClient)
}

interface RelayHostReceive {
    /**
     * Handles this data received on the relay host
     * @param host the [PublicServer] object to handle the request
     */
    fun handleRelayHostReceive(host: PublicServer)
}