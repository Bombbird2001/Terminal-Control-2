package com.bombbird.terminalcontrol2.json

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.byte
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import ktx.ashley.get

/** Data class for storing runway reference information (airport and runway ID) */
@JsonClass(generateAdapter = true)
data class RunwayRefJSON(val arptId: Byte, val rwyId: Byte) {
    /**
     * Method to get a runway entity from this input runway JSON object
     * @return the runway entity
     */
    fun toRunwayEntity(): Entity {
        val arpt = GAME.gameServer?.airports?.get(arptId) ?: return Entity()
        return arpt.entity[RunwayChildren.mapper]?.rwyMap?.get(rwyId)?.entity ?: Entity()
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
data class ApproachRefJSON(val arptId: Byte, val appName: String) {
    /**
     * Method to get an approach entity from this input approach JSON object
     * @return the approach entity
     */
    fun toApproachEntity(): Entity {
        val arpt = GAME.gameServer?.airports?.get(arptId) ?: return Entity()
        return arpt.entity[ApproachChildren.mapper]?.approachMap?.get(appName)?.entity ?: Entity()
    }
}

/**
 * Function to get a [ApproachRefJSON] from the input approach entity
 * @param app the approach entity to turn into JSON
 * @return the approach JSON reference object
 */
fun toApproachRefJSON(app: Entity): ApproachRefJSON {
    val appInfo = app[ApproachInfo.mapper]
    return ApproachRefJSON(appInfo?.airportId ?: -1, appInfo?.approachName ?: "")
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
