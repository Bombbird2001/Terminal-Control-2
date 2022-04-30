package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.GPolygon
import com.bombbird.terminalcontrol2.components.SRColor
import com.bombbird.terminalcontrol2.components.SectorInfo
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Sector class that creates a sector entity with the required components on instantiation */
class Sector(id: Byte, ctrlName: String, freq: String, callsign: String, sectorBoundary: ShortArray, onClient: Boolean = true) {
    val entity = Constants.getEngine(onClient).entity {
        with<SectorInfo> {
            sectorId = id
            controllerName = ctrlName
            frequency = freq
            sectorCallsign = callsign
        }
        with<GPolygon> {
            vertices = sectorBoundary.map { it.toFloat() }.toFloatArray()
        }
        with<SRColor> {
            color = Color.WHITE
        }
    }

    companion object {
        /** De-serialises a [SerialisedSector] and creates a new [Sector] object from it */
        fun fromSerialisedObject(serialisedSector: SerialisedSector): Sector {
            return Sector(
                serialisedSector.sectorId,
                serialisedSector.controllerName, serialisedSector.frequency, serialisedSector.callsign,
                serialisedSector.vertices
            )
        }
    }

    /** Object that contains [Sector] data to be serialised by Kryo */
    class SerialisedSector(val sectorId: Byte = -3,
                           val controllerName: String = "", val frequency: String = "", val callsign: String = "",
                           val vertices: ShortArray = shortArrayOf()
    )

    /** Gets a [SerialisedSector] from current state */
    fun getSerialisableObject(): SerialisedSector {
        entity.apply {
            val sectorInfo = get(SectorInfo.mapper) ?: return SerialisedSector()
            val polygon = get(GPolygon.mapper) ?: return SerialisedSector()
            return SerialisedSector(
                sectorInfo.sectorId,
                sectorInfo.controllerName, sectorInfo.frequency, sectorInfo.sectorCallsign,
                polygon.vertices.map { it.toInt().toShort() }.toShortArray()
            )
        }
    }
}