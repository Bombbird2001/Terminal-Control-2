package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.*
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.GeometryUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Queue
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.systems.TrafficSystemInterval
import com.bombbird.terminalcontrol2.systems.TrajectorySystemInterval
import com.esotericsoftware.minlog.Log
import ktx.ashley.*
import ktx.collections.toGdxArray
import ktx.scene2d.Scene2DSkin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.roundToInt

val alreadyPrintedErrors = HashSet<String>()

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

/** Renders the MVA exclusion zone minimum altitude for the current selected aircraft */
fun renderRouteZoneAlts() {
    GAME.gameServer?.aircraft?.get(CLIENT_SCREEN?.selectedAircraft?.entity?.get(AircraftInfo.mapper)?.icaoCallsign)?.apply {
        entity[ArrivalRouteZone.mapper]?.let {
            for (i in 0 until it.starZone.size) {
                val polygon = it.starZone[i].entity[GPolygon.mapper] ?: continue
                val minAlt = it.starZone[i].entity[Altitude.mapper]?.altitudeFt ?: continue
                Label(minAlt.toString(), Scene2DSkin.defaultSkin, "GameInfo").apply {
                    val centroid = Vector2()
                    GeometryUtils.polygonCentroid(polygon.vertices, 0, polygon.vertices.size, centroid)
                    setPosition(centroid.x, centroid.y)
                    draw(GAME.batch, 1f)
                }
            }
            for (i in 0 until it.appZone.size) {
                val polygon = it.appZone[i].entity[GPolygon.mapper] ?: continue
                val minAlt = it.appZone[i].entity[Altitude.mapper]?.altitudeFt ?: continue
                Label(minAlt.toString(), Scene2DSkin.defaultSkin, "GameInfo").apply {
                    val centroid = Vector2()
                    GeometryUtils.polygonCentroid(polygon.vertices, 0, polygon.vertices.size, centroid)
                    setPosition(centroid.x, centroid.y)
                    draw(GAME.batch, 1f)
                }
            }
        }
        entity[DepartureRouteZone.mapper]?.let {
            for (i in 0 until it.sidZone.size) {
                val polygon = it.sidZone[i].entity[GPolygon.mapper] ?: continue
                val minAlt = it.sidZone[i].entity[Altitude.mapper]?.altitudeFt ?: continue
                Label(minAlt.toString(), Scene2DSkin.defaultSkin, "GameInfo").apply {
                    val centroid = Vector2()
                    GeometryUtils.polygonCentroid(polygon.vertices, 0, polygon.vertices.size, centroid)
                    setPosition(centroid.x, centroid.y)
                    draw(GAME.batch, 1f)
                }
            }
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

/**
 * Renders all available sectors for the map
 * @param shapeRenderer the [ShapeRenderer] to use to render the zones
 */
fun renderAllSectors(shapeRenderer: ShapeRenderer) {
    val colors = arrayOf(Color.WHITE, Color.BLUE, Color.YELLOW, Color.PURPLE, Color.CYAN, Color.MAGENTA)
    GAME.gameServer?.sectors?.let {
        for (j in it.size downTo 1) {
            shapeRenderer.color = colors[j - 1]
            val sectors = it[j.toByte()]
            for (i in 0 until sectors.size) {
                sectors[i].entity[GPolygon.mapper]?.let { polygon -> shapeRenderer.polygon(polygon.vertices) }
            }
        }
    }
}

/**
 * Renders all available ACC sectors for the map
 * @param shapeRenderer the [ShapeRenderer] to use to render the zones
 */
fun renderAllACCSectors(shapeRenderer: ShapeRenderer) {
    val colors = arrayOf(Color.WHITE, Color.BLUE, Color.YELLOW, Color.PURPLE, Color.CYAN, Color.MAGENTA, Color.CORAL, Color.GOLD, Color.CHARTREUSE, Color.FIREBRICK, Color.MAROON)
    GAME.gameServer?.accSectors?.let {
        for (i in 0 until it.size) {
            val sector = it[i]
            shapeRenderer.color = colors[i]
            sector.entity[GPolygon.mapper]?.let { polygon -> shapeRenderer.polygon(polygon.vertices) }
        }
    }
}

/**
 * Renders all trajectory prediction points
 * @param shapeRenderer the [ShapeRenderer] to use to render the points
 */
fun renderAllTrajectoryPoints(shapeRenderer: ShapeRenderer) {
    getEngine(false).getSystem<TrajectorySystemInterval>().trajectoryTimeStates.let {
        shapeRenderer.color = Color.BROWN
        for (i in it.indices) {
            for (j in 0 until it[i].size) {
                for (k in 0 until it[i][j].size) {
                    val pos = it[i][j][k].entity[Position.mapper] ?: continue
                    shapeRenderer.circle(pos.x, pos.y, 5f)
                }
            }
        }
    }
}

/**
 * Helper extension function that logs down missing components - use for debugging components that should be present
 * but are missing
 * @param mapper the [ComponentMapper] of the component to get
 */
inline fun <reified T : Component> Entity.getOrLogMissing(mapper: ComponentMapper<T>): T? {
    val comp: T? = mapper.get(this)
    if (comp == null && Log.WARN) {
        val trace = Thread.currentThread().stackTrace
        val errorCat = "${trace[1].fileName}:${trace[1].lineNumber}"
        if (!alreadyPrintedErrors.contains(errorCat)) {
            FileLog.warn("MissingComponent", "Component of type ${T::class.simpleName} is required but missing\n" +
                    Exception().stackTraceToString())
            alreadyPrintedErrors.add(errorCat)
        }
    }
    return comp
}

/** Checks whether code is running on main rendering thread */
fun isOnRenderingThread(): Boolean {
    return Thread.currentThread().name == "main" || Thread.currentThread().name.contains("GLThread")
}

/** Checks whether code is running on game server update thread */
fun isOnGameServerThread(): Boolean {
    return Thread.currentThread().name == GAME_SERVER_THREAD_NAME
}

/**
 * Create and add an [Entity] to the [Engine], additionally also checking that the code is running on the main rendering
 * thread (if on client) or game server update thread (if on server)
 *
 * @param onClient whether the entity is created on the client or server
 * @param configure inlined function with the created [EngineEntity] as the receiver to allow further configuration of
 * the [Entity]. The [EngineEntity] holds the created [Entity] and this [Engine].
 * @return the created [Entity].
 */
@OptIn(ExperimentalContracts::class)
inline fun Engine.entityOnMainThread(onClient: Boolean, configure: EngineEntity.() -> Unit = {}): Entity {
    contract { callsInPlace(configure, InvocationKind.EXACTLY_ONCE) }

    if (onClient && !isOnRenderingThread()) {
        FileLog.warn("EngineEntity", "Entity created on non-rendering thread\n" +
                Exception().stackTraceToString())
    } else if (!onClient && !isOnGameServerThread()) {
        FileLog.warn("EngineEntity", "Entity created on non-game server thread\n" +
                Exception().stackTraceToString())
    }

    val entity = createEntity()
    EngineEntity(this, entity).configure()
    addEntity(entity)
    return entity
}

/**
 * Removes an entity from the engine, checking if it is on the main rendering thread (if on client) or game server
 * update thread (if on server)
 * @param entity the entity to remove
 * @param onClient whether the entity is removed on the client or server
 */
fun Engine.removeEntityOnMainThread(entity: Entity, onClient: Boolean) {
    if (onClient && !isOnRenderingThread()) {
        FileLog.warn("EngineEntity", "Entity removed on non-rendering thread\n" +
                Exception().stackTraceToString())
    } else if (!onClient && !isOnGameServerThread()) {
        FileLog.warn("EngineEntity", "Entity removed on non-game server thread\n" +
                Exception().stackTraceToString())
    }

    removeEntity(entity)
}

/**
 * Removes all entities from the engine, checking if it is on the main rendering thread (if on client) or game server
 * @param onClient whether the entities are removed on the client or server
 */
fun Engine.removeAllEntitiesOnMainThread(onClient: Boolean) {
    if (onClient && !isOnRenderingThread()) {
        FileLog.warn("EngineEntity", "Entities removed on non-rendering thread\n" +
                Exception().stackTraceToString())
    } else if (!onClient && !isOnGameServerThread()) {
        FileLog.warn("EngineEntity", "Entities removed on non-game server thread\n" +
                Exception().stackTraceToString())
    }

    removeAllEntities()
}

/**
 * Removes all systems from the engine, checking if it is on the main rendering thread (if on client) or game server
 * @param onClient whether the systems are removed on the client or server
 */
fun Engine.removeAllSystemsOnMainThread(onClient: Boolean) {
    if (onClient && !isOnRenderingThread()) {
        FileLog.warn("EngineEntity", "Systems removed on non-rendering thread\n" +
                Exception().stackTraceToString())
    } else if (!onClient && !isOnGameServerThread()) {
        FileLog.warn("EngineEntity", "Systems removed on non-game server thread\n" +
                Exception().stackTraceToString())
    }

    removeAllSystems()
}
