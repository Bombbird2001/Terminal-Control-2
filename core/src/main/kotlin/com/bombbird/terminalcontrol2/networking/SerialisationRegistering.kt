package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
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
            register(InitialLoadData::class.java)
            register(Sector.SerialisedSector::class.java)
            register(Array<Sector.SerialisedSector>::class.java)
            register(Aircraft.SerialisedAircraft::class.java)
            register(Array<Aircraft.SerialisedAircraft>::class.java)
            register(Airport.SerialisedAirport::class.java)
            register(Array<Airport.SerialisedAirport>::class.java)
            register(Airport.Runway.SerialisedRunway::class.java)
            register(Array<Airport.Runway.SerialisedRunway>::class.java)
            register(Array<Waypoint.SerialisedWaypoint>::class.java)
            register(Waypoint.SerialisedWaypoint::class.java)
            register(Array<MinAltSector.SerialisedMinAltSector>::class.java)
            register(MinAltSector.SerialisedMinAltSector::class.java)
            register(Array<Shoreline.SerialisedShoreline>::class.java)
            register(Shoreline.SerialisedShoreline::class.java)
            register(Array<Route.Leg>::class.java)
            register(Route.Leg::class.java)
            register(Route.InitClimbLeg::class.java)
            register(Route.VectorLeg::class.java)
            register(Route.WaypointLeg::class.java)
            register(Route.DiscontinuityLeg::class.java)
            register(Route.HoldLeg::class.java)
            register(Array<Route.SerialisedRoute>::class.java)
            register(Route.SerialisedRoute::class.java)
            register(Array<SidStar.SID.SerialisedRwyInitClimb>::class.java)
            register(SidStar.SID.SerialisedRwyInitClimb::class.java)
            register(Array<SidStar.SerialisedRwyLegs>::class.java)
            register(SidStar.SerialisedRwyLegs::class.java)
            register(Array<SidStar.SID.SerialisedSID>::class.java)
            register(SidStar.SID.SerialisedSID::class.java)
            register(Array<SidStar.STAR.SerialisedSTAR>::class.java)
            register(SidStar.STAR.SerialisedSTAR::class.java)

            // METAR classes
            register(MetarData::class.java)
            register(Airport.SerialisedMetar::class.java)
            register(Array<Airport.SerialisedMetar>::class.java)

            // Fast update UDP classes
            register(FastUDPData::class.java)
            register(Aircraft.SerialisedAircraftUDP::class.java)
            register(Array<Aircraft.SerialisedAircraftUDP>::class.java)

        } ?: Gdx.app.log("SerialisationRegistering", "Null kryo passed, unable to register classes")
    }

    /** Class representing the data sent on initial connection, loading of the game on a client */
    class InitialLoadData(
        var sectors: Array<Sector.SerialisedSector> = arrayOf(),
        var aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf(),
        var airports: Array<Airport.SerialisedAirport> = arrayOf(),
        var waypoints: Array<Waypoint.SerialisedWaypoint> = arrayOf(),
        val minAltSectors: Array<MinAltSector.SerialisedMinAltSector> = arrayOf(),
        val shoreline: Array<Shoreline.SerialisedShoreline> = arrayOf()
    )

    /** Class representing the data to be sent during METAR updates */
    class MetarData(var metars: Array<Airport.SerialisedMetar> = arrayOf())

    /** Class representing data sent on fast UDP updates (i.e. 20 times per second) */
    class FastUDPData(var aircraft: Array<Aircraft.SerialisedAircraftUDP> = arrayOf())
}