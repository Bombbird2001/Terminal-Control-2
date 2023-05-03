package com.bombbird.terminalcontrol2.networking.dataclasses

import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.MainMenu
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.traffic.ConflictManager
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.ui.getNewDatatagLabelText
import com.bombbird.terminalcontrol2.ui.setDatatagFlash
import com.bombbird.terminalcontrol2.ui.updateDatatagText
import com.bombbird.terminalcontrol2.utilities.getAircraftIcon
import com.esotericsoftware.minlog.Log
import ktx.ashley.*
import java.util.*

/**
 * Class representing a request to the client to send its UUID to the server
 *
 * This will always be sent by the server on initial client connection, and a reply by the client (see below [ClientUUIDData])
 * must be received in order for the server to send the client initialisation data
 * */
class RequestClientUUID

/**
 * Class representing an error when a player is attempting to connect - this could be caused by a few different reasons,
 * such as duplicate or missing UUID
 */
data class ConnectionError(private val cause: String = "Unknown cause"): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        Log.info("NetworkingTools", "Connection failed - $cause")
        GAME.quitCurrentGame()
        GAME.getScreen<MainMenu>().showDialog(CustomDialog("Failed to connect", cause, "", "Ok"))
    }
}

/** Class representing data sent on fast UDP updates (i.e. 20 times per second) */
class FastUDPData(private val aircraft: Array<Aircraft.SerialisedAircraftUDP> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        aircraft.forEach {
            rs.aircraft[it.icaoCallsign]?.apply { updateFromUDPData(it) }
        }
    }
}

/**
 * Class representing a request to the client to clear all currently data, such as objects stored in the game engine
 *
 * This should always be sent before initial loading data (those below) to ensure no duplicate objects become present
 * on the client
 * */
class ClearAllClientData: ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        // Nuke everything
        rs.sectors.clear()
        rs.aircraft.clear()
        rs.airports.clear()
        rs.waypoints.clear()
        rs.updatedWaypointMapping.clear()
        rs.publishedHolds.clear()
        rs.minAltSectors.clear()
        getEngine(true).removeAllEntities()
        rs.afterClearData()
    }
}

/** Class representing airspace data sent on initial connection, loading of the game on a client */
data class InitialAirspaceData(private val magHdgDev: Float = 0f, private val minAlt: Int = 2000, private val maxAlt: Int = 20000,
                               private val minSep: Float = 3f, private val transAlt: Int = 18000, private val transLvl: Int = 180):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        MAG_HDG_DEV = magHdgDev
        MIN_ALT = minAlt
        MAX_ALT = maxAlt
        MIN_SEP = minSep
        TRANS_ALT = transAlt
        TRANS_LVL = transLvl
    }
}

/** Class representing sector data sent on new player connections, disconnections */
class IndividualSectorData(private val assignedSectorId: Byte = 0, private val sectors: Array<Sector.SerialisedSector> = arrayOf(),
                           private val primarySector: FloatArray = floatArrayOf()): ClientReceive {
    companion object {
        private val sectorFamily = allOf(SectorInfo::class).get()
    }

    override fun handleClientReceive(rs: RadarScreen) {
        // Remove all existing sector mapping and entities
        println("Individual sector received")
        rs.sectors.clear()
        getEngine(true).removeAllEntities(sectorFamily)
        rs.playerSector = assignedSectorId
        sectors.onEach { sector -> rs.sectors.add(Sector.fromSerialisedObject(sector)) }
        rs.primarySector.vertices = primarySector
        rs.swapSectorRequest = null
        rs.incomingSwapRequests.clear()
        rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
    }
}

/** Class representing aircraft data sent on initial connection, loading of the game on a client */
class InitialAircraftData(private val aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        aircraft.onEach {
            Aircraft.fromSerialisedObject(it).apply {
                entity[AircraftInfo.mapper]?.icaoCallsign?.let { callsign ->
                    rs.aircraft.put(callsign, this)
                }
            }
        }
    }
}

/** Class representing airport data sent on initial connection, loading of the game on a client */
class AirportData(private val airports: Array<Airport.SerialisedAirport> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        airports.forEach {
            Airport.fromSerialisedObject(it).apply {
                entity[AirportInfo.mapper]?.arptId?.let { id ->
                    rs.airports.put(id, this)
                }
                // printAirportSIDs(entity)
                // printAirportSTARs(entity)
                // printAirportApproaches(entity)
            }
        }
        rs.uiPane.mainInfoObj.let {
            it.initializeAtisDisplay()
            it.initializeAirportRwyConfigPanes()
        }
    }
}

