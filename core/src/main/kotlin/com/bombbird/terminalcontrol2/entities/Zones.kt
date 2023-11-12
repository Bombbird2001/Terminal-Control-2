package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.WAKE_LENGTH_EXTENSION_NM
import com.bombbird.terminalcontrol2.global.WAKE_WIDTH_NM
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.*
import kotlin.math.roundToInt

/** Zone interface for implementing zone related functions such as position checking */
interface Zone {
    /**
     * Checks whether the polygon of this zone contains the input coordinates
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if ([x], [y]) is inside the zone polygon, else false
     */
    fun contains(x: Float, y: Float): Boolean
}

/** Class for storing a runway's approach NOZ information */
class ApproachNormalOperatingZone(posX: Float, posY: Float, appHdg: Short, private val widthNm: Float, private val lengthNm: Float,
                                  onClient: Boolean = true): Zone, SerialisableEntity<ApproachNormalOperatingZone.SerialisedApproachNOZ> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
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
            with<DoNotRenderShape>()
        }
    }

    /**
     * Returns a default empty [SerialisedApproachNOZ] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedApproachNOZ {
        FileLog.info("Zones", "Empty serialised approachNOZ returned due to missing $missingComponent component")
        return SerialisedApproachNOZ()
    }

    /** Gets a [SerialisedApproachNOZ] from current state */
    override fun getSerialisableObject(): SerialisedApproachNOZ {
        entity.apply {
            val pos = get(Position.mapper) ?: return emptySerialisableObject("Position")
            val dir = get(Direction.mapper) ?: return emptySerialisableObject("Direction")
            val appHdg = convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + 180 + MAG_HDG_DEV
            return SerialisedApproachNOZ(pos.x, pos.y, appHdg.roundToInt().toShort(), widthNm, lengthNm)
        }
    }

    /**
     * Checks whether the polygon of this Approach NOZ contains the input coordinates
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if ([x], [y]) is inside the zone polygon, else false
     */
    override fun contains(x: Float, y: Float): Boolean {
       return entity[GPolygon.mapper]?.polygonObj?.contains(x, y) == true
    }

    companion object {
        /**
         * De-serialises a [SerialisedApproachNOZ] and creates a new [ApproachNormalOperatingZone] object from it
         * @param serialisedApproachNOZ the object to de-serialise
         * @return a newly created [ApproachNormalOperatingZone] object
         */
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
class DepartureNormalOperatingZone(posX: Float, posY: Float, appHdg: Short, private val widthNm: Float, private val lengthNm: Float,
                                   onClient: Boolean = true): Zone, SerialisableEntity<DepartureNormalOperatingZone.SerialisedDepartureNOZ> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
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
            with<DoNotRenderShape>()
        }
    }

    /**
     * Returns a default empty [SerialisedDepartureNOZ] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedDepartureNOZ {
        FileLog.info("Zones", "Empty serialised departureNOZ returned due to missing $missingComponent component")
        return SerialisedDepartureNOZ()
    }

    /** Gets a [SerialisedDepartureNOZ] from current state */
    override fun getSerialisableObject(): SerialisedDepartureNOZ {
        entity.apply {
            val pos = get(Position.mapper) ?: return emptySerialisableObject("Position")
            val dir = get(Direction.mapper) ?: return emptySerialisableObject("Direction")
            val appHdg = convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
            return SerialisedDepartureNOZ(pos.x, pos.y, appHdg.roundToInt().toShort(), widthNm, lengthNm)
        }
    }

    /**
     * Checks whether the polygon of this Departure NOZ contains the input coordinates
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if ([x], [y]) is inside the zone polygon, else false
     */
    override fun contains(x: Float, y: Float): Boolean {
        return entity[GPolygon.mapper]?.polygonObj?.contains(x, y) == true
    }

    companion object {
        /**
         * De-serialises a [SerialisedDepartureNOZ] and creates a new [DepartureNormalOperatingZone] object from it
         * @param serialisedDepartureNOZ the object to de-serialise
         * @return a newly created [ApproachNormalOperatingZone] object
         */
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
 * Note that NTZs are mainly for aesthetic purposes, and will be used for positional checking to detect NTZ transgressions
 */
class NoTransgressionZone(posX: Float, posY: Float, appHdg: Short, private val widthNm: Float, private val lengthNm: Float,
                          onClient: Boolean = true): Zone, SerialisableEntity<NoTransgressionZone.SerialisedNTZ> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
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
            with<DoNotRenderShape>()
        }
    }

    /**
     * Returns a default empty [SerialisedNTZ] due to missing component, and logs a message to the console
     * @param missingComponent the missing aircraft component
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedNTZ {
        FileLog.info("Zones", "Empty serialised NTZ returned due to missing $missingComponent component")
        return SerialisedNTZ()
    }

    /** Gets a [SerialisedNTZ] from current state */
    override fun getSerialisableObject(): SerialisedNTZ {
        entity.apply {
            val pos = get(Position.mapper) ?: return SerialisedNTZ()
            val dir = get(Direction.mapper) ?: return SerialisedNTZ()
            val appHdg = convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + MAG_HDG_DEV
            return SerialisedNTZ(pos.x, pos.y, appHdg.roundToInt().toShort(), widthNm, lengthNm)
        }
    }

    /**
     * Checks whether the polygon of this NTZ contains the input coordinates
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if ([x], [y]) is inside the zone polygon, else false
     */
    override fun contains(x: Float, y: Float): Boolean {
        return entity[GPolygon.mapper]?.polygonObj?.contains(x, y) == true
    }

    companion object {
        /**
         * De-serialises a [SerialisedNTZ] and creates a new [NoTransgressionZone] object from it
         * @param serialisedNTZ the object to de-serialise
         * @return a newly created [NoTransgressionZone] object
         */
        fun fromSerialisedObject(serialisedNTZ: SerialisedNTZ): NoTransgressionZone {
            return NoTransgressionZone(serialisedNTZ.posX, serialisedNTZ.posY,
                serialisedNTZ.appHdg, serialisedNTZ.widthNm, serialisedNTZ.lengthNm)
        }
    }

    /** Object that contains [NoTransgressionZone] data to be serialised by Kryo */
    class SerialisedNTZ(val posX: Float = 0f, val posY: Float = 0f, val appHdg: Short = 0,
                                 val widthNm: Float = 0f, val lengthNm: Float = 0f)
}

