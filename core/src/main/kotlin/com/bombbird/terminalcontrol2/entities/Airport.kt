package com.bombbird.terminalcontrol2.entities

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport.Runway.SerialisedRunway
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import com.bombbird.terminalcontrol2.utilities.mToPx
import com.bombbird.terminalcontrol2.utilities.updateWindVector
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.with

/** Airport class that creates an airport entity with the required components on instantiation */
class Airport(id: Byte, icao: String, arptName: String, trafficRatio: Byte, posX: Float, posY: Float, elevation: Short, onClient: Boolean = true) {
    val entity = getEngine(onClient).entity {
        with<Position> {
            x = posX
            y = posY
        }
        with<Altitude> {
            altitudeFt = elevation.toFloat()
        }
        with<AirportInfo> {
            arptId = id
            icaoCode = icao
            name = arptName
            tfcRatio = trafficRatio
        }
        with<RunwayChildren>()
        with<SIDChildren>()
        with<STARChildren>()
        with<ApproachChildren>()
        with<MetarInfo>()
    }

    companion object {
        /** De-serialises a [SerialisedAirport] and creates a new [Airport] object from it */
        fun fromSerialisedObject(serialisedAirport: SerialisedAirport): Airport {
            return Airport(
                serialisedAirport.arptId, serialisedAirport.icaoCode, serialisedAirport.name, serialisedAirport.tfcRatio,
                serialisedAirport.x, serialisedAirport.y,
                serialisedAirport.altitude
            ).also { arpt ->
                arpt.entity.apply {
                    get(RunwayChildren.mapper)?.apply {
                        rwyMap.clear()
                        for (sRwy in serialisedAirport.rwys) {
                            rwyMap.put(sRwy.rwyId, Runway.fromSerialisedObject(arpt, sRwy))
                        }
                        updatedRwyMapping.clear()
                        for (sMap in serialisedAirport.rwyMapping) {
                            updatedRwyMapping.put(sMap.rwyName, sMap.rwyId)
                        }
                    }
                    get(SIDChildren.mapper)?.apply {
                        sidMap.clear()
                        for (sSid in serialisedAirport.sids) {
                            sidMap.put(sSid.name, SidStar.SID.fromSerialisedObject(sSid))
                        }
                    }
                    get(STARChildren.mapper)?.apply {
                        starMap.clear()
                        for (sStar in serialisedAirport.stars) {
                            starMap.put(sStar.name, SidStar.STAR.fromSerialisedObject(sStar))
                        }
                    }
                    get(ApproachChildren.mapper)?.apply {
                        approachMap.clear()
                        for (sApp in serialisedAirport.approaches) {
                            approachMap.put(sApp.name, Approach.fromSerialisedObject(sApp))
                        }
                    }
                }
                arpt.updateFromSerialisedMetar(serialisedAirport.metar)
            }
        }
    }

    /** Object that contains [Airport] data to be serialised by Kryo */
    class SerialisedAirport(val x: Float = 0f, val y: Float = 0f,
                            val altitude: Short = 0,
                            val arptId: Byte = -1, val icaoCode: String = "", val name: String = "", val tfcRatio: Byte = 0,
                            val rwys: Array<SerialisedRunway> = arrayOf(),
                            val rwyMapping: Array<SerialisedRunwayMapping> = arrayOf(),
                            val sids: Array<SidStar.SID.SerialisedSID> = arrayOf(),
                            val stars: Array<SidStar.STAR.SerialisedSTAR> = arrayOf(),
                            val approaches: Array<Approach.SerialisedApproach> = arrayOf(),
                            val metar: SerialisedMetar = SerialisedMetar()
    )

    /** Gets a [SerialisedAirport] from current state */
    fun getSerialisableObject(): SerialisedAirport {
        entity.apply {
            val position = get(Position.mapper) ?: return SerialisedAirport()
            val altitude = get(Altitude.mapper) ?: return SerialisedAirport()
            val arptInfo = get(AirportInfo.mapper) ?: return SerialisedAirport()
            val rwys = get(RunwayChildren.mapper) ?: return SerialisedAirport()
            val sids = get(SIDChildren.mapper) ?: return SerialisedAirport()
            val stars = get(STARChildren.mapper) ?: return SerialisedAirport()
            val approaches = get(ApproachChildren.mapper) ?: return SerialisedAirport()
            return SerialisedAirport(
                position.x, position.y,
                altitude.altitudeFt.toInt().toShort(),
                arptInfo.arptId, arptInfo.icaoCode, arptInfo.name, arptInfo.tfcRatio,
                rwys.rwyMap.map { it.value.getSerialisableObject() }.toTypedArray(),
                rwys.updatedRwyMapping.map { SerialisedRunwayMapping(it.key, it.value) }.toTypedArray(),
                sids.sidMap.map { it.value.getSerialisedObject() }.toTypedArray(),
                stars.starMap.map { it.value.getSerialisedObject() }.toTypedArray(),
                approaches.approachMap.map { it.value.getSerialisableObject() }.toTypedArray(),
                getSerialisedMetar()
            )
        }
    }

