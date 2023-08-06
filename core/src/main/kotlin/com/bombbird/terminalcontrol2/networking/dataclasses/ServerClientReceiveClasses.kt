package com.bombbird.terminalcontrol2.networking.dataclasses

import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.calculateRouteSegments
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.systems.RenderingSystemClient
import com.bombbird.terminalcontrol2.ui.panes.CommsPane
import com.bombbird.terminalcontrol2.ui.datatag.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagText
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.ashley.getSystem
import ktx.ashley.plusAssign

/** Class representing control state data sent when the aircraft command state is updated (either through player command, or due to leg being reached) */
data class AircraftControlStateUpdateData(val callsign: String = "", val primaryName: String = "",
                                          val route: Route.SerialisedRoute = Route.SerialisedRoute(),
                                          val hiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                                          val vectorHdg: Short? = null, val vectorTurnDir: Byte? = null,
                                          val clearedAlt: Int = 0, val expedite: Boolean = false,
                                          val clearedIas: Short = 0, val minIas: Short = 0,
                                          val maxIas: Short = 0, val optimalIas: Short = 0,
                                          val clearedApp: String? = null, val clearedTrans: String? = null,
                                          val sendingSector: Byte = SECTOR_GAMESERVER): ServerReceive, ClientReceive, NeedsEncryption {
    companion object {
        const val SECTOR_GAMESERVER: Byte = -5
    }

    override fun handleServerReceive(gs: GameServer, connection: ConnectionMeta) {
        gs.postRunnableAfterEngineUpdate {
            gs.aircraft[callsign]?.entity?.let {
                // Validate the sender
                if (it[Controllable.mapper]?.sectorId != sendingSector) return@postRunnableAfterEngineUpdate
                addNewClearanceToPendingClearances(it, this, connection.returnTripTime)
            }
        }
    }

    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.let { aircraft ->
            val route = Route.fromSerialisedObject(route)
            aircraft.entity += ClearanceAct(
                ClearanceState(primaryName, route, Route.fromSerialisedObject(hiddenLegs),
                    vectorHdg, vectorTurnDir, clearedAlt, expedite,
                    clearedIas, minIas, maxIas, optimalIas, clearedApp, clearedTrans).ActingClearance())
            aircraft.entity[Datatag.mapper]?.let { updateDatatagText(it, getNewDatatagLabelText(aircraft.entity, it.minimised)) }
            if (rs.selectedAircraft == aircraft) rs.uiPane.updateSelectedAircraft(aircraft)
            getEngine(true).getSystem<RenderingSystemClient>().updateWaypointDisplay(rs.selectedAircraft)
            aircraft.entity[RouteSegment.mapper]?.segments?.let { segments ->
                calculateRouteSegments(route, segments, null)
            }
        }
    }
}

/** Class representing client request to swap sectors */
data class SectorSwapRequest(private val requestedSector: Byte? = null, private val sendingSector: Byte = -1): ServerReceive,
    ClientReceive, NeedsEncryption {
    override fun handleServerReceive(gs: GameServer, connection: ConnectionMeta) {
        // Validate the sender
        if (gs.sectorMap[connection.uuid] != sendingSector) return
        // Validate requested sector (either requested sector is null or it must be in the sector map)
        if (requestedSector != null && !gs.sectorUUIDMap.containsKey(requestedSector)) return
        // Clear all existing requests from the sending sector
        for (i in gs.sectorSwapRequests.size - 1 downTo 0) {
            if (gs.sectorSwapRequests[i].second == sendingSector && gs.sectorSwapRequests[i].first != requestedSector) {
                gs.networkServer.sendTCPToConnection(connection.uuid, SectorSwapRequest(null, sendingSector))
                gs.sectorSwapRequests.removeIndex(i)
            }
        }
        // If requested sector is null, return
        if (requestedSector == null) return
        // Check swap array for existing matching request
        val index = gs.sectorSwapRequests.indexOf(Pair(sendingSector, requestedSector), false)
        val requestedUUID = gs.sectorUUIDMap[requestedSector]
        if (index > -1) {
            // If connection that originally requested swap is not present for some reason, return
            swapPlayerSectors(requestedUUID, requestedSector, connection.uuid, sendingSector, gs.sectorMap, gs.sectorUUIDMap)
            gs.sectorSwapRequests.removeIndex(index)
            // Forward all existing swap requests to the 2 sectors (and remove any from, though there shouldn't be any at all)
            for (i in gs.sectorSwapRequests.size - 1 downTo 0) {
                gs.sectorSwapRequests[i]?.let {
                    // Request originally bound for player who initiated the swap request
                    if (it.first == requestedSector)
                        gs.networkServer.sendTCPToConnection(connection.uuid, SectorSwapRequest(it.first, it.second))
                    // Request originally bound for player who accepted the swap request
                    if (it.first == sendingSector)
                        gs.networkServer.sendTCPToConnection(requestedUUID, SectorSwapRequest(it.first, it.second))
                    // Request from one of the players (there shouldn't be any, but just in case)
                    if (it.second == requestedSector || it.second == sendingSector)
                        gs.sectorSwapRequests.removeIndex(i)
                }
            }
            return
        }
        // Not found - store new request in array
        gs.sectorSwapRequests.add(Pair(requestedSector, sendingSector))
        // Send the request notification to target sector
        gs.networkServer.sendTCPToConnection(requestedUUID, this)
    }

    override fun handleClientReceive(rs: RadarScreen) {
        if (requestedSector == null) {
            // Requesting sector cancelled, remove swap request
            rs.incomingSwapRequests.removeValue(sendingSector, false)
            rs.uiPane.commsPane.addMessage("Sector ${sendingSector + 1} has cancelled the sector swap request", CommsPane.ALERT)
        } else {
            // Check the request has not been received before
            if (rs.incomingSwapRequests.contains(sendingSector, false)) return
            rs.incomingSwapRequests.add(sendingSector)
            rs.uiPane.commsPane.addMessage("Sector ${sendingSector + 1} has requested to swap sectors with you", CommsPane.ALERT)
        }
        rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
    }
}

/** Class representing client request to decline the incoming swap request from another sector */
data class DeclineSwapRequest(private val requestingSector: Byte = -1, private val decliningSector: Byte = -1): ServerReceive,
    ClientReceive, NeedsEncryption {
    override fun handleServerReceive(gs: GameServer, connection: ConnectionMeta) {
        // Validate the declining player
        if (gs.sectorMap[connection.uuid] != decliningSector) return
        // Ensure requesting sector did indeed request the sector
        if (!gs.sectorSwapRequests.contains(Pair(decliningSector, requestingSector), false)) return
        gs.sectorSwapRequests.removeValue(Pair(decliningSector, requestingSector), false)
        gs.networkServer.sendTCPToConnection(gs.sectorUUIDMap[requestingSector], this)
    }

    override fun handleClientReceive(rs: RadarScreen) {
        // Check the requesting sector is indeed this player
        if (requestingSector != rs.playerSector) return
        if (rs.swapSectorRequest != decliningSector) return
        rs.swapSectorRequest = null
        rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
        rs.uiPane.commsPane.addMessage("Sector ${decliningSector + 1} has declined your sector swap request", CommsPane.ALERT)
    }
}
