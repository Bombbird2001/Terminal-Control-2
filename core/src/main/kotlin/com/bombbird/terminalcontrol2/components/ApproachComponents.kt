package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper
import ktx.ashley.get

/** Component for tagging basic approach information */
@JsonClass(generateAdapter = true)
data class ApproachInfo(var approachName: String = "", var airportId: Byte = 0, var rwyId: Byte = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.APPROACH_INFO

    @Json(ignore = true)
    val rwyObj: Airport.Runway by lazy {
        (GAME.gameServer?.airports ?: CLIENT_SCREEN?.airports)?.get(airportId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(rwyId) ?:
        throw NullPointerException("No runway with ID $rwyId found in airport with ID $airportId")
    }

    companion object {
        val mapper = object: Mapper<ApproachInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging localizer information */
@JsonClass(generateAdapter = true)
data class Localizer(var maxDistNm: Byte = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LOCALIZER

    companion object {
        val mapper = object: Mapper<Localizer>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging the distance from the runway threshold to turn and line up (in an offset approach) */
@JsonClass(generateAdapter = true)
data class LineUpDist(var lineUpDistNm: Float = 0f): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.LINE_UP_DIST

    companion object {
        val mapper = object: Mapper<LineUpDist>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging glide slope information */
@JsonClass(generateAdapter = true)
data class GlideSlope(var glideAngle: Float = 0f, var offsetNm: Float = 0f, var maxInterceptAlt: Short = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.GLIDE_SLOPE

    companion object {
        val mapper = object: Mapper<GlideSlope>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging step-down approach information */
@JsonClass(generateAdapter = true)
data class StepDown(var altAtDist: Array<Step> = arrayOf()): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.STEP_DOWN

    companion object {
        val mapper = object: Mapper<StepDown>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    /** Class for steps on the step-down approach */
    @JsonClass(generateAdapter = true)
    data class Step(val dist: Float, val alt: Short)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StepDown

        if (!altAtDist.contentEquals(other.altAtDist)) return false
        if (componentType != other.componentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = altAtDist.contentHashCode()
        result = 31 * result + componentType.hashCode()
        return result
    }
}

/** Component for tagging circling approach information */
@JsonClass(generateAdapter = true)
data class Circling(var minBreakoutAlt: Int = 0, var maxBreakoutAlt: Int = 0, var breakoutDir: Byte = CommandTarget.TURN_LEFT): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.CIRCLING

    companion object {
        val mapper = object: Mapper<Circling>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component for tagging approach minimums information */
@JsonClass(generateAdapter = true)
data class Minimums(var baroAltFt: Short = 0, var rvrM: Short = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.MINIMUMS

    companion object {
        val mapper = object: Mapper<Minimums>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component for tagging visual approach (one will be created for every runway with their own extended centerline up to
 * 10nm and glide path of 3 degrees)
 */
@JsonClass(generateAdapter = true)
class Visual: Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.VISUAL

    companion object {
        val mapper = object: Mapper<Visual>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return other::class == Visual::class
    }

    override fun hashCode(): Int {
        return componentType.hashCode()
    }
}
