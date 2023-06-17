package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.SECTOR_GREEN
import com.bombbird.terminalcontrol2.global.getEngine
import com.esotericsoftware.minlog.Log
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Interface for sectors that return its controller's callsign and frequency */
interface SectorContactable {
    data class ControllerInfo(val callsign: String, val frequency: String)

    /** Returns a [ControllerInfo] of the sector depending on the flight type of the aircraft if applicable */
    fun getControllerCallsignFrequency(flightType: Byte): ControllerInfo
}

/** Sector class that creates a sector entity with the required components on instantiation */
class Sector(id: Byte, freq: String, arrCallsign: String, depCallsign: String, sectorBoundary: ShortArray,
             onClient: Boolean = true): SerialisableEntity<Sector.SerialisedSector>, SectorContactable {
    val entity = getEngine(onClient).entity {
        with<SectorInfo> {
            sectorId = id
            frequency = freq
            arrivalCallsign = arrCallsign
            departureCallsign = depCallsign
        }
        with<GPolygon> {
            vertices = sectorBoundary.map { it.toFloat() }.toFloatArray()
        }
        if (onClient) {
            val mySector = CLIENT_SCREEN?.playerSector == id
            with<SRColor> {
                color = if (mySector) Color.WHITE else SECTOR_GREEN
            }
            if (mySector) with<RenderLast>()
        }
    }

    companion object {
        /** De-serialises a [SerialisedSector] and creates a new [Sector] object from it */
        fun fromSerialisedObject(serialisedSector: SerialisedSector): Sector {
            return Sector(
                serialisedSector.sectorId, serialisedSector.frequency, serialisedSector.appCallsign, serialisedSector.depCallsign,
                serialisedSector.vertices
            )
        }
    }

    /** Object that contains [Sector] data to be serialised by Kryo */
    class SerialisedSector(val sectorId: Byte = -3,
                           val frequency: String = "", val appCallsign: String = "", val depCallsign: String = "",
                           val vertices: ShortArray = shortArrayOf()
    )

    /**
     * Returns a default empty [SerialisedSector] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedSector {
        Log.info("Sector", "Empty serialised sector returned due to missing $missingComponent component")
        return SerialisedSector()
    }

    /** Gets a [SerialisedSector] from current state */
    override fun getSerialisableObject(): SerialisedSector {
        entity.apply {
            val sectorInfo = get(SectorInfo.mapper) ?: return emptySerialisableObject("SectorInfo")
            val polygon = get(GPolygon.mapper) ?: return emptySerialisableObject("Polygon")
            return SerialisedSector(
                sectorInfo.sectorId,
                sectorInfo.frequency, sectorInfo.arrivalCallsign, sectorInfo.departureCallsign,
                polygon.vertices.map { it.toInt().toShort() }.toShortArray()
            )
        }
    }

    override fun getControllerCallsignFrequency(flightType: Byte): SectorContactable.ControllerInfo {
        val sectorInfo = entity[SectorInfo.mapper] ?: SectorInfo()
        val callsign = if (flightType == FlightType.ARRIVAL) sectorInfo.arrivalCallsign else sectorInfo.departureCallsign
        return SectorContactable.ControllerInfo(callsign, sectorInfo.frequency)
    }
}

/** Sector class that creates an ACC sector entity with the required components on instantiation */
class ACCSector(id: Byte, freq: String, callsign: String, sectorBoundary: ShortArray,
                onClient: Boolean = true): SerialisableEntity<ACCSector.SerialisedACCSector>, SectorContactable {
    val entity = getEngine(onClient).entity {
        with<ACCSectorInfo> {
            sectorId = id
            frequency = freq
            accCallsign = callsign
        }
        with<GPolygon> {
            vertices = sectorBoundary.map { it.toFloat() }.toFloatArray()
        }
    }

    companion object {
        /** De-serialises a [SerialisedACCSector] and creates a new [ACCSector] object from it */
        fun fromSerialisedObject(serialisedACCSector: SerialisedACCSector): ACCSector {
            return ACCSector(
                serialisedACCSector.sectorId, serialisedACCSector.frequency, serialisedACCSector.accCallsign,
                serialisedACCSector.vertices
            )
        }
    }

    /** Object that contains [Sector] data to be serialised by Kryo */
    class SerialisedACCSector(val sectorId: Byte = -3, val frequency: String = "", val accCallsign: String = "",
                              val vertices: ShortArray = shortArrayOf()
    )

    /**
     * Returns a default empty [SerialisedACCSector] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedACCSector {
        Log.info("Sector", "Empty serialised sector returned due to missing $missingComponent component")
        return SerialisedACCSector()
    }

    /** Gets a [SerialisedACCSector] from current state */
    override fun getSerialisableObject(): SerialisedACCSector {
        entity.apply {
            val sectorInfo = get(ACCSectorInfo.mapper) ?: return emptySerialisableObject("ACCSectorInfo")
            val polygon = get(GPolygon.mapper) ?: return emptySerialisableObject("Polygon")
            return SerialisedACCSector(
                sectorInfo.sectorId,
                sectorInfo.frequency, sectorInfo.accCallsign,
                polygon.vertices.map { it.toInt().toShort() }.toShortArray()
            )
        }
    }

    override fun getControllerCallsignFrequency(flightType: Byte): SectorContactable.ControllerInfo {
        val sectorInfo = entity[ACCSectorInfo.mapper] ?: ACCSectorInfo()
        return SectorContactable.ControllerInfo(sectorInfo.accCallsign, sectorInfo.frequency)
    }
}