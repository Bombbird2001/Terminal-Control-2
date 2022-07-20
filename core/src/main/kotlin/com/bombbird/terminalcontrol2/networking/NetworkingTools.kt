package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.traffic.RunwayConfiguration
import com.bombbird.terminalcontrol2.ui.*
import com.bombbird.terminalcontrol2.utilities.*
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Connection
import ktx.ashley.*
import java.util.*

private val sectorFamily = allOf(SectorInfo::class).get()

/**
 * Registers all the required classes into the input Kryo
 * @param kryo the [Kryo] instance to register classes to
 * */
fun registerClassesToKryo(kryo: Kryo?) {
    // Register all classes to be transmitted
    kryo?.apply {
        // Classes to register for generic serialisation
        register(Vector2::class.java)
        register(ShortArray::class.java)
        register(FloatArray::class.java)
        register(ByteArray::class.java)

        // Initial load classes
        register(RequestClientUUID::class.java)
        register(ClientUUIDData::class.java)
        register(ClearAllClientData::class.java)
        register(InitialAirspaceData::class.java)
        register(IndividualSectorData::class.java)
        register(InitialAircraftData::class.java)
        register(AirportData::class.java)
        register(WaypointData::class.java)
        register(WaypointMappingData::class.java)
        register(PublishedHoldData::class.java)
        register(MinAltData::class.java)
        register(ShorelineData::class.java)
        register(Array<Sector.SerialisedSector>::class.java)
        register(Sector.SerialisedSector::class.java)
        register(Array<Aircraft.SerialisedAircraft>::class.java)
        register(Aircraft.SerialisedAircraft::class.java)
        register(Array<Airport.SerialisedAirport>::class.java)
        register(Airport.SerialisedAirport::class.java)
        register(Array<Airport.Runway.SerialisedRunway>::class.java)
        register(Airport.Runway.SerialisedRunway::class.java)
        register(ApproachNormalOperatingZone.SerialisedApproachNOZ::class.java)
        register(DepartureNormalOperatingZone.SerialisedDepartureNOZ::class.java)
        register(Array<Airport.SerialisedRunwayMapping>::class.java)
        register(Airport.SerialisedRunwayMapping::class.java)
        register(Array<Waypoint.SerialisedWaypoint>::class.java)
        register(RunwayConfiguration.SerialisedRwyConfig::class.java)
        register(Array<RunwayConfiguration.SerialisedRwyConfig>::class.java)
        register(NoTransgressionZone.SerialisedNTZ::class.java)
        register(Array<NoTransgressionZone.SerialisedNTZ>::class.java)
        register(Waypoint.SerialisedWaypoint::class.java)
        register(Array<Waypoint.SerialisedWaypointMapping>::class.java)
        register(Waypoint.SerialisedWaypointMapping::class.java)
        register(Array<PublishedHold.SerialisedPublishedHold>::class.java)
        register(PublishedHold.SerialisedPublishedHold::class.java)
        register(Array<MinAltSector.SerialisedMinAltSector>::class.java)
        register(MinAltSector.SerialisedMinAltSector::class.java)
        register(Array<Shoreline.SerialisedShoreline>::class.java)
        register(Shoreline.SerialisedShoreline::class.java)

        // Route classes
        register(Array<Route.Leg>::class.java)
        register(Route.Leg::class.java)
        register(Route.InitClimbLeg::class.java)
        register(Route.VectorLeg::class.java)
        register(Route.WaypointLeg::class.java)
        register(Route.DiscontinuityLeg::class.java)
        register(Route.HoldLeg::class.java)
        register(Array<Route.SerialisedRoute>::class.java)
        register(Route.SerialisedRoute::class.java)

        // SID, STAR classes
        register(Array<SidStar.SID.SerialisedRwyInitClimb>::class.java)
        register(SidStar.SID.SerialisedRwyInitClimb::class.java)
        register(Array<SidStar.SerialisedRwyLegs>::class.java)
        register(SidStar.SerialisedRwyLegs::class.java)
        register(Array<SidStar.SID.SerialisedSID>::class.java)
        register(SidStar.SID.SerialisedSID::class.java)
        register(Array<SidStar.STAR.SerialisedSTAR>::class.java)
        register(SidStar.STAR.SerialisedSTAR::class.java)

        // Approach classes
        register(Array<Approach.SerialisedApproach>::class.java)
        register(Approach.SerialisedApproach::class.java)
        register(Array<Approach.SerialisedTransition>::class.java)
        register(Approach.SerialisedTransition::class.java)
        register(Array<Approach.SerialisedStep>::class.java)
        register(Approach.SerialisedStep::class.java)

        // METAR classes
        register(MetarData::class.java)
        register(Airport.SerialisedMetar::class.java)
        register(Array<Airport.SerialisedMetar>::class.java)

        // Fast update UDP classes
        register(FastUDPData::class.java)
        register(Aircraft.SerialisedAircraftUDP::class.java)
        register(Array<Aircraft.SerialisedAircraftUDP>::class.java)

        // Miscellaneous event triggered updated classes
        register(AircraftSectorUpdateData::class.java)
        register(AircraftSpawnData::class.java)
        register(AircraftDespawnData::class.java)
        register(AircraftControlStateUpdateData::class.java)
        register(HandoverRequest::class.java)
        register(SectorSwapRequest::class.java)
        register(DeclineSwapRequest::class.java)
        register(CustomWaypointData::class.java)
        register(RemoveCustomWaypointData::class.java)
        register(GameRunningStatus::class.java)
        register(PendingRunwayUpdateData::class.java)
        register(ActiveRunwayUpdateData::class.java)
        register(ScoreData::class.java)

    } ?: Gdx.app.log("NetworkingTools", "Null kryo passed, unable to register classes")
}

