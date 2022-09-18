package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.GLineArray
import com.bombbird.terminalcontrol2.components.SRColor
import com.bombbird.terminalcontrol2.global.getEngine
import com.esotericsoftware.minlog.Log
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

class Shoreline(lineArray: ShortArray, onClient: Boolean = true): SerialisableEntity<Shoreline.SerialisedShoreline> {
    val entity = getEngine(onClient).entity {
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

    /**
     * Returns a default empty [SerialisedShoreline] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedShoreline {
        Log.info("Shoreline", "Empty serialised shoreline returned due to missing $missingComponent component")
        return SerialisedShoreline()
    }

    /** Gets a [SerialisedShoreline] from current state */
    override fun getSerialisableObject(): SerialisedShoreline {
        entity.apply {
            val lineArray = get(GLineArray.mapper) ?: return emptySerialisableObject("GLineArray")
            return SerialisedShoreline(lineArray.vertices.map { it.toInt().toShort() }.toShortArray())
        }
    }
}