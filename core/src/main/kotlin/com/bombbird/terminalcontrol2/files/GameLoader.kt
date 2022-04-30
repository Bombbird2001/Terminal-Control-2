package com.bombbird.terminalcontrol2.files

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.RunwayLabel
import com.bombbird.terminalcontrol2.components.SIDChildren
import com.bombbird.terminalcontrol2.components.STARChildren
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.entities.MinAltSector
import com.bombbird.terminalcontrol2.entities.Waypoint
import com.bombbird.terminalcontrol2.global.Variables
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.navigation.UsabilityFilter
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.MathTools
import ktx.ashley.get
import ktx.assets.toInternalFile
import ktx.collections.isNotEmpty

/** Helper object that deals with loading of game data files */
object GameLoader {
    fun loadWorldData(mainName: String, gameServer: GameServer) {
        "Airports/$mainName.arpt".toInternalFile().readString().split("\\r?\\n".toRegex()).toTypedArray().apply {
            var parseMode = ""
            var currAirport: Airport? = null
            var currSid: SidStar.SID? = null
            var currStar: SidStar.STAR? = null
            for (line in this) {
                val lineArray = line.split(" ")
                when (lineArray[0]) {
                    "AIRPORT" -> currAirport = parseAirport(lineArray, gameServer)
                    "/AIRPORT" -> {
                        currAirport = null
                        currSid = null
                    }
                    "SID" -> currSid = parseSID(lineArray, currAirport ?: continue)
                    "/SID" -> currSid = null
                    "STAR" -> currStar = parseSTAR(lineArray, currAirport ?: continue)
                    "/STAR" -> currStar = null
                    "RWY" -> if (currSid != null) parseSIDRwyRoute(lineArray, currSid) else if (currStar != null) parseSTARRwyRoute(lineArray, currStar)
                    "ROUTE" -> if (currSid != null) parseSIDSTARRoute(lineArray, currSid) else if (currStar != null) parseSIDSTARRoute(lineArray, currStar)
                    "OUTBOUND" -> if (currSid != null) parseSIDSTARinOutboundRoute(lineArray, currSid)
                    "INBOUND" -> if (currStar != null) parseSIDSTARinOutboundRoute(lineArray, currStar)
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
        gameServer.waypoints[id] = Waypoint(id, wptName, posX, posY, false)
        gameServer.updatedWaypointMapping[wptName] = id
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
        gameServer.airports.put(id, arpt)
        gameServer.updatedAirportMapping.put(icao, id)
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

    /** Parse the given [data] into a [SidStar.SID], and adds it to the supplied [airport]'s [SIDChildren] component
     *
     * Returns the constructed [SidStar.SID]
     * */
    private fun parseSID(data: List<String>, airport: Airport): SidStar.SID {
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
        val sid = SidStar.SID(name, dayNight, pronunciation)
        airport.entity[SIDChildren.mapper]?.sidMap?.put(name, sid)
        return sid
    }

    /** Parse the given [data] into the route data for runway segment of the SID, and adds it to the supplied [sid]'s [SidStar.rwyLegs] */
    private fun parseSIDRwyRoute(data: List<String>, sid: SidStar.SID) {
        val rwy = data[1]
        val initClimb = data[2].toInt()
        val route = parseLegs(data.subList(3, data.size), Route.Leg.NORMAL)
        sid.rwyLegs.put(rwy, route)
        sid.rwyInitialClimbs.put(rwy, initClimb)
    }

    /** Parse the given [data] into a [SidStar.STAR], and adds it to the supplied [airport]'s [STARChildren] component
     *
     * Returns the constructed [SidStar.STAR]
     * */
    private fun parseSTAR(data: List<String>, airport: Airport): SidStar.STAR {
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
        val star = SidStar.STAR(name, dayNight, pronunciation)
        airport.entity[STARChildren.mapper]?.starMap?.put(name, star)
        return star
    }

    /** Parse the given [data] into the route data for runway segment of the STAR, and adds it to the supplied [star]'s [SidStar.rwyLegs] */
    private fun parseSTARRwyRoute(data: List<String>, star: SidStar.STAR) {
        val rwy = data[1]
        val route = parseLegs(data.subList(2, data.size), Route.Leg.NORMAL)
        star.rwyLegs.put(rwy, route)
    }

    /** Parse the given [data] into the route legs data, and adds it to the supplied [sidStar]'s [SidStar.routeLegs] */
    private fun parseSIDSTARRoute(data: List<String>, sidStar: SidStar) {
        if (sidStar.routeLegs.legs.isNotEmpty()) {
            Gdx.app.log("GameLoader", "Multiple routes for SID/STAR: ${sidStar.name}")
        }
        sidStar.routeLegs.extendRoute(parseLegs(data.subList(1, data.size), Route.Leg.NORMAL))
    }

    /** Parse the given [data] into the route data for the inbound/outbound segment of the SID/STAR respectively, and adds it to the supplied [sidStar]'s [SidStar.routeLegs] */
    private fun parseSIDSTARinOutboundRoute(data: List<String>, sidStar: SidStar) {
        sidStar.addToInboundOutboundLegs(parseLegs(data.subList(1, data.size), Route.Leg.NORMAL))
    }

    /** Parse the given [data], [flightPhase] into an array of legs and returns it */
    private fun parseLegs(data: List<String>, flightPhase: Byte): Route {
        val legArray = Route()
        var legType = ""
        var dataStream = ""
        for (part in data) {
            when (part) {
                "INITCLIMB", "HDNG", "WYPT", "HOLD" -> {
                    parseLeg(legType, dataStream, flightPhase)?.apply { legArray.addLeg(this) }
                    legType = part
                    dataStream = ""
                }
                else -> when (legType) {
                    "INITCLIMB", "HDNG", "WYPT", "HOLD" -> {
                        dataStream += " $part "
                    }
                    else -> Gdx.app.log("GameLoader", "Unknown leg type: $legType")
                }
            }
        }
        parseLeg(legType, dataStream, flightPhase)?.apply { legArray.addLeg(this) }

        return legArray
    }

    /** Parses a single leg from the given [data], [flightPhase]
     *
     * [data] should be a string of the required parameters for the relevant leg
     * */
    private fun parseLeg(legType: String, data: String, flightPhase: Byte): Route.Leg? {
        val hdgRegex = " (\\d{1,3}) ".toRegex() // Heading values of 1 to 3 digits
        val atAltRegex = " (-?\\d+) ".toRegex() // Altitude values of at least 1 digit
        val aboveAltRegex = " A(-?\\d+) ".toRegex() // Altitude values of at least 1 digit, with "A" as a prefix
        val belowAltRegex = " B(-?\\d+) ".toRegex()// Altitude values of at least 1 digit, with "B" as a prefix
        val spdRegex = " S(\\d{3}) ".toRegex() // Speed values of 3 digits, with "S" as a prefix
        val wptRegex = " ([A-Z]{3}|[A-Z]{5}) ".toRegex() // Only waypoints with 3 or 5 letters allowed
        val foRegex = " FLYOVER ".toRegex() // For flyover waypoints
        val dirRegex = " (LEFT|RIGHT) ".toRegex() // For forced turn directions
        when (legType) {
            "INITCLIMB" -> {
                val hdg = hdgRegex.find(data)?.groupValues?.get(1)?.toInt()?.toShort() ?: return null
                val minAlt = aboveAltRegex.find(data)?.groupValues?.get(1)?.toInt() ?: return null
                return Route.InitClimbLeg(hdg, minAlt)
            }
            "HDNG" -> return Route.VectorLeg(hdgRegex.find(data)?.groupValues?.get(1)?.toInt()?.toShort() ?: return null)
            "WYPT" -> {
                val wptName = wptRegex.find(data)?.groupValues?.get(1) ?: return null
                val atAlt = atAltRegex.find(data)?.groupValues?.get(1)?.toInt()
                val maxAlt = atAlt ?: belowAltRegex.find(data)?.groupValues?.get(1)?.toInt()
                val minAlt = atAlt ?: aboveAltRegex.find(data)?.groupValues?.get(1)?.toInt()
                val maxSpd = spdRegex.find(data)?.groupValues?.get(1)?.toInt()?.toShort()
                val flyOver = foRegex.find(data) != null
                val turnDir = dirRegex.find(data)?.let {
                    when (it.groupValues[1]) {
                        "LEFT" -> CommandTarget.TURN_LEFT
                        "RIGHT" -> CommandTarget.TURN_RIGHT
                        else -> {
                            Gdx.app.log("GameLoader", "Unknown turn direction for $wptName: ${it.groupValues[0]}")
                            CommandTarget.TURN_DEFAULT
                        }
                    }
                } ?: CommandTarget.TURN_DEFAULT
                return Route.WaypointLeg(wptName, maxAlt, minAlt, maxSpd, legActive = true, altRestrActive = true, spdRestrActive = true, flyOver, turnDir, flightPhase)
            }
            else -> {
                if (legType.isNotEmpty()) Gdx.app.log("GameLoader", "Unknown leg type $legType")
                return null
            }
        }
    }
}