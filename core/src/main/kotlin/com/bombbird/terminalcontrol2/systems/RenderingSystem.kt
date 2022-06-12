package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.LABEL_PADDING
import com.bombbird.terminalcontrol2.ui.UIPane
import com.bombbird.terminalcontrol2.ui.updateDatatagLabelSize
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.ashley.has
import ktx.collections.GdxArray
import ktx.math.*
import kotlin.math.sqrt

/**
 * Main rendering system, which renders to [GAME]'s spriteBatch or radarScreen's [shapeRenderer]
 *
 * Used only in RadarScreen
 * */
class RenderingSystem(private val shapeRenderer: ShapeRenderer,
                      private val stage: Stage, private val constZoomStage: Stage, private val uiStage: Stage,
                      private val uiPane: UIPane): EntitySystem() {
    private val lineArrayFamily: Family = allOf(GLineArray::class, SRColor::class).get()
    private val polygonFamily: Family = allOf(GPolygon::class, SRColor::class)
        .exclude(RenderLast::class).get()
    private val polygonLastFamily: Family = allOf(GPolygon::class, SRColor::class, RenderLast::class).get()
    private val circleFamily: Family = allOf(Position::class, GCircle::class, SRColor::class)
        .exclude(SRConstantZoomSize::class).get()
    private val runwayFamily: Family = allOf(RunwayInfo::class, SRColor::class).get()
    private val locFamily: Family = allOf(Position::class, Localizer::class, Direction::class).get()
    private val trajectoryFamily: Family = allOf(RadarData::class, Controllable::class, SRColor::class)
        .exclude(WaitingTakeoff::class).get()
    private val visualFamily: Family = allOf(VisualCaptured::class, RadarData::class).get()
    private val datatagLineFamily: Family = allOf(Datatag::class, RadarData::class)
        .exclude(WaitingTakeoff::class).get()
    private val constCircleFamily: Family = allOf(Position::class, GCircle::class, SRColor::class, SRConstantZoomSize::class).get()
    private val rwyLabelFamily: Family = allOf(GenericLabel::class, RunwayInfo::class, RunwayLabel::class).get()
    private val labelFamily: Family = allOf(GenericLabel::class, Position::class)
        .exclude(ConstantZoomSize::class).get()
    private val aircraftFamily: Family = allOf(AircraftInfo::class, RadarData::class, RSSprite::class)
        .exclude(WaitingTakeoff::class).get()
    private val constSizeLabelFamily: Family = allOf(GenericLabel::class, Position::class, ConstantZoomSize::class).get()
    private val datatagFamily: Family = allOf(Datatag::class, RadarData::class)
        .exclude(WaitingTakeoff::class).get()

    /** Main update function */
    override fun update(deltaTime: Float) {
        val camZoom = (stage.camera as OrthographicCamera).zoom
        val camX = stage.camera.position.x
        val camY = stage.camera.position.y

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.projectionMatrix = stage.camera.combined
        // Estimation circles
        shapeRenderer.color = Color.WHITE
        shapeRenderer.circle(0f, 0f, 1250f)
        shapeRenderer.circle(0f, 0f, 125f)
        shapeRenderer.circle(0f, 0f, 12.5f)

        // Render lineArrays
        val lineArrays = engine.getEntitiesFor(lineArrayFamily)
        for (i in 0 until lineArrays.size()) {
            lineArrays[i]?.apply {
                val lineArray = get(GLineArray.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                lineArray.vertices.let {
                    for (j in 0 until it.size - 3 step 2) {
                        shapeRenderer.line(it[j], it[j + 1], it[j + 2], it[j + 3])
                    }
                }
            }
        }

        // Render polygons
        val polygons = engine.getEntitiesFor(polygonFamily)
        for (i in 0 until polygons.size()) {
            polygons[i]?.apply {
                val poly = get(GPolygon.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.polygon(poly.vertices)
            }
        }

        // Render polygons with RenderLast
        val polygonsLast = engine.getEntitiesFor(polygonLastFamily)
        for (i in 0 until polygonsLast.size()) {
            polygonsLast[i]?.apply {
                val poly = get(GPolygon.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.polygon(poly.vertices)
            }
        }

        // Render circles
        val circles = engine.getEntitiesFor(circleFamily)
        for (i in 0 until circles.size()) {
            circles[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val circle = get(GCircle.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.circle(pos.x, pos.y, circle.radius)
            }
        }

        // Render runways
        val rwyWidthPx = RWY_WIDTH_PX_ZOOM_1 + (camZoom - 1) * RWY_WIDTH_CHANGE_PX_PER_ZOOM
        val rwys = engine.getEntitiesFor(runwayFamily)
        for (i in 0 until rwys.size()) {
            rwys[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val rect = get(GRect.mapper) ?: return@apply
                val deg = get(Direction.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                rect.height = rwyWidthPx
                shapeRenderer.color = srColor.color
                shapeRenderer.rect(pos.x, pos.y - rect.height / 2, 0f, rect.height / 2, rect.width, rect.height, 1f, 1f, deg.trackUnitVector.angleDeg())

                if (!has(ActiveLanding.mapper)) return@apply
                // Render extended centreline (21nm)
                shapeRenderer.color = Color.CYAN
                val startPos = Vector2(pos.x, pos.y) - deg.trackUnitVector * nmToPx(1)
                val perpendicularVector = Vector2(deg.trackUnitVector).rotate90(-1).scl(nmToPx(0.4f))
                for (j in 1..21 step 2) {
                    if (j % 5 == 0) shapeRenderer.line(startPos - perpendicularVector, startPos + perpendicularVector)
                    val endPos = startPos - deg.trackUnitVector * nmToPx(1)
                    shapeRenderer.line(startPos, endPos)
                    if ((j + 1) % 5 == 0) shapeRenderer.line(endPos - perpendicularVector, endPos + perpendicularVector)
                    startPos.minusAssign(deg.trackUnitVector * nmToPx(2))
                }
            }
        }

        // Render localizers
        val localizer = engine.getEntitiesFor(locFamily)
        for (i in 0 until localizer.size()) {
            localizer[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val loc = get(Localizer.mapper) ?: return@apply
                val dir = get(Direction.mapper)?.trackUnitVector ?: return@apply
                val posVec = Vector2(pos.x, pos.y)
                shapeRenderer.color = Color.CYAN
                shapeRenderer.line(posVec, posVec + dir * nmToPx(loc.maxDistNm.toFloat()))
            }
        }

        // Render line from aircraft on visual approach to runway touchdown zone
        val visual = engine.getEntitiesFor(visualFamily)
        for (i in 0 until visual.size()) {
            visual[i]?.apply {
                val rwyPos = get(VisualCaptured.mapper)?.visApp?.get(Position.mapper) ?: return@apply
                val rDataPos = get(RadarData.mapper)?.position ?: return@apply
                shapeRenderer.line(rwyPos.x, rwyPos.y, rDataPos.x, rDataPos.y)
            }
        }

        // Render current UI selected aircraft's lateral navigation state, accessed via radarScreen's uiPane
        GAME.gameClientScreen?.selectedAircraft?.let {
            val aircraftPos = it.entity[RadarData.mapper]?.position ?: return@let
            val vectorUnchanged = uiPane.clearanceState.vectorHdg == uiPane.userClearanceState.vectorHdg
            uiPane.clearanceState.vectorHdg?.let { hdg -> renderVector(aircraftPos.x, aircraftPos.y, hdg, false) } ?:
            run { renderRouteSegments(aircraftPos.x, aircraftPos.y, uiPane.clearanceRouteSegments) }
            if (!vectorUnchanged) uiPane.userClearanceState.vectorHdg?.let { newHdg ->
                // Render new vector if changed and is not null
                renderVector(aircraftPos.x, aircraftPos.y, newHdg, true)
            }
            renderRouteSegments(aircraftPos.x, aircraftPos.y, uiPane.userClearanceRouteSegments)
        }

        // Render trajectory line for controlled aircraft
        val trajectory = engine.getEntitiesFor(trajectoryFamily)
        for (i in 0 until trajectory.size()) {
            trajectory[i]?.apply {
                val controllable = get(Controllable.mapper) ?: return@apply
                if (controllable.sectorId != GAME.gameClientScreen?.playerSector) return@apply
                val rData = get(RadarData.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                val wind = get(AffectedByWind.mapper)
                val spdVector = Vector2(rData.direction.trackUnitVector).scl(ktToPxps(rData.speed.speedKts) * 90) // TODO change projection time based on settings
                val windVector = wind?.windVectorPxps?.let { Vector2(it).scl(90f) }
                shapeRenderer.color = srColor.color
                shapeRenderer.line(rData.position.x, rData.position.y, rData.position.x + spdVector.x + (windVector?.x ?: 0f), rData.position.y + spdVector.y + (windVector?.y ?: 0f))
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


        shapeRenderer.projectionMatrix = constZoomStage.camera.combined
        // Render datatag to aircraft icon line
        val datatagLines = engine.getEntitiesFor(datatagLineFamily)
        for (i in 0 until datatagLines.size()) {
            datatagLines[i]?.apply {
                val datatag = get(Datatag.mapper) ?: return@apply
                val radarData = get(RadarData.mapper) ?: return@apply
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
                val results = pointsAtBorder(floatArrayOf(leftX, leftX + width), floatArrayOf(bottomY, bottomY + height), startX, startY, degree)
                startX = results[0]
                startY = results[1]
                shapeRenderer.color = Color.WHITE
                shapeRenderer.line(startX, startY, radarX, radarY)
            }
        }

        // Render circles with constant zoom size
        val constCircles = engine.getEntitiesFor(constCircleFamily)
        for (i in 0 until constCircles.size()) {
            constCircles[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val circle = get(GCircle.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.circle((pos.x - camX) / camZoom, (pos.y - camY) / camZoom, circle.radius)
            }
        }
        shapeRenderer.end()

        GAME.batch.projectionMatrix = stage.camera.combined
        GAME.batch.begin()
        GAME.batch.packedColor = Color.WHITE_FLOAT_BITS // Prevent fading out behaviour during selectBox animations due to tint being changed

        // Update runway labels rendering size, position
        val rwyLabels = engine.getEntitiesFor(rwyLabelFamily)
        for (i in 0 until rwyLabels.size()) {
            rwyLabels[i]?.apply {
                val labelInfo = get(GenericLabel.mapper) ?: return@apply
                val rwyLabel = get(RunwayLabel.mapper) ?: return@apply
                val direction = get(Direction.mapper) ?: return@apply
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
                                    if (positionToRunway != 0.byte) Gdx.app.log("Render runway label", "Invalid positionToRunway $positionToRunway set, using default value 0")
                                }
                            }
                            dirSet = true
                        }
                        val fullDirVector = dirUnitVector.times(spacingFromCentre)
                        labelInfo.xOffset = fullDirVector.x - prefWidth / 2
                        labelInfo.yOffset = fullDirVector.y
                    }
                }
            }
        }

        // Render generic labels (non-constant size)
        val labels = engine.getEntitiesFor(labelFamily)
        for (i in 0 until labels.size()) {
            labels[i]?.apply {
                val labelInfo = get(GenericLabel.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                labelInfo.label.apply {
                    setPosition(pos.x + labelInfo.xOffset, pos.y + labelInfo.yOffset)
                    draw(GAME.batch, 1f)
                }
            }
        }

        // Render aircraft blip
        val blipSize = if (camZoom <= 1) AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 * camZoom else AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 + (camZoom - 1) * AIRCRAFT_BLIP_LENGTH_CHANGE_PX_PER_ZOOM
        val allAircraft = engine.getEntitiesFor(aircraftFamily)
        for (i in 0 until allAircraft.size()) {
            allAircraft[i]?.apply {
                val rsSprite = get(RSSprite.mapper) ?: return@apply
                val radarData = get(RadarData.mapper) ?: return@apply
                rsSprite.drawable.draw(GAME.batch, radarData.position.x - blipSize / 2, radarData.position.y - blipSize / 2, blipSize, blipSize)
            }
        }

        GAME.batch.projectionMatrix = constZoomStage.camera.combined
        // Render generic constant size labels
        val constLabels = engine.getEntitiesFor(constSizeLabelFamily)
        for (i in 0 until constLabels.size()) {
            constLabels[i].apply {
                val labelInfo = get(GenericLabel.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                labelInfo.label.apply {
                    setPosition((pos.x - camX) / camZoom + labelInfo.xOffset, (pos.y - camY) / camZoom + labelInfo.yOffset)
                    draw(GAME.batch, 1f)
                }
            }
        }

        // Render aircraft datatags
        val datatags = engine.getEntitiesFor(datatagFamily)
        for (i in 0 until datatags.size()) {
            datatags[i]?.apply {
                val datatag = get(Datatag.mapper) ?: return@apply
                val radarData = get(RadarData.mapper) ?: return@apply
                if (!datatag.smallLabelFont && camZoom > DATATAG_ZOOM_THRESHOLD) updateDatatagLabelSize(datatag, true)
                else if (datatag.smallLabelFont && camZoom <= DATATAG_ZOOM_THRESHOLD) updateDatatagLabelSize(datatag, false)
                val leftX = (radarData.position.x - camX) / camZoom + datatag.xOffset
                val bottomY = (radarData.position.y - camY) / camZoom + datatag.yOffset
                datatag.imgButton.apply {
                    setPosition(leftX, bottomY)
                    draw(GAME.batch, 1f)
                }
                datatag.clickSpot.setPosition(leftX, bottomY)
                var labelY = bottomY + LABEL_PADDING
                for (j in datatag.labelArray.size - 1 downTo 0) {
                    datatag.labelArray[j].let { label ->
                        if (label.text.isNullOrEmpty()) return@let
                        label.setPosition(leftX + LABEL_PADDING, labelY)
                        label.draw(GAME.batch, 1f)
                        labelY += (label.height + datatag.lineSpacing)
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
     */
    private fun renderRouteSegments(posX: Float, posY: Float, segments: GdxArray<Route.LegSegment>) {
        var firstPhase = Route.Leg.NORMAL

        for (i in 0 until segments.size) { segments[i]?.also { seg ->
            shapeRenderer.color = if (seg.changed) Color.YELLOW else Color.WHITE
            val leg1 = seg.leg1
            val leg2 = seg.leg2
            // Set first leg phase to be either leg1's phase (for holding) or leg2's phase
            if (i == 0) firstPhase = leg2?.phase ?: Route.Leg.NORMAL
            if ((leg1?.phase == Route.Leg.MISSED_APP || leg2?.phase == Route.Leg.MISSED_APP) && firstPhase != Route.Leg.MISSED_APP) return
            when {
                (leg1 == null && leg2 is Route.WaypointLeg) -> {
                    // Aircraft to waypoint segment
                    val wptPos = GAME.gameClientScreen?.waypoints?.get(leg2.wptId)?.entity?.get(Position.mapper) ?: return@also
                    shapeRenderer.line(posX, posY, wptPos.x, wptPos.y)
                }
                (leg1 is Route.WaypointLeg && leg2 is Route.WaypointLeg) -> {
                    // Waypoint to waypoint segment
                    val pos1 = GAME.gameClientScreen?.waypoints?.get(leg1.wptId)?.entity?.get(Position.mapper) ?: return@also
                    val pos2 = GAME.gameClientScreen?.waypoints?.get(leg2.wptId)?.entity?.get(Position.mapper) ?: return@also
                    shapeRenderer.line(pos1.x, pos1.y, pos2.x, pos2.y)
                }
                (leg1 == null && leg2 is Route.HoldLeg) -> {
                    // Hold segment
                    val wptPos = if (leg2.wptId.toInt() == -1) Position(posX, posY)
                    else GAME.gameClientScreen?.waypoints?.get(leg2.wptId)?.entity?.get(Position.mapper) ?: return@also
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
                    val wptPos = GAME.gameClientScreen?.waypoints?.get(leg1.wptId)?.entity?.get(Position.mapper) ?: return@also
                    renderVector(wptPos.x, wptPos.y, leg2.heading, seg.changed)
                }
                (leg1 is Route.HoldLeg && leg2 is Route.WaypointLeg) -> {
                    // Hold to waypoint segment
                    val pos1 = if (leg1.wptId.toInt() == -1) Position(posX, posY)
                    else GAME.gameClientScreen?.waypoints?.get(leg1.wptId)?.entity?.get(Position.mapper) ?: return@also
                    val pos2 = GAME.gameClientScreen?.waypoints?.get(leg2.wptId)?.entity?.get(Position.mapper) ?: return@also
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
        val sectorBoundingRectangle = GAME.gameClientScreen?.primarySector?.boundingRectangle ?: return
        val lineEnd = pointsAtBorder(floatArrayOf(sectorBoundingRectangle.x, sectorBoundingRectangle.x  + sectorBoundingRectangle.width),
            floatArrayOf(sectorBoundingRectangle.y, sectorBoundingRectangle.y + sectorBoundingRectangle.height), posX, posY, hdg - MAG_HDG_DEV)
        shapeRenderer.line(posX, posY, lineEnd[0], lineEnd[1])
    }
}