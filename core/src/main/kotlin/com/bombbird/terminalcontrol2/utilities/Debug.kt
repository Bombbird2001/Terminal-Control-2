package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.systems.TrafficSystemInterval
import ktx.ashley.*
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
 */
fun toggleMinAltSectorsOnClick(x: Float, y: Float, unprojectFromRadarCamera: (Float, Float) -> Vector2, clientEngine: Engine) {
    unprojectFromRadarCamera(x, y).apply { println("${pxToNm(this.x)} ${pxToNm(this.y)}") }
    for (mva in clientEngine.getEntitiesFor(minAltSectorFamily)) {
        mva[GPolygon.mapper]?.apply {
            if (polygonObj.contains(unprojectFromRadarCamera(x, y))) {
                println("${mva[MinAltSectorInfo.mapper]?.minAltFt} ${vertices.map { pxToNm(it) }.toGdxArray()}")
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

/**
 * Renders the MVA exclusion zones for the currently selected aircraft
 * @param shapeRenderer the [ShapeRenderer] to use to render the zones
 */
fun renderSelectedAircraftRouteZones(shapeRenderer: ShapeRenderer) {
    shapeRenderer.color = Color.WHITE
    GAME.gameServer?.aircraft?.get(CLIENT_SCREEN?.selectedAircraft?.entity?.get(AircraftInfo.mapper)?.icaoCallsign)?.apply {
        entity[ArrivalRouteZone.mapper]?.let {
            for (i in 0 until it.starZone.size) shapeRenderer.polygon(it.starZone[i].entity[GPolygon.mapper]?.vertices ?: continue)
            for (i in 0 until it.appZone.size) shapeRenderer.polygon(it.appZone[i].entity[GPolygon.mapper]?.vertices ?: continue)
        }
        entity[DepartureRouteZone.mapper]?.let {
            for (i in 0 until it.sidZone.size) shapeRenderer.polygon(it.sidZone[i].entity[GPolygon.mapper]?.vertices ?: continue)
        }
    }
}

/**
 * Renders the wake turbulence zones for all aircraft in game; both zones stored in the aircraft's [WakeTrail] component,
 * and that stored in [TrafficSystemInterval.conflictManager]'s wakeManager
 * @param shapeRenderer the [ShapeRenderer] to use to render the zones
 */
fun renderWakeZones(shapeRenderer: ShapeRenderer) {
    shapeRenderer.color = Color.RED
    getEngine(false).getSystem<TrafficSystemInterval>().conflictManager.wakeManager.wakeLevels.forEach {
        for (i in 0 until it.size) {
            it[0].entity[GPolygon.mapper]?.let { polygon -> shapeRenderer.polygon(polygon.vertices) }
        }
    }
    shapeRenderer.color = Color.WHITE
    GAME.gameServer?.aircraft?.values()?.toArray()?.forEach { aircraft ->
        for (zone in Queue.QueueIterator(aircraft.entity[WakeTrail.mapper]?.wakeZones)) zone.second?.entity?.get(GPolygon.mapper)?.let {
            shapeRenderer.polygon(it.vertices)
        }
    }
}
