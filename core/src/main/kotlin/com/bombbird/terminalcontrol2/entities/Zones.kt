package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import com.bombbird.terminalcontrol2.utilities.nmToPx
import ktx.ashley.*
import kotlin.math.roundToInt

/** Class for storing a runway's approach NOZ information */
class ApproachNormalOperatingZone(posX: Float, posY: Float, appHdg: Short, private val widthNm: Float, private val lengthNm: Float, onClient: Boolean = true) {
    val entity = getEngine(onClient).entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Direction> {
            trackUnitVector = Vector2(Vector2.Y).rotateDeg(180 - (appHdg - MAG_HDG_DEV))
        }
        with<GPolygon> {
            val halfWidthVec = Vector2(Vector2.Y).rotateDeg(270 - (appHdg - MAG_HDG_DEV)).scl(nmToPx(widthNm / 2))
            val lengthVec = Vector2(Vector2.Y).rotateDeg(180 - (appHdg - MAG_HDG_DEV)).scl(nmToPx(lengthNm))
            vertices = floatArrayOf(posX + halfWidthVec.x, posY + halfWidthVec.y,
                posX + halfWidthVec.x + lengthVec.x, posY + halfWidthVec.y + lengthVec.y,
                posX - halfWidthVec.x + lengthVec.x, posY - halfWidthVec.y + lengthVec.y,
                posX - halfWidthVec.x, posY - halfWidthVec.y)
        }
        if (onClient) {
            with<SRColor> {
                color = Color.GREEN
            }
            with<DoNotRender>()
        }
    }

    /** Gets a [SerialisedApproachNOZ] from current state */
    fun getSerialisableObject(): SerialisedApproachNOZ {
        entity.apply {
            val pos = get(Position.mapper) ?: return SerialisedApproachNOZ()
            val dir = get(Direction.mapper) ?: return SerialisedApproachNOZ()
            val appHdg = convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + 180 + MAG_HDG_DEV
            return SerialisedApproachNOZ(pos.x, pos.y, appHdg.roundToInt().toShort(), widthNm, lengthNm)
        }
    }

    companion object {
        /**
         * De-serialises a [SerialisedApproachNOZ] and creates a new [ApproachNormalOperatingZone] object from it
         * @param serialisedApproachNOZ the object to de-serialise
         * @return a newly created [ApproachNormalOperatingZone] object
         * */
        fun fromSerialisedObject(serialisedApproachNOZ: SerialisedApproachNOZ): ApproachNormalOperatingZone {
            return ApproachNormalOperatingZone(serialisedApproachNOZ.posX, serialisedApproachNOZ.posY,
                serialisedApproachNOZ.appHdg, serialisedApproachNOZ.widthNm, serialisedApproachNOZ.lengthNm)
        }
    }

    /** Object that contains [ApproachNormalOperatingZone] data to be serialised by Kryo */
    class SerialisedApproachNOZ(val posX: Float = 0f, val posY: Float = 0f, val appHdg: Short = 0,
                                val widthNm: Float = 0f, val lengthNm: Float = 0f)
}

