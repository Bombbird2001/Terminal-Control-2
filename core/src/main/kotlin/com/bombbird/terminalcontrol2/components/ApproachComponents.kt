package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.GAME
import ktx.ashley.Mapper
import ktx.ashley.get

/** Component for tagging basic approach information */
data class ApproachInfo(var approachName: String = "", var airportId: Byte = 0, var rwyId: Byte = 0): Component {
    val rwyObj: Airport.Runway by lazy {
        (GAME.gameServer?.airports ?: GAME.gameClientScreen?.airports)?.get(airportId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(rwyId) ?:
        throw NullPointerException("No runway with ID $rwyId found in airport with ID $airportId")
    }

    companion object: Mapper<ApproachInfo>()
}

/** Component for tagging localizer information */
data class Localizer(var maxDistNm: Byte = 0): Component {
    companion object: Mapper<Localizer>()
}

/** Component for tagging the distance from the runway threshold to turn and line up (in an offset approach) */
data class LineUpDist(var lineUpDistNm: Float = 0f): Component {
    companion object: Mapper<LineUpDist>()
}

/** Component for tagging glide slope information */
data class GlideSlope(var glideAngle: Float = 0f, var offsetNm: Float = 0f, var maxInterceptAlt: Short = 0): Component {
    companion object: Mapper<GlideSlope>()
}

/** Component for tagging step down approach information */
class StepDown(var altAtDist: Array<Pair<Float, Short>> = arrayOf()): Component {
    companion object: Mapper<StepDown>()
}

/** Component for tagging circling approach information */
class Circling(var minBreakoutAlt: Int = 0, var maxBreakoutAlt: Int = 0, var breakoutDir: Byte = CommandTarget.TURN_LEFT): Component {
    companion object: Mapper<Circling>()
}

/** Component for tagging approach minimums information */
data class Minimums(var baroAltFt: Short = 0, var rvrM: Short = 0): Component {
    companion object: Mapper<Minimums>()
}

/**
 * Component for tagging visual approach (one will be created for every runway with their own extended centerline up to
 * 10nm and glide path of 3 degrees)
 * */
class Visual: Component {
    companion object: Mapper<Visual>()
}
