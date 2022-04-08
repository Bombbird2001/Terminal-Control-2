package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.allOf
import ktx.ashley.get

class RenderingSystem(private val shapeRenderer: ShapeRenderer, private val stage: Stage): EntitySystem() {


    override fun update(deltaTime: Float) {
        val camZoom = (stage.camera as OrthographicCamera).zoom

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        // Estimation circles
        shapeRenderer.circle(0f, 0f, 1250f)
        shapeRenderer.circle(0f, 0f, 125f)
        shapeRenderer.circle(0f, 0f, 12.5f)
        // shapeRenderer.circle(-15f, 5f, 5f)
        // shapeRenderer.circle(10f, -10f, 5f)

        // Sector test
        shapeRenderer.polygon(floatArrayOf(461f, 983f, 967f, 969f, 1150f, 803f, 1326f, 509f, 1438f, 39f, 1360f, -551f, 1145f, -728f,
            955f, -861f, 671f, -992f, 365f, -1018f, -86f, -871f, -316f, -1071f, -620f, -1867f, -1856f, -1536f, -1109f, -421f, 461f, 983f))

        // Render runways
        val runwayFamily = allOf(RunwayInfo::class).get()
        val rwyWidthPx = Constants.RWY_WIDTH_PX_ZOOM_1 + (camZoom - 1) * Constants.RWY_WIDTH_CHANGE_PX_PER_ZOOM
        for (rwy in Constants.ENGINE.getEntitiesFor(runwayFamily)) {
            rwy?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val rect = get(GRect.mapper) ?: return@apply
                val deg = get(Direction.mapper) ?: return@apply
                rect.height = rwyWidthPx
                shapeRenderer.rect(pos.x, pos.y - rect.height / 2, 0f, rect.height / 2, rect.width, rect.height, 1f, 1f, deg.dirUnitVector.angleDeg())
            }
        }
        shapeRenderer.end()

        Constants.GAME.batch.projectionMatrix = stage.camera.combined
        Constants.GAME.batch.begin()
        // Render aircraft
        val aircraftFamily = allOf(AircraftInfo::class).allOf(Position::class).allOf(RSSprite::class).get()
        val blipSize = if (camZoom <= 1) Constants.AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 * camZoom else Constants.AIRCRAFT_BLIP_LENGTH_PX_ZOOM_1 + (camZoom - 1) * Constants.AIRCRAFT_BLIP_LENGTH_CHANGE_PX_PER_ZOOM
        for (acft in Constants.ENGINE.getEntitiesFor(aircraftFamily)) {
            acft?.apply {
                val rsSprite = get(RSSprite.mapper) ?: return@apply
                val pos = get(Position.mapper) ?: return@apply
                rsSprite.drawable.draw(Constants.GAME.batch, pos.x - blipSize / 2, pos.y - blipSize / 2, blipSize, blipSize)
            }
        }
        Constants.GAME.batch.end()
    }
}