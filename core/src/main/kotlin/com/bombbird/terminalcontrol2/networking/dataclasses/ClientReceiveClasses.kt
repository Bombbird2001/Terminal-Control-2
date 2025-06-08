package com.bombbird.terminalcontrol2.networking.dataclasses

import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.systems.FamilyWithListener
import com.bombbird.terminalcontrol2.systems.RenderingSystemClient
import com.bombbird.terminalcontrol2.systems.updateAircraftDatatagText
import com.bombbird.terminalcontrol2.systems.updateAircraftRadarData
import com.bombbird.terminalcontrol2.traffic.TrafficMode
import com.bombbird.terminalcontrol2.traffic.conflict.Conflict
import com.bombbird.terminalcontrol2.traffic.conflict.PotentialConflict
import com.bombbird.terminalcontrol2.traffic.conflict.PredictedConflict
import com.bombbird.terminalcontrol2.ui.*
import com.bombbird.terminalcontrol2.ui.datatag.*
import com.bombbird.terminalcontrol2.ui.panes.CommsPane
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.*
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import java.util.*

/**
 * Class representing a request to the client to send its UUID to the server
 *
 * This will always be sent by the server on initial client connection, and a reply by the client (see [ClientData])
 * must be received in order for the server to send the client initialisation data
 */
class RequestClientData: NeedsEncryption

/**
 * Class representing an error when a player is attempting to connect - this could be caused by a few different reasons,
 * such as duplicate or missing UUID
 */
data class ConnectionError(private val cause: String = "Unknown cause"): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.info("NetworkingTools", "Connection failed - $cause")
        GAME.quitCurrentGameWithDialog { CustomDialog("Failed to connect", cause, "", "Ok") }
    }
}

/** Class representing player joined event */
class PlayerJoined: ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.uiPane.commsPane.addMessage("A player has joined the game", CommsPane.ALERT)
    }
}

/** Class representing player left event */
data class PlayerLeft(private val sector: Byte = -1): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.uiPane.commsPane.addMessage("Player controlling sector ${sector + 1} has left the game", CommsPane.ALERT)
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
 * Class representing a request to the client to clear all current data, such as objects stored in the game engine
 *
 * This should always be sent before initial loading data (those below) to ensure no duplicate objects become present
 * on the client
 */
class ClearAllClientData: ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        // Nuke everything
        FileLog.debug("ClientReceiveClasses", "Received ClearAllClientData")
        rs.sectors.clear()
        rs.aircraft.clear()
        rs.airports.clear()
        rs.waypoints.clear()
        rs.updatedWaypointMapping.clear()
        rs.publishedHolds.clear()
        rs.minAltSectors.clear()
        rs.storms.clear()
        rs.stormReceiveBuffer.clear()
        getEngine(true).removeAllEntitiesOnMainThread(true)
        rs.afterClearData()
    }
}

/** Class representing airspace data sent on initial connection, loading of the game on a client */
class InitialAirspaceData(private val magHdgDev: Float = 0f, private val minAlt: Int = 2000,
                          private val maxAlt: Int = 20000, private val minSep: Float = 3f,
                          private val transAlt: Int = 18000, private val transLvl: Int = 180,
                          private val intermediateAlts: IntArray = intArrayOf(), private val mainName: String = "",
                          private val maxPlayers: Byte = 4, private val useRecat: Boolean = true
):
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received InitialAirspaceData")
        MAG_HDG_DEV = magHdgDev
        MIN_ALT = minAlt
        MAX_ALT = maxAlt
        MIN_SEP = minSep
        TRANS_ALT = transAlt
        TRANS_LVL = transLvl
        INTERMEDIATE_ALTS.clear()
        intermediateAlts.forEach { INTERMEDIATE_ALTS.add(it) }
        rs.mainName = mainName
        rs.maxPlayers = maxPlayers.toInt()
        rs.useRecat = useRecat
    }
}

