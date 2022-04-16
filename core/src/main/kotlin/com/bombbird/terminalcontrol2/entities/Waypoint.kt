package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Waypoint class that creates a waypoint entity with the required components on instantiation */
class Waypoint(name: String, posX: Short, posY: Short, onClient: Boolean = true) {
    val entity = (if (onClient) Constants.CLIENT_ENGINE else Constants.SERVER_ENGINE).entity {
        with<WaypointInfo> {
            wptName = name
        }
        with<Position> {
            x = posX.toFloat()
            y = posY.toFloat()
        }
        with<GenericLabel> {
            updateStyle("Waypoint")
            updateText(name)
            xOffset = -label.prefWidth / 2
            yOffset = 25f
        }
        with<GCircle> {
            radius = 10f
        }
        with<SRColor> {
            color = Color.GRAY
        }
    }

    companion object {
        /** De-serialises a [SerialisedWaypoint] and creates a new [Waypoint] object from it */
        fun fromSerialisedObject(serialisedWpt: SerialisedWaypoint): Waypoint {
            return Waypoint(
                serialisedWpt.name,
                serialisedWpt.posX, serialisedWpt.posY
            )
        }
    }

    /** Object that contains [Waypoint] data to be serialised by Kryo */
    class SerialisedWaypoint(val name: String = "",
                             val posX: Short = 0, val posY: Short = 0
    )

    /** Gets a [SerialisedWaypoint] from current state */
    fun getSerialisableObject(): SerialisedWaypoint {
        entity.apply {
            val wptInfo = get(WaypointInfo.mapper)!!
            val pos = get(Position.mapper)!!
            return SerialisedWaypoint(
                wptInfo.wptName,
                pos.x.toInt().toShort(), pos.y.toInt().toShort()
            )
        }
    }
}