/**
 * Class representing a request to the client to send its UUID to the server
 *
 * This will always be sent by the server on initial client connection, and a reply by the client (see below [ClientUUIDData])
 * must be received in order for the server to send the client initialisation data
 * */
class RequestClientUUID

/** Class representing response to the above UUID request, containing the UUID of the client */
data class ClientUUIDData(val uuid: String? = null)

/**
 * Class representing a request to the client to clear all currently data, such as objects stored in the game engine
 *
 * This should always be sent before initial loading data (those below) to ensure no duplicate objects become present
 * on the client
 * */
class ClearAllClientData

/** Class representing airspace data sent on initial connection, loading of the game on a client */
data class InitialAirspaceData(val magHdgDev: Float = 0f, val minAlt: Int = 2000, val maxAlt: Int = 20000, val minSep: Float = 3f, val transAlt: Int = 18000, val transLvl: Int = 180)

/** Class representing sector data sent on new player connections, disconnections */
class IndividualSectorData(val assignedSectorId: Byte = 0, val sectors: Array<Sector.SerialisedSector> = arrayOf(), val primarySector: FloatArray = floatArrayOf())

/** Class representing aircraft data sent on initial connection, loading of the game on a client */
class InitialAircraftData(val aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf())

/** Class representing airport data sent on initial connection, loading of the game on a client */
class AirportData(val airports: Array<Airport.SerialisedAirport> = arrayOf())

/** Class representing waypoint data sent on initial connection, loading of the game on a client */
class WaypointData(val waypoints: Array<Waypoint.SerialisedWaypoint> = arrayOf())

/** Class representing waypoint mapping data sent on initial connection, loading of the game on a client */
class WaypointMappingData(val waypointMapping: Array<Waypoint.SerialisedWaypointMapping> = arrayOf())

/** Class representing published hold data sent on initial connection, loading of the game on a client */
class PublishedHoldData(val publishedHolds: Array<PublishedHold.SerialisedPublishedHold> = arrayOf())

/** Class representing minimum altitude sector data sent on initial connection, loading of the game on a client */
class MinAltData(val minAltSectors: Array<MinAltSector.SerialisedMinAltSector> = arrayOf())

/** Class representing shoreline data sent on initial connection, loading of the game on a client */
class ShorelineData(val shoreline: Array<Shoreline.SerialisedShoreline> = arrayOf())

