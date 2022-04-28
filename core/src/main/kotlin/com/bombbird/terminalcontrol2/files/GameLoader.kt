package com.bombbird.terminalcontrol2.files

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.components.RunwayLabel
import com.bombbird.terminalcontrol2.components.SIDChildren
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.MinAltSector
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.navigation.SID
import com.bombbird.terminalcontrol2.navigation.UsabilityFilter
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.MathTools
import ktx.ashley.get
import ktx.assets.toInternalFile

/** Helper object that deals with loading of game data files */
object GameLoader {
    fun loadWorldData(mainName: String, gameServer: GameServer) {
        "Airports/$mainName.arpt".toInternalFile().readString().split("\\r?\\n".toRegex()).toTypedArray().apply {
            var parseMode = ""
            var currAirport: Airport? = null
            var currSid: SID? = null
            for (line in this) {
                val lineArray = line.split(" ")
                when (lineArray[0]) {
                    "AIRPORT" -> currAirport = parseAirport(lineArray, gameServer)
                    "/AIRPORT" -> {
                        currAirport = null
                        currSid = null
                    }
                    "SID" -> currSid = parseSID(lineArray, currAirport ?: continue)
                    "/SID" -> {
                        currSid = null
                    }
                    "/$parseMode" -> parseMode = ""
                    else -> when (parseMode) {
                        "WAYPOINTS" -> parseWaypoint(lineArray, gameServer)
                        "MIN_ALT_SECTORS" -> parseMinAltSector(lineArray, gameServer)
                        "RWYS" -> parseRunway(lineArray, currAirport ?: continue)
                        "" -> when (lineArray[0]) {
                            "MIN_ALT" -> Variables.MIN_ALT = lineArray[1].toInt()
                            "MAX_ALT" -> Variables.MAX_ALT = lineArray[1].toInt()
                            "TRANS_ALT" -> Variables.TRANS_ALT = lineArray[1].toInt()
                            "TRANS_LVL" -> Variables.TRANS_LVL = lineArray[1].toInt()
                            "MIN_SEP" -> Variables.MIN_SEP = lineArray[1].toFloat()
                            "MAG_HDG_DEV" -> Variables.MAG_HDG_DEV = lineArray[1].toFloat()
                            else -> parseMode = lineArray[0]
                        }
                    }
                }
            }
        }
    }

    /** Parse the given [data] into a [Waypoint], and adds it to [GameServer.waypoints] */
    private fun parseWaypoint(data: List<String>, gameServer: GameServer) {
        val id: Short = data[0].toShort()
        val wptName = data[1]
        val pos = data[2].split(",")
        val posX = MathTools.nmToPx(pos[0].toFloat()).toInt().toShort()
        val posY = MathTools.nmToPx(pos[1].toFloat()).toInt().toShort()
        gameServer.waypoints[wptName] = Waypoint(id, wptName, posX, posY, false)
    }

    /** Parse the given [data] into a [MinAltSector], and adds it to [GameServer.minAltSectors] */
    private fun parseMinAltSector(data: List<String>, gameServer: GameServer) {
        val type = data[0]
        val enforced = if (data[1] == "RESTR") true else if (data[1] == "MVA") false else {
            Gdx.app.log("GameLoader", "Unknown minAltSector restriction ${data[1]} provided")
            false
        }
        val minAlt = if (data[2] == "UNL") null else data[2].toInt()
        when (type) {
            "POLYGON" -> {
                val polygon = ArrayList<Short>()
                for (i in 3 until data.size) {
                    val pos = data[i].split(",")
                    polygon.add(MathTools.nmToPx(pos[0].toFloat()).toInt().toShort())
                    polygon.add(MathTools.nmToPx(pos[1].toFloat()).toInt().toShort())
                }
                gameServer.minAltSectors.add(MinAltSector(minAlt, polygon.toShortArray(), restr = enforced, onClient = false))
            }
            "CIRCLE" -> {
                val pos = data[3].split(",")
                val posX = MathTools.nmToPx(pos[0].toFloat()).toInt().toShort()
                val posY = MathTools.nmToPx(pos[1].toFloat()).toInt().toShort()
                val radius = MathTools.nmToPx(data[4].toFloat())
                gameServer.minAltSectors.add(MinAltSector(minAlt, null, posX, posY, radius, enforced, false))
            }
            else -> Gdx.app.log("GameLoader", "Unknown minAltSector type $type")
        }
    }

    /** Parse the given [data] into an [Airport], and adds it to [GameServer.airports]
     *
     * Returns the constructed [Airport]
     * */
    private fun parseAirport(data: List<String>, gameServer: GameServer): Airport {
        val id = data[1].toByte()
        val icao = data[2]
        val name = data[3]
        val ratio = data[4].toByte()
        val pos = data[5].split(",")
        val posX = MathTools.nmToPx(pos[0].toFloat())
        val posY = MathTools.nmToPx(pos[1].toFloat())
        val elevation = data[6].toShort()
        val realLifeIcao = data[7]
        val arpt = Airport(id, icao, name, ratio, posX, posY, elevation).apply {
            setMetarRealLifeIcao(realLifeIcao)
        }
        gameServer.airports[icao] = arpt
        return arpt
    }

    /** Parse the given [data] into a runway, and adds it to the supplied [airport] */
    private fun parseRunway(data: List<String>, airport: Airport) {
        val id = data[0].toByte()
        val name = data[1]
        val pos = data[2].split(",")
        val posX = MathTools.nmToPx(pos[0].toFloat())
        val posY = MathTools.nmToPx(pos[1].toFloat())
        val trueHdg = data[3].toFloat()
        val rwyLengthM = data[4].toShort()
        val elevation = data[5].toShort()
        val labelPos = when (data[6]) {
            "LABEL_BEFORE" -> RunwayLabel.BEFORE
            "LABEL_RIGHT" -> RunwayLabel.RIGHT
            "LABEL_LEFT" -> RunwayLabel.LEFT
            else -> {
                Gdx.app.log("GameLoader", "Unknown runway label placement for runway $name: ${data[6]}")
                RunwayLabel.BEFORE
            }
        }
        airport.addRunway(id, name, posX, posY, trueHdg, rwyLengthM, elevation, labelPos)
    }

    /** Parse the given [data] into a [SID], and adds it to the supplied [airport]'s [SIDChildren] component
     *
     * Returns the constructed [SID]
     * */
    private fun parseSID(data: List<String>, airport: Airport): SID {
        val name = data[1]
        val dayNight = when (data[2]) {
            "DAY_NIGHT" -> UsabilityFilter.DAY_AND_NIGHT
            "DAY_ONLY" -> UsabilityFilter.DAY_ONLY
            "NIGHT_ONLY" -> UsabilityFilter.NIGHT_ONLY
            else -> {
                Gdx.app.log("GameLoader", "Unknown dayNight for SID $name: ${data[2]}")
                UsabilityFilter.DAY_AND_NIGHT
            }
        }
        val pronunciation = data[3].replace("-", " ")
        val sid = SID(name, dayNight, pronunciation)
        airport.entity[SIDChildren.mapper]?.sidMap?.set(name, sid)
        return sid
    }
}