/** Class representing sector data sent on new player connections, disconnections */
class IndividualSectorData(private val assignedSectorId: Byte = 0, private val sectors: Array<Sector.SerialisedSector> = arrayOf(),
                           private val primarySector: FloatArray = floatArrayOf()): ClientReceive, NeedsEncryption {
    companion object {
        private val sectorFamily = allOf(SectorInfo::class).get()

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received IndividualSectorData")
        // Remove all existing sector mapping and entities
        rs.sectors.clear()
        getEngine(true).removeAllEntities(sectorFamily)
        rs.playerSector = assignedSectorId
        sectors.onEach { sector -> rs.sectors.add(Sector.fromSerialisedObject(sector)) }
        rs.primarySector.vertices = primarySector
        rs.primarySectorBound.set(rs.primarySector.boundingRectangle)
        rs.swapSectorRequest = null
        rs.incomingSwapRequests.clear()
        rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
        rs.uiPane.commsPane.addMessage("You are now controlling sector ${assignedSectorId + 1}", CommsPane.ALERT)
        if (sectors.size > 1) GAME.achievementManager.unlockKnockKnock()
    }
}

/** Class representing ACC sector data sent on initial connection, loading of the game on a client */
class InitialACCSectorData(private val accSectors: Array<ACCSector.SerialisedACCSector> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received InitialACCSectorData")
        accSectors.forEach {
            ACCSector.fromSerialisedObject(it).apply {
                rs.accSectors.add(this)
            }
        }
    }
}

/** Class representing aircraft data sent on initial connection, loading of the game on a client */
class InitialAircraftData(private val aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received InitialAircraftData")
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
class AirportData(private val airports: Array<Airport.SerialisedAirport> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received AirportData")
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
class WaypointData(private val waypoints: Array<Waypoint.SerialisedWaypoint> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received WaypointData")
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
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received WaypointMappingData")
        rs.updatedWaypointMapping.clear()
        waypointMapping.onEach { rs.updatedWaypointMapping[it.name] = it.wptId }
    }
}

/** Class representing published hold data sent on initial connection, loading of the game on a client */
class PublishedHoldData(private val publishedHolds: Array<PublishedHold.SerialisedPublishedHold> = arrayOf()):
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received PublishedHoldData")
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
class MinAltData(private val minAltSectors: Array<MinAltSector.SerialisedMinAltSector> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received MinAltData")
        minAltSectors.onEach {
            rs.minAltSectors.add(MinAltSector.fromSerialisedObject(it))
            rs.minAltSectors.sort(MinAltSector::sortByDescendingMinAltComparator)
        }
    }
}

/** Class representing shoreline data sent on initial connection, loading of the game on a client */
class ShorelineData(private val shoreline: Array<Shoreline.SerialisedShoreline> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received ShorelineData")
        shoreline.onEach { Shoreline.fromSerialisedObject(it) }
    }
}

/** Class representing the data to be sent during METAR updates */
class MetarData(private val metars: Array<Airport.SerialisedMetar> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received MetarData")
        metars.forEach {
            rs.airports[it.arptId]?.updateFromSerialisedMetar(it)
        }
        rs.uiPane.mainInfoObj.updateAtisInformation()
    }
}

/** Class representing data sent for traffic settings on the server */
class TrafficSettingsData(private val trafficMode: Byte = TrafficMode.NORMAL, private val trafficValue: Float = 0f,
                          private val arrivalClosed: ByteArray = byteArrayOf(), private val departureClosed: ByteArray = byteArrayOf(),
                          private val airportTrafficValues: Array<AirportTrafficValue> = arrayOf()
):
    ClientReceive, NeedsEncryption {
    class AirportTrafficValue(val airportByte: Byte = 0, val trafficValue: Int = 0)

    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received TrafficSettingsData")
        rs.serverTrafficMode = trafficMode
        rs.serverTrafficValue = trafficValue
        // Remove all airport closed components/flags
        Entries(rs.airports).forEach { arptEntry ->
            val arpt = arptEntry.value
            arpt.entity.remove<ArrivalClosed>()
            arpt.entity += DepartureInfo(closed = false)
        }
        arrivalClosed.forEach { id -> rs.airports[id]?.entity?.plusAssign(ArrivalClosed()) }
        departureClosed.forEach { id -> rs.airports[id]?.entity?.plusAssign(DepartureInfo(closed = true)) }
        airportTrafficValues.forEach { value ->
            rs.airports[value.airportByte]?.entity?.get(AirportArrivalStats.mapper)?.targetTrafficValue = value.trafficValue
        }
    }
}

