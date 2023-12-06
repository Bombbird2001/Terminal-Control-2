package com.bombbird.terminalcontrol2.entities

import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.TrajectoryPointInfo
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.entityOnMainThread
import ktx.ashley.with

/** TrajectoryPoint class that creates a trajectory point entity with the required components on instantiation */
class TrajectoryPoint(aircraft: Entity, posX: Float, posY: Float, altitude: Float, advanceTimeS: Int) {
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
}