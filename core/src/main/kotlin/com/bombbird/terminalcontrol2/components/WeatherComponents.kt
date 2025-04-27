package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.json.BaseComponentJSONInterface
import com.bombbird.terminalcontrol2.utilities.InitializeCompanionObjectOnStart
import com.squareup.moshi.JsonClass
import ktx.ashley.Mapper

/**
 * Component to tag information related to a thunderstorm, including border cells,
 * time to finish developing and time remaining before dissipation
 */
@JsonClass(generateAdapter = true)
data class ThunderStormInfo(
    var id: Int = -1,
    var timeToMature: Float = 1800f,
    var timeToDissipate: Float = 1200f
): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.THUNDERSTORM_INFO

    companion object {
        val mapper = object: Mapper<ThunderStormInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/**
 * Component to tag information of a thunderstorm cell with respect to the
 * main thunderstorm position
 */
@JsonClass(generateAdapter = true)
data class ThunderCellInfo(var offsetXIndex: Int = 0, var offsetYIndex: Int = 0, var intensity: Int = 0): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.THUNDER_CELL_INFO

    companion object {
        val mapper = object: Mapper<ThunderCellInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}

/** Component to tag the heading deviation requested by an aircraft to avoid thunderstorms */
@JsonClass(generateAdapter = true)
data class WeatherAvoidanceInfo(var deviationHeading: Short? = null): Component, BaseComponentJSONInterface {
    override val componentType = BaseComponentJSONInterface.ComponentType.WEATHER_AVOIDANCE_INFO

    companion object {
        val mapper = object: Mapper<WeatherAvoidanceInfo>() {}.mapper

        fun initialise() = InitializeCompanionObjectOnStart.initialise(this::class)
    }
}