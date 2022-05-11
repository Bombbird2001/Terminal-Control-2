package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.getEngine
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Waypoint class that creates a waypoint entity with the required components on instantiation */
class Waypoint(id: Short, name: String, posX: Short, posY: Short, onClient: Boolean = true) {
    val entity = getEngine(onClient).entity {
        with<WaypointInfo> {
            wptId = id
            wptName = name
        }
        with<Position> {
            x = posX.toFloat()
            y = posY.toFloat()
        }
        if (onClient) {
            with<GenericLabel> {
                updateStyle("Waypoint")
                updateText(name)
                xOffset = -label.prefWidth / 2
                yOffset = 12f
            }
            with<ConstantZoomSize>()
            with<SRConstantZoomSize>()
            with<GCircle> {
                radius = 5f
            }
            with<SRColor> {
                color = Color.GRAY
            }
        }
    }

    companion object {
        /** De-serialises a [SerialisedWaypoint] and creates a new [Waypoint] object from it */
        fun fromSerialisedObject(serialisedWpt: SerialisedWaypoint): Waypoint {
            return Waypoint(
                serialisedWpt.id, serialisedWpt.name,
                serialisedWpt.posX, serialisedWpt.posY
            )
        }
    }

    /** Object that contains [Waypoint] data to be serialised by Kryo */
    class SerialisedWaypoint(val id: Short = 0, val name: String = "",
                             val posX: Short = 0, val posY: Short = 0
    )

    /** Gets a [SerialisedWaypoint] from current state */
    fun getSerialisableObject(): SerialisedWaypoint {
        entity.apply {
            val wptInfo = get(WaypointInfo.mapper) ?: return SerialisedWaypoint()
            val pos = get(Position.mapper) ?: return SerialisedWaypoint()
            return SerialisedWaypoint(
                wptInfo.wptId, wptInfo.wptName,
                pos.x.toInt().toShort(), pos.y.toInt().toShort()
            )
        }
    }
}