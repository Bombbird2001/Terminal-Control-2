package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.Direction
import com.bombbird.terminalcontrol2.components.GPolygon
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.SRColor
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.nmToPx
import ktx.ashley.*

abstract class Zones {
    class ApproachNormalOperatingZone(posX: Float, posY: Float, appHdg: Short, widthNm: Float, lengthNm: Float, onClient: Boolean = true) {
        val entity = getEngine(onClient).entity {
            with<Position> {
                x = posX
                y = posY
            }
            with<Direction> {
                trackUnitVector = Vector2(Vector2.Y).rotateDeg(180 - (appHdg - MAG_HDG_DEV))
            }
            with<GPolygon> {
                val halfWidthVec = Vector2(Vector2.Y).rotateDeg(270 - (appHdg - MAG_HDG_DEV)).scl(nmToPx(widthNm / 2))
                val lengthVec = Vector2(Vector2.Y).rotateDeg(180 - (appHdg - MAG_HDG_DEV)).scl(nmToPx(lengthNm))
                vertices = floatArrayOf(posX + halfWidthVec.x, posY + halfWidthVec.y,
                    posX + halfWidthVec.x + lengthVec.x, posY + halfWidthVec.y + lengthVec.y,
                    posX - halfWidthVec.x + lengthVec.x, posY - halfWidthVec.y + lengthVec.y,
                    posX - halfWidthVec.x, posY - halfWidthVec.y)
            }
            if (onClient) with<SRColor> {
                color = Color.GREEN
            }
        }
    }
}