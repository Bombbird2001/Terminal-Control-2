package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.esotericsoftware.kryo.Kryo

/** Object that handles the registering of classes to be serialised and sent over the network using Kryonet */
object SerialisationRegistering {
    /** Registers all the required classes into the specified [kryo] */
    fun registerAll(kryo: Kryo?) {
        // Register all classes to be transmitted
        kryo?.apply {
            // Classes to register for generic serialisation
            register(Vector2::class.java)
            register(ShortArray::class.java)

            // Initial load classes
            register(InitialSectorData::class.java)
            register(InitialAircraftData::class.java)
            register(AirportData::class.java)
            register(WaypointData::class.java)
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

        } ?: Gdx.app.log("SerialisationRegistering", "Null kryo passed, unable to register classes")
    }

    /** Class representing sector data sent on initial connection, loading of the game on a client */
    class InitialSectorData(val sectors: Array<Sector.SerialisedSector> = arrayOf())

    /** Class representing aircraft data sent on initial connection, loading of the game on a client */
    class InitialAircraftData(val aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf())

    /** Class representing airport data sent on initial connection, loading of the game on a client */
    class AirportData(val airports: Array<Airport.SerialisedAirport> = arrayOf())

    /** Class representing waypoint data sent on initial connection, loading of the game on a client */
    class WaypointData(val waypoints: Array<Waypoint.SerialisedWaypoint> = arrayOf())

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
    class AircraftSectorUpdateData(val callsign: String = "", val newSector: Byte = 0)

    /** Class representing control state data sent when the aircraft command state is updated (either through player command, or due to leg being reached) */
    class AircraftControlStateUpdateData(val callsign: String = "", var primaryName: String = "", var route: Route.SerialisedRoute = Route.SerialisedRoute(), var hiddenLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                                         val vectorHdg: Short? = null, val clearedAlt: Int = 0)
}