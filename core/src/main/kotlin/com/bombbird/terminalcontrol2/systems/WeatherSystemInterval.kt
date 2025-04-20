package com.bombbird.terminalcontrol2.systems

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IntervalSystem
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.AffectedByWind
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.ThunderCellInfo
import com.bombbird.terminalcontrol2.components.ThunderStormCellChildren
import com.bombbird.terminalcontrol2.components.ThunderStormInfo
import com.bombbird.terminalcontrol2.entities.ThunderCell
import com.bombbird.terminalcontrol2.entities.ThunderStorm
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MIN_ALT
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CELL_SIZE_PX
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_MAX_ALTITUDE_FT
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_MAX_ALTITUDE_GROWTH_FT_PER_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_MIN_ALTITUDE_GROWTH_FT_PER_S
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.removeEntityOnMainThread
import ktx.ashley.allOf
import ktx.ashley.get
import ktx.collections.GdxArray
import kotlin.math.pow
import kotlin.math.sqrt

/** A low frequency weather system used to calculate weather related changes */
class WeatherSystemInterval: IntervalSystem(10f) {
    companion object {
        private val thunderStormFamily = allOf(ThunderStormInfo::class).get()
    }

    private val thunderStormEntities = FamilyWithListener.newServerFamilyWithListener(thunderStormFamily)

    override fun updateInterval() {
        val stormSetting = GAME.gameServer?.stormsDensity
        val stormsRequired = when (stormSetting) {
            GameServer.STORMS_OFF -> 0
            GameServer.STORMS_LOW -> 2
            GameServer.STORMS_MEDIUM -> 4
            GameServer.STORMS_HIGH -> 8
            GameServer.STORMS_NIGHTMARE -> 12
            else -> {
                FileLog.warn("WeatherSystemInterval", "Unknown storm setting $stormSetting")
                0
            }
        }

        val thunderStorms = thunderStormEntities.getEntities()
        val allStormCenters = GdxArray<Position>()
        GAME.gameServer?.storms?.let { storms ->
            for (i in 0 until thunderStorms.size()) {
                thunderStorms[i].apply {
                    val thunderStormInfo = get(ThunderStormInfo.mapper) ?: return@apply

                    if (i >= stormsRequired) {
                        // Remove excess storms
                        engine.removeEntityOnMainThread(this, false)
                        storms[thunderStormInfo.id] = null
                        return@apply
                    }

                    val position = get(Position.mapper) ?: return@apply
                    val altitude = get(Altitude.mapper) ?: return@apply
                    val affectedByWind = get(AffectedByWind.mapper) ?: return@apply

                    allStormCenters.add(position)

                    when {
                        thunderStormInfo.timeToMature > 0 -> {
                            // Developing stage, start generating cells
                            generateCells(0.01f, this)

                            // Increase the intensity of cells
                            increaseIntensity(0.03f, this)

                            // Increase top altitude if needed
                            if (altitude.altitudeFt < THUNDERSTORM_MAX_ALTITUDE_FT) {
                                altitude.altitudeFt += MathUtils.random(
                                    THUNDERSTORM_MIN_ALTITUDE_GROWTH_FT_PER_S,
                                    THUNDERSTORM_MAX_ALTITUDE_GROWTH_FT_PER_S
                                ) * interval
                            }

                            // Decrease time to mature
                            thunderStormInfo.timeToMature -= interval
                        }
                        thunderStormInfo.timeToDissipate > 0 -> {
                            // Mature stage - do small random changes on cell intensities
                            generateCells(0.0025f, this)
                            increaseIntensity(0.005f, this)

                            // Decrease time to dissipate
                            thunderStormInfo.timeToDissipate -= interval
                        }
                        else -> {
                            // Dissipating stage - reduce intensity in cells, delete once intensity is 0
                            decreaseIntensity(this)
                        }
                    }

                    // Change centreX, centreY according to winds
                    position.x += affectedByWind.windVectorPxps.x * interval
                    position.y += affectedByWind.windVectorPxps.y * interval
                }
            }

            // Check if storms need to be created
            val existingStormCount = storms.filterNotNull().size
            if (existingStormCount < stormsRequired) {
                GAME.gameServer?.primarySector?.boundingRectangle?.let {
                    val minX = it.x
                    val minY = it.y
                    val maxX = it.width + minX
                    val maxY = it.height + minY
                    // No storm should be within 40/3 NM of each other (square area)
                    val maxTries = 10
                    val minDist = 2 * THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS * THUNDERSTORM_CELL_SIZE_PX
                    for (i in 0 until maxTries) {
                        val randomX = MathUtils.random(minX, maxX)
                        val randomY = MathUtils.random(minY, maxY)
                        var tooClose = false
                        for (j in 0 until allStormCenters.size) {
                            val stormPos = allStormCenters[j]
                            if (randomX >= stormPos.x - minDist && randomX <= stormPos.x + minDist
                                && randomY >= stormPos.y - minDist && randomY <= stormPos.y + minDist) {
                                // Too close to another storm
                                tooClose = true
                                break
                            }
                        }
                        if (!tooClose) {
                            // Suitable position found
                            val id = storms.indexOf(null)
                            if (id > -1) {
                                val newStorm = ThunderStorm(
                                    id,
                                    randomX, randomY,
                                    MIN_ALT + MathUtils.random(1000f, 3000f),
                                    false
                                )
                                storms[id] = newStorm
                            }
                            break
                        }
                    }
                }
            }
        }

        GAME.gameServer?.sendThunderStorms()
    }