/** Class representing data sent during setting/un-setting of a pending runway change */
data class PendingRunwayUpdateData(private val airportId: Byte = 0, private val configId: Byte? = null): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received PendingRunwayUpdateData")
        rs.airports[airportId]?.pendingRunwayConfigClient(configId)
        if (configId != null)
            rs.uiPane.commsPane.addMessage("Runway change pending for ${rs.airports[airportId]?.entity?.get(AirportInfo.mapper)?.icaoCode}", CommsPane.ALERT)
    }
}

/** Class representing data sent during a runway change */
data class ActiveRunwayUpdateData(private val airportId: Byte = 0, private val configId: Byte = 0): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received ActiveRunwayUpdateData")
        rs.airports[airportId]?.activateRunwayConfig(configId, true)
        rs.uiPane.mainInfoObj.updateAtisInformation()
        GAME.soundManager.playRunwayChange()
    }
}

/** Class representing data sent when the score is updated */
data class ScoreData(private val score: Int = 0, private val highScore: Int = 0): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.uiPane.mainInfoObj.updateScoreDisplay(score, highScore)
    }
}

/** Class for notifying clients of an arrival that has landed and de-spawned */
data class ArrivalLanded(private val airportIcao: String = ""): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.info("ClientReceiveClasses", "Received ArrivalLanded")
        GAME.achievementManager.incrementArrival(airportIcao)
        if (rs.sectors.size > 1) GAME.achievementManager.incrementMultiplayerArrival()
    }
}

/** Class notifying client that all initial required data has been sent, they can now accept other transmission data */
class InitialDataSendComplete: ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.info("ClientReceiveClasses", "Received InitialDataSendComplete")
        FamilyWithListener.addAllClientFamilyEntityListeners()
        getEngine(true).getSystem<RenderingSystemClient>().updateWaypointDisplay(null)
        // rs.notifyInitialDataSendComplete() Handled separately
    }
}

/** Class representing data sent during aircraft sector update */
data class AircraftSectorUpdateData(private val callsign: String = "", private val newSector: Byte = 0,
                                    private val newUUID: String? = null, private val needsSendMessage: Boolean = false,
                                    private val sayMissedApproach: Boolean = false,
                                    private val tagFlashing: Boolean = false, private val tagMinimised: Boolean = false):
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        // If the client has not received the initial load data, ignore this sector update
        if (rs.sectors.isEmpty) return
        rs.aircraft[callsign]?.let { aircraft ->
            val controllable = aircraft.entity[Controllable.mapper] ?: return
            controllable.sectorId = newSector
            aircraft.entity[RSSprite.mapper]?.drawable = getAircraftIcon(aircraft.entity[FlightType.mapper]?.type ?: return, newSector)
            aircraft.entity[Datatag.mapper]?.let {
                it.minimised = newSector != rs.playerSector || tagMinimised
                updateDatatagText(it, getNewDatatagLabelText(aircraft.entity, it.minimised))
                CLIENT_SCREEN?.sendAircraftDatatagPositionUpdateIfControlled(
                    aircraft.entity, it.xOffset, it.yOffset,
                    it.minimised, it.shouldFlashColourFlags and Datatag.FLASH_ORANGE != 0
                )
            }
            if (newSector != rs.playerSector && controllable.controllerUUID.toString() == myUuid.toString() && newUUID != myUuid.toString() && needsSendMessage) {
                // Send contact other sector message only if aircraft is not in player's sector, old UUID is this
                // player's UUID, and new UUID is not this player's UUID
                // Will only add contact other message if needed (e.g. not during sector swap)
                rs.uiPane.commsPane.contactOther(aircraft.entity, newSector)
            }
            if (newSector == rs.playerSector && controllable.controllerUUID.toString() != newUUID && newUUID == myUuid.toString()) {
                // Send message only if aircraft is in player's sector, old UUID is not the player's UUID and the new UUID is the player's UUID
                // Will only perform contact if needed
                if (needsSendMessage) rs.uiPane.commsPane.also { commsPane ->
                    if (sayMissedApproach) commsPane.goAround(aircraft.entity)
                    else commsPane.initialContact(aircraft.entity)
                }
                if (needsSendMessage || tagFlashing) {
                    aircraft.entity += ContactNotification()
                    aircraft.entity[Datatag.mapper]?.let {
                        startDatatagNotificationFlash(it, aircraft)
                    }
                }
            } else if (newSector != rs.playerSector || newUUID != myUuid.toString()) {
                aircraft.entity.remove<ContactNotification>()
                aircraft.entity[AircraftRequestNotification.mapper]?.requestTypes?.clear()
                aircraft.entity[Datatag.mapper]?.let { stopDatatagContactFlash(it, aircraft) }
            }
            controllable.controllerUUID = newUUID?.let { UUID.fromString(it) }
            if (rs.selectedAircraft == aircraft) rs.setUISelectedAircraft(aircraft)
        }
    }
}

