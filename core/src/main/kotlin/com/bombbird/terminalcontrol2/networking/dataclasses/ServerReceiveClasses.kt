package com.bombbird.terminalcontrol2.networking.dataclasses

import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.utilities.getSectorForExtrapolatedPosition
import ktx.ashley.get
import ktx.ashley.hasNot
import ktx.ashley.plusAssign

/** Class representing data sent on a client request to pause/run the game */
data class GameRunningStatus(private val running: Boolean = true): ServerReceive, NeedsEncryption {
    override fun handleServerReceive(gs: GameServer, connection: ConnectionMeta) {
        gs.updateGameRunningStatus(running)
    }
}

/** Class representing response to the [RequestClientData] request, containing the UUID of the client */
@Deprecated("Old version of UUID data class, kept for compatibility with builds < 7", ReplaceWith("ClientUUIDData"))
data class ClientUUIDDataOld(val uuid: String? = null): NeedsEncryption

/** Class representing response to the [RequestClientData] request, containing the UUID and the build version of the client */
data class ClientData(val uuid: String? = null, val buildVersion: Int = 0): NeedsEncryption

/** Class representing client request to hand over an aircraft to the new sector */
data class HandoverRequest(private val callsign: String = "", private val newSector: Byte = 0, private val sendingSector: Byte = 0):
    ServerReceive, NeedsEncryption {
    override fun handleServerReceive(gs: GameServer, connection: ConnectionMeta) {
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
            if (alt < MAX_ALT - 1900) return
            gs.incrementScoreBy(1, FlightType.DEPARTURE)
            aircraft += PendingCruiseAltitude(MathUtils.random(6f, 12f))
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
                                             private val yOffset: Float = 0f, private val minimised: Boolean = false,
                                             private val flashing: Boolean = false):
    ServerReceive, NeedsEncryption {
    override fun handleServerReceive(gs: GameServer, connection: ConnectionMeta) {
        val aircraft = gs.aircraft[aircraft] ?: return
        // Validate that the sector controlling the aircraft is indeed the sector who sent the request
        val sendingSector = gs.sectorMap[connection.uuid] ?: return
        val controllingSector = aircraft.entity[Controllable.mapper]?.sectorId ?: return
        if (sendingSector != controllingSector) return
        // Update the aircraft's initial datatag position component
        aircraft.entity[InitialClientDatatagPosition.mapper]?.let {
            it.xOffset = xOffset
            it.yOffset = yOffset
            it.minimised = minimised
            it.flashing = flashing
        }
    }
}