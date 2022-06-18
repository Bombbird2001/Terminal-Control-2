package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Timer
import ktx.ashley.Mapper
import ktx.math.ImmutableVector2
import ktx.scene2d.Scene2DSkin

/** Component for rendering a sprite/drawable on radarScreen */
data class RSSprite(var drawable: Drawable = BaseDrawable(), var width: Float = 0f, var height: Float = 0f): Component {
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

/**
 * Component for additional label positioning info to a runway
 *
 * [positionToRunway] = 0 -> before the runway threshold
 *
 * [positionToRunway] = 1 -> to the right of the runway threshold
 *
 * [positionToRunway] = -1 -> to the left of the runway threshold
 * */
data class RunwayLabel(var positionToRunway: Byte = 0): Component {
    var dirUnitVector = ImmutableVector2(0f, 0f)
    var dirSet = false
    companion object: Mapper<RunwayLabel>() {
        const val BEFORE: Byte = 0
        const val RIGHT: Byte = 1
        const val LEFT: Byte = -1
    }
}

/** Component for tagging drawables that should remain the same size regardless of zoom level */
class ConstantZoomSize: Component {
    companion object: Mapper<ConstantZoomSize>()
}

/** Component for tagging shapeRenderer shapes that should remain the same size regardless of zoom level */
class SRConstantZoomSize: Component {
    companion object: Mapper<SRConstantZoomSize>()
}

/** Component for shapeRenderer rendering colour */
data class SRColor(var color: Color = Color()): Component {
    companion object: Mapper<SRColor>()
}

/**
 * Component for tagging entities that should be rendered the last (when compared to entities of the same family -
 * this by itself does not ensure the entity is rendered above every single other entity; behaviour for the required
 * family must also be implemented in RenderingSystem)
 * */
class RenderLast: Component {
    companion object: Mapper<RenderLast>()
}

/** Component for tagging generic shapes that should not be rendered for whatever reason */
class DoNotRender: Component {
    companion object: Mapper<DoNotRender>()
}

/** Component for tagging labels that do not need to be shown (by default, all [GenericLabel]s are rendered) */
class HideLabel: Component {
    companion object: Mapper<HideLabel>()
}

/**
 * Component for rendering a datatag with position offsets on radarScreen
 *
 * Use functions in DatatagTools to update text/style/spacing of underlying imageButton and label
 *
 * This component will have [ConstantZoomSize] properties applied to it
 * */
class Datatag(var xOffset: Float = 0f, var yOffset: Float = 0f, var minimised: Boolean = false, var lineSpacing: Short = 4): Component {
    var dragging = false
    var clicks = 0
    val tapTimer = Timer()
    val imgButton: ImageButton = ImageButton(Scene2DSkin.defaultSkin, "DatatagGreenNoBG")
    val clickSpot: ImageButton = ImageButton(Scene2DSkin.defaultSkin, "DatatagNoBG")
    val labelArray: Array<Label> = arrayOf(Label("", Scene2DSkin.defaultSkin, "Datatag"), Label("", Scene2DSkin.defaultSkin, "Datatag"),
                                           Label("", Scene2DSkin.defaultSkin, "Datatag"), Label("", Scene2DSkin.defaultSkin, "Datatag"))
    var smallLabelFont = false
    companion object: Mapper<Datatag>()
}
