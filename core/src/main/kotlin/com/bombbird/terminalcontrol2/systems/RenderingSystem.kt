package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Array
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.ui.LABEL_PADDING
import com.bombbird.terminalcontrol2.ui.updateDatatagLabelSize
import com.bombbird.terminalcontrol2.utilities.*
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.math.ImmutableVector2
import kotlin.math.sqrt

/**
 * Main rendering system, which renders to [GAME]'s spriteBatch or radarScreen's [shapeRenderer]
 *
 * Used only in RadarScreen
 * */
class RenderingSystem(private val shapeRenderer: ShapeRenderer, private val stage: Stage, private val constZoomStage: Stage, private val uiStage: Stage): EntitySystem() {
    // "Buffer" polygon/circle arrays as a workaround to occasional errors when engine does not filter any polygon/circles to render
    private var prevPolygon: ImmutableArray<Entity?> = ImmutableArray(Array())
    private var prevCircles: ImmutableArray<Entity?> = ImmutableArray(Array())

    override fun update(deltaTime: Float) {
        val camZoom = (stage.camera as OrthographicCamera).zoom
        val camX = stage.camera.position.x
        val camY = stage.camera.position.y
        // println(camZoom)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.projectionMatrix = stage.camera.combined
        // Estimation circles
        shapeRenderer.circle(0f, 0f, 1250f)
        shapeRenderer.circle(0f, 0f, 125f)
        shapeRenderer.circle(0f, 0f, 12.5f)
        // shapeRenderer.circle(-15f, 5f, 5f)
        // shapeRenderer.circle(10f, -10f, 5f)

        // Render lineArrays
        val lineArrayFamily = allOf(GLineArray::class, SRColor::class).get()
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
        val polygonFamily = allOf(GPolygon::class, SRColor::class).exclude(RenderLast::class).get()
        var polygons = engine.getEntitiesFor(polygonFamily)
        if (polygons.size() == 0) {
            polygons = prevPolygon
            println("New polygon size ${polygons.size()}")
        }
        else prevPolygon = polygons
        for (i in 0 until polygons.size()) {
            polygons[i]?.apply {
                val poly = get(GPolygon.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.polygon(poly.vertices)
            }
        }

        // Render polygons with RenderLast
        val polygonLastFamily = allOf(GPolygon::class, SRColor::class, RenderLast::class).get()
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
        val circleFamily = allOf(Position::class, GCircle::class, SRColor::class).exclude(SRConstantZoomSize::class).get()
        var circles = engine.getEntitiesFor(circleFamily)
        if (circles.size() == 0) {
            circles = prevCircles
            println("New polygon size ${circles.size()}")
        }
        else prevCircles = circles
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
        val runwayFamily = allOf(RunwayInfo::class, SRColor::class).get()
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
            }
        }

        // Render trajectory line for controlled aircraft
        val trajectoryFamily = allOf(RadarData::class, Controllable::class, SRColor::class).get()
        val trajectory = engine.getEntitiesFor(trajectoryFamily)
        for (i in 0 until trajectory.size()) {
            trajectory[i]?.apply {
                val controllable = get(Controllable.mapper) ?: return@apply
                if (controllable.sectorId != 0.byte) return@apply // TODO check if is player's sector
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
        val datatagLineFamily = allOf(Datatag::class, RadarData::class).get()
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
        val constCircleFamily = allOf(Position::class, GCircle::class, SRColor::class, SRConstantZoomSize::class).get()
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
        // println("stage: ${stage.camera.combined}")
        GAME.batch.begin()
        GAME.batch.packedColor = Color.WHITE_FLOAT_BITS // Prevent fading out behaviour during selectBox animations due to tint being changed

        // Update runway labels rendering size, position
        val rwyLabelFamily = allOf(GenericLabel::class, RunwayInfo::class, RunwayLabel::class).get()
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
        val labelFamily = allOf(GenericLabel::class, Position::class).exclude(ConstantZoomSize::class).get()
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
        val aircraftFamily = allOf(AircraftInfo::class, RadarData::class, RSSprite::class).get()
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
        val constSizeLabelFamily = allOf(GenericLabel::class, Position::class, ConstantZoomSize::class).get()
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
        val datatagFamily = allOf(Datatag::class, RadarData::class).get()
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
}