/** Class representing sent when an aircraft spawns in the game */
data class AircraftSpawnData(private val newAircraft: Aircraft.SerialisedAircraft = Aircraft.SerialisedAircraft()):
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        Aircraft.fromSerialisedObject(newAircraft).apply {
            entity[AircraftInfo.mapper]?.icaoCallsign?.let { callsign ->
                rs.aircraft.put(callsign, this)
            }
        }
    }
}

/** Class representing de-spawn data sent when an aircraft is removed from the game */
data class AircraftDespawnData(private val callsign: String = ""): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        val entity = rs.aircraft[callsign]?.entity ?: return
        entity[Datatag.mapper]?.despawn()
        GAME.engine.removeEntityOnMainThread(entity, true)
        rs.aircraft.removeKey(callsign)
    }
}

/** Class representing data sent during creation of a new custom waypoint */
data class CustomWaypointData(private val customWpt: Waypoint.SerialisedWaypoint = Waypoint.SerialisedWaypoint()):
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        if (rs.waypoints.containsKey(customWpt.id)) {
            FileLog.info("NetworkingTools", "Existing waypoint with ID ${customWpt.id} found, ignoring this custom waypoint")
            return
        }
        rs.waypoints[customWpt.id] = Waypoint.fromSerialisedObject(customWpt)
    }
}

/** Class representing data sent during removal of a custom waypoint */
data class RemoveCustomWaypointData(private val wptId: Short = -1): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        if (wptId >= -1) {
            FileLog.info("NetworkingTools", "Custom waypoint must have ID < -1; $wptId was provided")
            return
        }
        rs.waypoints[wptId]?.let { getEngine(true).removeEntityOnMainThread(it.entity, true) }
        rs.waypoints.remove(wptId)
    }
}

/** Class representing data sent for ongoing conflicts and potential conflicts */
class ConflictData(private val conflicts: Array<Conflict.SerialisedConflict> = arrayOf(),
                   private val potentialConflicts: Array<PotentialConflict.SerialisedPotentialConflict> = arrayOf()):
    ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        if (!rs.isInitialDataReceived()) return
        rs.conflicts.clear()
        rs.potentialConflicts.clear()
        conflicts.forEach { conflict ->
            rs.conflicts.add(Conflict.fromSerialisedObject(conflict))
        }
        potentialConflicts.forEach { potentialConflict ->
            rs.potentialConflicts.add(PotentialConflict.fromSerialisedObject(potentialConflict))
        }
    }
}

