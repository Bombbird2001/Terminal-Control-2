package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.PLAYER_SIZE
import com.esotericsoftware.kryonet.Connection
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.util.UUID

/**
 * Assigns all the sectors to the players connected to the server, and sends them a TCP request informing them of the new
 * sector configuration as well as their assigned sector ID
 * @param connections the list of connections to the server
 * @param currentIdMap the map of connections to their current sector IDs; this function will attempt to keep players
 * assigned to their previously assigned ID if it exists
 * @param sectorCount the number of sectors assignable
 * @param sectors mapping of each sector configuration to the number of players
 */
fun assignSectorsToPlayers(connections: Collection<Connection>, currentIdMap: GdxArrayMap<Connection, Byte>,
                           connectionUUIDMap: GdxArrayMap<Connection, UUID>, sectorUUIDMap: GdxArrayMap<Byte, UUID>,
                           sectorCount: Byte, sectors: GdxArrayMap<Byte, GdxArray<Sector>>) {
    if (connections.size != sectorCount.toInt())
        return Gdx.app.log("SectorTools", "Connection size ${connections.size} is not equal to sector count $sectorCount")
    val newSectorArray = sectors[sectorCount].toArray().map { it.getSerialisableObject() }.toTypedArray()
    val emptySectors = GdxArray<Byte>(PLAYER_SIZE)
    // If the ID map no longer contains the sector, add it to the empty sector array
    for (i in 0 until sectorCount) {
        if (!currentIdMap.containsValue(i.byte, false)) emptySectors.add(i.byte)
    }
    connections.forEach {
        val newId = currentIdMap[it]?.let { currId ->
            // Check existing mappings: If their sector ID no longer exists, give them a new ID from the empty sectors
            if (currId >= sectorCount) {
                sectorUUIDMap.removeKey(currId)
                emptySectors.pop()
            } else currId
        } ?: emptySectors.pop() // If the connection has not been mapped to an ID, give them a new ID from the empty sectors
        // Update the sector to connection and UUID map
        currentIdMap.put(it, newId)
        sectorUUIDMap.put(newId, connectionUUIDMap[it])
        GAME.gameServer?.sendIndividualSectorUpdateTCP(it, newId, newSectorArray)
    }
}