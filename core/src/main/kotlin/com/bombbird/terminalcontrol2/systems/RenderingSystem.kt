package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.bombbird.terminalcontrol2.components.Direction
import com.bombbird.terminalcontrol2.components.GRect
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.RunwayInfo
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.allOf
import ktx.ashley.get

class RenderingSystem(private val shapeRenderer: ShapeRenderer, private val stage: Stage): EntitySystem() {
    override fun update(deltaTime: Float) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        // Estimation circles
        shapeRenderer.circle(0f, 0f, 1250f)
        shapeRenderer.circle(0f, 0f, 125f)
        shapeRenderer.circle(0f, 0f, 12.5f)

        // Render runways
        val runwayFamily = allOf(RunwayInfo::class).get()
        for (rwy in Constants.ENGINE.getEntitiesFor(runwayFamily)) {
            rwy?.apply {
                val pos = get(Position.mapper) ?: return@apply
                val rect = get(GRect.mapper) ?: return@apply
                val deg = get(Direction.mapper) ?: return@apply
                rect.height = Constants.RWY_WIDTH_PX_ZOOM_1 + ((stage.camera as OrthographicCamera).zoom - 1) * Constants.RWY_WIDTH_CHANGE_PX_PER_ZOOM
                shapeRenderer.rect(pos.x, pos.y - rect.height / 2, pos.x, pos.y + rect.height / 2, rect.width, rect.height, 1f, 1f, deg.dirUnitVector.angleDeg())
            }
        }
        shapeRenderer.end()
    }
}