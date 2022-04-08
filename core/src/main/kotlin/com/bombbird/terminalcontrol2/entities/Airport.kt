package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.utilities.MathTools
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Airport class that creates an entity with the required components on instantiation */
class Airport(icao: String, arptName: String, posX: Float, posY: Float, elevation: Float) {
    val entity = Constants.ENGINE.entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitude = elevation
        }
        with<AirportInfo> {
            icaoCode = icao
            name = arptName
        }
    }

    /** Creates a runway entity with the required components, and adds it to airport component's runway map */
    fun addRunway(name: String, posX: Float, posY: Float, trueHdg: Float, runwayLengthM: Int, elevation: Float) {
        entity[AirportInfo.mapper]!!.rwys.rwyMap[name] = Constants.ENGINE.entity {
            with<Position> {
                x = posX
                y = posY
            }
            with<Altitude> {
                altitude = elevation
            }
            with<Direction> {
                dirUnitVector = Vector2(Vector2.Y).rotateDeg(-trueHdg)
            }
            with<GRect> {
                width = MathTools.mToPx(runwayLengthM)
            }
            with<RunwayInfo> {
                rwyName = name
                airport = this@Airport
            }
            with<SRColor> {
                color = Color.WHITE
            }
        }
    }
}