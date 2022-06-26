package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.PLAYER_SIZE
import com.esotericsoftware.kryonet.Connection
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap

/**
 * Assigns all the sectors to the players connected to the server, and sends them a TCP request informing them of the new
 * sector configuration as well as their assigned sector ID
 * @param connections the list of connections to the server
 * @param currentIdMap the map of connections to their current sector IDs; this function will attempt to keep players
 * assigned to their previously assigned ID if it exists
 */
fun assignSectorsToPlayers(connections: Collection<Connection>, currentIdMap: GdxArrayMap<Connection, Byte>, sectorCount: Byte, sectors: GdxArrayMap<Byte, GdxArray<Sector>>) {
    if (connections.size != sectorCount.toInt())
        return Gdx.app.log("SectorTools", "Connection size ${connections.size} is not equal to sector count $sectorCount")
    val newSectorArray = sectors[sectorCount].toArray().map { it.getSerialisableObject() }.toTypedArray()
    val emptySectors = GdxArray<Byte>(PLAYER_SIZE)
    for (i in 0 until sectorCount) {
        if (!currentIdMap.containsValue(i.byte, false)) emptySectors.add(i.byte)
    }
    connections.forEach {
        val newId = currentIdMap[it]?.let { currId ->
            if (currId >= sectorCount) emptySectors.pop()
            else currId
        } ?: emptySectors.pop()
        GAME.gameServer?.sendIndividualSectorUpdateTCP(it, newId, newSectorArray)
    }
}