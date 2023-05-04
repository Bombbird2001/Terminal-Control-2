package com.bombbird.terminalcontrol2.networking.relayserver

import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.publicserver.PublicServer
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.esotericsoftware.kryonet.Connection
import java.util.*

/** Class representing data sent to the relay server to open a new room for a new game */
data class NewGameRequest(val maxPlayers: Byte = 1, val uuid: String? = null): RelayServerReceive {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        if (uuid == null) return
        val res = rs.createNewRoom(UUID.fromString(uuid), conn, maxPlayers)
        rs.sendCreateRoomResult(res, conn)
    }
}

/** Class representing data sent to the host with the new room data for a new game */
data class RoomCreationStatus(val roomId: Short = Short.MAX_VALUE): RelayHostReceive {
    override fun handleRelayHostReceive(host: PublicServer) {
        host.setRoomId(roomId)
    }
}

/**
 * Class representing data sent to the host/client to request for their intentions, and to identify this entity as the
 * relay server
 * */
class RequestRelayAction: RelayHostReceive, RelayClientReceive {
    override fun handleRelayClientReceive(rs: RadarScreen) {
        rs.requestToJoinRoom()
    }

    override fun handleRelayHostReceive(host: PublicServer) {
        host.requestGameCreation()
    }
}

/** Class representing data sent to the relay server to join an existing room */
data class JoinGameRequest(val roomId: Short = Short.MAX_VALUE, val uuid: String? = null): RelayServerReceive {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        if (uuid == null) return
        rs.addPlayerToRoom(roomId, UUID.fromString(uuid), conn)
    }
}

/** Class representing data sent to the host when a player requests to join the game */
data class PlayerConnect(val uuid: String? = null): RelayHostReceive {
    override fun handleRelayHostReceive(host: PublicServer) {
        if (uuid == null) return
        host.onConnect(ConnectionMeta(UUID.fromString(uuid)))
    }
}

/** Class representing data sent to the client after the relay server processes the [PlayerConnect] request */
data class PlayerConnectStatus(val addStatus: Byte = 0): RelayClientReceive {
    override fun handleRelayClientReceive(rs: RadarScreen) {
        if (addStatus > 0) GAME.quitCurrentGame()
    }
}

/** Class representing data sent by a client to the server. */
class ClientToServer(val roomId: Short = Short.MAX_VALUE, val data: ByteArray = byteArrayOf()): RelayServerReceive, RelayHostReceive {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        rs.forwardToServer(this, conn)
    }

    override fun handleRelayHostReceive(host: PublicServer) {
        TODO("Not yet implemented")
    }
}

/** Class representing data sent by the server to client(s). If [uuid] is null, data is sent to all connected clients */
class ServerToClient(val roomId: Short = Short.MAX_VALUE, val uuid: String? = null, val data: ByteArray = byteArrayOf(),
                     val tcp: Boolean = false): RelayServerReceive, RelayClientReceive {
    override fun handleRelayServerReceive(rs: RelayServer, conn: Connection) {
        // UDP/TCP will be handled by the method using the TCP flag in this class
        rs.forwardToClient(this, conn)
    }

    override fun handleRelayClientReceive(rs: RadarScreen) {
        TODO("Not yet implemented")
    }
}