/** Class representing waypoint data sent on initial connection, loading of the game on a client */
class WaypointData(private val waypoints: Array<Waypoint.SerialisedWaypoint> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        waypoints.onEach {
            Waypoint.fromSerialisedObject(it).apply {
                entity[WaypointInfo.mapper]?.wptId?.let { id ->
                    rs.waypoints[id] = this
                }
            }
        }
    }
}

/** Class representing waypoint mapping data sent on initial connection, loading of the game on a client */
class WaypointMappingData(private val waypointMapping: Array<Waypoint.SerialisedWaypointMapping> = arrayOf()):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.updatedWaypointMapping.clear()
        waypointMapping.onEach { rs.updatedWaypointMapping[it.name] = it.wptId }
    }
}

/** Class representing published hold data sent on initial connection, loading of the game on a client */
class PublishedHoldData(private val publishedHolds: Array<PublishedHold.SerialisedPublishedHold> = arrayOf()):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        publishedHolds.onEach {
            PublishedHold.fromSerialisedObject(it).apply {
                rs.waypoints[entity[PublishedHoldInfo.mapper]?.wptId]?.entity?.get(WaypointInfo.mapper)?.wptName?.let { wptName ->
                    rs.publishedHolds.put(wptName, this)
                }
            }
        }
    }
}

/** Class representing minimum altitude sector data sent on initial connection, loading of the game on a client */
class MinAltData(private val minAltSectors: Array<MinAltSector.SerialisedMinAltSector> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        minAltSectors.onEach {
            rs.minAltSectors.add(MinAltSector.fromSerialisedObject(it))
            rs.minAltSectors.sort(MinAltSector::sortByDescendingMinAltComparator)
        }
    }
}

/** Class representing shoreline data sent on initial connection, loading of the game on a client */
class ShorelineData(private val shoreline: Array<Shoreline.SerialisedShoreline> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        shoreline.onEach { Shoreline.fromSerialisedObject(it) }
    }
}

/** Class representing the data to be sent during METAR updates */
class MetarData(private val metars: Array<Airport.SerialisedMetar> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        metars.forEach {
            rs.airports[it.arptId]?.updateFromSerialisedMetar(it)
        }
        rs.uiPane.mainInfoObj.updateAtisInformation()
    }
}

/** Class representing data sent during aircraft sector update */
data class AircraftSectorUpdateData(private val callsign: String = "", private val newSector: Byte = 0,
                                    private val newUUID: String? = null, private val ignoreInitialContact: Boolean = false,
                                    private val tagFlashing: Boolean = false):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        // If the client has not received the initial load data, ignore this sector update
        if (rs.sectors.isEmpty) return
        rs.aircraft[callsign]?.let { aircraft ->
            val controllable = aircraft.entity[Controllable.mapper] ?: return
            controllable.sectorId = newSector
            aircraft.entity[RSSprite.mapper]?.drawable = getAircraftIcon(aircraft.entity[FlightType.mapper]?.type ?: return, newSector)
            aircraft.entity[Datatag.mapper]?.let {
                it.minimised = newSector != rs.playerSector
                updateDatatagText(it, getNewDatatagLabelText(aircraft.entity, it.minimised))
                CLIENT_SCREEN?.sendAircraftDatatagPositionUpdate(aircraft.entity, it.xOffset, it.yOffset, it.minimised, it.flashing)
            }
            if (newSector != rs.playerSector && controllable.controllerUUID.toString() == myUuid.toString() && newUUID != myUuid.toString()) {
                // Send contact other sector message only if aircraft is not in player's sector, old UUID is this
                // player's UUID, and new UUID is not this player's UUID
                rs.uiPane.commsPane.contactOther(aircraft.entity, newSector)
            }
            if (newSector == rs.playerSector && controllable.controllerUUID.toString() != newUUID && newUUID == myUuid.toString()) {
                // Send message only if aircraft is in player's sector, old UUID is not the player's UUID and the new UUID is the player's UUID
                // If ignore initial contact, will not perform contact, but will flash if needed
                if (!ignoreInitialContact) rs.uiPane.commsPane.also { commsPane ->
                    if (aircraft.entity.has(RecentGoAround.mapper)) commsPane.goAround(aircraft.entity)
                    else commsPane.initialContact(aircraft.entity)
                }
                if (!ignoreInitialContact || tagFlashing) {
                    aircraft.entity += ContactNotification()
                    aircraft.entity[Datatag.mapper]?.let {
                        setDatatagFlash(it, aircraft, true)
                    }
                }
            } else if (newSector != rs.playerSector || newUUID != myUuid.toString()) {
                aircraft.entity.remove<ContactNotification>()
                aircraft.entity[Datatag.mapper]?.let { setDatatagFlash(it, aircraft, false) }
            }
            controllable.controllerUUID = newUUID?.let { UUID.fromString(it) }
            if (rs.selectedAircraft == aircraft) {
                if (newSector == rs.playerSector) rs.setUISelectedAircraft(aircraft)
                else rs.deselectUISelectedAircraft()
            }
        }
    }
}

