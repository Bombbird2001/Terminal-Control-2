package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.Sector
import com.esotericsoftware.kryo.Kryo

/** Object that handles the registering of classes to be serialised and sent over the network using Kryonet */
object SerialisationRegistering {
    /** Registers all the required classes into the specified [kryo] */
    fun registerAll(kryo: Kryo?) {
        // Register all classes to be transmitted
        kryo?.apply {
            // Classes to register for generic serialisation
            register(Vector2::class.java)
            register(LinkedHashMap::class.java)
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

            // Fast update UDP classes
            register(FastUDPData::class.java)
            register(Aircraft.SerialisedAircraftUDP::class.java)
            register(Array<Aircraft.SerialisedAircraftUDP>::class.java)

        } ?: Gdx.app.log("SerialisationRegistering", "Null kryo passed, unable to register classes")
    }

    /** Class representing the data sent on initial connection, loading of the game on a client */
    class InitialLoadData(var sectors: Array<Sector.SerialisedSector> = arrayOf(),
                          var aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf(),
                          var airports: Array<Airport.SerialisedAirport> = arrayOf())

    /** Class representing data sent on fast UDP updates (i.e. 20 times per second) */
    class FastUDPData(var aircraft: Array<Aircraft.SerialisedAircraftUDP> = arrayOf())
}