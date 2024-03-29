package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.CommandTarget
import ktx.math.plusAssign
import ktx.math.times
import kotlin.math.*

/** Game specific math tools for use */

/** Convenience [Int] to [Byte] getter */
inline val Int.byte
    get() = this.toByte()

/** Unit conversions */
const val NM_TO_PX = 25f
private const val NM_TO_FT = 6076.12f
private const val NM_TO_M = 1852f

/** Convert from nautical miles to pixels */
fun nmToPx(nm: Int): Float {
    return nmToPx(nm.toFloat())
}

fun nmToPx(nm: Float): Float {
    return nm * NM_TO_PX
}

/** Convert from pixels to nautical miles */
fun pxToNm(px: Int): Float {
    return pxToNm(px.toFloat())
}

fun pxToNm(px: Float): Float {
    return px / NM_TO_PX
}

/** Convert from feet to pixels */
fun ftToPx(ft: Int): Float {
    return ftToPx(ft.toFloat())
}

fun ftToPx(ft: Float): Float {
    return nmToPx(ft / NM_TO_FT)
}

/** Convert from pixels to feet */
fun pxToFt(px: Int): Float {
    return pxToFt(px.toFloat())
}

fun pxToFt(px: Float): Float {
    return pxToNm(px) * NM_TO_FT
}

/** Convert from metres to pixels */
fun mToPx(m: Int): Float {
    return mToPx(m.toFloat())
}

fun mToPx(m: Float): Float {
    return nmToPx(m / NM_TO_M)
}

/** Convert from pixels to metres */
fun pxToM(px: Float): Float {
    return pxToNm(px) * NM_TO_M
}

fun pxToM(px: Int): Float {
    return pxToM(px.toFloat())
}

/** Convert from metres to nautical miles */
fun mToNm(m: Float): Float {
    return m / NM_TO_M
}

/** Convert from metres to feet */
fun mToFt(m: Float): Float {
    return pxToFt(mToPx(m))
}

/** Convert from feet to metres */
fun ftToM(ft: Float): Float {
    return ft / mToFt(1.0f)
}

/** Convert from knots to pixels per second */
fun ktToPxps(kt: Int): Float {
    return ktToPxps(kt.toFloat())
}

fun ktToPxps(kt: Float): Float {
    return nmToPx(kt / 3600)
}

/** Convert from pixels per second to knots */
fun pxpsToKt(pxps: Float): Float {
    return pxps / ktToPxps(1)
}

/** Convert from metres per second to knots */
fun mpsToKt(mps: Float): Float {
    return mToNm(mps * 3600)
}

/** Convert from knots to metres per second */
fun ktToMps(kt: Int): Float {
    return ktToMps(kt.toFloat())
}

fun ktToMps(kt: Float): Float {
    return kt / mpsToKt(1f)
}

/** Convert from knots to feet per minute */
fun ktToFpm(kt: Float): Float {
    return kt * NM_TO_FT / 60
}

/** Convert from metres per second to feet per minute */
fun mpsToFpm(ms: Float): Float {
    return mToFt(ms * 60)
}

/** Convert from feet per minute to metres per second */
fun fpmToMps(fpm: Float): Float {
    return fpm / mpsToFpm(1f)
}

/**
 * Converts between in-game world degrees and degree used by the rendering systems
 *
 * World heading: 360 is up, 90 is right, 180 is down and 270 is left
 *
 * Render degrees: 90 is up, 0 is right, -90 is down and -180 is left
 */
fun convertWorldAndRenderDeg(origDeg: Float): Float {
    return 90 - origDeg
}

/** Modulates the heading such that 0 < [hdg] <= 360 */
fun modulateHeading(hdg: Float): Float {
    return hdg - floor((hdg - 0.0001f) / 360) * 360
}

/** Calculates the effective heading difference (i.e. how much the aircraft needs to turn through) given [initHdg], [targetHdg] and [turnDir] */
fun findDeltaHeading(initHdg: Float, targetHdg: Float, turnDir: Byte): Float {
    var diff = targetHdg - initHdg
    when (turnDir) {
        CommandTarget.TURN_DEFAULT -> {
            diff %= 360
            if (diff > 180) diff -= 360
            else if (diff <= -180) diff += 360
        }
        CommandTarget.TURN_LEFT -> {
            diff %= 360
            if (diff > 0) diff -= 360
        }
        CommandTarget.TURN_RIGHT -> {
            diff %= 360
            if (diff < 0) diff += 360
        }
    }

    return diff
}

/**
 * Calculates the distance between the 2 input points
 * @param x1 the x position of the first point
 * @param y1 the y position of the first point
 * @param x2 the x position of the second point
 * @param y2 the y position of the second point
 * @return the distance between ([x1], [y1]) and ([x2], [y2]), units will be the same as whatever units were used in the
 * input
 */
fun calculateDistanceBetweenPoints(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val deltaX = x2 - x1
    val deltaY = y2 - y1
    return sqrt(deltaX * deltaX + deltaY * deltaY)
}

/** Calculates the shortest distance required to reach the border supplied with a given track  */
fun distanceFromBorder(xBorder: FloatArray, yBorder: FloatArray, x: Float, y: Float, direction: Float): Float {
    val cos = cos(Math.toRadians(90 - direction.toDouble())).toFloat()
    val xDistRight = (xBorder[1] - x) / cos
    val xDistLeft = (xBorder[0] - x) / cos
    val sin = sin(Math.toRadians(90 - direction.toDouble())).toFloat()
    val yDistUp = (yBorder[1] - y) / sin
    val yDistDown = (yBorder[0] - y) / sin
    val xDist = if (xDistRight > 0) xDistRight else xDistLeft
    val yDist = if (yDistUp > 0) yDistUp else yDistDown
    return xDist.coerceAtMost(yDist)
}