/** Class representing data sent for predicted conflicts */
class PredictedConflictData(private val predictedConflicts: Array<PredictedConflict.SerialisedPredictedConflict> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        if (!rs.isInitialDataReceived()) return

        // Get ongoing conflicts to exclude from predicted conflicts
        val conflictNames = GdxArray<String>()
        for (i in 0 until rs.conflicts.size) {
            val conflict = rs.conflicts[i]
            val name1 = conflict.entity1.getOrLogMissing(AircraftInfo.mapper)?.icaoCallsign ?: continue
            val name2 = conflict.entity2?.get(AircraftInfo.mapper)?.icaoCallsign ?: ""
            conflictNames.add("$name1${name2}_${conflict.reason}")
        }

        rs.predictedConflicts.clear()
        val noConflictAircraftMap = GdxArrayMap<String, Aircraft>(rs.aircraft)
        predictedConflicts.filter {
            val entry = "${it.name1}${it.name2 ?: ""}_${it.reason}"
            val entry2 = "${it.name2 ?: ""}${it.name1}_${it.reason}"
            ((it.name2 == null && it.advanceTimeS <= APW_DURATION_S) ||
                    (it.name2 != null && it.advanceTimeS <= STCA_DURATION_S)) &&
                    !conflictNames.contains(entry, false) &&
                    !conflictNames.contains(entry2, false)
        }.forEach { predictedConflict ->
            val conflict = PredictedConflict.fromSerialisedObject(predictedConflict)
            rs.predictedConflicts.add(conflict)

            // Flash datatag of the aircraft involved
            val aircraft1Callsign = conflict.aircraft1[AircraftInfo.mapper]?.icaoCallsign ?: return@forEach
            val aircraft1 = rs.aircraft[aircraft1Callsign] ?: return@forEach
            aircraft1.entity[Datatag.mapper]?.let { startDatatagPredictedConflictFlash(it, aircraft1) }
            noConflictAircraftMap.removeKey(aircraft1Callsign)

            val aircraft2Callsign = conflict.aircraft2?.get(AircraftInfo.mapper)?.icaoCallsign
            val aircraft2 = rs.aircraft[aircraft2Callsign]
            aircraft2?.entity?.get(Datatag.mapper)?.let {
                startDatatagPredictedConflictFlash(it, aircraft2)
                noConflictAircraftMap.removeKey(aircraft2Callsign)
            }
        }

        // Disable predicted conflict flashes for aircraft not involved in any predicted conflicts
        Entries(noConflictAircraftMap).forEach { entry ->
            entry.value.entity[Datatag.mapper]?.let { stopDatatagPredictedConflictFlash(it, entry.value) }
        }
    }
}

/** Class representing data sent from server to clients to add a trail dot to the aircraft */
data class TrailDotData(val callsign: String = "", val posX: Float = 0f, val posY: Float = 0f)

/** Class representing all trail dot data to be sent */
class AllTrailDotData(private val trails: Array<TrailDotData> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        if (!rs.isInitialDataReceived()) return
        trails.forEach { trail ->
            rs.aircraft[trail.callsign]?.entity?.get(TrailInfo.mapper)?.positions?.addFirst(Position(trail.posX, trail.posY))
        }
    }
}

/** Class representing data sent from the server to clients to update night mode */
data class NightModeData(val night: Boolean = false): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        FileLog.debug("ClientReceiveClasses", "Received NightModeData")
        rs.isNight = night
    }
}

/**
 * Class representing data sent from server to clients to update an aircraft that is cleared for takeoff - some position
 * data is also sent to account for the delay in UDP updates
 */
