package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.json.BaseLegJSONInterface
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.networking.dataclasses.*
import com.bombbird.terminalcontrol2.networking.lanserver.LANClientDiscoveryHandler
import com.bombbird.terminalcontrol2.networking.relayserver.ClientToServer
import com.bombbird.terminalcontrol2.networking.relayserver.JoinGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.NewGameRequest
import com.bombbird.terminalcontrol2.networking.relayserver.ServerToClient
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.traffic.*
import com.bombbird.terminalcontrol2.ui.*
import com.bombbird.terminalcontrol2.utilities.*
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.minlog.Log
import ktx.ashley.*
import java.util.*

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
        register(ConnectionError::class.java)
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
        register(BaseLegJSONInterface::class.java)
        register(BaseLegJSONInterface.LegType::class.java)

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

        // Conflict classes
        register(ConflictData::class.java)
        register(ConflictManager.Conflict.SerialisedConflict::class.java)
        register(Array<ConflictManager.Conflict.SerialisedConflict>::class.java)
        register(ConflictManager.PotentialConflict.SerialisedPotentialConflict::class.java)
        register(Array<ConflictManager.PotentialConflict.SerialisedPotentialConflict>::class.java)

        // Miscellaneous event triggered updated classes
        register(AircraftSectorUpdateData::class.java)
        register(AircraftSpawnData::class.java)
        register(AircraftDespawnData::class.java)
        register(AircraftControlStateUpdateData::class.java)
        register(HandoverRequest::class.java)
        register(SectorSwapRequest::class.java)
        register(DeclineSwapRequest::class.java)
        register(AircraftDatatagPositionUpdateData::class.java)
        register(CustomWaypointData::class.java)
        register(RemoveCustomWaypointData::class.java)
        register(GameRunningStatus::class.java)
        register(PendingRunwayUpdateData::class.java)
        register(ActiveRunwayUpdateData::class.java)
        register(ScoreData::class.java)
        register(TrafficSettingsData::class.java)
        register(TrailDotData::class.java)
        register(Array<TrailDotData>::class.java)
        register(AllTrailDotData::class.java)

    } ?: Log.info("NetworkingTools", "Null kryo passed, unable to register classes")
}

/**
 * Registers relay specific classes to the relay server's Kryo instance
 * @param kryo the relay server's [Kryo] instance
 */
fun registerRelayClassesToRelayServerKryo(kryo: Kryo?) {
    kryo?.apply {
        register(ByteArray::class.java)
        register(NewGameRequest::class.java)
        register(JoinGameRequest::class.java)
        register(ClientToServer::class.java)
        register(ServerToClient::class.java)
    }
}

/**
 * Registers relay specific classes to the client's (those connected to relay server) Kryo instance
 * @param kryo the client's [Kryo] instance
 */
fun registerRelayClassesToClientKryo(kryo: Kryo?) {
    kryo?.apply {
        register(ByteArray::class.java)
        register(ClientToServer::class.java)
        register(ServerToClient::class.java)
    }
}

/**
 * Gets an instance of the game client for connection to server
 * @return the [Client] object
 */
fun getGameClientInstance(lanClientDiscoveryHandler: LANClientDiscoveryHandler): Client {
    return Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        setDiscoveryHandler(lanClientDiscoveryHandler)
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                (obj as? RequestClientUUID)?.apply {
                    connection.sendTCP(ClientUUIDData(myUuid.toString()))
                } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, obj)
            }
        })
    }
}

/**
 * Handles an incoming request from the server to client, and performs the appropriate actions
 * @param rs the [RadarScreen] to apply changes to
 * @param obj the incoming data object whose class should have been registered to [Kryo]
 * */
private fun handleIncomingRequestClient(rs: RadarScreen, obj: Any?) {
    if (obj !is ClientReceive) return
    if (obj is IndividualSectorData) println("IndividualSectorData scheduled")
    rs.postRunnableAfterEngineUpdate(obj is IndividualSectorData) {
        obj.handleClientReceive(rs)
    }
}

/**
 * Handles an incoming request from the client to server, and performs the appropriate actions
 * @param gs the [GameServer] to apply changes to
 * @param connection the [ConnectionMeta] data of the incoming connection
 * @param obj the incoming data object whose class should have been registered to [Kryo]
 * */
fun handleIncomingRequestServer(gs: GameServer, connection: ConnectionMeta, obj: Any?) {
    if (obj !is ServerReceive) return
    obj.handleServerReceive(gs, connection)
}
