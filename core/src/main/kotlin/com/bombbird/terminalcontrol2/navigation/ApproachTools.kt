package com.bombbird.terminalcontrol2.navigation

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.VIS_GLIDE_ANGLE_DEG
import com.bombbird.terminalcontrol2.global.VIS_MAX_DIST_NM
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.get
import ktx.math.plus
import ktx.math.times
import kotlin.math.max
import kotlin.math.tan

/** Helper file containing functions for dealing with approaches */

/**
 * Calculates the altitude an aircraft on the approach should be at a given position
 *
 * This will correct for time lag in response (10s since AI will set target vertical speed to reach altitude in 10s) if
 * a visual approach glide path is used
 * @param approach the approach entity
 * @param posX the x coordinate of aircraft position
 * @param posY the y coordinate of aircraft position
 * @param gsKt the ground speed of the aircraft; required for visual approaches only
 * @return the altitude the aircraft should be at, or null if aircraft is too far from approach position
 * */
fun getAppAltAtPos(approach: Entity, posX: Float, posY: Float, gsKt: Float): Float? {
    val pos = approach[Position.mapper] ?: return null
    val appInfo = approach[ApproachInfo.mapper] ?: return null
    val glide = approach[GlideSlope.mapper]
    val loc = approach[Localizer.mapper]
    val vis = approach[Visual.mapper]
    val npa = approach[StepDown.mapper]

    val distPx = calculateDistanceBetweenPoints(posX, posY, pos.x, pos.y)

    if (glide != null || vis != null) {
        // Return null if aircraft is further away than the max operating range of the localizer or visual range for visual approaches (10nm)
        if (loc != null && distPx > nmToPx(loc.maxDistNm.toFloat())) return null
        if (vis != null && distPx > nmToPx(VIS_MAX_DIST_NM.toFloat())) return null
        // Add the glide slope offset, or subtract distance covered in 10s at current GS if visual
        val slopeDistPx = distPx + nmToPx(glide?.offsetNm ?: (-gsKt / 360))
        return max(pxToFt((slopeDistPx * tan(Math.toRadians((glide?.glideAngle ?: VIS_GLIDE_ANGLE_DEG).toDouble()))).toFloat()), -20f) +
                (appInfo.rwyObj.entity[Altitude.mapper]?.altitudeFt ?: 0f)
    } else if (npa != null) {
        val distNm = pxToNm(distPx)
        for (i in npa.altAtDist.size - 1 downTo 0) if (distNm > npa.altAtDist[i].first) return npa.altAtDist[i].second.toFloat()
    } else {
        Gdx.app.log("ApproachTools", "No suitable approach type for ${appInfo.approachName}")
        return null
    }

    // If last point of NPA already reached, follow visual segment of runway
    return getAppAltAtPos(appInfo.rwyObj.entity[VisualApproach.mapper]?.visual ?: return null, posX, posY, gsKt)
}

/**
 * Calculates the position the aircraft should target given its current position on the approach
 *
 * The position on the approach track 1.5/0.75/0.25nm (depending on the current distance from the approach position) in
 * front of the aircraft's current distance from the approach origin will be calculated and returned
 * @param approach the approach entity
 * @param posX the x coordinate of aircraft position
 * @param posY the y coordinate of aircraft position
 * @return a [Vector2] containing the position the aircraft should target, or null if aircraft is too far from approach
 * position
 * */
fun getTargetPos(approach: Entity, posX: Float, posY: Float): Vector2? {
    val pos = approach[Position.mapper] ?: return null
    val dir = approach[Direction.mapper] ?: return null
    val loc = approach[Localizer.mapper]
    val vis = approach[Visual.mapper]

    val distPx = calculateDistanceBetweenPoints(posX, posY, pos.x, pos.y)

    if ((loc != null && distPx < nmToPx(loc.maxDistNm.toFloat())) || (vis != null && distPx < nmToPx(VIS_MAX_DIST_NM.toFloat()))) {
        val distNmSubtracted = if (pxToNm(distPx) > 10) 1.5f else 0.5f
        val targetDistPx = distPx - nmToPx(distNmSubtracted)
        return Vector2(pos.x, pos.y) + dir.trackUnitVector * targetDistPx
    }

    return null
}

/**
 * Checks whether the aircraft position has reached past the line-up distance from the runway
 * @param approach the offset approach entity which should contain a [LineUpDist] component
 * @param posX the x coordinate of aircraft position
 * @param posY the y coordinate of aircraft position
 * @return whether the aircraft position has reached the distance from the runway threshold
 * */
fun checkLineUpDistReached(approach: Entity, posX: Float, posY: Float): Boolean {
    val lineUpDist = approach[LineUpDist.mapper]?.lineUpDistNm ?: return false
    val rwyPos = approach[ApproachInfo.mapper]?.rwyObj?.entity?.get(Position.mapper) ?: return false
    val distPx = calculateDistanceBetweenPoints(posX, posY, rwyPos.x, rwyPos.y)
    return pxToNm(distPx) < lineUpDist
}

/**
 * Checks whether the aircraft is inside the localizer arc with the given angle range to both sides and the input arc length
 * @param locApp the approach entity
 * @param posX the x coordinate of aircraft position
 * @param posY the y coordinate of aircraft position
 * @param angleDeg the maximum angle range on both sides of the localizer course
 * @param distNm the range of the arc
 * @return whether the aircraft is within range of the specified localizer arc
 * */
fun isInsideLocArc(locApp: Entity, posX: Float, posY: Float, angleDeg: Float, distNm: Byte): Boolean {
    val pos = locApp[Position.mapper] ?: return false
    val dir = locApp[Direction.mapper] ?: return false

    return checkInArc(pos.x, pos.y, convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()), nmToPx(distNm.toFloat()), angleDeg, posX, posY)
}
