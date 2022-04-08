package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.entity
import ktx.ashley.with
import ktx.scene2d.Scene2DSkin

class DepartureAircraft(callsign: String, posX: Float, posY: Float, alt: Float) {
    val entity = Constants.ENGINE.entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitude = alt
        }
        with<AircraftInfo> {
            icaoCallsign = callsign
        }
        with<Direction> {
            dirUnitVector = Vector2(Vector2.Y)
        }
        with<Speed> {

        }
        with<Acceleration> {

        }
        with<RadarData> {

        }
        with<Departure>()
        with<RSSprite> {
            drawable = TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion("aircraftDeparture"))
        }
    }
}