package com.bombbird.terminalcontrol2.utilities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.bombbird.terminalcontrol2.components.FlightType
import com.bombbird.terminalcontrol2.components.SectorInfo
import ktx.scene2d.Scene2DSkin

/** Helper object containing functions for dealing with aircraft control states */
object ControlStateTools {

    /** Gets the appropriate aircraft blip icon for its [flightType] and [sectorID] */
    fun getAircraftIcon(flightType: Byte, sectorID: Byte): TextureRegionDrawable {
        return TextureRegionDrawable(Scene2DSkin.defaultSkin.getRegion(
            when (sectorID) {
                SectorInfo.CENTRE -> "aircraftEnroute"
                SectorInfo.TOWER -> "aircraftTower"
                else -> when (flightType) {
                    FlightType.DEPARTURE -> "aircraftDeparture"
                    FlightType.ARRIVAL -> "aircraftArrival"
                    FlightType.EN_ROUTE -> "aircraftEnroute"
                    else -> {
                        Gdx.app.log("ControlState", "Unknown flight type $flightType")
                        "aircraftEnroute"
                    }
            }
        }))
    }
}