/**
 * Class for storing route segment zone (for waypoint -> waypoint leg segments)
 *
 * This class should be initialized only on the server as it is not required on the client
 */
class RouteZone(posX1: Float, posY1: Float, posX2: Float, posY2: Float, rnpNm: Float, minAlt: Int?): Zone {
    val entity = getEngine(false).entityOnMainThread(false) {
        with<GPolygon> {
            val halfWidth = Vector2(posX2 - posX1, posY2 - posY1).apply { scl(nmToPx(rnpNm) / len()) }.rotate90(-1)
            val halfWidthOppTrack = Vector2(halfWidth).rotate90(-1)
            vertices = floatArrayOf(posX1 + halfWidth.x + halfWidthOppTrack.x, posY1 + halfWidth.y + halfWidthOppTrack.y,
                posX1 - halfWidth.x + halfWidthOppTrack.x, posY1 - halfWidth.y + halfWidthOppTrack.y,
                posX2 - halfWidth.x - halfWidthOppTrack.x, posY2 - halfWidth.y - halfWidthOppTrack.y,
                posX2 + halfWidth.x - halfWidthOppTrack.x, posY2 + halfWidth.y - halfWidthOppTrack.y)
        }
        if (minAlt != null) with<Altitude> {
            altitudeFt = minAlt.toFloat()
        }
    }

    /** Empty route zone constructor for loading of saves */
    constructor(): this(0f, 0f, 0f, 0f, 3f, null)

    /**
     * Checks whether the polygon of this route zone contains the input coordinates
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if ([x], [y]) is inside the zone polygon, else false
     */
    override fun contains(x: Float, y: Float): Boolean {
        return entity[GPolygon.mapper]?.polygonObj?.contains(x, y) == true
    }
}

/**
 * Class for storing wake turbulence zones
 *
 * This class should be initialized only on the server as it is not required on the client
 */
class WakeZone(prevPosX: Float, prevPosY: Float, currPosX: Float, currPosY: Float, wakeAlt: Float, callsign: String, leadingWakeCat: Char, leadingRecatCat: Char): Zone {
    val entity = getEngine(false).entityOnMainThread(false) {
        with<GPolygon> {
            val halfWidth = Vector2(currPosX - prevPosX, currPosY - prevPosY).apply { scl(nmToPx(WAKE_WIDTH_NM / 2) / len()) }.rotate90(-1)
            val halfWidthOppTrack = Vector2(halfWidth).apply { scl(nmToPx(WAKE_LENGTH_EXTENSION_NM / 2) / len()) }.rotate90(-1)
            vertices = floatArrayOf(prevPosX + halfWidth.x, prevPosY + halfWidth.y, prevPosX - halfWidth.x, prevPosY - halfWidth.y,
                currPosX - halfWidth.x - halfWidthOppTrack.x, currPosY - halfWidth.y - halfWidthOppTrack.y,
                currPosX + halfWidth.x - halfWidthOppTrack.x, currPosY + halfWidth.y - halfWidthOppTrack.y)
        }
        with<Altitude> {
            altitudeFt = wakeAlt
        }
        with<WakeInfo> {
            aircraftCallsign = callsign
            leadingWake = leadingWakeCat
            leadingRecat = leadingRecatCat
        }
        with<Position> {
            x = currPosX
            y = currPosY
        }
    }

    /** Empty wake zone constructor for loading of saves */
    constructor(): this(0f, 0f, 0f, 0f, 0f, "", 'H', 'B')

    /**
     * Checks whether the polygon of this wake zone contains the input coordinates
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if ([x], [y]) is inside the zone polygon, else false
     */
    override fun contains(x: Float, y: Float): Boolean {
        return entity[GPolygon.mapper]?.polygonObj?.contains(x, y) == true
    }
}
