package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Rectangle

/**
 * Shape renderer that can be bounded to a rectangle, so that it will only render within the rectangle
 */
class ShapeRendererBoundingBox(maxVertices: Int): ShapeRenderer(maxVertices) {
    private val boundingRect = Rectangle()

    /**
     * Sets the bounding rectangle of this bounded shape renderer to the specified rectangle [x], [y], [width] and
     * [height]
     */
    fun setBoundingRect(x: Float, y: Float, width: Float, height: Float) {
        boundingRect.set(x, y, width, height)
    }

    override fun circle(x: Float, y: Float, radius: Float) {
        if (!overlapsBounds(x - radius, x + radius, y + radius, y - radius)) return

        super.circle(x, y, radius)
    }

    fun polygon(polygon: Polygon) {
        if (!polygon.boundingRectangle.overlaps(boundingRect)) return

        super.polygon(polygon.vertices)
    }

    override fun rect(x: Float, y: Float, width: Float, height: Float) {
        if (!overlapsBounds(x, x + width, y + height, y)) return

        super.rect(x, y, width, height)
    }

    override fun line(x: Float, y: Float, z: Float, x2: Float, y2: Float, z2: Float, c1: Color?, c2: Color?) {
        val leftX = x.coerceAtMost(x2)
        val rightX = x.coerceAtLeast(x2)
        val topY = y.coerceAtLeast(y2)
        val bottomY = y.coerceAtMost(y2)
        if (!overlapsBounds(leftX, rightX, topY, bottomY)) return

        super.line(x, y, z, x2, y2, z2, c1, c2)
    }

    private fun overlapsBounds(leftX: Float, rightX: Float, topY: Float, bottomY: Float): Boolean {
        return (leftX <= boundingRect.x + boundingRect.width && rightX >= boundingRect.x) &&
                (bottomY <= boundingRect.y + boundingRect.height && topY >= boundingRect.y)
    }
}