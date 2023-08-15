package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/** Component for polygons */
@JsonClass(generateAdapter = true)
class GPolygon(var vertices: FloatArray = FloatArray(0)): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.G_POLYGON

    val polygonObj: Polygon by lazy { Polygon(vertices) }

    companion object {
        val mapper = object: Mapper<GPolygon>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GPolygon mapper")
        }
    }
}

/**
 * Component for an array of points to be joined together with lines
 *
 * Essentially the same as [GPolygon] except this does not need to be a closed shape
 */
class GLineArray(var vertices: FloatArray = FloatArray(0)): Component {
    companion object {
        val mapper = object: Mapper<GLineArray>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GLineArray mapper")
        }
    }
}

/** Component for circles */
data class GCircle(var radius: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<GCircle>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GCircle mapper")
        }
    }
}

/** Component for lines */
data class GLine(var vector2: Vector2 = Vector2()): Component {
    companion object {
        val mapper = object: Mapper<GLine>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GLine mapper")
        }
    }
}

/** Component for rectangles */
data class GRect(var width: Float = 0f, var height: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<GRect>() {}.mapper

        fun initialise() {
            FileLog.info("Component", "Initialising GRect mapper")
        }
    }
}
