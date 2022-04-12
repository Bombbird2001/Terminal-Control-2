package com.bombbird.terminalcontrol2.networking

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.Sector
import com.esotericsoftware.kryo.Kryo

object SerialisationRegistering {
    fun registerAll(kryo: Kryo?) {
        // Register all classes to be transmitted
        kryo?.apply {
            // Classes to register for generic serialisation
            register(Vector2::class.java)
            register(LinkedHashMap::class.java)
            register(FloatArray::class.java)

            // Entity classes
            register(InitialLoadData::class.java)
            register(Sector.SerialisedSector::class.java)
            register(Array<Sector.SerialisedSector>::class.java)
            register(Aircraft.SerialisedAircraft::class.java)
            register(Array<Aircraft.SerialisedAircraft>::class.java)
            register(Airport.SerialisedAirport::class.java)
            register(Array<Airport.SerialisedAirport>::class.java)
            register(Airport.Runway.SerialisedRunway::class.java)

        } ?: Gdx.app.log("SerialisationRegistering", "Null kryo passed, unable to register classes")
    }

    class InitialLoadData(var sectors: Array<Sector.SerialisedSector> = arrayOf(),
                          var aircraft: Array<Aircraft.SerialisedAircraft> = arrayOf(),
                          var airports: Array<Airport.SerialisedAirport> = arrayOf())
}