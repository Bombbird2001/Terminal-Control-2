package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.byte
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.get
import ktx.ashley.has
import ktx.collections.GdxArray

/**
 * Array to store runnables for delayed retrieval of entities based on their references (as per below) till after the entities
 * have been loaded and created
 * */
internal val delayedEntityRetrieval = GdxArray<() -> Unit>()

/** Runs all functions stored in the delayed entity retrieval array, and clears the array after running */
fun runDelayedEntityRetrieval() {
    for (i in 0 until delayedEntityRetrieval.size) delayedEntityRetrieval[i]()
    delayedEntityRetrieval.clear()
}

/** Data class for storing runway reference information (airport and runway ID) */
@JsonClass(generateAdapter = true)
data class RunwayRefJSON(val arptId: Byte, val rwyId: Byte) {
    /**
     * Convenience function for retrieving the appropriate runway entity based on [arptId] and [rwyId]
     *
     * This function should only be run after the airport and runway children entities have been loaded into the airport;
     * i.e. during invocation of [runDelayedEntityRetrieval]
     */
    fun getRunwayEntity(): Entity {
        return GAME.gameServer?.airports?.get(arptId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(rwyId)?.entity ?: Entity()
    }
}

/**
 * Function to get a [RunwayRefJSON] from the input runway entity
 * @param rwy the runway entity to turn into JSON
 * @return the runway JSON reference object
 * */
fun toRunwayRefJSON(rwy: Entity): RunwayRefJSON {
    val rwyInfo = rwy[RunwayInfo.mapper]
    val arptId = rwyInfo?.airport?.entity?.get(AirportInfo.mapper)?.arptId ?: (-1).byte
    return RunwayRefJSON(arptId, rwyInfo?.rwyId ?: -1)
}

/** Data class for storing approach reference information (airport ID and approach name) */
@JsonClass(generateAdapter = true)
data class ApproachRefJSON(val arptId: Byte, val appName: String, val visRwyId: Byte?) {
    /**
     * Method to retrieve the runway entity based on this approach reference, delayed to after the entities themselves
     * have been fully loaded
     * @param component the component to set the approach entity to - only component subclasses defined in this method will
     * have their approach entity field set after the delayed retrieval
     */
    fun delayedApproachEntityRetrieval(component: Component) {
        delayedEntityRetrieval.add {
            val app = if (visRwyId != null) GAME.gameServer?.airports?.get(arptId)?.entity?.get(RunwayChildren.mapper)
                ?.rwyMap?.get(visRwyId)?.entity?.get(VisualApproach.mapper)?.visual
            else GAME.gameServer?.airports?.get(arptId)?.entity?.get(ApproachChildren.mapper)?.approachMap?.get(appName)?.entity
            if (app == null) return@add
            when (component) {
                is VisualCaptured -> {
                    if (visRwyId != null) component.visApp = app
                    else component.parentApp = app
                }
                is LocalizerArmed -> component.locApp = app
                is LocalizerCaptured -> component.locApp = app
                is GlideSlopeArmed -> component.gsApp = app
                is GlideSlopeCaptured -> component.gsApp = app
                is StepDownApproach -> component.stepDownApp = app
                is CirclingApproach -> component.circlingApp = app
            }
        }
    }
}

/**
 * Function to get a [ApproachRefJSON] from the input approach entity
 * @param app the approach entity to turn into JSON
 * @return the approach JSON reference object
 */
fun toApproachRefJSON(app: Entity): ApproachRefJSON {
    val appInfo = app[ApproachInfo.mapper]
    return ApproachRefJSON(appInfo?.airportId ?: -1, appInfo?.approachName ?: "", if (app.has(Visual.mapper)) appInfo?.rwyId else null)
}

/** Data class for storing Vector2 information */
@JsonClass(generateAdapter = true)
data class Vector2JSON(val x: Float, val y: Float)

/** Adapter object for serialization between [Vector2] and [Vector2JSON] */
object Vector2Adapter {
    @ToJson
    fun toJson(vector2: Vector2): Vector2JSON {
        return Vector2JSON(vector2.x, vector2.y)
    }

    @FromJson
    fun fromJson(vector2JSON: Vector2JSON): Vector2 {
        return Vector2(vector2JSON.x, vector2JSON.y)
    }
}