/** Class representing sent when an aircraft spawns in the game */
data class AircraftSpawnData(private val newAircraft: Aircraft.SerialisedAircraft = Aircraft.SerialisedAircraft()):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        Aircraft.fromSerialisedObject(newAircraft).apply {
            entity[AircraftInfo.mapper]?.icaoCallsign?.let { callsign ->
                rs.aircraft.put(callsign, this)
            }
        }
    }
}

/** Class representing de-spawn data sent when an aircraft is removed from the game */
data class AircraftDespawnData(private val callsign: String = ""): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        GAME.engine.removeEntity(rs.aircraft[callsign]?.entity)
        rs.aircraft.removeKey(callsign)
    }
}

/** Class representing data sent during creation of a new custom waypoint */
data class CustomWaypointData(private val customWpt: Waypoint.SerialisedWaypoint = Waypoint.SerialisedWaypoint()):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        if (rs.waypoints.containsKey(customWpt.id)) {
            Log.info("NetworkingTools", "Existing waypoint with ID ${customWpt.id} found, ignoring this custom waypoint")
            return
        }
        rs.waypoints[customWpt.id] = Waypoint.fromSerialisedObject(customWpt)
    }
}

/** Class representing data sent during removal of a custom waypoint */
data class RemoveCustomWaypointData(private val wptId: Short = -1): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        if (wptId >= -1) {
            Log.info("NetworkingTools", "Custom waypoint must have ID < -1; $wptId was provided")
            return
        }
        rs.waypoints[wptId]?.let { getEngine(true).removeEntity(it.entity) }
        rs.waypoints.remove(wptId)
    }
}

/** Class representing data sent during setting/un-setting of a pending runway change */
data class PendingRunwayUpdateData(private val airportId: Byte = 0, private val configId: Byte? = null): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.airports[airportId]?.pendingRunwayConfigClient(configId)
        GAME.soundManager.playAlert()
    }
}

/** Class representing data sent during a runway change */
data class ActiveRunwayUpdateData(private val airportId: Byte = 0, private val configId: Byte = 0): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.airports[airportId]?.activateRunwayConfig(configId)
        rs.uiPane.mainInfoObj.updateAtisInformation()
        GAME.soundManager.playRunwayChange()
    }
}

/** Class representing data sent when the score is updated */
data class ScoreData(private val score: Int = 0, private val highScore: Int = 0): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.uiPane.mainInfoObj.updateScoreDisplay(score, highScore)
    }
}

/** Class representing data sent for ongoing conflicts and potential conflicts */
class ConflictData(private val conflicts: Array<ConflictManager.Conflict.SerialisedConflict> = arrayOf(),
                   private val potentialConflicts: Array<ConflictManager.PotentialConflict.SerialisedPotentialConflict> = arrayOf()):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.conflicts.clear()
        rs.potentialConflicts.clear()
        conflicts.forEach { conflict ->
            rs.conflicts.add(ConflictManager.Conflict.fromSerialisedObject(conflict))
        }
        potentialConflicts.forEach { potentialConflict ->
            rs.potentialConflicts.add(ConflictManager.PotentialConflict.fromSerialisedObject(potentialConflict))
        }
    }
}

/** Class representing data sent for traffic settings on the server */
class TrafficSettingsData(private val trafficMode: Byte = TrafficMode.NORMAL, private val trafficValue: Float = 0f,
                          private val arrivalClosed: ByteArray = byteArrayOf(), private val departureClosed: ByteArray = byteArrayOf()):
    ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.serverTrafficMode = trafficMode
        rs.serverTrafficValue = trafficValue
        // Remove all airport closed components/flags
        rs.airports.values().forEach { arpt ->
            arpt.entity.remove<ArrivalClosed>()
            arpt.entity += DepartureInfo(closed = false)
        }
        arrivalClosed.forEach { id -> rs.airports[id]?.entity?.plusAssign(ArrivalClosed()) }
        departureClosed.forEach { id -> rs.airports[id]?.entity?.plusAssign(DepartureInfo(closed = true)) }
    }
}

/** Class representing data sent from server to clients to add a trail dot to the aircraft */
data class TrailDotData(val callsign: String = "", val posX: Float = 0f, val posY: Float = 0f)

/** Class representing all trail dot data to be sent */
class AllTrailDotData(private val trails: Array<TrailDotData> = arrayOf()): ClientReceive {
    override fun handleClientReceive(rs: RadarScreen) {
        trails.forEach { trail ->
            rs.aircraft[trail.callsign].entity[TrailInfo.mapper]?.positions?.addFirst(Position(trail.posX, trail.posY))
        }
    }
}