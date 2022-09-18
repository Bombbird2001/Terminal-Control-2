package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.GPolygon
import com.bombbird.terminalcontrol2.components.RenderLast
import com.bombbird.terminalcontrol2.components.SRColor
import com.bombbird.terminalcontrol2.components.SectorInfo
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.SECTOR_GREEN
import com.bombbird.terminalcontrol2.global.getEngine
import com.esotericsoftware.minlog.Log
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Sector class that creates a sector entity with the required components on instantiation */
class Sector(id: Byte, freq: String, arrCallsign: String, depCallsign: String, sectorBoundary: ShortArray,
             onClient: Boolean = true): SerialisableEntity<Sector.SerialisedSector> {
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
}