    /** Generates cells at the borders for the [stormEntity], given a [baseProbability] */
    private fun generateCells(baseProbability: Float, stormEntity: Entity) {
        val children = stormEntity[ThunderStormCellChildren.mapper] ?: return
        val borderCells = children.stormBorderCells
        val childrenCells = children.cells

        val borderIterator = borderCells.iterator()
        while (borderIterator.hasNext()) {
            val spotCoords = borderIterator.next()
            val x = spotCoords.first
            val y = spotCoords.second
            val spot = childrenCells[x]?.get(y) ?: continue
            val spotIntensity = spot.entity[ThunderCellInfo.mapper]?.intensity ?: continue
            val distSqr = x * x + y * y
            val distMultiplier = (THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS.toDouble().pow(3) - distSqr.toFloat().pow(3 / 2f)).pow(1 / 3.0) / THUNDERSTORM_CELL_MAX_HALF_WIDTH_UNITS
            val intensityMultiplier = if (spotIntensity >= 2) 2.5f else 1f
            val probability = (baseProbability * distMultiplier.toFloat() * intensityMultiplier).coerceAtLeast(0.002f)
            var allBordersFilled = true
            for (i in x - 1..x + 1) {
                for (j in y - 1..y + 1) {
                    if (i == 0 && j == 0) continue
                    if (i < childrenCells.minimumIndex || i > childrenCells.maximumIndex
                        || j < childrenCells.minimumIndex || j > childrenCells.maximumIndex) continue
                    if (childrenCells[i]?.get(j) == null) {
                        if (MathUtils.randomBoolean(probability)) {
                            childrenCells[i]?.let {
                                it[j] = ThunderCell(i, j, false)
                                borderCells.add(Pair(i, j))
                                children.activeCells += 1
                            }
                        }

                        allBordersFilled = false
                    }
                }
            }
            if (allBordersFilled) borderIterator.remove()
        }
    }

    /**
     * Increases the intensity of the thunderstorm cells for the [stormEntity],
     * given a [baseProbability]
     */
    private fun increaseIntensity(baseProbability: Float, stormEntity: Entity) {
        val childrenCells = stormEntity[ThunderStormCellChildren.mapper]?.cells ?: return

        for (row in childrenCells) {
            for (cell in row ?: continue) {
                val spot = cell?.entity?.get(ThunderCellInfo.mapper) ?: continue
                if (spot.intensity >= 10) continue
                val x = spot.offsetXIndex
                val y = spot.offsetXIndex
                val dist = sqrt((x * x + y * y).toDouble()).toFloat()
                var probability = baseProbability * (25 - dist) / 25
                for (i in x - 1..x + 1) {
                    for (j in y - 1..y + 1) {
                        if (i == 0 && j == 0) continue
                        if (i < childrenCells.minimumIndex || i > childrenCells.maximumIndex
                            || j < childrenCells.minimumIndex || j > childrenCells.maximumIndex) continue
                        val intensity = childrenCells[i]?.get(j)?.entity?.get(ThunderCellInfo.mapper)?.intensity ?: continue
                        probability *= when (intensity >= 4) {
                            (intensity <= 6) -> 1.2f
                            else -> 1.125f
                        }
                    }
                }
                if (MathUtils.randomBoolean(probability)) {
                    spot.intensity += 1
                }
            }
        }
    }

    /** Decrease intensity of cells of [stormEntity] */
    private fun decreaseIntensity(stormEntity: Entity) {
        val childrenCells = stormEntity[ThunderStormCellChildren.mapper] ?: return

        for (row in childrenCells.cells) {
            for (cell in row ?: continue) {
                val spot = cell?.entity?.get(ThunderCellInfo.mapper) ?: continue
                val x = spot.offsetXIndex
                val y = spot.offsetYIndex
                val dist = sqrt((x * x + y * y).toDouble()).toFloat()
                var probability = 0.05f * (35 - dist) / 35 * (if (spot.intensity >= 7) 1.5f else 1f)
                if (childrenCells.activeCells <= 100) probability = 0.16f
                if (spot.intensity > 0 && MathUtils.randomBoolean(probability)) {
                    spot.intensity -= 1
                }
                if (spot.intensity <= 0) {
                    childrenCells.cells[x]?.set(y, null)
                    engine.removeEntityOnMainThread(cell.entity, false)
                    childrenCells.activeCells -= 1
                }
            }
        }

        if (childrenCells.activeCells <= 0) {
            engine.removeEntityOnMainThread(stormEntity, false)
        }
    }
}