data class ClearedForTakeoffData(val callsign: String = "", val depArptId: Byte = 0, val newPosX: Float = 0f,
                                 val newPosY: Float = 0f, val newAlt: Float = 0f): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.apply {
            if (entity.hasNot(WaitingTakeoff.mapper)) return@apply
            // Was waiting takeoff, but now isn't: update position, radar data and datatag
            entity[Position.mapper]?.let {
                it.x = newPosX
                it.y = newPosY
            }
            entity[Altitude.mapper]?.let {
                it.altitudeFt = newAlt
            }
            updateAircraftRadarData(entity)
            updateAircraftDatatagText(entity)
            entity[Datatag.mapper]?.let { addDatatagInputListeners(it, this) }
            entity.remove<WaitingTakeoff>()
            entity += DepartureAirport(depArptId, 0)
        }
    }
}

/**
 * Class representing data sent from server to clients to declare an emergency for an aircraft, with the type of
 * emergency
 */
data class EmergencyStart(val callsign: String = "", val type: Byte = -1): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.apply {
            val depArpt = entity[DepartureAirport.mapper] ?: return
            entity[FlightType.mapper]?.type = FlightType.ARRIVAL
            entity[RSSprite.mapper]?.drawable = getAircraftIcon(entity[FlightType.mapper]?.type ?: return,
                entity[Controllable.mapper]?.sectorId ?: return)
            entity[SRColor.mapper]?.color = ARRIVAL_BLUE
            entity += ArrivalAirport(depArpt.arptId)
            entity += EmergencyPending(true, type, 0)
            entity.remove<DepartureAirport>()
            if (entity[Controllable.mapper]?.sectorId != rs.playerSector) return
            entity += ContactNotification()
            entity[Datatag.mapper]?.let {
                it.emergency = true
                it.minimised = false
                updateDatatagStyle(it, FlightType.ARRIVAL, rs.selectedAircraft == this)
                startDatatagNotificationFlash(it, this)
            }
            rs.uiPane.commsPane.declareEmergency(entity, type)
        }
    }
}

/**
 * Class representing data sent from server to clients to notify the controlling
 * player that the aircraft is nearing completion of emergency checklists, and
 * may need a fuel dump
 */
data class ChecklistsNearingDone(val callsign: String = "", val needsFuelDump: Boolean = false): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.apply {
            if (needsFuelDump)
                entity += RequiresFuelDump(false, 0f, 0f, 0f,
                    informedDumpStarted = true, informedNearingDone = true)
            if (entity[Controllable.mapper]?.sectorId != rs.playerSector) return
            entity += ContactNotification()
            entity[Datatag.mapper]?.let {
                startDatatagNotificationFlash(it, this)
            }
            rs.uiPane.commsPane.checklistNearingDone(entity, needsFuelDump)
        }
    }
}

/**
 * Class representing data sent from server to clients to notify the controlling
 * player of the aircraft's fuel dumping status, either it just started or it
 * will end soon
 */
data class FuelDumpStatus(val callsign: String = "", val dumpingEnding: Boolean = false): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.apply {
            entity[RequiresFuelDump.mapper]?.active = true
            if (entity[Controllable.mapper]?.sectorId != rs.playerSector) return
            entity += ContactNotification()
            entity[Datatag.mapper]?.let {
                startDatatagNotificationFlash(it, this)
            }
            rs.uiPane.commsPane.fuelDumpStatus(entity, dumpingEnding)
        }
    }
}

/**
 * Class representing data sent from server to clients to notify the controlling
 * player that the aircraft is ready for approach, and may remain on the runway
 * after landing
 */
data class ReadyForApproach(val callsign: String = "", val immobilizeOnLanding: Boolean = false): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.apply {
            entity[RequiresFuelDump.mapper]?.active = false
            entity += ReadyForApproachClient()
            if (immobilizeOnLanding) entity += ImmobilizeOnLanding(0f)
            if (entity[Controllable.mapper]?.sectorId != rs.playerSector) return
            entity += ContactNotification()
            entity[Datatag.mapper]?.let {
                startDatatagNotificationFlash(it, this)
            }
            rs.uiPane.commsPane.readyForApproach(entity, immobilizeOnLanding)
        }
    }
}

