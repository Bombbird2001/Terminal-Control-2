package com.bombbird.terminalcontrol2.networking.relayserver

import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.networking.hostserver.PublicServer
import com.bombbird.terminalcontrol2.networking.playerclient.PublicClient
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.minlog.Log
import java.util.*

/** Class representing data sent to the relay server to open a new room for a new game */
data class NewGameRequest(val roomId: Short = Short.MAX_VALUE, val maxPlayers: Byte = 1, val mapName: String = "",
                          val uuid: String? = null): RelayServerReceive, NeedsEncryption {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        if (uuid == null) return
        if (!rs.createNewRoom(roomId, UUID.fromString(uuid), conn, maxPlayers, mapName)) conn.close()
    }
}

/**
 * Class representing data sent to the host/client to request for their intentions, and to identify this entity as the
 * relay server
 * */
class RequestRelayAction: RelayHostReceive, RelayClientReceive, NeedsEncryption {
    override fun handleRelayClientReceive(client: PublicClient) {
        client.requestToJoinRoom()
    }

    override fun handleRelayHostReceive(host: PublicServer) {
        host.requestGameCreation()
    }
}

/** Class representing data sent to the relay server to join an existing room */
data class JoinGameRequest(val roomId: Short = Short.MAX_VALUE, val uuid: String? = null): RelayServerReceive, NeedsEncryption {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        if (uuid == null) return
        val res = rs.addPlayerToRoom(roomId, UUID.fromString(uuid), conn)
        rs.sendAddPlayerResult(res, conn)
    }
}

/** Class representing data sent to the host when a player requests to join the game */
data class PlayerConnect(val uuid: String? = null): RelayHostReceive, NeedsEncryption {
    override fun handleRelayHostReceive(host: PublicServer) {
        if (uuid == null) return
        host.onConnect(UUID.fromString(uuid))
        Log.info("PublicServer", "Player $uuid connected")
    }
}

/** Class representing data sent to the host when a player disconnects from the game */
data class PlayerDisconnect(val uuid: String? = null): RelayHostReceive, NeedsEncryption {
    override fun handleRelayHostReceive(host: PublicServer) {
        if (uuid == null) return
        host.onDisconnect(UUID.fromString(uuid))
        Log.info("PublicServer", "Player $uuid disconnected")
    }
}

/** Class representing data sent by a client to the server. */
class ClientToServer(val roomId: Short = Short.MAX_VALUE, val sendingUUID: String? = null,
                     val data: ByteArray = byteArrayOf()): RelayServerReceive, RelayHostReceive, NeedsEncryption {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        rs.forwardToServer(this, conn)
    }

    override fun handleRelayHostReceive(host: PublicServer) {
        val uuid = UUID.fromString(sendingUUID ?: return)
        host.decodeRelayMessageObject(data, uuid)
    }
}

/** Class representing data sent by the server to client(s). If [uuid] is null, data is sent to all connected clients */
class ServerToClient(val roomId: Short = Short.MAX_VALUE, val uuid: String? = null, val data: ByteArray = byteArrayOf(),
                     val tcp: Boolean = false): RelayServerReceive, RelayClientReceive, NeedsEncryption {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        // UDP/TCP will be handled by the method using the TCP flag in this class
        rs.forwardToClient(this, conn)
    }

    override fun handleRelayClientReceive(client: PublicClient) {
        client.decodeRelayMessageObject(data)
    }
}

/** Class representing data sent by the host to all clients via UDP. Traffic is not encrypted. */
class ServerToAllClientsUDP(val data: ByteArray = byteArrayOf()): RelayServerReceive, RelayClientReceive {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        // Forward to all clients using UDP
        rs.forwardToAllClientsUDP(this, conn)
    }

    override fun handleRelayClientReceive(client: PublicClient) {
        client.decodeRelayMessageObject(data)
    }
}