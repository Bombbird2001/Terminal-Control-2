package com.bombbird.terminalcontrol2.utilities

import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.ThunderCellInfo
import com.bombbird.terminalcontrol2.components.ThunderStormCellChildren
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CELL_SIZE_PX
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CONFLICT_THRESHOLD
import ktx.ashley.get
import kotlin.math.floor

/**
 * Calculates the number of storm cell red zones at a given position ([x], [y])
 * and [altitude], in a square of length (2 * [halfWidthZones]) + 1
 */
fun getRedCellCountAtPosition(x: Float, y: Float, altitude: Float, halfWidthZones: Int): Int {
    return getStormCellsAtPositionWithCriteria(x, y, altitude, halfWidthZones) {
        it.intensity >= THUNDERSTORM_CONFLICT_THRESHOLD
    }
}

/**
 * Calculates the number of storm cell zones at a given position ([x], [y])
 * and [altitude], in a square of length (2 * [halfWidthZones]) + 1
 */
fun getAllZoneCountAtPosition(x: Float, y: Float, altitude: Float, halfWidthZones: Int): Int {
    return getStormCellsAtPositionWithCriteria(x, y, altitude, halfWidthZones) {
        it.intensity > 0
    }
}

private fun getStormCellsAtPositionWithCriteria(x: Float, y: Float, altitude: Float, halfWidthZones: Int,
                                                criteria: (ThunderCellInfo) -> Boolean): Int {
    return GAME.gameServer?.storms?.fold(0) { zoneCount, storm ->
        val stormPos = storm?.entity[Position.mapper] ?: return@fold zoneCount
        val stormAlt = storm.entity[Altitude.mapper]?.altitudeFt ?: return@fold zoneCount
        if (altitude > stormAlt) return@fold zoneCount

        val posXIndex = floor((x - stormPos.x) / THUNDERSTORM_CELL_SIZE_PX).toInt()
        val posYIndex = floor((y - stormPos.y) / THUNDERSTORM_CELL_SIZE_PX).toInt()

        var newZones = 0

        for (i in -halfWidthZones..halfWidthZones) {
            for (j in -halfWidthZones..halfWidthZones) {
                val xIndex = posXIndex + i
                val yIndex = posYIndex + j

                if (xIndex < -THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
                    || xIndex >= THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
                    || yIndex < -THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
                    || yIndex >= THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS) continue

                val stormCells = storm.entity[ThunderStormCellChildren.mapper]?.cells ?: continue
                val cell = stormCells[xIndex]?.get(yIndex) ?: continue
                val cellInfo = cell.entity[ThunderCellInfo.mapper] ?: continue

                if (criteria(cellInfo)) newZones++
            }
        }

        zoneCount + newZones
    } ?: 0
}