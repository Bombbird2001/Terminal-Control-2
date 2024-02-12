package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.RELAY_UDP_PORT
import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types

private val roomListType = Types.newParameterizedType(List::class.java, Room::class.java)
val moshi: Moshi = Moshi.Builder().add(RoomJSONAdapter).build()
val moshiGamesAdapter: JsonAdapter<List<Room>> = moshi.adapter(roomListType)

/** JSON adapter from [Room] to [JoinGame.MultiplayerGameInfo] for use in searching games */
object RoomJSONAdapter {
    @ToJson
    fun toJson(room: Room): JoinGame.MultiplayerGameInfo {
        return JoinGame.MultiplayerGameInfo(Secrets.RELAY_ADDRESS, RELAY_UDP_PORT, room.getConnectedPlayerCount(), room.maxPlayers, room.mapName, room.id)
    }

    @FromJson
    fun fromJson(str: String): Room? {
        return null
    }
}