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
class Sector(id: Byte, ctrlName: String, freq: String, sectorBoundary: ShortArray) {
    val entity = Constants.SERVER_ENGINE.entity {
        with<SectorInfo> {
            sectorId = id
            controllerName = ctrlName
            frequency = freq
        }
        with<GPolygon> {
            vertices = sectorBoundary.map { it.toFloat() }.toFloatArray()
        }
        with<SRColor> {
            color = Color.GRAY
        }
    }

    companion object {
        /** De-serialises a [SerialisedSector] and creates a new [Sector] object from it */
        fun fromSerialisedObject(serialisedSector: SerialisedSector): Sector {
            return Sector(
                serialisedSector.sectorId, serialisedSector.controllerName, serialisedSector.frequency,
                serialisedSector.vertices
            )
        }
    }

    /** Object that contains [Sector] data to be serialised by Kryo */
    class SerialisedSector(val sectorId: Byte = -3, val controllerName: String = "", val frequency: String = "",
                           val vertices: ShortArray = shortArrayOf()
    )

    /** Gets a [SerialisedSector] from current state */
    fun getSerialisableObject(): SerialisedSector {
        entity.apply {
            val sectorInfo = get(SectorInfo.mapper)!!
            val polygon = get(GPolygon.mapper)!!
            return SerialisedSector(
                sectorInfo.sectorId, sectorInfo.controllerName, sectorInfo.frequency,
                polygon.vertices.map { it.toInt().toShort() }.toShortArray()
            )
        }
    }
}