/** Class representing data sent from server to clients to notify the player of a change in runway closed state */
data class RunwayClosedState(val airportId: Byte = 0, val runwayId: Byte = 0, val closed: Boolean = false): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        val airport = rs.airports[airportId]?.entity ?: return
        airport[RunwayChildren.mapper]?.rwyMap?.get(runwayId)?.apply {
            val oppRwy = entity[OppositeRunway.mapper]?.oppRwy ?: return
            if (closed && entity.hasNot(RunwayClosed.mapper)) {
                entity += RunwayClosed()
                oppRwy += RunwayClosed()
                rs.uiPane.commsPane.addMessage("Runways ${entity[RunwayInfo.mapper]?.rwyName}, " +
                        "${oppRwy[RunwayInfo.mapper]?.rwyName} at ${airport[AirportInfo.mapper]?.icaoCode} are closed",
                    CommsPane.OTHERS)
            } else if (!closed && entity.has(RunwayClosed.mapper)) {
                entity.remove<RunwayClosed>()
                oppRwy.remove<RunwayClosed>()
                rs.uiPane.commsPane.addMessage("Runways ${entity[RunwayInfo.mapper]?.rwyName}, " +
                        "${oppRwy[RunwayInfo.mapper]?.rwyName} at ${airport[AirportInfo.mapper]?.icaoCode} have reopened",
                    CommsPane.OTHERS)
            }
        }
    }
}

/**
 * Class representing data sent from server to client to notify a player (who is in control of the aircraft) that the
 * aircraft has performed a go around or missed approach
 */
data class MissedApproachMessage(val callsign: String = "", val reason: Byte = -1): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.let { aircraft ->
            if (aircraft.entity[Controllable.mapper]?.sectorId != rs.playerSector) return
            rs.uiPane.commsPane.also { commsPane ->
                commsPane.missedApproach(aircraft.entity, reason)
            }
        }
    }
}

/** Class representing data sent from server to client to notify the player of an aircraft request */
class AircraftRequestMessage(val callsign: String = "", val requestType: AircraftRequest.RequestType = AircraftRequest.RequestType.NONE,
                             val params: Array<String> = arrayOf()): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        rs.aircraft[callsign]?.let { aircraft ->
            if (aircraft.entity[Controllable.mapper]?.sectorId != rs.playerSector) return
            aircraft.entity[Datatag.mapper]?.let {
                startDatatagNotificationFlash(it, aircraft)
            }
            aircraft.entity[AircraftRequestNotification.mapper]?.requestTypes?.add(requestType)
            rs.uiPane.commsPane.also { commsPane ->
                commsPane.aircraftRequest(aircraft.entity, requestType, params)
            }
        }
    }
}

/**
 * Class representing data sent from server to client to update players of all
 * current thunderstorms
 */
class ThunderStormData(
    val thunderStorms: Array<ThunderStorm.SerialisedThunderStorm> = arrayOf(),
    val finishedSending: Boolean = false
): ClientReceive, NeedsEncryption {
    override fun handleClientReceive(rs: RadarScreen) {
        thunderStorms.forEach { storm ->
            rs.stormReceiveBuffer.add {
                ThunderStorm.fromSerialisedObject(storm)
            }
        }
        if (finishedSending) {
            for (i in 0 until rs.storms.size) {
                val storm = rs.storms[i] ?: continue
                val childrenCells = storm.entity[ThunderStormCellChildren.mapper]?.cells ?: continue
                // Clear all cell entities and storm entity
                for (j in childrenCells.minimumIndex..childrenCells.maximumIndex) {
                    val row = childrenCells[j] ?: continue
                    for (k in row.minimumIndex..row.maximumIndex) {
                        val cell = row[k] ?: continue
                        getEngine(true).removeEntityOnMainThread(cell.entity, true)
                    }
                }
                getEngine(true).removeEntityOnMainThread(storm.entity, true)
            }
            rs.storms.clear()
            for (i in 0 until rs.stormReceiveBuffer.size) {
                rs.storms.add(rs.stormReceiveBuffer[i]())
            }
            rs.stormReceiveBuffer.clear()
        }
    }
}
