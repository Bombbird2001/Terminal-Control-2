package com.bombbird.terminalcontrol2.entities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Pool.Poolable
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.TrajectoryPointInfo
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.get
import ktx.ashley.with

/** TrajectoryPoint class that creates a trajectory point entity with the required components on instantiation */
class TrajectoryPoint(aircraft: Entity, posX: Float, posY: Float, altitude: Float, advanceTimeS: Int): Poolable {
    val entity = getEngine(false).entityOnMainThread(false) {
        with<TrajectoryPointInfo> {
            this.aircraft = aircraft
            advanceTimingS = advanceTimeS
        }
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitudeFt = altitude
        }
    }

    constructor(): this(Entity(), 0f, 0f, 0f, 0)

    /**
     * Initialises the trajectory point with the given parameters; used for pooling
     */
    fun init(aircraft: Entity, posX: Float, posY: Float, altitude: Float, advanceTimeS: Int) {
        entity[Position.mapper]?.apply {
            x = posX
            y = posY
        }
        entity[Altitude.mapper]?.altitudeFt = altitude
        entity[TrajectoryPointInfo.mapper]?.apply {
            this.aircraft = aircraft
            advanceTimingS = advanceTimeS
        }
    }

    override fun reset() {
        // Don't need to do anything
    }
}