    /** Object that contains METAR data to be serialised by Kryo */
    class SerialisedMetar(val arptId: Byte = 0, val realLifeIcao: String = "",
                          val letterCode: Char? = null, val rawMetar: String? = null,
                          val windHeadingDeg: Short = 360, val windSpeedKt: Short = 0, val windGustKt: Short = 0,
                          val visibilityM: Short = 10000, val ceilingFtAGL: Short? = null, val windshear: String = "")

    /** Gets a [SerialisedMetar] from current METAR state */
    fun getSerialisedMetar(): SerialisedMetar {
        val arptId = entity[AirportInfo.mapper]?.arptId ?: return SerialisedMetar()
        return entity[MetarInfo.mapper]?.let {
            SerialisedMetar(arptId, it.realLifeIcao, it.letterCode, it.rawMetar, it.windHeadingDeg, it.windSpeedKt, it.windGustKt, it.visibilityM, it.ceilingHundredFtAGL, it.windshear)
        } ?: SerialisedMetar()
    }

    /** De-serialises a [SerialisedMetar] and updates this airport's [MetarInfo] from it */
    fun updateFromSerialisedMetar(serialisedMetar: SerialisedMetar) {
        entity[MetarInfo.mapper]?.apply {
            realLifeIcao = serialisedMetar.realLifeIcao
            letterCode = serialisedMetar.letterCode
            rawMetar = serialisedMetar.rawMetar
            windHeadingDeg = serialisedMetar.windHeadingDeg
            windSpeedKt = serialisedMetar.windSpeedKt
            windGustKt = serialisedMetar.windGustKt
            visibilityM = serialisedMetar.visibilityM
            ceilingHundredFtAGL = serialisedMetar.ceilingFtAGL
            windshear = serialisedMetar.windshear
            updateWindVector(windVectorPx, windHeadingDeg, windSpeedKt)
        }
    }

    /** Runway mapping class that contains data to be serialised by Kryo */
    class SerialisedRunwayMapping(val rwyName: String = "", val rwyId: Byte = 0)

    /** Runway class that creates a runway entity with the required components on instantiation */
    class Runway(parentAirport: Airport, id: Byte, name: String, posX: Float, posY: Float, trueHdg: Float, runwayLengthM: Short, elevation: Short, labelPos: Byte, onClient: Boolean = true) {
        val entity = getEngine(onClient).entity {
            with<Position> {
                x = posX
                y = posY
            }
            with<Altitude> {
                altitudeFt = elevation.toFloat()
            }
            with<Direction> {
                trackUnitVector = Vector2(Vector2.Y).rotateDeg(-trueHdg)
            }
            with<GRect> {
                width = mToPx(runwayLengthM.toInt())
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
                               val altitude: Short = 0,
                               val trueHdg: Float = 0f,
                               val rwyId: Byte = -1, val rwyName: String = "", val lengthM: Short = 0,
                               val rwyLabelPos: Byte = 0)

        /** Gets a [SerialisedRunway] from current state */
        fun getSerialisableObject(): SerialisedRunway {
            entity.apply {
                val position = get(Position.mapper) ?: return SerialisedRunway()
                val altitude = get(Altitude.mapper) ?: return SerialisedRunway()
                val direction = get(Direction.mapper) ?: return SerialisedRunway()
                val rwyInfo = get(RunwayInfo.mapper) ?: return SerialisedRunway()
                val rwyLabel = get(RunwayLabel.mapper) ?: return SerialisedRunway()
                return SerialisedRunway(
                    position.x, position.y,
                    altitude.altitudeFt.toInt().toShort(),
                    convertWorldAndRenderDeg(direction.trackUnitVector.angleDeg()),
                    rwyInfo.rwyId, rwyInfo.rwyName, rwyInfo.lengthM,
                    rwyLabel.positionToRunway
                )
            }
        }
    }

    /** Creates a runway entity with the required components, and adds it to airport component's runway map */
    fun addRunway(id: Byte, name: String, posX: Float, posY: Float, trueHdg: Float, runwayLengthM: Short, elevation: Short, labelPos: Byte) {
        Runway(this, id, name, posX, posY, trueHdg, runwayLengthM, elevation, labelPos, false).also { rwy ->
            entity[RunwayChildren.mapper]?.rwyMap?.put(id, rwy)
        }
    }

    /** Maps the given runway name to a certain ID - this method should be used only when loading runways from
     *  internal game files, and not during save file loads since they may contain old runways with the same name (runway added/renamed/etc.) leading to incorrect mappings */
    fun setRunwayMapping(rwyName: String, rwyId: Byte) {
        entity[RunwayChildren.mapper]?.updatedRwyMapping?.put(rwyName, rwyId)
    }

    /** Sets [MetarInfo.realLifeIcao] for the airport, only needed for the game server */
    fun setMetarRealLifeIcao(realLifeIcao: String) {
        entity[MetarInfo.mapper]?.realLifeIcao = realLifeIcao
    }
}