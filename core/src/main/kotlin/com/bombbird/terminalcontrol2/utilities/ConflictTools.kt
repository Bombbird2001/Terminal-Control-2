package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Circle
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.ApproachNormalOperatingZone
import com.bombbird.terminalcontrol2.entities.RouteZone
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MIN_SEP
import com.bombbird.terminalcontrol2.global.VERT_SEP
import com.bombbird.terminalcontrol2.navigation.establishedOnFinalApproachTrack
import com.bombbird.terminalcontrol2.traffic.conflict.Conflict
import com.bombbird.terminalcontrol2.traffic.conflict.ConflictMinima
import com.bombbird.terminalcontrol2.traffic.conflict.DEFAULT_CONFLICT_MINIMA
import com.bombbird.terminalcontrol2.traffic.conflict.MVAConflictInfo
import ktx.ashley.get
import ktx.ashley.has
import ktx.collections.GdxArray

/**
 * Checks and returns true if the provided [aircraft1] and [aircraft2] fulfil conditions for inhibition of conflicts
 */
fun checkIsAircraftConflictInhibited(aircraft1: Entity, aircraft2: Entity): Boolean {
    if (aircraft1 == aircraft2) return true
    val alt1 = aircraft1[Altitude.mapper] ?: return true
    val alt2 = aircraft2[Altitude.mapper] ?: return true
    val pos1 = aircraft1[Position.mapper] ?: return true
    val pos2 = aircraft2[Position.mapper] ?: return true

    val arrArpt1 = aircraft1[ArrivalAirport.mapper]?.arptId?.let {
        GAME.gameServer?.airports?.get(it)?.entity
    }
    val arrArpt2 = aircraft2[ArrivalAirport.mapper]?.arptId?.let {
        GAME.gameServer?.airports?.get(it)?.entity
    }
    val dep1 = aircraft1[DepartureAirport.mapper]
    val dep2 = aircraft2[DepartureAirport.mapper]
    val depArpt1 = GAME.gameServer?.airports?.get(dep1?.arptId)?.entity
    val depArpt2 = GAME.gameServer?.airports?.get(dep2?.arptId)?.entity

    // Inhibit if either plane is less than 1000 ft AGL (to their respective arrival/departure airports)
    val arptElevation1 = (arrArpt1 ?: depArpt1)?.get(Altitude.mapper)?.altitudeFt ?: 0f
    val arptElevation2 = (arrArpt2 ?: depArpt2)?.get(Altitude.mapper)?.altitudeFt ?: 0f
    if (alt1.altitudeFt < arptElevation1 + 1000 || alt2.altitudeFt < arptElevation2 + 1000) return true

    // Inhibit if both planes are arriving at same airport, and are both inside different NOZs of their cleared approaches
    val app1 = aircraft1[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { arrArpt1?.get(
        ApproachChildren.mapper)?.approachMap?.get(it) }
    val app2 = aircraft2[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { arrArpt2?.get(
        ApproachChildren.mapper)?.approachMap?.get(it) }
    val app1Name = app1?.entity?.get(ApproachInfo.mapper)?.approachName
    val app2Name = app2?.entity?.get(ApproachInfo.mapper)?.approachName
    val nozGroups = if (arrArpt1 != null && arrArpt1 == arrArpt2 && app1Name != null && app2Name != null) {
        arrArpt1[ApproachNOZChildren.mapper]?.nozGroups
    } else null
    if (nozGroups != null && app1Name != null && app2Name != null) {
        // Find an approach NOZ group containing both approaches each in a different zone
        for (i in 0 until nozGroups.size) {
            val zones = nozGroups[i].appNoz
            var app1Zone: ApproachNormalOperatingZone? = null
            var app2Zone: ApproachNormalOperatingZone? = null
            for (j in 0 until zones.size) {
                val zone = zones[j]
                val appNames = zone.entity[ApproachList.mapper] ?: continue
                if (appNames.approachList.contains(app1Name) && zone.contains(pos1.x, pos1.y)) app1Zone = zone
                if (appNames.approachList.contains(app2Name) && zone.contains(pos2.x, pos2.y)) app2Zone = zone
            }
            // If approach NOZs are found for both, and are not the same zone, inhibit
            if (app1Zone != null && app2Zone != null && app1Zone !== app2Zone) return true
        }
    }

    // Inhibit if both planes are departing from the same airport, and are both inside different NOZs of their departure runways
    if (dep1 != null && dep2 != null && depArpt1 != null && depArpt2 != null) {
        val depRwy1 = depArpt1[RunwayChildren.mapper]?.rwyMap?.get(dep1.rwyId)
        val depRwy2 = depArpt2[RunwayChildren.mapper]?.rwyMap?.get(dep2.rwyId)
        val depNoz1 = depRwy1?.entity?.get(DepartureNOZ.mapper)?.depNoz
        val depNoz2 = depRwy2?.entity?.get(DepartureNOZ.mapper)?.depNoz
        // Since depNoz1 and depNoz2 are nullable, the .contains method must return a true (not false or null)
        if (depArpt1 === depArpt2 && depNoz1 !== depNoz2 &&
            depNoz1?.contains(pos1.x, pos1.y) == true && depNoz2?.contains(pos2.x, pos2.y) == true) return true

        // Allow simultaneous departures on divergent headings of at least 15 degrees
        if (aircraft1.has(DivergentDepartureAllowed.mapper) && aircraft2.has(DivergentDepartureAllowed.mapper) && depArpt1 == depArpt2) {
            val track1 = convertWorldAndRenderDeg(aircraft1[Direction.mapper]?.trackUnitVector?.angleDeg() ?: 0f)
            val track2 = convertWorldAndRenderDeg(aircraft2[Direction.mapper]?.trackUnitVector?.angleDeg() ?: 0f)

            // Identify which plane is on the left, right
            depArpt1[Position.mapper]?.let { arptPos ->
                val plane1Offset = Vector2(pos1.x - arptPos.x, pos1.y - arptPos.y)
                val plane2Offset = Vector2(pos2.x - arptPos.x, pos2.y - arptPos.y)

                // Check which angle is more left
                val plane1PosTrack = convertWorldAndRenderDeg(plane1Offset.angleDeg())
                val plane2PosTrack = convertWorldAndRenderDeg(plane2Offset.angleDeg())
                val plane2PosTrackDiff = findDeltaHeading(plane1PosTrack, plane2PosTrack, CommandTarget.TURN_DEFAULT)
                if (plane2PosTrackDiff > 0) {
                    // Plane 1 on left, 2 on right
                    if (findDeltaHeading(track1, track2, CommandTarget.TURN_DEFAULT) >= 15) return true
                } else {
                    // Plane 1 on right, 2 on left
                    if (findDeltaHeading(track2, track1, CommandTarget.TURN_DEFAULT) >= 15) return true
                }
            }
        }
    }

    // Inhibit if either plane just did a go around
    if (aircraft1.has(RecentGoAround.mapper) || aircraft2.has(RecentGoAround.mapper)) return true

    // Inhibit if either plane is flying visual approach
    if (aircraft1.has(VisualCaptured.mapper) || aircraft2.has(VisualCaptured.mapper)) return true

    return false
}

/**
 * Gets the minimum separation required between 2 aircraft, in terms of lateral and vertical separation, as well as the
 * reason for infringement
 *
 * Returns a [ConflictMinima]
 */
fun getMinimaRequired(aircraft1: Entity, aircraft2: Entity): ConflictMinima {
    val pos1 = aircraft1[Position.mapper] ?: return DEFAULT_CONFLICT_MINIMA
    val pos2 = aircraft2[Position.mapper] ?: return DEFAULT_CONFLICT_MINIMA

    val arrival1 = aircraft1[ArrivalAirport.mapper]?.arptId?.let {
        GAME.gameServer?.airports?.get(it)?.entity
    }
    val arrival2 = aircraft2[ArrivalAirport.mapper]?.arptId?.let {
        GAME.gameServer?.airports?.get(it)?.entity
    }

    val app1 = aircraft1[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { arrival1?.get(
        ApproachChildren.mapper)?.approachMap?.get(it) }
    val app2 = aircraft2[ClearanceAct.mapper]?.actingClearance?.clearanceState?.clearedApp?.let { arrival2?.get(
        ApproachChildren.mapper)?.approachMap?.get(it) }
    val appNoz1 = app1?.entity?.get(ApproachInfo.mapper)?.rwyObj?.entity?.get(ApproachNOZGroup.mapper)?.appNoz
    val appNoz2 = app2?.entity?.get(ApproachInfo.mapper)?.rwyObj?.entity?.get(ApproachNOZGroup.mapper)?.appNoz

    var latMinima = MIN_SEP
    var altMinima = VERT_SEP
    var conflictReason = Conflict.NORMAL_CONFLICT

    val appRwy1 = app1?.entity?.get(ApproachInfo.mapper)?.rwyObj
    val appRwy2 = app2?.entity?.get(ApproachInfo.mapper)?.rwyObj
    val appRwy1Pos = appRwy1?.entity?.get(Position.mapper)

    // Reduce vertical separation to half if aircraft is an emergency
    if (aircraft1[EmergencyPending.mapper]?.active == true || aircraft2[EmergencyPending.mapper]?.active == true) {
        altMinima = VERT_SEP / 2
        conflictReason = Conflict.EMERGENCY_SEPARATION_CONFLICT
    }

    // Reduce lateral separation to 2nm (staggered) if both aircraft are established on different final approach tracks
    if (appRwy1 !== appRwy2 && app1 != null && establishedOnFinalApproachTrack(app1.entity, pos1.x, pos1.y) &&
        app2 != null && establishedOnFinalApproachTrack(app2.entity, pos2.x, pos2.y)
    ) {
        latMinima = 2f
        conflictReason = Conflict.PARALLEL_DEP_APP
    }

    // Reduced lateral separation to 2.5nm if both aircraft are established on same final approach track and both are
    // less than 10nm away from runway (should we take into account weather e.g. visibility?)
    else if (appRwy1 === appRwy2 && app1 != null && establishedOnFinalApproachTrack(app1.entity, pos1.x, pos1.y) &&
        app2 != null && establishedOnFinalApproachTrack(app2.entity, pos2.x, pos2.y) &&
        appRwy1Pos != null &&
        calculateDistanceBetweenPoints(pos1.x, pos1.y, appRwy1Pos.x, appRwy1Pos.y) < nmToPx(10) &&
        calculateDistanceBetweenPoints(pos2.x, pos2.y, appRwy1Pos.x, appRwy1Pos.y) < nmToPx(10)
    ) {
        latMinima = 2.5f
        conflictReason = Conflict.SAME_APP_LESS_THAN_10NM
    }

    // Check for NTZ infringement
    if (arrival1 === arrival2 && appNoz1 != null && appNoz2 != null && appNoz1 !== appNoz2) {
        // Find matching runway config that contains both runways for landing, and check its NTZ(s)
        run { arrival1?.get(RunwayConfigurationChildren.mapper)?.rwyConfigs?.values()?.forEach {
            if (!it.depRwys.contains(appRwy1, true) || !it.depRwys.contains(appRwy2, true)) return@run
            // Suitable runway configuration found
            for (i in 0 until it.ntzs.size) {
                if (it.ntzs[i].contains(pos1.x, pos1.y) || it.ntzs[i].contains(pos2.x, pos2.y)) {
                    conflictReason = Conflict.PARALLEL_INDEP_APP_NTZ
                    return@run
                }
            }
        }}
    }

    return ConflictMinima(latMinima, altMinima, conflictReason)
}

/**
 * Checks if the provided [point] will cause an MVA/restricted areas infringement for the [aircraft]
 *
 * Returns a [MVAConflictInfo], or null if no infringement
 */
fun checkMVARestrictedConflict(aircraft: Entity, point: Entity): MVAConflictInfo? {
    // Whether the aircraft has already captured the vertical component of the approach (or phase >= 1 for circling)
    val approachVertCaptured = aircraft.has(GlideSlopeCaptured.mapper) || aircraft.has(StepDownApproach.mapper) ||
            (aircraft[CirclingApproach.mapper]?.phase ?: 0) >= 1 || aircraft.has(VisualCaptured.mapper)

    val gsArmed = if (!approachVertCaptured && aircraft.has(LocalizerCaptured.mapper)) aircraft[GlideSlopeArmed.mapper]
    else null

    // Whether the aircraft is following vectors (if false, it is following the route)
    val underVector = getLatestClearanceState(aircraft)?.vectorHdg != null

    // The aircraft's route zones
    val routeZones = GdxArray<RouteZone>()
    aircraft[ArrivalRouteZone.mapper]?.let { arrZone ->
        routeZones.addAll(arrZone.appZone)
        routeZones.addAll(arrZone.starZone)
    }
    aircraft[DepartureRouteZone.mapper]?.let { depZone -> routeZones.addAll(depZone.sidZone) }

    val pos = point[Position.mapper] ?: return null
    val alt = point[Altitude.mapper] ?: return null

    checkPointMVARestrictedConflict(pos.x, pos.y, alt.altitudeFt, approachVertCaptured, gsArmed, underVector,
        aircraft.has(RecentGoAround.mapper), routeZones)?.let {
        return it
    }

    return null
}

/**
 * Checks if the provided [point] will cause an MVA/restricted area infringement for its aircraft
 *
 * Returns an [MVAConflictInfo] object if infringement, else null
 */
fun checkTrajectoryPointMVARestrictedConflict(point: Entity): MVAConflictInfo? {
    val aircraft = point[TrajectoryPointInfo.mapper]?.aircraft ?: return null
    return checkMVARestrictedConflict(aircraft, point)
}

/**
 * Checks if the [aircraft] is infringing an MVA/restricted area
 *
 * Returns a [Conflict] object if infringement is found, or null if no infringement
 */
fun checkAircraftMVARestrictedConflict(aircraft: Entity): Conflict? {
    return checkMVARestrictedConflict(aircraft, aircraft)?.let {
        Conflict(aircraft, null, it.minAltSectorIndex, 3f, it.reason)
    }
}

/**
 * Checks if the provided [posX], [posY] and [altitude] will cause an MVA/restricted areas infringement given the
 * aircraft's [approachVertCaptured], [glideslopeArmed], [underVector], [recentGoAround] status, as well as its [routeZones]
 *
 * Returns a byte specifying the reason if infringement is found, or null if no infringement
 */
private fun checkPointMVARestrictedConflict(posX: Float, posY: Float, altitude: Float, approachVertCaptured: Boolean,
                                            glideslopeArmed: GlideSlopeArmed?, underVector: Boolean,
                                            recentGoAround: Boolean, routeZones: GdxArray<RouteZone>): MVAConflictInfo? {
    // Whether the aircraft has already captured localizer but not the glide slope yet, but is above the ROC slope
    val aboveGsRoc = !approachVertCaptured && (glideslopeArmed?.let { gsArmed ->
        val gs = gsArmed.gsApp[GlideSlope.mapper] ?: return@let false
        val appPos = gsArmed.gsApp[Position.mapper] ?: return@let false
        val gradientRatio = 102 / gs.glideAngle
        val distFromTouchdownPx = calculateDistanceBetweenPoints(posX, posY, appPos.x, appPos.y) + nmToPx(gs.offsetNm)
        val rwyAlt = gsArmed.gsApp[ApproachInfo.mapper]?.rwyObj?.entity?.get(Altitude.mapper)?.altitudeFt ?: return@let false
        altitude > rwyAlt + pxToFt(distFromTouchdownPx) / gradientRatio
    } ?: false)

    var deviatedFromRoute = true
    run { for (i in 0 until routeZones.size) routeZones[i]?.also { zone ->
        val minAlt = zone.entity[Altitude.mapper]?.altitudeFt
        if ((minAlt == null || altitude > minAlt - 25) && zone.contains(posX, posY)) {
            deviatedFromRoute = false
            return@run
        }
    }}

    val allMinAltSectors = GAME.gameServer?.minAltSectors ?: return null
    for (i in 0 until allMinAltSectors.size) {
        allMinAltSectors[i]?.apply {
            val sectorInfo = entity[MinAltSectorInfo.mapper] ?: return@apply
            val minAlt = sectorInfo.minAltFt
            // If altitude is higher than min alt, break from the whole loop since the array is already sorted in
            // descending order, and all subsequent sectors will not trigger a conflict
            if (minAlt != null && altitude > minAlt - 25) return null

            // If aircraft is already on glide slope, step down, circling (phase 1 or later) or visual approach,
            // or is above the ROC slope of the glide slope, and sector is not restricted, skip checking
            if (!sectorInfo.restricted && (approachVertCaptured || aboveGsRoc))
                return@apply

            // If aircraft is not being vectored (i.e. SID/STAR/approach), is within the route's MVA exclusion
            // zone, or instead of all the above, the aircraft just did a go around, and sector is not restricted,
            // skip checking
            if (!sectorInfo.restricted && ((!underVector && !deviatedFromRoute) || recentGoAround)) return@apply

            var insideShape = false
            // Try to get either the polygon or the circle
            val polygon = entity[GPolygon.mapper]
            val circle = entity[GCircle.mapper]
            if (polygon != null) {
                insideShape = polygon.polygonObj.contains(posX, posY)
            } else if (circle != null) {
                insideShape = entity[Position.mapper]?.let { circlePos ->
                    Circle(circlePos.x, circlePos.y, circle.radius).contains(posX, posY)
                } ?: false
            }

            if (!insideShape) return@apply
            val reason = if (sectorInfo.restricted) Conflict.RESTRICTED
            else if (deviatedFromRoute) Conflict.SID_STAR_MVA
            else Conflict.MVA

            return MVAConflictInfo(reason, i)
        }
    }

    return null
}