/** Class for storing a runway's departure NOZ information */
class DepartureNormalOperatingZone(posX: Float, posY: Float, appHdg: Short, private val widthNm: Float, private val lengthNm: Float, onClient: Boolean = true) {
    val entity = getEngine(onClient).entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Direction> {
            trackUnitVector = Vector2(Vector2.Y).rotateDeg(-(appHdg - MAG_HDG_DEV))
        }
        with<GPolygon> {
            val halfWidthVec = Vector2(Vector2.Y).rotateDeg(90 - (appHdg - MAG_HDG_DEV)).scl(nmToPx(widthNm / 2))
            val lengthVec = Vector2(Vector2.Y).rotateDeg(-(appHdg - MAG_HDG_DEV)).scl(nmToPx(lengthNm))
            vertices = floatArrayOf(posX + halfWidthVec.x, posY + halfWidthVec.y,
                posX + halfWidthVec.x + lengthVec.x, posY + halfWidthVec.y + lengthVec.y,
                posX - halfWidthVec.x + lengthVec.x, posY - halfWidthVec.y + lengthVec.y,
                posX - halfWidthVec.x, posY - halfWidthVec.y)
        }
        if (onClient) {
            with<SRColor> {
                color = Color.CYAN
            }
            with<DoNotRender>()
        }
    }

    /** Gets a [SerialisedDepartureNOZ] from current state */
    fun getSerialisableObject(): SerialisedDepartureNOZ {
        entity.apply {
            val pos = get(Position.mapper) ?: return SerialisedDepartureNOZ()
            val dir = get(Direction.mapper) ?: return SerialisedDepartureNOZ()
            val appHdg = convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
            return SerialisedDepartureNOZ(pos.x, pos.y, appHdg.roundToInt().toShort(), widthNm, lengthNm)
        }
    }

    companion object {
        /**
         * De-serialises a [SerialisedDepartureNOZ] and creates a new [DepartureNormalOperatingZone] object from it
         * @param serialisedDepartureNOZ the object to de-serialise
         * @return a newly created [ApproachNormalOperatingZone] object
         * */
        fun fromSerialisedObject(serialisedDepartureNOZ: SerialisedDepartureNOZ): DepartureNormalOperatingZone {
            return DepartureNormalOperatingZone(serialisedDepartureNOZ.posX, serialisedDepartureNOZ.posY,
                serialisedDepartureNOZ.appHdg, serialisedDepartureNOZ.widthNm, serialisedDepartureNOZ.lengthNm)
        }
    }

    /** Object that contains [DepartureNormalOperatingZone] data to be serialised by Kryo */
    class SerialisedDepartureNOZ(val posX: Float = 0f, val posY: Float = 0f, val appHdg: Short = 0,
                                val widthNm: Float = 0f, val lengthNm: Float = 0f)
}

/**
 * Class for storing NTZ information
 *
 * Note that NTZs are purely for aesthetic purposes and will not handle any sort of logic or positional checking
 * */
class NoTransgressionZone(posX: Float, posY: Float, appHdg: Short, private val widthNm: Float, private val lengthNm: Float, onClient: Boolean = true) {
    val entity = getEngine(onClient).entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Direction> {
            trackUnitVector = Vector2(Vector2.Y).rotateDeg(-(appHdg - MAG_HDG_DEV))
        }
        if (onClient) {
            with<GPolygon> {
                val halfWidthVec = Vector2(Vector2.Y).rotateDeg(90 - (appHdg - MAG_HDG_DEV)).scl(nmToPx(widthNm / 2))
                val lengthVec = Vector2(Vector2.Y).rotateDeg(-(appHdg - MAG_HDG_DEV)).scl(nmToPx(lengthNm))
                vertices = floatArrayOf(posX + halfWidthVec.x, posY + halfWidthVec.y,
                    posX + halfWidthVec.x + lengthVec.x, posY + halfWidthVec.y + lengthVec.y,
                    posX - halfWidthVec.x + lengthVec.x, posY - halfWidthVec.y + lengthVec.y,
                    posX - halfWidthVec.x, posY - halfWidthVec.y)
            }
            with<SRColor> {
                color = Color.RED
            }
        }
    }

    /** Gets a [SerialisedNTZ] from current state */
    fun getSerialisableObject(): SerialisedNTZ {
        entity.apply {
            val pos = get(Position.mapper) ?: return SerialisedNTZ()
            val dir = get(Direction.mapper) ?: return SerialisedNTZ()
            val appHdg = convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
            return SerialisedNTZ(pos.x, pos.y, appHdg.roundToInt().toShort(), widthNm, lengthNm)
        }
    }

    companion object {
        /**
         * De-serialises a [SerialisedNTZ] and creates a new [NoTransgressionZone] object from it
         * @param serialisedNTZ the object to de-serialise
         * @return a newly created [NoTransgressionZone] object
         * */
        fun fromSerialisedObject(serialisedNTZ: SerialisedNTZ): NoTransgressionZone {
            return NoTransgressionZone(serialisedNTZ.posX, serialisedNTZ.posY,
                serialisedNTZ.appHdg, serialisedNTZ.widthNm, serialisedNTZ.lengthNm)
        }
    }

    /** Object that contains [NoTransgressionZone] data to be serialised by Kryo */
    class SerialisedNTZ(val posX: Float = 0f, val posY: Float = 0f, val appHdg: Short = 0,
                                 val widthNm: Float = 0f, val lengthNm: Float = 0f)
}