/** Class representing the data to be sent during METAR updates */
class MetarData(val metars: Array<Airport.SerialisedMetar> = arrayOf())

/** Class representing data sent on fast UDP updates (i.e. 20 times per second) */
class FastUDPData(val aircraft: Array<Aircraft.SerialisedAircraftUDP> = arrayOf())

/** Class representing data sent during aircraft sector update */
data class AircraftSectorUpdateData(val callsign: String = "", val newSector: Byte = 0, val newUUID: String? = null)

/** Class representing sent when an aircraft spawns in the game */
data class AircraftSpawnData(val newAircraft: Aircraft.SerialisedAircraft = Aircraft.SerialisedAircraft())

/** Class representing de-spawn data sent when an aircraft is removed from the game */
data class AircraftDespawnData(val callsign: String = "")

/** Class representing control state data sent when the aircraft command state is updated (either through player command, or due to leg being reached) */
data class AircraftControlStateUpdateData(val callsign: String = "", val primaryName: String = "",
                                          val route: Route.SerialisedRoute = Route.SerialisedRoute(),
                                          val hiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                                          val vectorHdg: Short? = null, val vectorTurnDir: Byte? = null,
                                          val clearedAlt: Int = 0, val clearedIas: Short = 0,
                                          val minIas: Short = 0, val maxIas: Short = 0, val optimalIas: Short = 0,
                                          val clearedApp: String? = null, val clearedTrans: String? = null,
                                          val sendingSector: Byte = -5)

/** Class representing client request to hand over an aircraft to the new sector */
data class HandoverRequest(val callsign: String = "", val newSector: Byte = 0, val sendingSector: Byte = 0)

/** Class representing client request to swap sectors */
data class SectorSwapRequest(val requestedSector: Byte? = null, val sendingSector: Byte = -1)

/** Class representing client request to decline the incoming swap request from another sector */
data class DeclineSwapRequest(val requestingSector: Byte = -1, val decliningSector: Byte = -1)

/** Class representing data sent during creation of a new custom waypoint */
data class CustomWaypointData(val customWpt: Waypoint.SerialisedWaypoint = Waypoint.SerialisedWaypoint())

/** Class representing data sent during removal of a custom waypoint */
data class RemoveCustomWaypointData(val wptId: Short = -1)

/** Class representing data sent during setting/un-setting of a pending runway change */
data class PendingRunwayUpdateData(val airportId: Byte = 0, val configId: Byte? = null)

/** Class representing data sent during a runway change */
data class ActiveRunwayUpdateData(val airportId: Byte = 0, val configId: Byte = 0)

/** Class representing data sent when the score is updated */
data class ScoreData(val score: Int = 0, val highScore: Int = 0)

/** Class representing data sent on a client request to pause/run the game */
data class GameRunningStatus(val running: Boolean = true)

/**
 * Handles an incoming request from the server to client, and performs the appropriate actions
 * @param rs the [RadarScreen] to apply changes to
 * @param obj the incoming data object whose class should have been registered to [Kryo]
 * */
