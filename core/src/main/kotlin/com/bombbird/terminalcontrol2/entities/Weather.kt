package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.AffectedByWind
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.BoundingBox
import com.bombbird.terminalcontrol2.components.CustomPosition
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.ThunderCellInfo
import com.bombbird.terminalcontrol2.components.ThunderStormCellChildren
import com.bombbird.terminalcontrol2.components.ThunderStormInfo
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CELL_SIZE_PX
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_DISSIPATE_MAX_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_DISSIPATE_MIN_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_MATURE_MAX_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_MATURE_MIN_S
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.with

/** Thunderstorm class that creates an airport entity with the required components on instantiation */
class ThunderStorm(posX: Float, posY: Float, altitude: Float, onClient: Boolean = true) {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
        with<Position> {
            x = posX
            y = posY
        }
        with<ThunderStormInfo> {
            timeToMature = MathUtils.random(
                THUNDERSTORM_TIME_TO_MATURE_MIN_S,
                THUNDERSTORM_TIME_TO_MATURE_MAX_S
            )
            timeToDissipate = MathUtils.random(
                THUNDERSTORM_TIME_TO_DISSIPATE_MIN_S,
                THUNDERSTORM_TIME_TO_DISSIPATE_MAX_S
            )
        }
        with<Altitude> {
            altitudeFt = altitude
        }
        with<ThunderStormCellChildren>()
        with<AffectedByWind>()
        with<BoundingBox>()
    }
}

/** Thunder cell class that creates an airport entity with the required components on instantiation */
class ThunderCell(offsetXIndex: Int, offsetYIndex: Int, onClient: Boolean = true) {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
        with<Position> {
            x = offsetXIndex * THUNDERSTORM_CELL_SIZE_PX
            y = offsetYIndex * THUNDERSTORM_CELL_SIZE_PX
        }
        with<CustomPosition> {
            x = offsetXIndex * THUNDERSTORM_CELL_SIZE_PX
            y = offsetYIndex * THUNDERSTORM_CELL_SIZE_PX
        }
        with<ThunderCellInfo> {
            this.offsetXIndex = offsetXIndex
            this.offsetYIndex = offsetYIndex
            intensity = 1
        }
    }
}
