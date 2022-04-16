package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.utilities.MathTools.byte
import ktx.ashley.allOf
import ktx.ashley.exclude
import ktx.ashley.get
import ktx.math.ImmutableVector2
import kotlin.math.sqrt

/** Main rendering system, which renders to [Constants.GAME]'s spriteBatch or radarScreen's [shapeRenderer] */
class RenderingSystem(private val shapeRenderer: ShapeRenderer, private val stage: Stage, private val uiStage: Stage): EntitySystem() {
    override fun update(deltaTime: Float) {
        val camZoom = (stage.camera as OrthographicCamera).zoom
        val camX = stage.camera.position.x
        val camY = stage.camera.position.y

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        // Estimation circles
        shapeRenderer.circle(0f, 0f, 1250f)
        shapeRenderer.circle(0f, 0f, 125f)
        shapeRenderer.circle(0f, 0f, 12.5f)
        // shapeRenderer.circle(-15f, 5f, 5f)
        // shapeRenderer.circle(10f, -10f, 5f)

        // Render polygons
        val polygonFamily = allOf(GPolygon::class, SRColor::class).get()
        val polygons = Constants.CLIENT_ENGINE.getEntitiesFor(polygonFamily)
        for (i in 0 until polygons.size()) {
            polygons[i]?.apply {
                val poly = get(GPolygon.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                shapeRenderer.color = srColor.color
                shapeRenderer.polygon(poly.vertices)
            }
        }

        // Render circles
        val circleFamily = allOf(Position::class, GCircle::class, SRColor::class).get()
        val circles = Constants.CLIENT_ENGINE.getEntitiesFor(circleFamily)
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
        val rwyWidthPx = Constants.RWY_WIDTH_PX_ZOOM_1 + (camZoom - 1) * Constants.RWY_WIDTH_CHANGE_PX_PER_ZOOM
        val rwys = Constants.CLIENT_ENGINE.getEntitiesFor(runwayFamily)
        for (i in 0 until rwys.size()) {
            rwys[i]?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val rect = get(GRect.mapper) ?: return@apply
                val deg = get(Direction.mapper) ?: return@apply
                val srColor = get(SRColor.mapper) ?: return@apply
                rect.height = rwyWidthPx
                shapeRenderer.color = srColor.color
                shapeRenderer.rect(pos.x, pos.y - rect.height / 2, 0f, rect.height / 2, rect.width, rect.height, 1f, 1f, deg.dirUnitVector.angleDeg())
            }
        }
        shapeRenderer.end()

        Constants.GAME.batch.projectionMatrix = stage.camera.combined
        Constants.GAME.batch.begin()
        // Render aircraft
        val aircraftFamily = allOf(AircraftInfo::class, Position::class, RSSprite::class).get()
        val blipSize = if (camZoom <= 1) Constants.AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 * camZoom else Constants.AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 + (camZoom - 1) * Constants.AIRCRAFT_BLIP_LENGTH_CHANGE_PX_PER_ZOOM
        val acfts = Constants.CLIENT_ENGINE.getEntitiesFor(aircraftFamily)
        for (i in 0 until acfts.size()) {
            acfts[i]?.apply {
                val rsSprite = get(RSSprite.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                rsSprite.drawable.draw(Constants.GAME.batch, pos.x - blipSize / 2, pos.y - blipSize / 2, blipSize, blipSize)
            }
        }

        // Update runway labels rendering size, position
        val rwyLabelFamily = allOf(GenericLabel::class, RunwayInfo::class, RunwayLabel::class).get()
        val rwyLabels = Constants.CLIENT_ENGINE.getEntitiesFor(rwyLabelFamily)
        for (i in 0 until rwyLabels.size()) {
            rwyLabels[i]?.apply {
                val labelInfo = get(GenericLabel.mapper) ?: return@apply
                val rwyLabel = get(RunwayLabel.mapper) ?: return@apply
                val direction = get(Direction.mapper) ?: return@apply
                labelInfo.label.apply {
                    rwyLabel.apply {
                        val spacingFromCentre = sqrt(prefWidth * prefWidth + prefHeight * prefHeight) / 2 + 3 / camZoom + if (positionToRunway == 0.byte) 0f else rwyWidthPx / 2
                        if (!dirSet) {
                            dirUnitVector = ImmutableVector2(direction.dirUnitVector.x, direction.dirUnitVector.y)
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
        val labels = Constants.CLIENT_ENGINE.getEntitiesFor(labelFamily)
        for (i in 0 until labels.size()) {
            labels[i]?.apply {
                val labelInfo = get(GenericLabel.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                labelInfo.label.apply {
                    setPosition(pos.x + labelInfo.xOffset, pos.y + labelInfo.yOffset)
                    draw(Constants.GAME.batch, 1f)
                }
            }
        }

        Constants.GAME.batch.projectionMatrix = uiStage.camera.combined
        // Render generic constant size labels
        val constSizeLabelFamily = allOf(GenericLabel::class, Position::class, ConstantZoomSize::class).get()
        val constLabels = Constants.CLIENT_ENGINE.getEntitiesFor(constSizeLabelFamily)
        for (i in 0 until constLabels.size()) {
            constLabels[i].apply {
                val labelInfo = get(GenericLabel.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                labelInfo.label.apply {
                    setPosition((pos.x - camX) / camZoom + labelInfo.xOffset, (pos.y - camY) / camZoom + labelInfo.yOffset)
                    draw(Constants.GAME.batch, 1f)
                }
            }
        }

        Constants.GAME.batch.end()

        // Draw UI pane the last, above all the other elements
        uiStage.draw()
    }
}