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
import com.bombbird.terminalcontrol2.ui.updateMetarInformation
import com.bombbird.terminalcontrol2.utilities.byte
import com.bombbird.terminalcontrol2.utilities.getAircraftIcon
import com.esotericsoftware.kryo.Kryo
import ktx.ashley.get
import ktx.ashley.plusAssign

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

        // Initial load classes
        register(InitialAirspaceData::class.java)
        register(InitialSectorData::class.java)
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
        register(Array<Airport.SerialisedRunwayMapping>::class.java)
        register(Airport.SerialisedRunwayMapping::class.java)
        register(Array<Waypoint.SerialisedWaypoint>::class.java)
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
        register(Array<Approach.SerialisedTransition>::class.java)
        register(Approach.SerialisedTransition::class.java)
        register(Array<Approach.SerialisedStep>::class.java)
        register(Approach.SerialisedStep::class.java)
        register(Approach.IlsGS.SerialisedIlsGS::class.java)
        register(Approach.IlsLOCOffset.SerialisedIlsLOCOffset::class.java)

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
        register(AircraftControlStateUpdateData::class.java)
        register(CustomWaypointData::class.java)
        register(RemoveCustomWaypointData::class.java)

    } ?: Gdx.app.log("NetworkingTools", "Null kryo passed, unable to register classes")
}

/** Class representing airspace data sent on initial connection, loading of the game on a client */
data class InitialAirspaceData(val magHdgDev: Float = 0f, val minAlt: Int = 2000, val maxAlt: Int = 20000, val minSep: Float = 3f, val transAlt: Int = 18000, val transLvl: Int = 180)

/** Class representing sector data sent on initial connection, loading of the game on a client */
class InitialSectorData(val sectors: Array<Sector.SerialisedSector> = arrayOf())

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
data class AircraftSectorUpdateData(val callsign: String = "", val newSector: Byte = 0)

/** Class representing control state data sent when the aircraft command state is updated (either through player command, or due to leg being reached) */
data class AircraftControlStateUpdateData(val callsign: String = "", var primaryName: String = "", var route: Route.SerialisedRoute = Route.SerialisedRoute(), var hiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                                          val vectorHdg: Short? = null, val vectorTurnDir: Byte? = null,
                                          val clearedAlt: Int = 0, val clearedIas: Short = 0,
                                          val minIas: Short = 0, val maxIas: Short = 0, val optimalIas: Short = 0)

/** Class representing data sent during creation of a new custom waypoint */
data class CustomWaypointData(val customWpt: Waypoint.SerialisedWaypoint = Waypoint.SerialisedWaypoint())

/** Class representing data sent during removal of a custom waypoint */
data class RemoveCustomWaypointData(val wptId: Short = -1)

/**
 * Handles an incoming request from the server to client, and performs the appropriate actions
 * @param rs the [RadarScreen] to apply changes to
 * @param obj the incoming data object whose class should have been registered to [Kryo]
 * */
fun handleIncomingRequest(rs: RadarScreen, obj: Any?) {
    rs.postRunnable {
        (obj as? String)?.apply {
            println(this)
        } ?: (obj as? FastUDPData)?.apply {
            aircraft.forEach {
                rs.aircraft[it.icaoCallsign]?.apply { updateFromUDPData(it) }
            }
        } ?: (obj as? InitialAirspaceData)?.apply {
            MAG_HDG_DEV = magHdgDev
            MIN_ALT = minAlt
            MAX_ALT = maxAlt
            MIN_SEP = minSep
            TRANS_ALT = transAlt
            TRANS_LVL = transLvl
        } ?: (obj as? InitialSectorData)?.sectors?.onEach {
            Sector.fromSerialisedObject(it)
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
                    // Debug.printAirportSIDs(entity)
                    // Debug.printAirportSTARs(entity)
                    // Debug.printAirportApproaches(entity)
                }
            }
            updateMetarInformation()
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
            updateMetarInformation()
        } ?: (obj as? AircraftSectorUpdateData)?.apply {
            rs.aircraft[obj.callsign]?.let { aircraft ->
                aircraft.entity[Controllable.mapper]?.sectorId = obj.newSector
                aircraft.entity[RSSprite.mapper]?.drawable = getAircraftIcon(aircraft.entity[FlightType.mapper]?.type ?: return@apply, obj.newSector)
                if (obj.newSector == 0.byte && rs.selectedAircraft == aircraft) rs.setUISelectedAircraft(aircraft)
            }
        } ?: (obj as? AircraftControlStateUpdateData)?.apply {
            rs.aircraft[obj.callsign]?.let { aircraft ->
                aircraft.entity += ClearanceAct(
                    ClearanceState.ActingClearance(
                        ClearanceState(obj.primaryName, Route.fromSerialisedObject(obj.route), Route.fromSerialisedObject(obj.hiddenLegs),
                    obj.vectorHdg, obj.vectorTurnDir, obj.clearedAlt, obj.clearedIas, obj.minIas, obj.maxIas, obj.optimalIas)
                    ))
                if (rs.selectedAircraft == aircraft) rs.uiPane.updateSelectedAircraft(aircraft)
            }
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
        }
    }
}