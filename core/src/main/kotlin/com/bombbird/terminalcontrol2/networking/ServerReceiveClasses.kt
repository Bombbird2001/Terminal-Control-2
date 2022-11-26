package com.bombbird.terminalcontrol2.networking

import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.traffic.getArrivalClosedAirports
import com.bombbird.terminalcontrol2.traffic.getDepartureClosedAirports
import com.bombbird.terminalcontrol2.utilities.assignSectorsToPlayers
import com.bombbird.terminalcontrol2.utilities.getSectorForExtrapolatedPosition
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.minlog.Log
import ktx.ashley.get
import ktx.ashley.hasNot
import java.util.*
import kotlin.math.min

/** Class representing data sent on a client request to pause/run the game */
data class GameRunningStatus(private val running: Boolean = true): ServerReceive {
    override fun handleServerReceive(gs: GameServer, connection: Connection) {
        gs.handleGameRunningRequest(running)
    }
}

/** Class representing response to the above UUID request, containing the UUID of the client */
data class ClientUUIDData(private val uuid: String? = null): ServerReceive {
    override fun handleServerReceive(gs: GameServer, connection: Connection) {
        // If the UUID is null or the map already contains the UUID, do not send the data
        if (uuid == null) {
            connection.sendTCP(ConnectionError("Missing player UUID"))
            return
        }
        val uuidObj = UUID.fromString(uuid)
        if (gs.connectionUUIDMap.containsValue(uuidObj, false)) {
            Log.info("NetworkingTools", "UUID $uuid is already in game")
            connection.sendTCP(ConnectionError("Player with same UUID already in server"))
            return
        }
        val currPlayerNo = gs.playerNo.incrementAndGet().toByte()
        gs.postRunnableAfterEngineUpdate {
            // Get data only after engine has completed this update to prevent threading issues
            gs.connectionUUIDMap.put(connection, uuidObj)
            connection.sendTCP(ClearAllClientData())
            connection.sendTCP(InitialAirspaceData(MAG_HDG_DEV, MIN_ALT, MAX_ALT, MIN_SEP, TRANS_ALT, TRANS_LVL))
            assignSectorsToPlayers(
                gs.server.connections,
                gs.sectorMap,
                gs.connectionUUIDMap,
                gs.sectorUUIDMap,
                currPlayerNo,
                gs.sectors
            )
            gs.sectorSwapRequests.clear()
            val aircraftArray = gs.aircraft.values().toArray()
            var itemsRemaining = aircraftArray.size
            while (itemsRemaining > 0) {
                val serialisedAircraftArray = Array(min(itemsRemaining, SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT)) {
                    aircraftArray[aircraftArray.size - itemsRemaining + it].getSerialisableObject()
                }
                itemsRemaining -= SERVER_AIRCRAFT_TCP_UDP_MAX_COUNT
                connection.sendTCP(InitialAircraftData(serialisedAircraftArray))
            }
            connection.sendTCP(AirportData(gs.airports.values().toArray().map { it.getSerialisableObject() }
                .toTypedArray()))
            val wptArray = gs.waypoints.values.toTypedArray()
            connection.sendTCP(WaypointData(wptArray.map { it.getSerialisableObject() }.toTypedArray()))
            connection.sendTCP(WaypointMappingData(wptArray.map { it.getMappingSerialisableObject() }.toTypedArray()))
            connection.sendTCP(PublishedHoldData(gs.publishedHolds.values().toArray().map { it.getSerialisableObject() }
                .toTypedArray()))
            connection.sendTCP(MinAltData(gs.minAltSectors.toArray().map { it.getSerialisableObject() }.toTypedArray()))
            connection.sendTCP(ShorelineData(gs.shoreline.toArray().map { it.getSerialisableObject() }.toTypedArray()))

            // Send current METAR
            connection.sendTCP(MetarData(gs.airports.values().map { it.getSerialisedMetar() }.toTypedArray()))

            // Send current traffic settings
            connection.sendTCP(
                TrafficSettingsData(
                    gs.trafficMode, gs.trafficValue,
                    getArrivalClosedAirports(), getDepartureClosedAirports()
                )
            )

            // Send runway configs
            gs.airports.values().toArray().forEach {
                val arptId = it.entity[AirportInfo.mapper]?.arptId ?: return@forEach
                connection.sendTCP(
                    ActiveRunwayUpdateData(
                        arptId,
                        it.entity[ActiveRunwayConfig.mapper]?.configId ?: return@forEach
                    )
                )
                connection.sendTCP(PendingRunwayUpdateData(arptId, it.entity[PendingRunwayConfig.mapper]?.pendingId))
            }

            // Send score data
            connection.sendTCP(ScoreData(gs.score, gs.highScore))
        }
    }
}

/** Class representing client request to hand over an aircraft to the new sector */
data class HandoverRequest(private val callsign: String = "", private val newSector: Byte = 0, private val sendingSector: Byte = 0): ServerReceive {
    override fun handleServerReceive(gs: GameServer, connection: Connection) {
        val aircraft = gs.aircraft[callsign]?.entity ?: return
        // Validate the sender
        val controllable = aircraft[Controllable.mapper] ?: return
        if (controllable.sectorId != sendingSector) return
        // Validate new sector
        if (newSector == SectorInfo.TOWER) {
            // Validate approach status
            if (aircraft.hasNot(LocalizerCaptured.mapper) && aircraft.hasNot(GlideSlopeCaptured.mapper) && aircraft.hasNot(
                    VisualCaptured.mapper))
                return
        } else if (newSector == SectorInfo.CENTRE) {
            // Validate aircraft altitude
            val alt = aircraft[Altitude.mapper]?.altitudeFt ?: return
            if (alt < MAX_ALT - 1500) return
        } else {
            // Validate the extrapolated position
            val pos = aircraft[Position.mapper] ?: return
            val track = aircraft[GroundTrack.mapper] ?: return
            if (getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S, true) != newSector) return
        }
        // Request validated - update controllable ID and send update to clients
        controllable.sectorId = newSector
        val uuid = gs.sectorUUIDMap[newSector]?.toString()
        gs.sendAircraftSectorUpdateTCPToAll(callsign, newSector, uuid)
    }
}

/** Class representing aircraft datatag position data (on client) sent to server */
data class AircraftDatatagPositionUpdateData(private val aircraft: String = "", private val xOffset: Float = 0f,
                                             private val yOffset: Float = 0f, private val minimised: Boolean = false): ServerReceive {
    override fun handleServerReceive(gs: GameServer, connection: Connection) {
        val aircraft = gs.aircraft[aircraft] ?: return
        // Validate that the sector controlling the aircraft is indeed the sector who sent the request
        val sendingSector = gs.sectorMap[connection] ?: return
        val controllingSector = aircraft.entity[Controllable.mapper]?.sectorId ?: return
        if (sendingSector != controllingSector) return
        // Update the aircraft's initial datatag position component
        aircraft.entity[InitialClientDatatagPosition.mapper]?.let {
            it.xOffset = xOffset
            it.yOffset = yOffset
            it.minimised = minimised
        }
    }
}