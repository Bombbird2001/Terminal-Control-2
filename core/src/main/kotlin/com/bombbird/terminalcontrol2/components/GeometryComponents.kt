package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector2
import ktx.ashley.Mapper

/** Component for polygons */
class GPolygon(var vertices: FloatArray = FloatArray(0)): Component {
    companion object: Mapper<GPolygon>()
}

/** Component for circles */
data class GCircle(var radius: Float = 0f): Component {
    companion object: Mapper<GCircle>()
}

/** Component for lines */
data class GLine(var vector2: Vector2 = Vector2()): Component {
    companion object: Mapper<GLine>()
}

/** Component for rectangles */
data class GRect(var width: Float = 0f, var height: Float = 0f): Component {
    companion object: Mapper<GRect>()
}
