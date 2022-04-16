package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport.Runway.SerialisedRunway
import com.bombbird.terminalcontrol2.global.Constants
import com.bombbird.terminalcontrol2.utilities.MathTools
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Airport class that creates an airport entity with the required components on instantiation */
class Airport(id: Byte, icao: String, arptName: String, posX: Float, posY: Float, elevation: Float, onClient: Boolean = true) {
    val entity = (if (onClient) Constants.CLIENT_ENGINE else Constants.SERVER_ENGINE).entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitude = elevation
        }
        with<AirportInfo> {
            arptId = id
            icaoCode = icao
            name = arptName
        }
        with<RunwayChildren>()
        with<MetarInfo>()
    }

    companion object {
        /** De-serialises a [SerialisedAirport] and creates a new [Airport] object from it */
        fun fromSerialisedObject(serialisedAirport: SerialisedAirport): Airport {
            return Airport(
                serialisedAirport.arptId, serialisedAirport.icaoCode, serialisedAirport.name,
                serialisedAirport.x, serialisedAirport.y,
                serialisedAirport.altitude
            ).also { arpt ->
                arpt.entity.apply {
                    get(RunwayChildren.mapper)!!.apply {
                        rwyMap = HashMap(serialisedAirport.rwys.mapValues { Runway.fromSerialisedObject(arpt, it.value) })
                    }
                }
            }
        }
    }

    /** Object that contains [Airport] data to be serialised by Kryo */
    class SerialisedAirport(val x: Float = 0f, val y: Float = 0f,
                            val altitude: Float = 0f,
                            val arptId: Byte = -1, val icaoCode: String = "", val name: String = "",
                            val rwys: Map<Byte, SerialisedRunway> = LinkedHashMap())

    /** Gets a [SerialisedAirport] from current state */
    fun getSerialisableObject(): SerialisedAirport {
        entity.apply {
            val position = get(Position.mapper)!!
            val altitude = get(Altitude.mapper)!!
            val arptInfo = get(AirportInfo.mapper)!!
            val rwys = get(RunwayChildren.mapper)!!
            return SerialisedAirport(
                position.x, position.y,
                altitude.altitude,
                arptInfo.arptId, arptInfo.icaoCode, arptInfo.name,
                rwys.rwyMap.mapValues { it.value.getSerialisableObject() }
            )
        }
    }

    /** Runway class that creates a runway entity with the required components on instantiation */
    class Runway(parentAirport: Airport, id: Byte, name: String, posX: Float, posY: Float, trueHdg: Float, runwayLengthM: Short, elevation: Float, labelPos: Byte, onClient: Boolean = true) {
        val entity = (if (onClient) Constants.CLIENT_ENGINE else Constants.SERVER_ENGINE).entity {
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
                width = MathTools.mToPx(runwayLengthM.toInt())
            }
            with<RunwayInfo> {
                rwyId = id
                rwyName = name
                lengthM = runwayLengthM
                airport = parentAirport
            }
            with<SRColor> {
                color = Color.WHITE
            }
            with<GenericLabel> {
                updateStyle("Runway")
                updateText(name)
            }
            with<RunwayLabel> {
                if (labelPos in RunwayLabel.LEFT..RunwayLabel.RIGHT) positionToRunway = labelPos
                else {
                    positionToRunway = 0
                    Gdx.app.log("Runway", "Invalid labelPos $labelPos set, using default value 0")
                }
            }
            with<ConstantZoomSize>()
        }

        companion object {
            /** De-serialises a [SerialisedRunway] and creates a new [Runway] object from it */
            fun fromSerialisedObject(parentAirport: Airport, serialisedRunway: SerialisedRunway): Runway {
                return Runway(
                    parentAirport, serialisedRunway.rwyId, serialisedRunway.rwyName,
                    serialisedRunway.x, serialisedRunway.y,
                    serialisedRunway.trueHdg,
                    serialisedRunway.lengthM,
                    serialisedRunway.altitude,
                    serialisedRunway.rwyLabelPos
                )
            }
        }

        /** Object that contains [Runway] data to be serialised by Kryo */
        class SerialisedRunway(val x: Float = 0f, val y: Float = 0f,
                               val altitude: Float = 0f,
                               val trueHdg: Float = 0f,
                               val rwyId: Byte = -1, val rwyName: String = "", val lengthM: Short = 0,
                               val rwyLabelPos: Byte = 0)

        /** Gets a [SerialisedRunway] from current state */
        fun getSerialisableObject(): SerialisedRunway {
            entity.apply {
                val position = get(Position.mapper)!!
                val altitude = get(Altitude.mapper)!!
                val direction = get(Direction.mapper)!!
                val rwyInfo = get(RunwayInfo.mapper)!!
                val rwyLabel = get(RunwayLabel.mapper)!!
                return SerialisedRunway(
                    position.x, position.y,
                    altitude.altitude,
                    MathTools.convertWorldAndRenderDeg(direction.dirUnitVector.angleDeg()),
                    rwyInfo.rwyId, rwyInfo.rwyName, rwyInfo.lengthM,
                    rwyLabel.positionToRunway
                )
            }
        }
    }

    /** Creates a runway entity with the required components, and adds it to airport component's runway map */
    fun addRunway(id: Byte, name: String, posX: Float, posY: Float, trueHdg: Float, runwayLengthM: Short, elevation: Float, labelPos: Byte) {
        Runway(this, id, name, posX, posY, trueHdg, runwayLengthM, elevation, labelPos, false).also { rwy ->
            entity[AirportInfo.mapper]!!.rwys.rwyMap[id] = rwy
            entity[RunwayChildren.mapper]!!.rwyMap[id] = rwy
        }
    }

    /** Sets [MetarInfo.realLifeIcao] for the airport, only needed for the game server */
    fun setMetarRealLifeIcao(realLifeIcao: String) {
        entity[MetarInfo.mapper]!!.realLifeIcao = realLifeIcao
    }
}