package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.AffectedByWind
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.BoundingBox
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.RSSprite
import com.bombbird.terminalcontrol2.components.ThunderCellInfo
import com.bombbird.terminalcontrol2.components.ThunderStormCellChildren
import com.bombbird.terminalcontrol2.components.ThunderStormInfo
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_CELL_SIZE_PX
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_DISSIPATE_MAX_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_DISSIPATE_MIN_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_MATURE_MAX_S
import com.bombbird.terminalcontrol2.global.THUNDERSTORM_TIME_TO_MATURE_MIN_S
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.get
import ktx.ashley.with
import ktx.scene2d.Scene2DSkin

/** Thunderstorm class that creates an airport entity with the required components on instantiation */
class ThunderStorm(id: Int, posX: Float, posY: Float, altitude: Float, onClient: Boolean = true): SerialisableEntity<ThunderStorm.SerialisedThunderStorm> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
        with<Position> {
            x = posX
            y = posY
        }
        with<ThunderStormInfo> {
            this.id = id
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
        with<ThunderStormCellChildren>().apply {
            if (!onClient) {
                val seedCell = ThunderCell(0, 0, false)
                cells[0]?.set(0, seedCell)
                stormBorderCells.add(0 to 0)
            }
        }
        with<AffectedByWind>()
        with<BoundingBox> {
            minX = posX
            minY = posY
            maxX = posX + THUNDERSTORM_CELL_SIZE_PX
            maxY = posY + THUNDERSTORM_CELL_SIZE_PX
        }
    }

    companion object {
        /** Converts a [SerialisedThunderStorm] to a [ThunderStorm] */
        fun fromSerialisedObject(storm: SerialisedThunderStorm): ThunderStorm {
            return ThunderStorm(-1, storm.x, storm.y, -1f).apply {
                storm.cells.forEach { cell ->
                    val thunderCell = ThunderCell.fromSerialisedObject(cell)
                    thunderCell.entity[Position.mapper]?.let {
                        it.x += storm.x
                        it.y += storm.y
                    }
                    entity[ThunderStormCellChildren.mapper]?.cells?.get(cell.offsetIndexX.toInt())?.set(cell.offsetIndexY.toInt(), thunderCell)
                }
            }
        }
    }

    /**
     * Object that contains select [ThunderStorm] data to be sent over TCP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     */
    class SerialisedThunderStorm(val x: Float = 0f, val y: Float = 0f,
                                 val cells: Array<ThunderCell.SerialisedThunderCell> = arrayOf())

    /**
     * Returns a default empty [SerialisedThunderStorm] due to [missingComponent],
     * and logs a message to the console
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedThunderStorm {
        FileLog.info("Weather", "Empty serialised thunder storm returned due to missing $missingComponent component")
        return SerialisedThunderStorm()
    }

    /** Gets a [SerialisedThunderStorm] from current state */
    override fun getSerialisableObject(): SerialisedThunderStorm {
        val position = entity[Position.mapper] ?: return emptySerialisableObject("Position")
        val cells = entity[ThunderStormCellChildren.mapper]?.cells ?: return emptySerialisableObject("ThunderStormCellChildren")
        return SerialisedThunderStorm(
            position.x, position.y,
            cells.flatMap { it?.filterNotNull()?.map { cell -> cell.getSerialisableObject() } ?: listOf() }.toTypedArray()
        )
    }
}

/** Thunder cell class that creates an airport entity with the required components on instantiation */
class ThunderCell(
    offsetXIndex: Int, offsetYIndex: Int, onClient: Boolean = true
): SerialisableEntity<ThunderCell.SerialisedThunderCell> {
    val entity = getEngine(onClient).entityOnMainThread(onClient) {
        if (onClient) {
            with<Position> {
                x = offsetXIndex * THUNDERSTORM_CELL_SIZE_PX
                y = offsetYIndex * THUNDERSTORM_CELL_SIZE_PX
            }
        }
        with<ThunderCellInfo> {
            this.offsetXIndex = offsetXIndex
            this.offsetYIndex = offsetYIndex
            intensity = 1
        }
    }

    /**
     * Sets the [intensity] of the thunder cell and updates the drawable
     *
     * Should only be called on the client side
     */
    private fun setClientIntensity(intensity: Int) {
        entity[ThunderCellInfo.mapper]?.intensity = intensity
        val drawable = when (intensity) {
            1, 2 -> STORM_BLUE
            3, 4 -> STORM_LIME
            5, 6 -> STORM_YELLOW
            7, 8 -> STORM_ORANGE
            9, 10 -> STORM_RED
            else -> {
                FileLog.error("Weather", "Invalid intensity $intensity")
                STORM_BLUE
            }
        }
        entity.add(RSSprite(
            drawable,
            THUNDERSTORM_CELL_SIZE_PX,
            THUNDERSTORM_CELL_SIZE_PX
        ))
    }

    companion object {
        private val STORM_BLUE = TextureRegionDrawable(Scene2DSkin.defaultSkin["StormBlue", TextureRegion::class.java])
        private val STORM_LIME = TextureRegionDrawable(Scene2DSkin.defaultSkin["StormLime", TextureRegion::class.java])
        private val STORM_YELLOW = TextureRegionDrawable(Scene2DSkin.defaultSkin["StormYellow", TextureRegion::class.java])
        private val STORM_ORANGE = TextureRegionDrawable(Scene2DSkin.defaultSkin["StormOrange", TextureRegion::class.java])
        private val STORM_RED = TextureRegionDrawable(Scene2DSkin.defaultSkin["StormRed", TextureRegion::class.java])

        /** Converts a [SerialisedThunderCell] to a [ThunderCell] */
        fun fromSerialisedObject(cell: SerialisedThunderCell): ThunderCell {
            return ThunderCell(cell.offsetIndexX.toInt(), cell.offsetIndexY.toInt()).apply {
                setClientIntensity(cell.intensity.toInt())
            }
        }
    }

    /**
     * Object that contains [ThunderCell] data to be sent over TCP, serialised by Kryo
     *
     * Variables will use as small a datatype as practically possible to reduce bandwidth
     */
    class SerialisedThunderCell(val offsetIndexX: Byte = 0, val offsetIndexY: Byte = 0, val intensity: Byte = 0)

    /**
     * Returns a default empty [SerialisedThunderCell] due to [missingComponent],
     * and logs a message to the console
     */
    override fun emptySerialisableObject(missingComponent: String): SerialisedThunderCell {
        FileLog.info("Weather", "Empty serialised thunder cell returned due to missing $missingComponent component")
        return SerialisedThunderCell()
    }

    /** Gets a [SerialisedThunderCell] from current state */
    override fun getSerialisableObject(): SerialisedThunderCell {
        val info = entity[ThunderCellInfo.mapper] ?: return emptySerialisableObject("ThunderCellInfo")
        return SerialisedThunderCell(
            info.offsetXIndex.toByte(), info.offsetYIndex.toByte(), info.intensity.toByte()
        )
    }
}
