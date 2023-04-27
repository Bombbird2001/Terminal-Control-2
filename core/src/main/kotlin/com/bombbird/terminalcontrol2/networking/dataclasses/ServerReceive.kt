package com.bombbird.terminalcontrol2.networking.dataclasses

import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.GameServer

interface ServerReceive {
    /**
     * Handles this data received on the server
     * @param gs the [GameServer] object
     * @param connection the [ConnectionMeta] of the player sending this data
     */
    fun handleServerReceive(gs: GameServer, connection: ConnectionMeta)
}