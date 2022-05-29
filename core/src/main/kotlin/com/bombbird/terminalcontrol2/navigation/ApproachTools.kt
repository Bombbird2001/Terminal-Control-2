package com.bombbird.terminalcontrol2.navigation

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.VIS_GLIDE_ANGLE_DEG
import com.bombbird.terminalcontrol2.global.VIS_MAX_DIST_NM
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.pxToFt
import com.bombbird.terminalcontrol2.utilities.pxToNm
import ktx.ashley.get
import ktx.math.plus
import ktx.math.times
import kotlin.math.sqrt
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
 * @param gsKt the ground speed of the aircraft
 * @return the altitude the aircraft should be at, or null if aircraft is too far from approach position
 * */
fun getAltAtPos(approach: Entity, posX: Float, posY: Float, gsKt: Float): Float? {
    val pos = approach[Position.mapper] ?: return null
    val appInfo = approach[ApproachInfo.mapper] ?: return null
    val glide = approach[GlideSlope.mapper]
    val loc = approach[Localizer.mapper]
    val vis = approach[Visual.mapper]
    val npa = approach[StepDown.mapper]

    val deltaX = pos.x - posX
    val deltaY = pos.y - posY
    val distPx = sqrt(deltaX * deltaX + deltaY * deltaY)

    if (glide != null || vis != null) {
        // Return null if aircraft is further away than the max operating range of the localizer or visual range for visual approaches (10nm)
        if (loc != null && distPx > nmToPx(loc.maxDistNm.toFloat())) return null
        if (vis != null && distPx > nmToPx(VIS_MAX_DIST_NM)) return null
        // Add the glide slope offset, or subtract distance covered in 10s at current GS if visual
        val slopeDistPx = distPx + nmToPx(glide?.offsetNm ?: (-gsKt / 360))
        return pxToFt((slopeDistPx * tan(Math.toRadians((glide?.glideAngle ?: VIS_GLIDE_ANGLE_DEG).toDouble()))).toFloat()) + (appInfo.rwyObj.entity[Altitude.mapper]?.altitudeFt ?: 0f)
    } else if (npa != null) {
        val distNm = pxToNm(distPx)
        for (i in npa.altAtDist.size - 1 downTo 0) if (distNm > npa.altAtDist[i].first) return npa.altAtDist[i].second.toFloat()
    } else {
        Gdx.app.log("ApproachTools", "No suitable approach type for ${appInfo.approachName}")
        return null
    }

    // If last point of NPA already reached, follow visual segment of runway
    return getAltAtPos(appInfo.rwyObj.entity[VisualApproach.mapper]?.visual ?: return null, posX, posY, gsKt)
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

    val deltaX = pos.x - posX
    val deltaY = pos.y - posY
    val distPx = sqrt(deltaX * deltaX + deltaY * deltaY)

    if ((loc != null && distPx < nmToPx(loc.maxDistNm.toFloat())) || (vis != null && distPx < nmToPx(VIS_MAX_DIST_NM))) {
        val distNmSubtracted = when {
            pxToNm(distPx) > 10 -> 1.5f
            pxToNm(distPx) > 4 -> 0.75f
            else -> 0.25f
        }
        val targetDistPx = distPx - nmToPx(distNmSubtracted)
        return Vector2(posX, posY) + dir.trackUnitVector * targetDistPx
    }

    return null
}
