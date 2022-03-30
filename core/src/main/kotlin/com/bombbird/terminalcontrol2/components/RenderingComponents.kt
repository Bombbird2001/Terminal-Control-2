package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import ktx.ashley.Mapper

/** Component for rendering a sprite/drawable on radarScreen */
class Sprite(var drawable: Drawable = BaseDrawable(), var x: Float = 0f, var y: Float = 0f, var width: Float = 0f, var height: Float = 0f): Component {
    companion object: Mapper<Sprite>()
}

/** Component for shapeRenderer rendering colour */
class SRColor(var color: Color = Color()): Component {
    companion object: Mapper<SRColor>()
}
