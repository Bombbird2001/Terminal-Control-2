package com.bombbird.terminalcontrol2.networking

import com.esotericsoftware.kryonet.Connection

interface ServerReceive {
    /**
     * Handles this data received on the server
     * @param gs the [GameServer] object
     * @param connection the [Connection] of the player sending this data
     */
    fun handleServerReceive(gs: GameServer, connection: Connection)
}