package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.get
import ktx.ashley.with

/** Waypoint class that creates a waypoint entity with the required components on instantiation */
class Waypoint(id: Short, name: String, posX: Short, posY: Short, onClient: Boolean = true): SerialisableEntity<Waypoint.SerialisedWaypoint> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
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

    /**
     * Returns a default empty [SerialisedWaypoint] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedWaypoint {
        FileLog.info("Waypoint", "Empty serialised waypoint returned due to missing $missingComponent component")
        return SerialisedWaypoint()
    }

    /** Gets a [SerialisedWaypoint] from current state */
    override fun getSerialisableObject(): SerialisedWaypoint {
        entity.apply {
            val wptInfo = get(WaypointInfo.mapper) ?: return emptySerialisableObject("WaypointInfo")
            val pos = get(Position.mapper) ?: return emptySerialisableObject("Position")
            return SerialisedWaypoint(
                wptInfo.wptId, wptInfo.wptName,
                pos.x.toInt().toShort(), pos.y.toInt().toShort()
            )
        }
    }

    /** Object that contains [Waypoint] data for mapping name to wptId, to be serialised by Kryo */
    class SerialisedWaypointMapping(val name: String = "", val wptId: Short = 0)

    fun getMappingSerialisableObject(): SerialisedWaypointMapping {
        val wptInfo = entity[WaypointInfo.mapper] ?: return SerialisedWaypointMapping()
        return SerialisedWaypointMapping(wptInfo.wptName, wptInfo.wptId)
    }
}