fun handleIncomingRequestClient(rs: RadarScreen, obj: Any?) {
    rs.postRunnableAfterEngineUpdate {
        (obj as? String)?.apply {
            println(this)
        } ?: (obj as? FastUDPData)?.apply {
            aircraft.forEach {
                rs.aircraft[it.icaoCallsign]?.apply { updateFromUDPData(it) }
            }
        } ?: (obj as? ClearAllClientData)?.apply {
            // Nuke everything
            rs.sectors.clear()
            rs.aircraft.clear()
            rs.airports.clear()
            rs.waypoints.clear()
            rs.updatedWaypointMapping.clear()
            rs.publishedHolds.clear()
            GAME.engine.removeAllEntities()
        } ?: (obj as? InitialAirspaceData)?.apply {
            MAG_HDG_DEV = magHdgDev
            MIN_ALT = minAlt
            MAX_ALT = maxAlt
            MIN_SEP = minSep
            TRANS_ALT = transAlt
            TRANS_LVL = transLvl
        } ?: (obj as? IndividualSectorData)?.apply {
            // Remove all existing sector mapping and entities
            println("Individual sector received")
            rs.sectors.clear()
            GAME.engine.removeAllEntities(sectorFamily)
            rs.playerSector = obj.assignedSectorId
            sectors.onEach { sector -> rs.sectors.add(Sector.fromSerialisedObject(sector)) }
            rs.primarySector.vertices = primarySector
            rs.swapSectorRequest = null
            rs.incomingSwapRequests.clear()
            rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
        } ?: (obj as? InitialAircraftData)?.aircraft?.onEach {
            Aircraft.fromSerialisedObject(it).apply {
                entity[AircraftInfo.mapper]?.icaoCallsign?.let { callsign ->
                    rs.aircraft.put(callsign, this)
                }
            }
        } ?: (obj as? AirportData)?.apply {
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
            CLIENT_SCREEN?.uiPane?.mainInfoObj?.let {
                it.initializeAtisDisplay()
                it.initializeAirportRwyConfigPanes()
            }
        } ?: (obj as? WaypointData)?.waypoints?.onEach {
            Waypoint.fromSerialisedObject(it).apply {
                entity[WaypointInfo.mapper]?.wptId?.let { id ->
                    rs.waypoints[id] = this
                }
            }
        } ?: (obj as? WaypointMappingData)?.waypointMapping?.apply {
            rs.updatedWaypointMapping.clear()
            onEach { rs.updatedWaypointMapping[it.name] = it.wptId }
        } ?: (obj as? PublishedHoldData)?.publishedHolds?.onEach {
            PublishedHold.fromSerialisedObject(it).apply {
                rs.waypoints[entity[PublishedHoldInfo.mapper]?.wptId]?.entity?.get(WaypointInfo.mapper)?.wptName?.let { wptName ->
                    rs.publishedHolds.put(wptName, this)
                }
            }
        } ?: (obj as? MinAltData)?.minAltSectors?.onEach {
            MinAltSector.fromSerialisedObject(it)
        } ?: (obj as? ShorelineData)?.shoreline?.onEach {
            Shoreline.fromSerialisedObject(it)
        } ?: (obj as? MetarData)?.apply {
            metars.forEach {
                rs.airports[it.arptId]?.updateFromSerialisedMetar(it)
            }
            CLIENT_SCREEN?.uiPane?.mainInfoObj?.updateAtisInformation()
        } ?: (obj as? AircraftSectorUpdateData)?.apply {
            // If the client has not received the initial load data, ignore this sector update
            if (rs.sectors.isEmpty) return@apply
            rs.aircraft[obj.callsign]?.let { aircraft ->
                val controllable = aircraft.entity[Controllable.mapper] ?: return@apply
                controllable.sectorId = obj.newSector
                aircraft.entity[RSSprite.mapper]?.drawable = getAircraftIcon(aircraft.entity[FlightType.mapper]?.type ?: return@apply, obj.newSector)
                aircraft.entity[Datatag.mapper]?.let { updateDatatagText(it, getNewDatatagLabelText(aircraft.entity, it.minimised)) }
                if (obj.newSector != rs.playerSector && controllable.controllerUUID.toString() == uuid.toString() && obj.newUUID != uuid.toString()) {
                    // Send contact other sector message only if aircraft is not in player's sector, old UUID is this
                    // player's UUID, and new UUID is not this player's UUID
                    CLIENT_SCREEN?.uiPane?.commsPane?.contactOther(aircraft.entity, obj.newSector)
                }
                if (obj.newSector == rs.playerSector && controllable.controllerUUID.toString() != obj.newUUID && obj.newUUID == uuid.toString()) {
                    // Send message only if aircraft is in player's sector, old UUID is not the player's UUID and the new UUID is the player's UUID
                    CLIENT_SCREEN?.uiPane?.commsPane?.also { commsPane ->
                        if (aircraft.entity.has(RecentGoAround.mapper)) commsPane.goAround(aircraft.entity)
                        else commsPane.initialContact(aircraft.entity)
                    }
                    aircraft.entity += ContactNotification()
                } else if (obj.newSector != rs.playerSector || obj.newUUID != uuid.toString()) aircraft.entity.remove<ContactNotification>()
                controllable.controllerUUID = obj.newUUID?.let { UUID.fromString(it) }
                if (rs.selectedAircraft == aircraft) {
                    if (obj.newSector == rs.playerSector) rs.setUISelectedAircraft(aircraft)
                    else rs.deselectUISelectedAircraft()
                }
            }
        } ?: (obj as? AircraftSpawnData)?.apply {
            Aircraft.fromSerialisedObject(newAircraft).apply {
                entity[AircraftInfo.mapper]?.icaoCallsign?.let { callsign ->
                    rs.aircraft.put(callsign, this)
                }
            }
        } ?: (obj as? AircraftDespawnData)?.apply {
            GAME.engine.removeEntity(rs.aircraft[obj.callsign]?.entity)
            rs.aircraft.removeKey(obj.callsign)
        } ?: (obj as? AircraftControlStateUpdateData)?.apply {
            rs.aircraft[obj.callsign]?.let { aircraft ->
                aircraft.entity += ClearanceAct(
                    ClearanceState.ActingClearance(
                        ClearanceState(obj.primaryName, Route.fromSerialisedObject(obj.route), Route.fromSerialisedObject(obj.hiddenLegs),
                            obj.vectorHdg, obj.vectorTurnDir, obj.clearedAlt,
                            obj.clearedIas, obj.minIas, obj.maxIas, obj.optimalIas, obj.clearedApp, obj.clearedTrans)))
                aircraft.entity[Datatag.mapper]?.let { updateDatatagText(it, getNewDatatagLabelText(aircraft.entity, it.minimised)) }
                if (rs.selectedAircraft == aircraft) rs.uiPane.updateSelectedAircraft(aircraft)
            }
        } ?: (obj as? SectorSwapRequest)?.apply {
            if (obj.requestedSector == null) {
                // Requesting sector cancelled, remove swap request
                rs.incomingSwapRequests.removeValue(obj.sendingSector, false)
            } else {
                // Check the request has not been received before
                if (rs.incomingSwapRequests.contains(obj.sendingSector, false)) return@apply
                rs.incomingSwapRequests.add(obj.sendingSector)
            }
            rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
        } ?: (obj as? DeclineSwapRequest)?.apply {
            // Check the requesting sector is indeed this player
            if (obj.requestingSector != rs.playerSector) return@apply
            if (rs.swapSectorRequest != obj.decliningSector) return@apply
            rs.swapSectorRequest = null
            rs.uiPane.sectorPane.updateSectorDisplay(rs.sectors)
        } ?: (obj as? CustomWaypointData)?.apply {
            if (rs.waypoints.containsKey(customWpt.id)) {
                Gdx.app.log("NetworkingTools", "Existing waypoint with ID ${customWpt.id} found, ignoring this custom waypoint")
                return@apply
            }
            rs.waypoints[customWpt.id] = Waypoint.fromSerialisedObject(customWpt)
        } ?: (obj as? RemoveCustomWaypointData)?.apply {
            if (wptId >= -1) {
                Gdx.app.log("NetworkingTools", "Custom waypoint must have ID < -1; $wptId was provided")
                return@apply
            }
            rs.waypoints[wptId]?.let { getEngine(true).removeEntity(it.entity) }
            rs.waypoints.remove(wptId)
        } ?: (obj as? PendingRunwayUpdateData)?.apply {
            rs.airports[obj.airportId]?.pendingRunwayConfigClient(obj.configId)
        } ?: (obj as? ActiveRunwayUpdateData)?.apply {
            rs.airports[obj.airportId]?.activateRunwayConfig(obj.configId)
            CLIENT_SCREEN?.uiPane?.mainInfoObj?.updateAtisInformation()
        } ?: (obj as? ScoreData)?.apply {
            CLIENT_SCREEN?.uiPane?.mainInfoObj?.updateScoreDisplay(obj.score, obj.highScore)
        }
    }
}

