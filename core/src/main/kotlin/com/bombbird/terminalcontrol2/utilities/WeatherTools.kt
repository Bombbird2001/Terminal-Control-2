package com.bombbird.terminalcontrol2.utilities

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
 * Calculates the number of storm cell red zones at a given position ([x], [y]),
 * in a square of length (2 * [halfWidthZones]) + 1
 */
fun getRedZonesAtPosition(x: Float, y: Float, halfWidthZones: Int): Int {
    return GAME.gameServer?.storms?.fold(0) { redZones, storm ->
        val stormPos = storm?.entity[Position.mapper] ?: return@fold redZones

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
                    || yIndex >= THUNDERSTORM_CELL_SIZE_PX) continue

                val stormCells = storm.entity[ThunderStormCellChildren.mapper]?.cells ?: continue
                val cell = stormCells[xIndex]?.get(yIndex) ?: continue
                val cellInfo = cell.entity[ThunderCellInfo.mapper] ?: continue

                if (cellInfo.intensity >= THUNDERSTORM_CONFLICT_THRESHOLD) {
                    newZones++
                }
            }
        }

        redZones + newZones
    } ?: 0
}