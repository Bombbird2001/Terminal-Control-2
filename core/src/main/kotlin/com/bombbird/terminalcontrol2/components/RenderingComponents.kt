package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import ktx.ashley.Mapper
import ktx.math.ImmutableVector2
import ktx.scene2d.Scene2DSkin

/** Component for rendering a sprite/drawable on radarScreen */
class RSSprite(var drawable: Drawable = BaseDrawable(), var width: Float = 0f, var height: Float = 0f): Component {
    companion object: Mapper<RSSprite>()
}

/** Component for rendering a generic label with position offsets on radarScreen, functions included to update text/style of underlying label */
class GenericLabel(var xOffset: Float = 0f, var yOffset: Float = 0f): Component {
    val label: Label = Label("", Scene2DSkin.defaultSkin)
    companion object: Mapper<GenericLabel>()

    fun updateText(newText: String) {
        label.setText(newText)
    }

    fun updateStyle(newStyle: String) {
        label.style = Scene2DSkin.defaultSkin.get(newStyle, LabelStyle::class.java)
    }
}

/** Component for additional label positioning info to a runway
 *
 * [positionToRunway] = 0 means before the runway threshold
 *
 * [positionToRunway] = 1 means to the right of the runway threshold
 *
 * [positionToRunway] = -1 means to the left of the runway threshold
 * */
class RunwayLabel(var positionToRunway: Int = 0): Component {
    var dirUnitVector = ImmutableVector2(0f, 0f)
    var dirSet = false
    companion object: Mapper<RunwayLabel>()
}

/** Component for tagging drawables that should remain the same size regardless of zoom level */
class ConstantZoomSize: Component {
    companion object: Mapper<ConstantZoomSize>()
}

/** Component for shapeRenderer rendering colour */
class SRColor(var color: Color = Color()): Component {
    companion object: Mapper<SRColor>()
}
