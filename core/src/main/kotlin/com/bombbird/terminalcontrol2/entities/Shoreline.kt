package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.GLineArray
import com.bombbird.terminalcontrol2.components.SRColor
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

class Shoreline(lineArray: ShortArray, onClient: Boolean = true) {
    val entity = Constants.getEngine(onClient).entity {
        with<GLineArray> {
            vertices = lineArray.map { it.toFloat() }.toFloatArray()
        }
        with<SRColor> {
            color = Color.BROWN
        }
    }

    companion object {
        /** De-serialises a [SerialisedShoreline] and creates a new [Shoreline] object from it */
        fun fromSerialisedObject(serialisedShoreline: SerialisedShoreline): Shoreline {
            return Shoreline(serialisedShoreline.vertices)
        }
    }

    /** Object that contains [Sector] data to be serialised by Kryo */
    class SerialisedShoreline(val vertices: ShortArray = shortArrayOf())

    /** Gets a [SerialisedShoreline] from current state */
    fun getSerialisableObject(): SerialisedShoreline {
        entity.apply {
            val lineArray = get(GLineArray.mapper) ?: return SerialisedShoreline()
            return SerialisedShoreline(lineArray.vertices.map { it.toInt().toShort() }.toShortArray())
        }
    }
}