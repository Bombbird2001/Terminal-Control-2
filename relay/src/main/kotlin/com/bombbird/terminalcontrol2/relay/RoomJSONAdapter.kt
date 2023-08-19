package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.global.Secrets
import com.bombbird.terminalcontrol2.screens.JoinGame
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/** JSON adapter from [Room] to [JoinGame.MultiplayerGameInfo] for use in searching games */
object RoomJSONAdapter {
    @ToJson
    fun toJson(room: Room): JoinGame.MultiplayerGameInfo {
        return JoinGame.MultiplayerGameInfo(Secrets.RELAY_ADDRESS, room.getConnectedPlayerCount(), room.maxPlayers, room.mapName, room.id)
    }

    @FromJson
    fun fromJson(str: String): Room? {
        return null
    }
}