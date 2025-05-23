package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.traffic.WakeMatrix
import com.bombbird.terminalcontrol2.ui.datatag.LABEL_PADDING
import com.bombbird.terminalcontrol2.ui.panes.UIPane
import com.bombbird.terminalcontrol2.ui.datatag.updateDatatagLabelSize
import com.bombbird.terminalcontrol2.utilities.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.ashley.*
import ktx.collections.GdxArray
import ktx.collections.GdxMap
import ktx.collections.set
import ktx.math.*
import ktx.scene2d.Scene2DSkin
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Main rendering system, which renders to [GAME]'s spriteBatch or radarScreen's [shapeRenderer]
 *
 * Used only in RadarScreen
 */
class RenderingSystemClient(private val shapeRenderer: ShapeRendererBoundingBox,
                            private val stage: Stage, private val constZoomStage: Stage,
                            private val uiStage: Stage, private val uiPane: UIPane
): EntitySystem() {
    companion object {
        private val lineArrayFamily: Family = allOf(GLineArray::class, SRColor::class)
            .exclude(DoNotRenderShape::class.java).get()
        private val polygonFamily: Family = allOf(GPolygon::class, SRColor::class)
            .exclude(RenderLast::class, DoNotRenderShape::class).get()
        private val polygonLastFamily: Family = allOf(GPolygon::class, SRColor::class, RenderLast::class)
            .exclude(DoNotRenderShape::class).get()
        private val circleFamily: Family = allOf(Position::class, GCircle::class, SRColor::class)
            .exclude(SRConstantZoomSize::class, DoNotRenderShape::class).get()
        private val runwayFamily: Family = allOf(RunwayInfo::class).exclude(DoNotRenderShape::class).get()
        private val locFamily: Family = allOf(Position::class, Localizer::class, Direction::class, ApproachInfo::class)
            .exclude(DeprecatedEntity::class).get()
        private val visualAppFamily: Family = allOf(VisualApproach::class).get()
        private val gsCircleFamily: Family = allOf(GlideSlopeCircle::class, ApproachInfo::class)
            .exclude(DeprecatedEntity::class).get()
        private val trajectoryFamily: Family = allOf(RadarData::class, Controllable::class, SRColor::class)
            .exclude(WaitingTakeoff::class).get()
        private val datatagLineFamily: Family = allOf(Datatag::class, RadarData::class)
            .exclude(WaitingTakeoff::class).get()
        private val constCircleFamily: Family = allOf(Position::class, GCircle::class, SRColor::class, SRConstantZoomSize::class)
            .exclude(DoNotRenderShape::class).get()
        private val rwyLabelFamily: Family = allOf(GenericLabel::class, RunwayInfo::class, RunwayLabel::class)
            .oneOf(ActiveLanding::class, ActiveTakeoff::class).get()
        private val labelFamily: Family = allOf(GenericLabel::class, Position::class)
            .exclude(ConstantZoomSize::class, DoNotRenderLabel::class).get()
        private val labelArrayFamily: Family = allOf(GenericLabels::class, Position::class)
            .exclude(ConstantZoomSize::class, DoNotRenderLabel::class).get()
        private val aircraftFamily: Family = allOf(AircraftInfo::class, RadarData::class, RSSprite::class, TrailInfo::class, FlightType::class, Controllable::class)
            .exclude(WaitingTakeoff::class).get()
        private val constSizeLabelFamily: Family = allOf(GenericLabel::class, Position::class, ConstantZoomSize::class)
            .exclude(DoNotRenderLabel::class, RunwayClosed::class).get()
        private val constSizeLabelArrayFamily: Family = allOf(GenericLabels::class, Position::class, ConstantZoomSize::class)
            .exclude(DoNotRenderLabel::class).get()
        private val datatagFamily: Family = allOf(Datatag::class, RadarData::class)
            .exclude(WaitingTakeoff::class).get()
        private val contactDotFamily: Family = allOf(ContactNotification::class, RadarData::class, FlightType::class).get()
        private val waypointFamily: Family = allOf(WaypointInfo::class).get()
        private val routeFamily: Family = oneOf(PendingClearances::class, ClearanceAct::class).get()
        private val wakeRenderFamily = allOf(ApproachWakeSequence::class).get()
        private val thunderstormCellFamily = allOf(ThunderCellInfo::class, RSSprite::class, Position::class).get()
        private val dotBlue: TextureRegion = Scene2DSkin.defaultSkin["DotBlue", TextureRegion::class.java]
        private val dotGreen: TextureRegion = Scene2DSkin.defaultSkin["DotGreen", TextureRegion::class.java]
        private val dotRed: TextureRegion = Scene2DSkin.defaultSkin["DotRed", TextureRegion::class.java]
        private val dotMagenta: TextureRegion = Scene2DSkin.defaultSkin["DotMagenta", TextureRegion::class.java]
        private const val DOT_RADIUS = 7f
        private val WAKE_INDICATOR_COLOUR = Color(0xffbc42ff.toInt())
        private val TRANSLUCENT_WHITE_COLOUR = Color(1f, 1f, 1f, 0.7f)

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    private val lineArrayFamilyEntities = FamilyWithListener.newClientFamilyWithListener(lineArrayFamily)
    private val polygonFamilyEntities = FamilyWithListener.newClientFamilyWithListener(polygonFamily)
    private val polygonLastFamilyEntities = FamilyWithListener.newClientFamilyWithListener(polygonLastFamily)
    private val circleFamilyEntities = FamilyWithListener.newClientFamilyWithListener(circleFamily)
    private val runwayFamilyEntities = FamilyWithListener.newClientFamilyWithListener(runwayFamily)
    private val locFamilyEntities = FamilyWithListener.newClientFamilyWithListener(locFamily)
    private val visualAppFamilyEntities = FamilyWithListener.newClientFamilyWithListener(visualAppFamily)
    private val gsCircleFamilyEntities = FamilyWithListener.newClientFamilyWithListener(gsCircleFamily)
    private val trajectoryFamilyEntities = FamilyWithListener.newClientFamilyWithListener(trajectoryFamily)
    private val datatagLineFamilyEntities = FamilyWithListener.newClientFamilyWithListener(datatagLineFamily)
    private val constCircleFamilyEntities = FamilyWithListener.newClientFamilyWithListener(constCircleFamily)
    private val rwyLabelFamilyEntities = FamilyWithListener.newClientFamilyWithListener(rwyLabelFamily)
    private val labelFamilyEntities = FamilyWithListener.newClientFamilyWithListener(labelFamily)
    private val labelArrayFamilyEntities = FamilyWithListener.newClientFamilyWithListener(labelArrayFamily)
    private val aircraftFamilyEntities = FamilyWithListener.newClientFamilyWithListener(aircraftFamily)
    private val constSizeLabelFamilyEntities = FamilyWithListener.newClientFamilyWithListener(constSizeLabelFamily)
    private val constSizeLabelArrayFamilyEntities = FamilyWithListener.newClientFamilyWithListener(constSizeLabelArrayFamily)
    private val datatagFamilyEntities = FamilyWithListener.newClientFamilyWithListener(datatagFamily)
    private val contactDotFamilyEntities = FamilyWithListener.newClientFamilyWithListener(contactDotFamily)
    private val waypointFamilyEntities = FamilyWithListener.newClientFamilyWithListener(waypointFamily)
    private val routeFamilyEntities = FamilyWithListener.newClientFamilyWithListener(routeFamily)
    private val wakeRenderFamilyEntities = FamilyWithListener.newClientFamilyWithListener(wakeRenderFamily)
    private val thunderstormCellFamilyEntities = FamilyWithListener.newClientFamilyWithListener(thunderstormCellFamily)

    private val renderedRunwayCenterlineIds = GdxArray<Pair<Byte, Byte>>()
    private val distMeasureLabel = Label("", Scene2DSkin.defaultSkin, "DistMeasure")

    private val worldBoundingRect = Rectangle()
    private val constZoomBoundingRect = Rectangle()

    /** Main update function */
    override fun update(deltaTime: Float) {
        val camZoom = (stage.camera as OrthographicCamera).zoom
        val camX = stage.camera.position.x
        val camY = stage.camera.position.y

        // Calculate bounding box for visible section of radar screen
        val viewWidth = (UI_WIDTH - uiPane.paneWidth) * camZoom
        val viewHeight = UI_HEIGHT * camZoom
        val paneOffset = uiPane.paneWidth * camZoom / 2
        shapeRenderer.setBoundingRect(camX + paneOffset - viewWidth / 2, camY - viewHeight / 2, viewWidth, viewHeight)
        worldBoundingRect.set(camX + paneOffset - viewWidth / 2, camY - viewHeight / 2, viewWidth, viewHeight)

        GAME.batch.begin()
        GAME.batch.projectionMatrix = stage.camera.combined
        GAME.batch.packedColor = TRANSLUCENT_WHITE_COLOUR.toFloatBits()

        // Render thunderstorm cells
        val thunderStormCells = thunderstormCellFamilyEntities.getEntities()
        for (i in 0 until thunderStormCells.size()) {
            thunderStormCells[i]?.apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val rsSprite = getOrLogMissing(RSSprite.mapper) ?: return@apply

                rsSprite.drawable.drawBounding(
                    GAME.batch, pos.x, pos.y,
                    rsSprite.width, rsSprite.height,
                    worldBoundingRect
                )
            }
        }

        GAME.batch.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.projectionMatrix = stage.camera.combined
        // Estimation circles
        shapeRenderer.color = Color.WHITE
        shapeRenderer.circle(0f, 0f, 1250f)
        shapeRenderer.circle(0f, 0f, 125f)
        shapeRenderer.circle(0f, 0f, 12.5f)

        // Render lineArrays
        val lineArrays = lineArrayFamilyEntities.getEntities()
        for (i in 0 until lineArrays.size()) {
            lineArrays[i]?.apply {
                val lineArray = getOrLogMissing(GLineArray.mapper) ?: return@apply
                val srColor = getOrLogMissing(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                lineArray.vertices.let {
                    for (j in 0 until it.size - 3 step 2) {
                        shapeRenderer.line(it[j], it[j + 1], it[j + 2], it[j + 3])
                    }
                }
            }
        }

        // Render polygons
        val polygons = polygonFamilyEntities.getEntities()
        for (i in 0 until polygons.size()) {
            polygons[i]?.apply {
                val poly = getOrLogMissing(GPolygon.mapper) ?: return@apply
                val srColor = getOrLogMissing(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.polygon(poly.polygonObj)
            }
        }

        // Debug: Render route MVA exclusion zone of selected aircraft
        // renderSelectedAircraftRouteZones(shapeRenderer)

        // Debug: Render wake zones
        // renderWakeZones(shapeRenderer)

        // Debug: Render all sectors
        // renderAllSectors(shapeRenderer)

        // Debug: Render all ACC sectors
        // renderAllACCSectors(shapeRenderer)

        // Debug: Render all trajectory prediction points
        // renderAllTrajectoryPoints(shapeRenderer)

        // Debug: Render all STARs
        // renderAllStars(shapeRenderer)

        // Debug: Render all SIDs
        // renderAllSids(shapeRenderer)

        // Debug: Render all approaches
        // renderAllApproaches(shapeRenderer)

        // Render circles
        val circles = circleFamilyEntities.getEntities()
        for (i in 0 until circles.size()) {
            circles[i]?.apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val circle = getOrLogMissing(GCircle.mapper) ?: return@apply
                val srColor = getOrLogMissing(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.circle(pos.x, pos.y, circle.radius)
            }
        }

        CLIENT_SCREEN?.also {
            // Render min alt sectors with predicted conflicts (will re-render over existing Polygon/Circle)
            shapeRenderer.color = Color.MAGENTA
            for (i in 0 until it.predictedConflicts.size) {
                it.predictedConflicts[i]?.apply {
                    if (minAltSectorIndex != null && minAltSectorIndex < it.minAltSectors.size) {
                        val minAltSector = it.minAltSectors[minAltSectorIndex]?.entity
                        val polygon = minAltSector?.get(GPolygon.mapper)
                        val circle = minAltSector?.get(GCircle.mapper)
                        if (polygon != null) shapeRenderer.polygon(polygon.polygonObj)
                        else if (circle != null) minAltSector[Position.mapper]?.let { pos ->
                            shapeRenderer.circle(pos.x, pos.y, circle.radius)
                        }
                    }
                }
            }

            // Render conflicting min alt sectors (will re-render over existing Polygon/Circle and predicted conflicts)
            shapeRenderer.color = Color.RED
            for (i in 0 until it.conflicts.size) {
                it.conflicts[i]?.apply {
                    if (minAltSectorIndex != null && minAltSectorIndex < it.minAltSectors.size) {
                        val minAltSector = it.minAltSectors[minAltSectorIndex]?.entity
                        val polygon = minAltSector?.get(GPolygon.mapper)
                        val circle = minAltSector?.get(GCircle.mapper)
                        if (polygon != null) shapeRenderer.polygon(polygon.polygonObj)
                        else if (circle != null) minAltSector[Position.mapper]?.let { pos ->
                            shapeRenderer.circle(pos.x, pos.y, circle.radius)
                        }
                    }
                }
            }
        }

        // Render polygons with RenderLast
        val polygonsLast = polygonLastFamilyEntities.getEntities()
        for (i in 0 until polygonsLast.size()) {
            polygonsLast[i]?.apply {
                val poly = getOrLogMissing(GPolygon.mapper) ?: return@apply
                val srColor = getOrLogMissing(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.polygon(poly.polygonObj)
            }
        }

        // Render runways
        val rwyWidthPx = RWY_WIDTH_PX_ZOOM_1 + (camZoom - 1) * RWY_WIDTH_CHANGE_PX_PER_ZOOM
        val rwys = runwayFamilyEntities.getEntities()
        for (i in 0 until rwys.size()) {
            rwys[i]?.apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val rect = getOrLogMissing(GRect.mapper) ?: return@apply
                val deg = getOrLogMissing(Direction.mapper) ?: return@apply
                rect.height = rwyWidthPx
                shapeRenderer.color = if ((has(ActiveLanding.mapper) || has(ActiveTakeoff.mapper)) && hasNot(RunwayClosed.mapper)) RUNWAY_ACTIVE
                else RUNWAY_INACTIVE
                shapeRenderer.rect(pos.x, pos.y - rect.height / 2, 0f, rect.height / 2, rect.width, rect.height, 1f, 1f, deg.trackUnitVector.angleDeg())
            }
        }

        renderedRunwayCenterlineIds.clear()
        // Render localizers
        val localizer = locFamilyEntities.getEntities()
        for (i in 0 until localizer.size()) {
            localizer[i]?.apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val loc = getOrLogMissing(Localizer.mapper) ?: return@apply
                val dir = getOrLogMissing(Direction.mapper)?.trackUnitVector ?: return@apply
                val appInfo = getOrLogMissing(ApproachInfo.mapper) ?: return@apply
                val allowedConfigs = getOrLogMissing(RunwayConfigurationList.mapper)?.rwyConfigs ?: return@apply
                if (appInfo.rwyObj.entity.hasNot(ActiveLanding.mapper)) return@apply
                if (appInfo.rwyObj.entity.has(RunwayClosed.mapper)) return@apply
                val activeConfig = CLIENT_SCREEN?.airports?.get(appInfo.airportId)?.entity?.get(ActiveRunwayConfig.mapper) ?: return@apply
                if (!allowedConfigs.contains(activeConfig.configId, false)) return@apply
                // Offset if glideslope is present
                val gs = get(GlideSlope.mapper)
                val offsetNm = gs?.offsetNm ?: 0f
                shapeRenderer.color = Color.CYAN
                dir.setLength((nmToPx(1) - nmToPx(offsetNm)))
                val startPos = Vector2(pos.x, pos.y)
                startPos.plusAssign(dir)
                dir.setLength(1f)
                val perpendicularVector = Vector2(dir).rotate90(-1).scl(nmToPx(0.4f))
                for (j in 1..loc.maxDistNm step 2) {
                    if (j % 5 == 0) shapeRenderer.line(startPos - perpendicularVector, startPos + perpendicularVector)
                    dir.setLength(nmToPx(1))
                    val endPos = startPos + dir
                    shapeRenderer.line(startPos, endPos)
                    if ((j + 1) % 5 == 0) shapeRenderer.line(endPos - perpendicularVector, endPos + perpendicularVector)
                    dir.setLength(nmToPx(2))
                    startPos.plusAssign(dir)
                }
                dir.setLength(1f)
                renderedRunwayCenterlineIds.add(Pair(appInfo.airportId, appInfo.rwyObj.entity[RunwayInfo.mapper]?.rwyId ?: return@apply))
            }
        }

        // Render extended centerline for visual approach if necessary (RNP approach, etc.)
        val visualApp = visualAppFamilyEntities.getEntities()
        for (i in 0 until visualApp.size()) {
            visualApp[i]?.apply {
                val visualAppEntity = getOrLogMissing(VisualApproach.mapper)?.visual ?: return@apply
                val pos = visualAppEntity.getOrLogMissing(Position.mapper) ?: return@apply
                val dir = visualAppEntity.getOrLogMissing(Direction.mapper)?.trackUnitVector ?: return@apply
                val appInfo = visualAppEntity.getOrLogMissing(ApproachInfo.mapper) ?: return@apply
                if (appInfo.rwyObj.entity.hasNot(ActiveLanding.mapper)) return@apply
                if (appInfo.rwyObj.entity.has(RunwayClosed.mapper)) return@apply
                // If this runway already has a localizer rendered, don't render extended centerline
                if (renderedRunwayCenterlineIds.contains(Pair(appInfo.airportId, appInfo.rwyObj.entity[RunwayInfo.mapper]?.rwyId))) return@apply
                shapeRenderer.color = Color.CYAN
                dir.setLength((nmToPx(1)))
                val startPos = Vector2(pos.x, pos.y)
                startPos.plusAssign(dir)
                dir.setLength(1f)
                val perpendicularVector = Vector2(dir).rotate90(-1).scl(nmToPx(0.4f))
                for (j in 1..15 step 2) {
                    if (j % 5 == 0) shapeRenderer.line(startPos - perpendicularVector, startPos + perpendicularVector)
                    dir.setLength(nmToPx(1))
                    val endPos = startPos + dir
                    shapeRenderer.line(startPos, endPos)
                    if ((j + 1) % 5 == 0) shapeRenderer.line(endPos - perpendicularVector, endPos + perpendicularVector)
                    dir.setLength(nmToPx(2))
                    startPos.plusAssign(dir)
                }
                dir.setLength(1f)
            }
        }

        // Render line from aircraft on visual approach to runway touchdown zone for current selected aircraft
        CLIENT_SCREEN?.selectedAircraft?.let {
            val rwyPos = it.entity[VisualCaptured.mapper]?.visApp?.getOrLogMissing(Position.mapper) ?: return@let
            val rDataPos = it.entity.getOrLogMissing(RadarData.mapper)?.position ?: return@let
            shapeRenderer.color = Color.WHITE
            shapeRenderer.line(rwyPos.x, rwyPos.y, rDataPos.x, rDataPos.y)
        }

        // Render current UI selected aircraft's lateral navigation state, accessed via radarScreen's uiPane
        CLIENT_SCREEN?.selectedAircraft?.let {
            val aircraftPos = it.entity.getOrLogMissing(RadarData.mapper)?.position ?: return@let
            val controllable = it.entity.getOrLogMissing(Controllable.mapper) ?: return@let
            val vectorUnchanged = uiPane.clearanceState.vectorHdg == uiPane.userClearanceState.vectorHdg
            val noVector = uiPane.userClearanceState.vectorHdg == null
            val clearanceStateVectorHdg = uiPane.clearanceState.vectorHdg
            val controlledByPlayer = controllable.sectorId == CLIENT_SCREEN?.playerSector
            if (clearanceStateVectorHdg != null && controlledByPlayer) {
                renderVector(aircraftPos.x, aircraftPos.y, clearanceStateVectorHdg, false)
            } else {
                renderRouteSegments(aircraftPos.x, aircraftPos.y, it.entity[RouteSegment.mapper]?.segments ?: GdxArray(),
                skipAircraftToFirstWaypoint = !controlledByPlayer && clearanceStateVectorHdg != null,
                    forceRenderChangedAircraftToFirstWaypoint = false)
            }
            if (controlledByPlayer) {
                if (!vectorUnchanged && !uiPane.appTrackCaptured) uiPane.userClearanceState.vectorHdg?.let { newHdg ->
                    // Render new vector if changed and is not null, and aircraft has not captured approach track
                    renderVector(aircraftPos.x, aircraftPos.y, newHdg, true)
                }
                renderRouteSegments(aircraftPos.x, aircraftPos.y, uiPane.userClearanceRouteSegments, skipAircraftToFirstWaypoint = !noVector,
                    forceRenderChangedAircraftToFirstWaypoint = !vectorUnchanged && noVector)
            }
        }

        // Render wake separation lines
        val wakeRenderEntities = wakeRenderFamilyEntities.getEntities()
        val reusedSideVector = Vector2()
        for (i in 0 until wakeRenderEntities.size()) {
            wakeRenderEntities[i]?.apply {
                val position = getOrLogMissing(Position.mapper) ?: return@apply
                val appOppDirUnitVector = get(Direction.mapper)?.trackUnitVector
                    ?: get(ApproachInfo.mapper)?.rwyObj?.entity?.getOrLogMissing(Direction.mapper)?.let { -it.trackUnitVector } ?: return@apply
                val acDistances = getOrLogMissing(ApproachWakeSequence.mapper)?.aircraftDist ?: return@apply
                val prevWakes = GdxArray<Pair<Entity, Float>>()
                for (j in 0 until acDistances.size) {
                    val followerAc = acDistances[j]
                    // If aircraft is from other approach, they will not be affected by wake from this approach
                    // But add to prevWakes to be included in calculation for aircraft behind
                    if (followerAc.isFromOtherApproach) {
                        prevWakes.add(Pair(followerAc.aircraft, followerAc.distFromThrNm))
                        continue
                    }
                    val followerAcPerf = followerAc.aircraft[AircraftInfo.mapper]?.aircraftPerf ?: continue

                    // Get all previous aircraft, find the one that imposes the maximum distance from runway on
                    // follower aircraft
                    var maxRequiredDistFromRwyNm = -1f
                    var maxDistPrevAc: Entity? = null
                    for (k in 0 until prevWakes.size) {
                        val prevWake = prevWakes[k]
                        val prevAc = prevWake.first
                        val prevAcPerf = prevAc[AircraftInfo.mapper]?.aircraftPerf ?: continue
                        val wakeDistNmRequired = WakeMatrix.getDistanceRequired(prevAcPerf.wakeCategory,
                            prevAcPerf.recat, followerAcPerf.wakeCategory, followerAcPerf.recat)
                        if (wakeDistNmRequired <= 2.5f) continue
                        if (prevWake.second + wakeDistNmRequired > maxRequiredDistFromRwyNm) {
                            maxRequiredDistFromRwyNm = prevWake.second + wakeDistNmRequired
                            maxDistPrevAc = prevAc
                        }
                    }
                    if (maxRequiredDistFromRwyNm > 0 && maxDistPrevAc != null) {
                        val distPxRequired = nmToPx(maxRequiredDistFromRwyNm)
                        val centerX = position.x + appOppDirUnitVector.x * distPxRequired
                        val centerY = position.y + appOppDirUnitVector.y * distPxRequired
                        val isSelected = maxDistPrevAc == CLIENT_SCREEN?.selectedAircraft?.entity
                        reusedSideVector.set(appOppDirUnitVector).rotate90(0).setLength(nmToPx(if (isSelected) 1.5f else 0.9f))
                        shapeRenderer.color = if (isSelected) Color.YELLOW else WAKE_INDICATOR_COLOUR
                        shapeRenderer.line(centerX - reusedSideVector.x, centerY - reusedSideVector.y,
                            centerX + reusedSideVector.x, centerY + reusedSideVector.y)
                    }

                    // Clear wake queue, add self to the queue
                    prevWakes.clear()
                    prevWakes.add(Pair(followerAc.aircraft, followerAc.distFromThrNm))
                }
            }
        }

        // Render trajectory line for controlled aircraft
        val trajectory = trajectoryFamilyEntities.getEntities()
        for (i in 0 until trajectory.size()) {
            trajectory[i]?.apply {
                val controllable = getOrLogMissing(Controllable.mapper) ?: return@apply
                if (controllable.sectorId != CLIENT_SCREEN?.playerSector) return@apply
                val rData = getOrLogMissing(RadarData.mapper) ?: return@apply
                val srColor = getOrLogMissing(SRColor.mapper) ?: return@apply
                val windPxps = getOrLogMissing(AffectedByWind.mapper)?.windVectorPxps ?: Vector2()
                val spdVector = Vector2(rData.direction.trackUnitVector).scl(ktToPxps(rData.speed.speedKts) * TRAJECTORY_DURATION_S)
                val windVector = Vector2(windPxps).scl(TRAJECTORY_DURATION_S.toFloat())
                shapeRenderer.color = srColor.color
                shapeRenderer.line(rData.position.x, rData.position.y, rData.position.x + spdVector.x + windVector.x, rData.position.y + spdVector.y + windVector.y)
            }
        }

        // Render conflicts and potential conflicts (potential conflicts rendered first so actual conflicts will draw over them)
        CLIENT_SCREEN?.also {
            shapeRenderer.color = Color.YELLOW
            for (i in 0 until it.potentialConflicts.size) {
                it.potentialConflicts[i]?.apply {
                    val pos1 = entity1.getOrLogMissing(RadarData.mapper)?.position ?: return@apply
                    val pos2 = entity2.getOrLogMissing(RadarData.mapper)?.position ?: return@apply
                    shapeRenderer.circle(pos1.x, pos1.y, nmToPx(latSepRequiredNm) / 2)
                    shapeRenderer.circle(pos2.x, pos2.y, nmToPx(latSepRequiredNm) / 2)
                    shapeRenderer.line(pos1.x, pos1.y, pos2.x, pos2.y)
                }
            }

            shapeRenderer.color = Color.RED
            for (i in 0 until it.conflicts.size) {
                it.conflicts[i]?.apply {
                    val pos1 = entity1.getOrLogMissing(RadarData.mapper)?.position ?: return@apply
                    val pos2 = entity2?.get(RadarData.mapper)?.position
                    shapeRenderer.circle(pos1.x, pos1.y, nmToPx(latSepRequiredNm) / 2)
                    if (pos2 != null) {
                        shapeRenderer.circle(pos2.x, pos2.y, nmToPx(latSepRequiredNm) / 2)
                    }
                }
            }

            shapeRenderer.color = Color.MAGENTA
            for (i in 0 until it.predictedConflicts.size) {
                val halfLength = nmToPx(0.75f)
                it.predictedConflicts[i]?.apply {
                    shapeRenderer.rect(posX - halfLength, posY - halfLength, halfLength * 2, halfLength * 2)
                }
            }
        }

        // Render distance measuring line
        GAME.gameClientScreen?.let {
            if (!it.distMeasurePoint1.isZero || !it.distMeasurePoint2.isZero) {
                shapeRenderer.color = Color.WHITE
                shapeRenderer.line(it.distMeasurePoint1, it.distMeasurePoint2)
            }
        }

        // Render aircraft trajectory (debug)
//        val trajectoryFamily = allOf(Position::class, Direction::class, Speed::class, AffectedByWind::class).get()
//        val trajectory = engine.getEntitiesFor(trajectoryFamily)
//        for (i in 0 until trajectory.size()) {
//            trajectory[i]?.apply {
//                val pos = get(Position.mapper) ?: return@apply
//                val dir = get(Direction.mapper) ?: return@apply
//                val spd = get(Speed.mapper) ?: return@apply
//                val wind = get(AffectedByWind.mapper) ?: return@apply
//                val spdVector = Vector2(dir.trackUnitVector).scl(ktToPxps(spd.speedKts) * 120)
//                shapeRenderer.color = Color.WHITE
//                shapeRenderer.line(pos.x, pos.y, pos.x + spdVector.x, pos.y + spdVector.y)
//                val windVector = Vector2(wind.windVectorPx).scl(120f)
//                shapeRenderer.color = Color.CYAN
//                shapeRenderer.line(pos.x + spdVector.x, pos.y + spdVector.y, pos.x + spdVector.x + windVector.x, pos.y + spdVector.y + windVector.y)
//                shapeRenderer.color = Color.YELLOW
//                shapeRenderer.line(pos.x, pos.y, pos.x + spdVector.x + windVector.x, pos.y + spdVector.y + windVector.y)
//            }
//        }

        // Calculate bounding box for const zoom stage's visible section
        shapeRenderer.setBoundingRect(-UI_WIDTH / 2 + uiPane.paneWidth, -UI_HEIGHT / 2, UI_WIDTH - uiPane.paneWidth, UI_HEIGHT)
        constZoomBoundingRect.set(-UI_WIDTH / 2 + uiPane.paneWidth, -UI_HEIGHT / 2, UI_WIDTH - uiPane.paneWidth, UI_HEIGHT)

        shapeRenderer.projectionMatrix = constZoomStage.camera.combined
        // Render datatag to aircraft icon line
        val datatagLines = datatagLineFamilyEntities.getEntities()
        // Single float array instance to prevent reallocation and reduce memory usage
        val xBorder = floatArrayOf(Float.MIN_VALUE, Float.MAX_VALUE)
        val yBorder = floatArrayOf(Float.MIN_VALUE, Float.MAX_VALUE)
        for (i in 0 until datatagLines.size()) {
            datatagLines[i]?.apply {
                val datatag = getOrLogMissing(Datatag.mapper) ?: return@apply
                if (!datatag.initialPosSet) return@apply
                val radarData = getOrLogMissing(RadarData.mapper) ?: return@apply
                val radarX = (radarData.position.x - camX) / camZoom
                val radarY = (radarData.position.y - camY) / camZoom
                val leftX = datatag.imgButton.x
                val width = datatag.imgButton.width
                val bottomY = datatag.imgButton.y
                val height = datatag.imgButton.height
                // Don't need to draw if aircraft blip is inside box
                val offset = 10 // Don't draw if icon center within 10px of datatag border
                if (withinRange(radarX, leftX - offset, leftX + width + offset) && withinRange(radarY, bottomY - offset, bottomY + height + offset)) return@apply
                var startX = leftX + width / 2
                var startY = bottomY + height / 2
                val degree = getRequiredTrack(startX, startY, radarX, radarY)
                xBorder[0] = leftX
                xBorder[1] = leftX + width
                yBorder[0] = bottomY
                yBorder[1] = bottomY + height
                val results = pointsAtBorder(xBorder, yBorder, startX, startY, degree)
                startX = results[0]
                startY = results[1]
                shapeRenderer.color = Color.WHITE
                shapeRenderer.line(startX, startY, radarX, radarY)
            }
        }

        // Render circles with constant zoom size
        val constCircles = constCircleFamilyEntities.getEntities()
        for (i in 0 until constCircles.size()) {
            constCircles[i]?.apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val circle = getOrLogMissing(GCircle.mapper) ?: return@apply
                val srColor = getOrLogMissing(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.circle((pos.x - camX) / camZoom, (pos.y - camY) / camZoom, circle.radius)
            }
        }

        // Render glide slope circles with constant zoom size
        val gsCircles = gsCircleFamilyEntities.getEntities()
        for (i in 0 until gsCircles.size()) {
            gsCircles[i]?.apply {
                val gsCirclePos = getOrLogMissing(GlideSlopeCircle.mapper) ?: return@apply
                val appInfo = getOrLogMissing(ApproachInfo.mapper) ?: return@apply
                val allowedConfigs = getOrLogMissing(RunwayConfigurationList.mapper)?.rwyConfigs ?: return@apply
                if (appInfo.rwyObj.entity.hasNot(ActiveLanding.mapper)) return@apply
                if (appInfo.rwyObj.entity.has(RunwayClosed.mapper)) return@apply
                val activeConfig = CLIENT_SCREEN?.airports?.get(appInfo.airportId)?.entity?.get(ActiveRunwayConfig.mapper) ?: return@apply
                if (!allowedConfigs.contains(activeConfig.configId, false)) return@apply
                shapeRenderer.color = Color.CYAN
                for (pos in gsCirclePos.positions) {
                    shapeRenderer.circle((pos.x - camX) / camZoom, (pos.y - camY) / camZoom, 4f)
                }
            }
        }
        shapeRenderer.end()

        GAME.batch.packedColor = Color.WHITE_FLOAT_BITS // Prevent fading out behaviour during selectBox animations due to tint being changed
        GAME.batch.begin()

        // Debug: Render route zone min altitude
        // renderRouteZoneAlts()

        // Update runway labels rendering size, position
        val rwyLabels = rwyLabelFamilyEntities.getEntities()
        for (i in 0 until rwyLabels.size()) {
            rwyLabels[i]?.apply {
                val labelInfo = getOrLogMissing(GenericLabel.mapper) ?: return@apply
                val rwyLabel = getOrLogMissing(RunwayLabel.mapper) ?: return@apply
                val direction = getOrLogMissing(Direction.mapper) ?: return@apply
                labelInfo.label.apply {
                    rwyLabel.apply {
                        val spacingFromCentre = sqrt(prefWidth * prefWidth + prefHeight * prefHeight) / 2 + 3 / camZoom + if (positionToRunway == 0.byte) 0f else rwyWidthPx / 2
                        if (!dirSet) {
                            dirUnitVector = ImmutableVector2(direction.trackUnitVector.x, direction.trackUnitVector.y)
                            when (positionToRunway) {
                                1.byte -> {
                                    dirUnitVector = dirUnitVector.withRotation90(-1)
                                }
                                (-1).byte -> {
                                    dirUnitVector = dirUnitVector.withRotation90(1)
                                }
                                else -> {
                                    dirUnitVector = dirUnitVector.withRotation90(0)
                                    dirUnitVector = dirUnitVector.withRotation90(0)
                                    if (positionToRunway != 0.byte) FileLog.info("Render runway label", "Invalid positionToRunway $positionToRunway set, using default value 0")
                                }
                            }
                            dirSet = true
                        }
                        val fullDirVector = dirUnitVector.times(spacingFromCentre)
                        labelInfo.xOffset = fullDirVector.x - prefWidth / 2
                        labelInfo.yOffset = fullDirVector.y - prefHeight / 2
                    }
                }
            }
        }

        // Render generic labels (non-constant size)
        val labels = labelFamilyEntities.getEntities()
        for (i in 0 until labels.size()) {
            labels[i]?.apply {
                val labelInfo = getOrLogMissing(GenericLabel.mapper) ?: return@apply
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                labelInfo.label.apply {
                    setPosition(pos.x + labelInfo.xOffset, pos.y + labelInfo.yOffset)
                    drawBounding(GAME.batch, 1f, worldBoundingRect)
                }
            }
        }

        // Render array of generic labels (non-constant size)
        val labelArray = labelArrayFamilyEntities.getEntities()
        for (i in 0 until labelArray.size()) {
            labelArray[i]?.apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val genericLabels = getOrLogMissing(GenericLabels.mapper) ?: return@apply
                for (j in 0 until genericLabels.labels.size) {
                    val labelInfo = genericLabels.labels[j]
                    if (labelInfo.label.text.isEmpty) continue
                    labelInfo.label.apply {
                        setPosition(pos.x + labelInfo.xOffset, pos.y + labelInfo.yOffset)
                        drawBounding(GAME.batch, 1f, worldBoundingRect)
                    }
                }
            }
        }

        // Render aircraft blip, aircraft trail if needed
        val blipSize = if (camZoom <= 1) AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 * camZoom
        else AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 + (camZoom - 1) * AIRCRAFT_BLIP_LENGTH_CHANGE_PX_PER_ZOOM
        val trailSize = if (camZoom <= 1) AIRCRAFT_TRAIL_LENGTH_PX_ZOOM_1 * camZoom
        else AIRCRAFT_TRAIL_LENGTH_PX_ZOOM_1 + (camZoom - 1) * AIRCRAFT_TRAIL_LENGTH_CHANGE_PX_PER_ZOOM
        val allAircraft = aircraftFamilyEntities.getEntities()
        for (i in 0 until allAircraft.size()) {
            allAircraft[i]?.apply {
                val trailInfo = getOrLogMissing(TrailInfo.mapper) ?: return@apply
                val rsSprite = getOrLogMissing(RSSprite.mapper) ?: return@apply
                val radarData = getOrLogMissing(RadarData.mapper) ?: return@apply
                val flightType = getOrLogMissing(FlightType.mapper) ?: return@apply
                val controllable = getOrLogMissing(Controllable.mapper) ?: return@apply
                run {
                    // If player has turned off trail for uncontrolled aircraft
                    if (SHOW_UNCONTROLLED_AIRCRAFT_TRAIL == UNCONTROLLED_AIRCRAFT_TRAIL_OFF
                        && controllable.sectorId != CLIENT_SCREEN?.playerSector) return@run
                    // If player enables trail for uncontrolled aircraft only when selected
                    if (SHOW_UNCONTROLLED_AIRCRAFT_TRAIL == UNCONTROLLED_AIRCRAFT_TRAIL_SELECTED
                        && controllable.sectorId != CLIENT_SCREEN?.playerSector
                        && CLIENT_SCREEN?.selectedAircraft?.entity != this) return@run
                    val textureToDraw = when (flightType.type) {
                        FlightType.ARRIVAL -> dotBlue
                        FlightType.DEPARTURE -> dotGreen
                        else -> {
                            FileLog.info("RenderingSystem", "Invalid flight type ${flightType.type} for trail dot rendering")
                            null
                        }
                    }
                    val maxSize = min(trailInfo.positions.size, TRAIL_DURATION_S / TRAIL_DOT_UPDATE_INTERVAL_S)
                    var index = 0
                    for (pos in trailInfo.positions) {
                        if (index >= maxSize) break
                        GAME.batch.drawBounding(textureToDraw, pos.x - trailSize / 2, pos.y - trailSize / 2,
                            trailSize, trailSize, worldBoundingRect)
                        index++
                    }
                }
                rsSprite.drawable.drawBounding(GAME.batch, radarData.position.x - blipSize / 2,
                    radarData.position.y - blipSize / 2, blipSize, blipSize, worldBoundingRect)
            }
        }

        GAME.batch.projectionMatrix = constZoomStage.camera.combined
        // Render generic constant size labels
        val constLabels = constSizeLabelFamilyEntities.getEntities()
        for (i in 0 until constLabels.size()) {
            constLabels[i].apply {
                val labelInfo = getOrLogMissing(GenericLabel.mapper) ?: return@apply
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                labelInfo.label.apply {
                    setPosition((pos.x - camX) / camZoom + labelInfo.xOffset, (pos.y - camY) / camZoom + labelInfo.yOffset)
                    drawBounding(GAME.batch, 1f, constZoomBoundingRect)
                }
            }
        }

        // Render generic constant size label arrays
        val constLabelArray = constSizeLabelArrayFamilyEntities.getEntities()
        for (i in 0 until constLabelArray.size()) {
            constLabelArray[i].apply {
                val pos = getOrLogMissing(Position.mapper) ?: return@apply
                val genericLabels = getOrLogMissing(GenericLabels.mapper) ?: return@apply
                for (j in 0 until genericLabels.labels.size) {
                    val labelInfo = genericLabels.labels[j]
                    if (labelInfo.label.text.isEmpty) continue
                    labelInfo.label.apply {
                        setPosition((pos.x - camX) / camZoom + labelInfo.xOffset, (pos.y - camY) / camZoom + labelInfo.yOffset)
                        drawBounding(GAME.batch, 1f, constZoomBoundingRect)
                    }
                }
            }
        }

        // Render conflicting min alt sector labels in red
        if (SHOW_MVA_ALTITUDE) CLIENT_SCREEN?.also {
            for (i in 0 until it.conflicts.size) {
                it.conflicts[i]?.apply {
                    if (minAltSectorIndex != null) {
                        val minAltSector = it.minAltSectors[minAltSectorIndex]?.entity ?: return@apply
                        val labelInfo = minAltSector.getOrLogMissing(GenericLabel.mapper) ?: return@apply
                        labelInfo.apply {
                            val currStyle = label.style
                            updateStyle("MinAltSectorConflict")
                            label.drawBounding(GAME.batch, 1f, constZoomBoundingRect)
                            label.style = currStyle
                        }
                    }
                }
            }
        }

        var lastRenderDatatagAircraft: Entity? = null
        // Render aircraft datatags (except the one marked with RenderLast)
        val datatags = datatagFamilyEntities.getEntities()
        for (i in 0 until datatags.size()) {
            datatags[i]?.apply {
                val datatag = getOrLogMissing(Datatag.mapper) ?: return@apply
                val radarData = getOrLogMissing(RadarData.mapper) ?: return@apply
                if (datatag.renderLast) {
                    if (lastRenderDatatagAircraft != null) FileLog.info("RenderingSystem", "Multiple render last aircraft datatags found")
                    lastRenderDatatagAircraft = this
                }
                if (!datatag.smallLabelFont && camZoom > DATATAG_ZOOM_THRESHOLD) updateDatatagLabelSize(datatag, true)
                else if (datatag.smallLabelFont && camZoom <= DATATAG_ZOOM_THRESHOLD) updateDatatagLabelSize(datatag, false)
                val leftX = (radarData.position.x - camX) / camZoom + datatag.xOffset
                val bottomY = (radarData.position.y - camY) / camZoom + datatag.yOffset
                datatag.initialPosSet = true
                datatag.imgButton.apply {
                    setPosition(leftX, bottomY)
                    drawBounding(GAME.batch, 1f, constZoomBoundingRect)
                }
                datatag.clickSpot.setPosition(leftX, bottomY)
                var labelY = bottomY + LABEL_PADDING
                for (j in datatag.labelArray.size - 1 downTo 0) {
                    datatag.labelArray[j].let { label ->
                        if (label.text.isNullOrEmpty()) return@let
                        label.setPosition(leftX + LABEL_PADDING, labelY)
                        label.drawBounding(GAME.batch, 1f, constZoomBoundingRect)
                        labelY += (label.height + DATATAG_ROW_SPACING_PX)
                    }
                }
            }
        }

        // Save the render last datatag to be rendered after all other datatags
        lastRenderDatatagAircraft?.apply {
            val datatag = getOrLogMissing(Datatag.mapper) ?: return@apply
            val radarData = getOrLogMissing(RadarData.mapper) ?: return@apply
            if (!datatag.smallLabelFont && camZoom > DATATAG_ZOOM_THRESHOLD) updateDatatagLabelSize(datatag, true)
            else if (datatag.smallLabelFont && camZoom <= DATATAG_ZOOM_THRESHOLD) updateDatatagLabelSize(datatag, false)
            val leftX = (radarData.position.x - camX) / camZoom + datatag.xOffset
            val bottomY = (radarData.position.y - camY) / camZoom + datatag.yOffset
            datatag.initialPosSet = true
            datatag.imgButton.apply {
                setPosition(leftX, bottomY)
                drawBounding(GAME.batch, 1f, constZoomBoundingRect)
            }
            datatag.clickSpot.setPosition(leftX, bottomY)
            var labelY = bottomY + LABEL_PADDING
            for (j in datatag.labelArray.size - 1 downTo 0) {
                datatag.labelArray[j].let { label ->
                    if (label.text.isNullOrEmpty()) return@let
                    label.setPosition(leftX + LABEL_PADDING, labelY)
                    label.drawBounding(GAME.batch, 1f, constZoomBoundingRect)
                    labelY += (label.height + DATATAG_ROW_SPACING_PX)
                }
            }
        }

        // Render dist measure label
        GAME.gameClientScreen?.let {
            if (!it.distMeasurePoint1.isZero || !it.distMeasurePoint2.isZero) {
                val distNm = pxToNm(calculateDistanceBetweenPoints(it.distMeasurePoint1.x, it.distMeasurePoint1.y,
                    it.distMeasurePoint2.x, it.distMeasurePoint2.y))
                distMeasureLabel.setText(((distNm * 10).roundToInt() / 10f).toString())
                distMeasureLabel.pack()
                val centerX = ((it.distMeasurePoint1.x + it.distMeasurePoint2.x) / 2 - camX) / camZoom
                val centerY = ((it.distMeasurePoint1.y + it.distMeasurePoint2.y) / 2 - camY) / camZoom
                distMeasureLabel.setPosition(centerX - distMeasureLabel.width / 2, centerY - distMeasureLabel.height / 2)
                distMeasureLabel.drawBounding(GAME.batch, 1f, constZoomBoundingRect)
            }
        }

        // Render contact/conflict notification dots for 1s every 2s
        if (System.currentTimeMillis() % 2000 > 1000) {
            val contactDots = contactDotFamilyEntities.getEntities()
            for (i in 0 until contactDots.size()) {
                contactDots[i]?.apply {
                    val radarData = getOrLogMissing(RadarData.mapper) ?: return@apply
                    val flightType = getOrLogMissing(FlightType.mapper) ?: return@apply
                    val radarX = (radarData.position.x - camX) / camZoom
                    val radarY = (radarData.position.y - camY) / camZoom
                    calculateContactDotPosition(radarX, radarY)?.let {
                        val textureToDraw = when (flightType.type) {
                            FlightType.ARRIVAL -> dotBlue
                            FlightType.DEPARTURE -> dotGreen
                            else -> {
                                FileLog.info("RenderingSystem", "Invalid flight type ${flightType.type} for contact dot rendering")
                                null
                            }
                        }
                        if (textureToDraw != null) GAME.batch.drawBounding(textureToDraw, it.x - DOT_RADIUS, it.y - DOT_RADIUS,
                            2 * DOT_RADIUS, 2 * DOT_RADIUS, constZoomBoundingRect)
                    }
                }
            }
            CLIENT_SCREEN?.also {
                for (i in 0 until it.conflicts.size) {
                    it.conflicts[i]?.apply {
                        val pos1 = entity1.getOrLogMissing(RadarData.mapper)?.position ?: return@apply
                        val pos2 = entity2?.get(RadarData.mapper)?.position
                        val radarX1 = (pos1.x - camX) / camZoom
                        val radarY1 = (pos1.y - camY) / camZoom
                        if (pos2 != null) {
                            val radarX2 = (pos2.x - camX) / camZoom
                            val radarY2 = (pos2.y - camY) / camZoom
                            calculateContactDotPosition((radarX1 + radarX2) / 2, (radarY1 + radarY2) / 2)?.let { pos ->
                                GAME.batch.drawBounding(dotRed, pos.x - DOT_RADIUS, pos.y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS, constZoomBoundingRect)
                            }
                        } else {
                            calculateContactDotPosition(radarX1, radarY1)?.let { pos ->
                                GAME.batch.drawBounding(dotRed, pos.x - DOT_RADIUS, pos.y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS, constZoomBoundingRect)
                            }
                        }
                    }
                }
                for (i in 0 until it.predictedConflicts.size) {
                    it.predictedConflicts[i]?.apply {
                        val radarX = (posX - camX) / camZoom
                        val radarY = (posY - camY) / camZoom
                        calculateContactDotPosition(radarX, radarY)?.let { pos ->
                            GAME.batch.drawBounding(dotMagenta, pos.x - DOT_RADIUS, pos.y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS, constZoomBoundingRect)
                        }
                    }
                }
            }
        }

        GAME.batch.end()

        constZoomStage.act(deltaTime)
        constZoomStage.draw()

        // Draw UI pane the last, above all the other elements
        uiStage.act(deltaTime)
        uiStage.draw()
    }

    /**
     * Renders the input route segment for the user to see, color depending on whether the segment being rendered has
     * changed
     * @param posX the x coordinate of the aircraft
     * @param posY the y coordinate of the aircraft
     * @param segments the route segments to render
     * @param skipAircraftToFirstWaypoint whether the skip rendering of the first aircraft -> waypoint segment (used when
     * aircraft has been cleared vectors while still on the SID/STAR); will be overridden by [forceRenderChangedAircraftToFirstWaypoint]
     * if it is true
     * @param forceRenderChangedAircraftToFirstWaypoint whether to force the rendering of the first aircraft -> waypoint
     * segment as a changed segment; will override [skipAircraftToFirstWaypoint] if this is true
     */
    private fun renderRouteSegments(posX: Float, posY: Float, segments: GdxArray<Route.LegSegment>, skipAircraftToFirstWaypoint: Boolean,
                                    forceRenderChangedAircraftToFirstWaypoint: Boolean) {
        for (i in 0 until segments.size) { segments[i]?.also { seg ->
            shapeRenderer.color = when {
                seg.changed -> Color.YELLOW
                skipAircraftToFirstWaypoint -> Color.GRAY
                else -> Color.WHITE
            }
            val leg1 = seg.leg1
            val leg2 = seg.leg2
            // Do not render any segments containing a missed approach leg
            if ((leg1?.phase == Route.Leg.MISSED_APP || leg2?.phase == Route.Leg.MISSED_APP)) return
            when {
                (leg1 == null && leg2 is Route.WaypointLeg) -> {
                    // Aircraft to waypoint segment
                    if (forceRenderChangedAircraftToFirstWaypoint) shapeRenderer.color = Color.YELLOW
                    else if (skipAircraftToFirstWaypoint) return@also
                    val wptPos = CLIENT_SCREEN?.waypoints?.get(leg2.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    shapeRenderer.line(posX, posY, wptPos.x, wptPos.y)
                }
                (leg1 is Route.WaypointLeg && leg2 is Route.WaypointLeg) -> {
                    // Waypoint to waypoint segment
                    val pos1 = CLIENT_SCREEN?.waypoints?.get(leg1.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    val pos2 = CLIENT_SCREEN?.waypoints?.get(leg2.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    shapeRenderer.line(pos1.x, pos1.y, pos2.x, pos2.y)
                }
                (leg1 == null && leg2 is Route.HoldLeg) -> {
                    // Hold segment
                    val wptPos = if (leg2.wptId.toInt() == -1) Position(posX, posY)
                    else CLIENT_SCREEN?.waypoints?.get(leg2.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    val wptVec = Vector2(wptPos.x, wptPos.y)
                    // Render a default 230 knot IAS @ 10000ft, 3 deg/s turn
                    val tasPxps = ktToPxps(266)
                    val turnRadPx = (tasPxps / Math.toRadians(3.0)).toFloat()
                    val legDistPx = nmToPx(leg2.legDist.toFloat())
                    val inboundLegDistPxps = sqrt(legDistPx * legDistPx - turnRadPx * turnRadPx)
                    val oppInboundLegVec = Vector2(Vector2.Y).rotateDeg(180f - (leg2.inboundHdg - MAG_HDG_DEV))
                        .scl(if (inboundLegDistPxps.isNaN()) 0f else inboundLegDistPxps)
                    val halfAbeamVec = Vector2(oppInboundLegVec).rotate90(leg2.turnDir.toInt()).scl(turnRadPx / inboundLegDistPxps)
                    shapeRenderer.line(wptVec, wptVec + oppInboundLegVec)
                    shapeRenderer.line(wptVec + halfAbeamVec * 2, wptVec + halfAbeamVec * 2 + oppInboundLegVec)

                    // Draw the top arc
                    val topArcCentreVec = wptVec + halfAbeamVec
                    val arcRotateVec = halfAbeamVec * leg2.turnDir.toInt() // This vector will always be facing right
                    var pVec = topArcCentreVec + arcRotateVec
                    for (j in 0 until 10) {
                        val nextVec = topArcCentreVec + arcRotateVec.rotateDeg(18f)
                        shapeRenderer.line(pVec, nextVec)
                        pVec = nextVec
                    }

                    // Draw the bottom arc
                    val bottomArcCentreVec = wptVec + oppInboundLegVec + halfAbeamVec
                    pVec = bottomArcCentreVec + arcRotateVec
                    for (j in 0 until 10) {
                        val nextVec = bottomArcCentreVec + arcRotateVec.rotateDeg(18f)
                        shapeRenderer.line(pVec, nextVec)
                        pVec = nextVec
                    }
                }
                (leg1 is Route.WaypointLeg && leg2 is Route.VectorLeg) -> {
                    // Waypoint to vector segment
                    val wptPos = CLIENT_SCREEN?.waypoints?.get(leg1.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    renderVector(wptPos.x, wptPos.y, leg2.heading, seg.changed)
                }
                (leg1 is Route.HoldLeg && leg2 is Route.WaypointLeg) -> {
                    // Hold to waypoint segment
                    val pos1 = if (leg1.wptId.toInt() == -1) Position(posX, posY)
                    else CLIENT_SCREEN?.waypoints?.get(leg1.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    val pos2 = CLIENT_SCREEN?.waypoints?.get(leg2.wptId)?.entity?.getOrLogMissing(Position.mapper) ?: return@also
                    shapeRenderer.line(pos1.x, pos1.y, pos2.x, pos2.y)
                }
            }
        }}
    }

    /**
     * Renders the input vector heading line given the starting position of the line and whether it differs from the
     * clearance status
     * @param posX the x coordinate of the start of the line
     * @param posY the y coordinate of the start of the line
     * @param hdg the heading the line should depict
     * @param differs whether the vector differs from the clearance state
     */
    private fun renderVector(posX: Float, posY: Float, hdg: Short, differs: Boolean) {
        shapeRenderer.color = if (differs) Color.YELLOW else Color.WHITE
        val sectorBoundingRectangle = CLIENT_SCREEN?.primarySectorBound ?: return
        val lineEnd = pointsAtBorder(floatArrayOf(sectorBoundingRectangle.x, sectorBoundingRectangle.x  + sectorBoundingRectangle.width),
            floatArrayOf(sectorBoundingRectangle.y, sectorBoundingRectangle.y + sectorBoundingRectangle.height), posX, posY, hdg - MAG_HDG_DEV)
        shapeRenderer.line(posX, posY, lineEnd[0], lineEnd[1])
    }

    /**
     * Refreshes the rendering status for each waypoint, so that each waypoint is only shown when the aircraft's current
     * direct is it, or if the selected aircraft has a waypoint in its route, or the modified selected route has the
     * waypoint in it
     * @param selectedAircraft the currently selected aircraft, or null if no aircraft selected
     */
    fun updateWaypointDisplay(selectedAircraft: Aircraft?) {
        val waypoints = waypointFamilyEntities.getEntities()
        val wptMap = GdxMap<Short, Entity>()
        for (i in 0 until waypoints.size()) {
            val wpt = waypoints[i]
             wpt += DoNotRenderShape()
             wpt += DoNotRenderLabel()
            wpt[WaypointInfo.mapper]?.wptId?.let { id -> wptMap[id] = wpt }
        }
        val clearanceRoutes = routeFamilyEntities.getEntities()
        // Check all aircraft routes for the first waypoint
        for (i in 0 until clearanceRoutes.size()) {
            clearanceRoutes[i]?.apply {
                val latest = get(PendingClearances.mapper)?.clearanceQueue?.last()?.clearanceState ?:
                getOrLogMissing(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return@apply
                if (latest.vectorHdg != null) return@apply
                if (latest.route.size < 1) return@apply
                val firstWpt = latest.route[0]
                if (firstWpt !is Route.WaypointLeg) return@apply
                val wptEntity = wptMap[firstWpt.wptId] ?: return@apply
                wptEntity.remove<DoNotRenderShape>()
                wptEntity.remove<DoNotRenderLabel>()
            }
        }
        // Check currently selected aircraft route
        if (selectedAircraft == null) return
        val latestClearance = selectedAircraft.entity[PendingClearances.mapper]?.clearanceQueue?.last()?.clearanceState ?:
        selectedAircraft.entity.getOrLogMissing(ClearanceAct.mapper)?.actingClearance?.clearanceState ?: return
        val route = latestClearance.route
        for (i in 0 until route.size) {
            val leg = route[i]
            if (leg !is Route.WaypointLeg) continue
            if (!leg.legActive) continue
            if (leg.phase == Route.Leg.MISSED_APP) continue
            val wptEntity = wptMap[leg.wptId] ?: continue
            wptEntity.remove<DoNotRenderShape>()
            wptEntity.remove<DoNotRenderLabel>()
        }
        // Check selected aircraft modified route - need to call this function in UI pane every time route is updated
        val uiSelectedClearanceRoute = uiPane.userClearanceState.route
        for (i in 0 until uiSelectedClearanceRoute.size) {
            val leg = uiSelectedClearanceRoute[i]
            if (leg !is Route.WaypointLeg) continue
            if (!leg.legActive) continue
            if (leg.phase == Route.Leg.MISSED_APP) continue
            val wptEntity = wptMap[leg.wptId] ?: continue
            wptEntity.remove<DoNotRenderShape>()
            wptEntity.remove<DoNotRenderLabel>()
        }
    }

    /**
     * Calculates the position of the flashing contact dot to render based on the world position of the event
     * @param radarX the x coordinate of the event
     * @param radarY the y coordinate of the event
     */
    private fun calculateContactDotPosition(radarX: Float, radarY: Float): Vector2? {
        val leftX = uiPane.paneWidth + 15 - UI_WIDTH / 2
        val rightX = UI_WIDTH / 2 - 15
        val bottomY = 15f - UI_HEIGHT / 2
        val topY = UI_HEIGHT / 2 - 15
        val centreX = uiPane.paneWidth / 2
        val centreY = 0f

        val intersectionVector = Vector2()
        if (Intersector.intersectSegments(centreX, centreY, radarX, radarY, leftX, topY, rightX, topY, intersectionVector))
            return intersectionVector
        if (Intersector.intersectSegments(centreX, centreY, radarX, radarY, rightX, topY, rightX, bottomY, intersectionVector))
            return intersectionVector
        if (Intersector.intersectSegments(centreX, centreY, radarX, radarY, rightX, bottomY, leftX, bottomY, intersectionVector))
            return intersectionVector
        if (Intersector.intersectSegments(centreX, centreY, radarX, radarY, leftX, bottomY, leftX, topY, intersectionVector))
            return intersectionVector
        return null
    }
}