package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.GeometryUtils
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.getEngine
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with
import kotlin.math.roundToInt

/** Sector class that creates a sector entity with the required components for implementing an MVA or restricted area */
class MinAltSector(minAlt: Int?, polygonBoundary: ShortArray?, circleX: Short = 0, circleY: Short = 0, radiusBoundary: Float = 0f,
                   labelX: Short? = null, labelY: Short? = null, restr: Boolean, onClient: Boolean = true): SerialisableEntity<MinAltSector.SerialisedMinAltSector> {
    val entity = getEngine(onClient).entity {
        with<MinAltSectorInfo> {
            minAltFt = minAlt
            restricted = restr
        }
        val polygonCentroid = Vector2()
        if (polygonBoundary != null) {
            with<GPolygon> {
                // Use polygon if provided
                vertices = polygonBoundary.map { it.toFloat() }.toFloatArray()
                if (onClient && (labelX == null || labelY == null)) GeometryUtils.polygonCentroid(vertices, 0, vertices.size, polygonCentroid)
            }
            with<Position> {
                if (labelX == null || labelY == null) {
                    x = polygonCentroid.x
                    y = polygonCentroid.y
                } else {
                    x = labelX.toFloat()
                    y = labelY.toFloat()
                }
            }
            if (labelX != null && labelY != null) with<CustomPosition> {
                x = labelX.toFloat()
                y = labelY.toFloat()
            }
        } else {
            with<GCircle> {
                // Otherwise, use circle
                radius = radiusBoundary
            }
            with<Position> {
                x = circleX.toFloat()
                y = circleY.toFloat()
            }
        }
        if (onClient) {
            with<SRColor> {
                color = if (restr) Color.ORANGE else Color.GRAY
            }
            with<GenericLabel> {
                updateStyle(if (restr) "MinAltSectorRestr" else "MinAltSector")
                updateText(if (minAlt == null) "UNL" else (minAlt / 100f).roundToInt().toString())
                xOffset = -label.prefWidth / 2
                yOffset = 0f
            }
            with<ConstantZoomSize>()
        }
    }

    companion object {
        /** De-serialises a [SerialisedMinAltSector] and creates a new [MinAltSector] object from it */
        fun fromSerialisedObject(serialisedMinAltSector: SerialisedMinAltSector): MinAltSector {
            return MinAltSector(
                serialisedMinAltSector.minAlt,
                serialisedMinAltSector.polygonBoundary,
                serialisedMinAltSector.circleX, serialisedMinAltSector.circleY, serialisedMinAltSector.radiusBoundary,
                serialisedMinAltSector.labelX, serialisedMinAltSector.labelY,
                serialisedMinAltSector.restr
            )
        }
    }

    /** Object that contains [MinAltSector] data to be serialised by Kryo */
    class SerialisedMinAltSector(val minAlt: Int? = null,
                                 val polygonBoundary: ShortArray? = null,
                                 val circleX: Short = 0, val circleY: Short = 0, val radiusBoundary: Float = 0f,
                                 val labelX: Short? = null, val labelY: Short? = null,
                                 val restr: Boolean = false
    )

    /**
     * Returns a default empty [SerialisedMinAltSector] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedMinAltSector {
        Gdx.app.log("MinAltSector", "Empty serialised minAltSector returned due to missing $missingComponent component")
        return SerialisedMinAltSector()
    }

    /** Gets a [SerialisedMinAltSector] from current state */
    override fun getSerialisableObject(): SerialisedMinAltSector {
        entity.apply {
            val sectorInfo = get(MinAltSectorInfo.mapper) ?: return emptySerialisableObject("SectorInfo")
            val pos = get(Position.mapper) ?: return emptySerialisableObject("Position")
            val polygon = get(GPolygon.mapper)
            val circle = get(GCircle.mapper)
            val customPos = get(CustomPosition.mapper)
            return SerialisedMinAltSector(
                sectorInfo.minAltFt,
                polygon?.vertices?.map { it.toInt().toShort() }?.toShortArray(),
                pos.x.toInt().toShort(), pos.y.toInt().toShort(), circle?.radius ?: 0f,
                customPos?.x?.toInt()?.toShort(), customPos?.y?.toInt()?.toShort(),
                sectorInfo.restricted
            )
        }
    }
}