package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.PLAYER_SIZE
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.esotericsoftware.minlog.Log
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
fun assignSectorsToPlayers(connections: Collection<ConnectionMeta>, currentIdMap: GdxArrayMap<UUID, Byte>,
                           sectorUUIDMap: GdxArrayMap<Byte, UUID>,
                           sectorCount: Byte, sectors: GdxArrayMap<Byte, GdxArray<Sector>>) {
    if (connections.size != sectorCount.toInt())
        return Log.info("SectorTools", "Connection size ${connections.size} is not equal to sector count $sectorCount")
    val newSectorArray = sectors[sectorCount].toArray().map { it.getSerialisableObject() }.toTypedArray()
    val emptySectors = GdxArray<Byte>(PLAYER_SIZE)
    // If the ID map no longer contains the sector, add it to the empty sector array
    for (i in 0 until sectorCount) {
        if (!currentIdMap.containsValue(i.byte, false)) emptySectors.add(i.byte)
    }
    connections.forEach {
        val newId = currentIdMap[it.uuid]?.let { currId ->
            // Check existing mappings: If their sector ID no longer exists, give them a new ID from the empty sectors
            if (currId >= sectorCount) {
                sectorUUIDMap.removeKey(currId)
                emptySectors.pop()
            } else currId
        } ?: emptySectors.pop() // If the connection has not been mapped to an ID, give them a new ID from the empty sectors
        // Update the sector to connection and UUID map
        currentIdMap[it.uuid] = newId
        sectorUUIDMap[newId] = it.uuid
        GAME.gameServer?.sendIndividualSectorUpdateTCP(it.uuid, newId, newSectorArray)
    }
}

/**
 * Performs a sector swap between the 2 connections
 * @param player1 the first player
 * @param currentSector1 the existing assigned sector of the first connection
 * @param player2 the second player
 * @param currentSector2 the existing assigned sector of the second connection
 * @param connectionSectorMap map from [ConnectionMeta] to sector ID
 * @param sectorUUIDMap map from sector ID to UUID
 */
fun swapPlayerSectors(player1: UUID, currentSector1: Byte, player2: UUID, currentSector2: Byte,
                      connectionSectorMap: GdxArrayMap<UUID, Byte>, sectorUUIDMap: GdxArrayMap<Byte, UUID>) {
    // Ensure both connections' existing sector matches with the map
    if (connectionSectorMap[player1] != currentSector1) return
    if (connectionSectorMap[player2] != currentSector2) return
    // Swap the connection to sector mappings
    connectionSectorMap[player1] = currentSector2
    connectionSectorMap[player2] = currentSector1
    // Swap the sector to UUID mappings
    val tmp = sectorUUIDMap[currentSector1]
    sectorUUIDMap[currentSector1] = sectorUUIDMap[currentSector2]
    sectorUUIDMap[currentSector2] = tmp
    GAME.gameServer?.apply {
        val sectorArray = sectors[playerNo.get().toByte()].toArray().map { it.getSerialisableObject() }.toTypedArray()
        sendIndividualSectorUpdateTCP(player1, currentSector2, sectorArray)
        sendIndividualSectorUpdateTCP(player2, currentSector1, sectorArray)
    }
}
