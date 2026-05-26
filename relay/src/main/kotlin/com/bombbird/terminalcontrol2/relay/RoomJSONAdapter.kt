package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.relay.RelayServer.RELAY_TCP_PORT
import com.bombbird.terminalcontrol2.relay.RelayServer.RELAY_UDP_PORT
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import java.util.UUID

private val roomListType = Types.newParameterizedType(List::class.java, Room::class.java)
val moshi: Moshi = Moshi.Builder().add(RoomJSONAdapter).add(UUIDAdapter).build()
val moshiGamesAdapter: JsonAdapter<List<Room>> = moshi.adapter(roomListType)

@JsonClass(generateAdapter = true)
data class ServerRooms(
    val relayId: UUID,
    val servers: List<Room>
)

/** JSON adapter for UUID type */
object UUIDAdapter {
    @ToJson
    fun toJson(uuid: UUID): String = uuid.toString()

    @FromJson
    fun fromJson(json: String): UUID = UUID.fromString(json)
}

/** JSON adapter from [Room] to [JoinGame.MultiplayerGameInfo] for use in searching games */
object RoomJSONAdapter {
    @ToJson
    fun toJson(room: Room): JoinGame.MultiplayerGameInfo {
        return JoinGame.MultiplayerGameInfo(
            Secrets.RELAY_INSTANCES[0].relayAddress, RELAY_UDP_PORT, room.getConnectedPlayerCount(),
            room.maxPlayers, room.mapName, room.id, RELAY_TCP_PORT, 1
        )
    }

    @FromJson
    fun fromJson(str: String): Room? {
        return null
    }
}