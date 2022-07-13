package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.ArrayMap
import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.PLAYER_SIZE
import com.esotericsoftware.kryonet.Connection
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
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
        currentIdMap[it] = newId
        sectorUUIDMap[newId] = connectionUUIDMap[it]
        GAME.gameServer?.sendIndividualSectorUpdateTCP(it, newId, newSectorArray)
    }
}

/**
 * Performs a sector swap between the 2 connections
 * @param connection1 the first connection
 * @param currentSector1 the existing assigned sector of the first connection
 * @param connection2 the second connection
 * @param currentSector2 the existing assigned sector of the second connection
 */
fun swapPlayerSectors(connection1: Connection, currentSector1: Byte, connection2: Connection, currentSector2: Byte,
                      connectionSectorMap: GdxArrayMap<Connection, Byte>, sectorUUIDMap: GdxArrayMap<Byte, UUID>) {
    // Ensure both connections' existing sector matches with the map
    if (connectionSectorMap[connection1] != currentSector1) return
    if (connectionSectorMap[connection2] != currentSector2) return
    // Swap the connection to sector mappings
    connectionSectorMap[connection1] = currentSector2
    connectionSectorMap[connection2] = currentSector1
    // Swap the sector to UUID mappings
    val tmp = sectorUUIDMap[currentSector1]
    sectorUUIDMap[currentSector1] = sectorUUIDMap[currentSector2]
    sectorUUIDMap[currentSector2] = tmp
    GAME.gameServer?.apply {
        val sectorArray = sectors[playerNo.get().toByte()].toArray().map { it.getSerialisableObject() }.toTypedArray()
        sendIndividualSectorUpdateTCP(connection1, currentSector2, sectorArray)
        sendIndividualSectorUpdateTCP(connection2, currentSector1, sectorArray)
    }
}

/**
 * Gets the connection that owns the sector ID
 * @param sector the sector to search the connection for
 * @param connectionSectorMap the connection to sector ID map
 * @return the connection that owns the sector currently, or null if none found
 */
fun getConnectionFromSector(sector: Byte, connectionSectorMap: GdxArrayMap<Connection, Byte>): Connection? {
    var connection: Connection? = null
    // Loop through map to find connection corresponding to the requesting sector
    for (entry in ArrayMap.Entries(connectionSectorMap)) {
        if (entry.value == sector) {
            connection = entry.key
            break
        }
    }

    return connection
}
