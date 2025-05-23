package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Timer
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.ui.isMobile
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper
import ktx.collections.GdxArray
import ktx.math.ImmutableVector2
import ktx.scene2d.Scene2DSkin

/** Component for rendering a sprite/drawable on radarScreen */
data class RSSprite(var drawable: Drawable = BaseDrawable(), var width: Float = 0f, var height: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<RSSprite>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for rendering a generic label with position offsets on radarScreen, functions included to update text/style
 * of underlying label
 */
class GenericLabel(var xOffset: Float = 0f, var yOffset: Float = 0f): Component {
    val label: Label = Label("", Scene2DSkin.defaultSkin)
    companion object {
        val mapper = object: Mapper<GenericLabel>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    fun updateText(newText: String) {
        label.setText(newText)
        label.pack()
    }

    fun updateStyle(newStyle: String) {
        label.style = Scene2DSkin.defaultSkin.get(newStyle, LabelStyle::class.java)
    }
}

/** Component for storing multiple labels to render */
class GenericLabels(var labels: Array<GenericLabel> = arrayOf()): Component {
    companion object {
        val mapper = object: Mapper<GenericLabels>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
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
 */
data class RunwayLabel(var positionToRunway: Byte = 0): Component {
    var dirUnitVector = ImmutableVector2(0f, 0f)
    var dirSet = false
    companion object {
        val mapper = object: Mapper<RunwayLabel>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)

        const val BEFORE: Byte = 0
        const val RIGHT: Byte = 1
        const val LEFT: Byte = -1
    }
}

/** Component for tagging drawables that should remain the same size regardless of zoom level */
class ConstantZoomSize: Component {
    companion object {
        val mapper = object: Mapper<ConstantZoomSize>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging shapeRenderer shapes that should remain the same size regardless of zoom level */
class SRConstantZoomSize: Component {
    companion object {
        val mapper = object: Mapper<SRConstantZoomSize>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for shapeRenderer rendering colour */
data class SRColor(var color: Color = Color()): Component {
    companion object {
        val mapper = object: Mapper<SRColor>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging entities that should be rendered the last (when compared to entities of the same family -
 * this by itself does not ensure the entity is rendered above every single other entity; behaviour for the required
 * family must also be implemented in RenderingSystem)
 */
class RenderLast: Component {
    companion object {
        val mapper = object: Mapper<RenderLast>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging generic shapes that should not be rendered for whatever reason */
class DoNotRenderShape: Component {
    companion object {
        val mapper = object: Mapper<DoNotRenderShape>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging [GenericLabel]s, [GenericLabels] that should not be rendered for whatever reason */
class DoNotRenderLabel: Component {
    companion object {
        val mapper = object: Mapper<DoNotRenderLabel>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for rendering a datatag with position offsets on radarScreen
 *
 * Use functions in DatatagTools to update text/style/spacing of underlying imageButton and label
 *
 * This component will have [ConstantZoomSize] properties applied to it
 */
class Datatag(var xOffset: Float = 0f, var yOffset: Float = 0f, var minimised: Boolean = false): Component {
    var dragging = false
    var clicks = 0
    val tapTimer = Timer()
    val flashTimer = Timer()
    var shouldFlashOrange = false
    var shouldFlashMagenta = false
    var flashingOrange = false
    var flashingMagenta = false
    var emergency = false
    var initialPosSet = false
    var currentDatatagStyle = "DatatagGreenNoBG"
    val imgButton: ImageButton = ImageButton(Scene2DSkin.defaultSkin, "DatatagGreenNoBG")
    val clickSpot: ImageButton = ImageButton(Scene2DSkin.defaultSkin, "DatatagNoBG")
    val labelArray: Array<Label> = arrayOf(Label("", Scene2DSkin.defaultSkin, DEFAULT_LABEL_FONT), Label("", Scene2DSkin.defaultSkin, DEFAULT_LABEL_FONT),
                                           Label("", Scene2DSkin.defaultSkin, DEFAULT_LABEL_FONT), Label("", Scene2DSkin.defaultSkin, DEFAULT_LABEL_FONT))
    var smallLabelFont = false
    var renderLast = false
    val datatagInfoMap = HashMap<String, String>()

    companion object {
        val mapper = object: Mapper<Datatag>() {}.mapper
        private val DEFAULT_LABEL_FONT = "Datatag${if (isMobile()) "Mobile" else ""}"

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    /** Called when the aircraft who owns this datatag despawns */
    fun despawn() {
        clickSpot.remove()
        tapTimer.stop()
        flashTimer.stop()
    }
}

/** Component for storing the datatag position to be sent to client on initial connection */
@JsonClass(generateAdapter = true)
data class InitialClientDatatagPosition(var xOffset: Float = 0f, var yOffset: Float = 0f, var minimised: Boolean = false,
                                        var flashing: Boolean = false): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.INITIAL_CLIENT_DATATAG_POSITION

    companion object {
        val mapper = object: Mapper<InitialClientDatatagPosition>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for storing route leg segments to be rendered by the rendering system */
data class RouteSegment(val segments: GdxArray<Route.LegSegment> = GdxArray()): Component {
    companion object {
        val mapper = object: Mapper<RouteSegment>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for storing the bounding box of an entity, which can be used for
 * improving collision detection and/or rendering
 */
data class BoundingBox(var minX: Float = 0f, var minY: Float = 0f, var maxX: Float = 0f, var maxY: Float = 0f): Component {
    companion object {
        val mapper = object: Mapper<BoundingBox>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}