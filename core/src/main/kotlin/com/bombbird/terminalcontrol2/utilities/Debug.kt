package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.remove
import ktx.collections.toGdxArray
import kotlin.math.roundToInt

/** A simple debug helper file */
private val minAltSectorFamily: Family = allOf(MinAltSectorInfo::class).get()

/** Print all the STARs from an airport's [entity] */
fun printAirportSTARs(entity: Entity) {
    entity[STARChildren.mapper]?.starMap?.apply {
        for (obj in this) {
            val entry = obj.value
            println("${entry.name} ${entry.pronunciation} ${entry.timeRestriction}")
            println(entry.routeLegs)
            println(entry.rwyLegs)
        }
    }
}

/** Print all the SIDs from an airport's [entity] */
fun printAirportSIDs(entity: Entity) {
    entity[SIDChildren.mapper]?.sidMap?.apply {
        for (obj in this) {
            val entry = obj.value
            println("${entry.name} ${entry.pronunciation} ${entry.timeRestriction}")
            println(entry.rwyInitialClimbs)
            println(entry.rwyLegs)
            println(entry.routeLegs)
            println(entry.outboundLegs)
        }
    }
}

/** Print all the approaches from an airport's [entity] */
fun printAirportApproaches(entity: Entity) {
    entity[ApproachChildren.mapper]?.approachMap?.let { app ->
        for (obj in app) {
            val appEntity = obj.value.entity
            val appInfo = appEntity[ApproachInfo.mapper]
            println(appInfo?.approachName)
            println((convertWorldAndRenderDeg(appEntity[Direction.mapper]?.trackUnitVector?.angleDeg() ?: 0f) + 180 + MAG_HDG_DEV).roundToInt().toShort())
            println(appEntity[Localizer.mapper]?.maxDistNm)
            appEntity[GlideSlope.mapper]?.apply {
                println("$glideAngle $offsetNm $maxInterceptAlt")
            }
            appEntity[LineUpDist.mapper]?.apply {
                println(lineUpDistNm)
            }
            appEntity[StepDown.mapper]?.apply {
                println(altAtDist.toGdxArray())
            }
            val mins = appEntity[Minimums.mapper]
            println("${mins?.baroAltFt} ${mins?.rvrM}")
            println(obj.value.transitions)
            println(obj.value.routeLegs)
            println(obj.value.missedLegs)
        }
    }
}

/**
 * Toggles the color of the MVA(s) tapped, and also prints their details out
 * @param x the x coordinate of the screen tap location
 * @param y the y coordinate of the screen tap location
 * @param unprojectFromRadarCamera the function that maps tap location to world coordinates
 * @param clientEngine the engine running on the client (not the server engine)
 * */
fun toggleMinAltSectorsOnClick(x: Float, y: Float, unprojectFromRadarCamera: (Float, Float) -> Vector2, clientEngine: Engine) {
    unprojectFromRadarCamera(x, y).apply { println("${pxToNm(this.x)} ${pxToNm(this.y)}") }
    for (mva in clientEngine.getEntitiesFor(minAltSectorFamily)) {
        mva[GPolygon.mapper]?.vertices?.apply {
            if (Polygon(this).contains(unprojectFromRadarCamera(x, y))) {
                println("${mva[MinAltSectorInfo.mapper]?.minAltFt} ${this.map { pxToNm(it) }.toGdxArray()}")
                mva[SRColor.mapper]?.apply {
                    color = if (color == Color.GRAY) {
                        mva += RenderLast()
                        Color.ORANGE
                    } else {
                        mva.remove<RenderLast>()
                        Color.GRAY
                    }
                }
                mva[GenericLabel.mapper]?.apply {
                    updateStyle(if (label.style.fontColor == Color.ORANGE) "MinAltSector" else "MinAltSectorRestr")
                }
            }
        }
    }
}