/**
 * Handles an incoming request from the client to server, and performs the appropriate actions
 * @param gs the [GameServer] to apply changes to
 * @param obj the incoming data object whose class should have been registered to [Kryo]
 * */
fun handleIncomingRequestServer(gs: GameServer, connection: Connection, obj: Any?) {
    (obj as? AircraftControlStateUpdateData)?.apply {
        gs.postRunnableAfterEngineUpdate {
            gs.aircraft[obj.callsign]?.entity?.let {
                // Validate the sender
                if (it[Controllable.mapper]?.sectorId != obj.sendingSector) return@postRunnableAfterEngineUpdate
                addNewClearanceToPendingClearances(it, obj, connection.returnTripTime)
            }
        }
    } ?: (obj as? GameRunningStatus)?.apply {
        gs.handleGameRunningRequest(obj.running)
    } ?: (obj as? ClientUUIDData)?.apply {
        // If the UUID is null or the map already contains the UUID, do not send the data
        if (uuid == null) return
        val uuidObj = UUID.fromString(uuid)
        if (gs.connectionUUIDMap.containsValue(uuidObj, false)) return
        val currPlayerNo = gs.playerNo.incrementAndGet().toByte()
        gs.postRunnableAfterEngineUpdate {
            // Get data only after engine has completed this update to prevent threading issues
            gs.connectionUUIDMap.put(connection, uuidObj)
            connection.sendTCP(ClearAllClientData())
            connection.sendTCP(InitialAirspaceData(MAG_HDG_DEV, MIN_ALT, MAX_ALT, MIN_SEP, TRANS_ALT, TRANS_LVL))
            assignSectorsToPlayers(gs.server.connections, gs.sectorMap, gs.connectionUUIDMap, gs.sectorUUIDMap, currPlayerNo, gs.sectors)
            gs.sectorSwapRequests.clear()
            connection.sendTCP(InitialAircraftData(gs.aircraft.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
            connection.sendTCP(AirportData(gs.airports.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
            val wptArray = gs.waypoints.values.toTypedArray()
            connection.sendTCP(WaypointData(wptArray.map { it.getSerialisableObject() }.toTypedArray()))
            connection.sendTCP(WaypointMappingData(wptArray.map { it.getMappingSerialisableObject() }.toTypedArray()))
            connection.sendTCP(PublishedHoldData(gs.publishedHolds.values().toArray().map { it.getSerialisableObject() }.toTypedArray()))
            connection.sendTCP(MinAltData(gs.minAltSectors.toArray().map { it.getSerialisableObject() }.toTypedArray()))
            connection.sendTCP(ShorelineData(gs.shoreline.toArray().map { it.getSerialisableObject() }.toTypedArray()))

            // Send current METAR
            connection.sendTCP(MetarData(gs.airports.values().map { it.getSerialisedMetar() }.toTypedArray()))

            // Send runway configs
            gs.airports.values().toArray().forEach {
                val arptId = it.entity[AirportInfo.mapper]?.arptId ?: return@forEach
                connection.sendTCP(ActiveRunwayUpdateData(arptId, it.entity[ActiveRunwayConfig.mapper]?.configId ?: return@forEach))
                connection.sendTCP(PendingRunwayUpdateData(arptId, it.entity[PendingRunwayConfig.mapper]?.pendingId))
            }

            // Send score data
            connection.sendTCP(ScoreData(gs.score, gs.highScore))
        }
    } ?: (obj as? HandoverRequest)?.apply {
        val aircraft = gs.aircraft[obj.callsign]?.entity ?: return@apply
        // Validate the sender
        val controllable = aircraft[Controllable.mapper] ?: return@apply
        if (controllable.sectorId != obj.sendingSector) return@apply
        // Validate new sector
        if (obj.newSector == SectorInfo.TOWER) {
            // Validate approach status
            if (aircraft.hasNot(LocalizerCaptured.mapper) && aircraft.hasNot(GlideSlopeCaptured.mapper) && aircraft.hasNot(VisualCaptured.mapper))
                return@apply
        } else if (obj.newSector == SectorInfo.CENTRE) {
            // Validate aircraft altitude
            val alt = aircraft[Altitude.mapper]?.altitudeFt ?: return@apply
            if (alt < MAX_ALT - 1500) return@apply
        } else {
            // Validate the extrapolated position
            val pos = aircraft[Position.mapper] ?: return@apply
            val track = aircraft[GroundTrack.mapper] ?: return@apply
            if (getSectorForExtrapolatedPosition(pos.x, pos.y, track.trackVectorPxps, TRACK_EXTRAPOLATE_TIME_S, true) != obj.newSector) return@apply
        }
        // Request validated - update controllable ID and send update to clients
        controllable.sectorId = obj.newSector
        val uuid = gs.sectorUUIDMap[obj.newSector]?.toString()
        gs.sendAircraftSectorUpdateTCPToAll(obj.callsign, obj.newSector, uuid)
    } ?: (obj as? SectorSwapRequest)?.apply {
        // Validate the sender
        if (gs.sectorMap[connection] != obj.sendingSector) return@apply
        // Validate requested sector (either requested sector is null or it must be in the sector map)
        if (obj.requestedSector != null && !gs.sectorMap.containsValue(obj.requestedSector, false)) return@apply
        // Clear all existing requests from the sending sector
        for (i in gs.sectorSwapRequests.size - 1 downTo 0) {
            if (gs.sectorSwapRequests[i].second == obj.sendingSector && gs.sectorSwapRequests[i].first != obj.requestedSector) {
                getConnectionFromSector(gs.sectorSwapRequests[i].first, gs.sectorMap)?.sendTCP(SectorSwapRequest(null, obj.sendingSector))
                gs.sectorSwapRequests.removeIndex(i)
            }
        }
        // If requested sector is null, return
        if (obj.requestedSector == null) return@apply
        // Check swap array for existing matching request
        val index = gs.sectorSwapRequests.indexOf(Pair(obj.sendingSector, obj.requestedSector), false)
        if (index > -1) {
            val requestingConnection = getConnectionFromSector(obj.requestedSector, gs.sectorMap) ?: return@apply
            // If connection that originally requested swap is not present for some reason, return
            swapPlayerSectors(requestingConnection, obj.requestedSector, connection, obj.sendingSector, gs.sectorMap, gs.sectorUUIDMap)
            gs.sectorSwapRequests.removeIndex(index)
            // Forward all existing swap requests to the 2 sectors (and remove any from, though there shouldn't be any at all)
            for (i in gs.sectorSwapRequests.size - 1 downTo 0) {
                gs.sectorSwapRequests[i]?.let {
                    // Request originally bound for player who initiated the swap request
                    if (it.first == obj.requestedSector)
                        connection.sendTCP(SectorSwapRequest(it.first, it.second))
                    // Request originally bound for player who accepted the swap request
                    if (it.first == obj.sendingSector)
                        requestingConnection.sendTCP(SectorSwapRequest(it.first, it.second))
                    // Request from one of the players (there shouldn't be any, but just in case)
                    if (it.second == obj.requestedSector || it.second == obj.sendingSector)
                        gs.sectorSwapRequests.removeIndex(i)
                }
            }
            return@apply
        }
        // Not found - store new request in array
        gs.sectorSwapRequests.add(Pair(obj.requestedSector, obj.sendingSector))
        // Send the request notification to target sector
        getConnectionFromSector(obj.requestedSector, gs.sectorMap)?.sendTCP(obj)
    } ?: (obj as? DeclineSwapRequest)?.apply {
        // Validate the declining player
        if (gs.sectorMap[connection] != obj.decliningSector) return@apply
        // Ensure requesting sector did indeed request the sector
        if (!gs.sectorSwapRequests.contains(Pair(obj.decliningSector, obj.requestingSector), false)) return@apply
        gs.sectorSwapRequests.removeValue(Pair(obj.decliningSector, obj.requestingSector), false)
        getConnectionFromSector(obj.requestingSector, gs.sectorMap)?.sendTCP(obj)
    }
}