/**
 * Calculates the point where the line from a point at a specified track meets a rectangle's border
 * @param xBorder an array of 2 floats, the first being the left border's x coordinate and the second being the right border's y coordinate
 * @param yBorder an array of 2 floats, the first being the bottom border's y coordinate and the second being the top border's y coordinate
 * @param x the line origin's x coordinate
 * @param y the line origin's y coordinate
 * @param track the track direction of the line
 * @return a float array containing 2 floats denoting the point of intersection between the line and the rectangle borders
 */
fun pointsAtBorder(xBorder: FloatArray, yBorder: FloatArray, x: Float, y: Float, track: Float): FloatArray {
    val dist = distanceFromBorder(xBorder, yBorder, x, y, track)
    return floatArrayOf(x + dist * cos(Math.toRadians(90 - track.toDouble())).toFloat(), y + dist * sin(Math.toRadians(90 - track.toDouble())).toFloat())
}

/** Checks whether integer is within range of 2 integers  */
fun withinRange(no: Int, min: Int, max: Int): Boolean {
    return no in min..max
}

/** Checks whether float is within range of 2 floats  */
fun withinRange(no: Float, min: Float, max: Float): Boolean {
    return no > min && no < max
}

/** Calculates the required track to achieve a displacement of deltaX, deltaY  */
fun getRequiredTrack(deltaX: Float, deltaY: Float): Float {
    return 90 - Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
}

/** Calculates the required track to go from initial point with x, y to destination point with destX, destY  */
fun getRequiredTrack(x: Float, y: Float, destX: Float, destY: Float): Float {
    return getRequiredTrack(destX - x, destY - y)
}

/** Calculates the required track to go from initial point with x, y to destination point with destX, destY  */
fun getRequiredTrack(x: Int, y: Int, destX: Int, destY: Int): Float {
    return getRequiredTrack((destX - x).toFloat(), (destY - y).toFloat())
}

/**
 * Calculates the distance prior to reaching a point required to turn through a change in heading, given the
 * turn rate and ground speed of the aircraft
 *
 * A positive and negative value of [deltaHeading] with the same magnitude should return the same result
 * @param deltaHeading the change in heading from the turn
 * @param turnRateDps the rate of turn of the aircraft
 * @param groundSpeedPxps the ground speed, in px per second, of the aircraft
 * @return the distance, in px, to turn early
 */
fun findTurnDistance(deltaHeading: Float, turnRateDps: Float, groundSpeedPxps: Float): Float {
    val radius = groundSpeedPxps / (MathUtils.degreesToRadians * turnRateDps)
    val halfTheta = (180 - abs(deltaHeading)) / 2f
    return max((radius / tan(Math.toRadians(halfTheta.toDouble()))).toFloat() + 8, 3f)
}

/**
 * Calculates the point of intersection point between a line and a polygon, choosing the point closest to the line origin
 * if multiple intersections with the polygon is found
 * @param originX the line origin's x coordinate
 * @param originY the line origin's y coordinate
 * @param endX the line end's x coordinate
 * @param endY the line end's y coordinate
 * @param vertices the borders of the polygon
 * @param extendLengthPx how much to extend the point past the intersection point along the line, in px
 * @return a [Vector2] object with the point of intersection, or null if none is found
 */
fun findClosestIntersectionBetweenSegmentAndPolygon(originX: Float, originY: Float, endX: Float, endY: Float, vertices: FloatArray, extendLengthPx: Float): Vector2? {
    if (vertices.size % 2 != 0) {
        FileLog.info("MathTools", "Coordinates cannot be odd in number: ${vertices.size}")
        return null
    }
    if (vertices.size < 4) return null
    var intersectionVector: Vector2? = null
    for (i in 0 until vertices.size - 2 step 2) {
        val posVector = Vector2()
        if (Intersector.intersectSegments(originX, originY, endX, endY, vertices[i], vertices[i + 1], vertices[i + 2], vertices[i + 3], posVector)) {
            // Intersect found, compare lengths and set if length is less than current
            if (intersectionVector == null || Vector2(intersectionVector).apply {
                    x -= originX
                    y -= originY
                }.len2() > Vector2(posVector).apply {
                    x -= originX
                    y -= originY
                }.len2()) intersectionVector = posVector
        }
    }

    // Extend the intersection point by amount specified
    return intersectionVector?.apply {
        // Get the vector between the intersection and origin
        val diff = Vector2(endX - originX, endY - originY)
        if (diff.isZero) return this
        // Calculate length in pixels
        val currLen = diff.len()
        // Add the diff vector scaled by extendLength/length
        plusAssign(diff * (extendLengthPx / currLen))
    }
}

/**
 * Tests whether an arc with the given coordinates, angles and length contains the input point
 * @param centerX the x coordinate of the arc center
 * @param centreY the y coordinate of the arc center
 * @param centerTrackDeg the track in which the line of symmetry of the arc extends to
 * @param arcLengthPx the radius of the arc
 * @param arcAngleRangeDeg the angle to which the arc rotates through towards both sides of the line of symmetry
 * @param posX the x coordinate of the position to test
 * @param posY the y coordinate of the position to test
 */
fun checkInArc(centerX: Float, centreY: Float, centerTrackDeg: Float, arcLengthPx: Float, arcAngleRangeDeg: Float, posX: Float, posY: Float): Boolean {
    if (calculateDistanceBetweenPoints(posX, posY, centerX, centreY) > arcLengthPx) return false
    val centerToPosTrack = getRequiredTrack(centerX, centreY, posX, posY)
    return abs(findDeltaHeading(centerToPosTrack, centerTrackDeg, CommandTarget.TURN_DEFAULT)) < arcAngleRangeDeg
}
