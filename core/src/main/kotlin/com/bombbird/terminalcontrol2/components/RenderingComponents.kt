package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import ktx.ashley.Mapper

/** Component for rendering a sprite/drawable on radarScreen */
class RSSprite(var drawable: Drawable = BaseDrawable(), var width: Float = 0f, var height: Float = 0f): Component {
    companion object: Mapper<RSSprite>()
}

/** Component for rendering a generic label with position offsets on radarScreen */
class GenericLabel(var text: String = "", var labelStyle: String = "default", var xOffset: Float = 0f, var yOffset: Float = 0f): Component {
    companion object: Mapper<GenericLabel>()
}

/** Component for shapeRenderer rendering colour */
class SRColor(var color: Color = Color()): Component {
    companion object: Mapper<SRColor>()
}
