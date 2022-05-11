package com.bombbird.terminalcontrol2.files

import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.navigation.UsabilityFilter
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.nmToPx
import ktx.ashley.get
import ktx.assets.toInternalFile
import ktx.collections.isNotEmpty

/** Helper object that deals with loading of game data files */
object GameLoader {
    /** Loads the "[mainName].arpt" file located in the Airports subfolder in the assets */
    fun loadWorldData(mainName: String, gameServer: GameServer) {
        "Airports/$mainName.arpt".toInternalFile().readString().split("\\r?\\n".toRegex()).toTypedArray().apply {
            var parseMode = ""
            var currAirport: Airport? = null
            var currSid: SidStar.SID? = null
            var currStar: SidStar.STAR? = null
            var currSectorCount = 0
            var currApp: Approach? = null
            for (line in this) {
                val lineData = line.split(" ")
                when (lineData[0]) {
                    "AIRPORT" -> currAirport = parseAirport(lineData, gameServer)
                    "/AIRPORT" -> {
                        currAirport = null
                        currSid = null
                    }
                    "SID" -> currSid = parseSID(lineData, currAirport ?: continue)
                    "/SID" -> currSid = null
                    "STAR" -> currStar = parseSTAR(lineData, currAirport ?: continue)
                    "/STAR" -> currStar = null
                    "RWY" -> if (currSid != null) parseSIDRwyRoute(lineData, currSid) else if (currStar != null) parseSTARRwyRoute(lineData, currStar)
                    "ROUTE" -> {
                        if (currSid != null) parseSIDSTARRoute(lineData, currSid)
                        else if (currStar != null) parseSIDSTARRoute(lineData, currStar)
                        else if (currApp != null) parseApproachRoute(lineData, currApp)
                    }
                    "OUTBOUND" -> if (currSid != null) parseSIDSTARinOutboundRoute(lineData, currSid)
                    "INBOUND" -> if (currStar != null) parseSIDSTARinOutboundRoute(lineData, currStar)
                    "APCH" -> currApp = parseApproach(lineData, currAirport ?: continue)
                    "/APCH" -> currApp = null
                    "TRANSITION" -> if (currApp != null) parseApproachTransition(lineData, currApp)
                    "MISSED" -> if (currApp != null) parseApproachMissed(lineData, currApp)
                    "/$currSectorCount" -> currSectorCount = 0
                    "/$parseMode" -> parseMode = ""
                    else -> {
                        when (parseMode) {
                            "WAYPOINTS" -> parseWaypoint(lineData, gameServer)
                            "HOLDS" -> parseHold(lineData, gameServer)
                            "MIN_ALT_SECTORS" -> parseMinAltSector(lineData, gameServer)
                            "SHORELINE" -> parseShoreline(lineData, gameServer)
                            "RWYS" -> parseRunway(lineData, currAirport ?: continue)
                            "SECTORS" -> {
                                if (currSectorCount == 0) currSectorCount = lineData[0].toInt()
                                else parseSector(lineData, gameServer)
                            }
                            "" -> when (lineData[0]) {
                                "MIN_ALT" -> MIN_ALT = lineData[1].toInt()
                                "MAX_ALT" -> MAX_ALT = lineData[1].toInt()
                                "TRANS_ALT" -> TRANS_ALT = lineData[1].toInt()
                                "TRANS_LVL" -> TRANS_LVL = lineData[1].toInt()
                                "MIN_SEP" -> MIN_SEP = lineData[1].toFloat()
                                "MAG_HDG_DEV" -> MAG_HDG_DEV = lineData[1].toFloat()
                                else -> parseMode = lineData[0]
                            }
                        }
                    }
                }
            }
        }
    }

    /** Parse the given [data] into a [Waypoint], and adds it to [GameServer.waypoints] */
    private fun parseWaypoint(data: List<String>, gameServer: GameServer) {
        val id = data[0].toShort()
        val wptName = data[1]
        val pos = data[2].split(",")
        val posX = nmToPx(pos[0].toFloat()).toInt().toShort()
        val posY = nmToPx(pos[1].toFloat()).toInt().toShort()
        gameServer.waypoints[id] = Waypoint(id, wptName, posX, posY, false)
        gameServer.updatedWaypointMapping[wptName] = id
    }

    /** Parse th given [data] into a [Sector], and adds it to [GameServer.sectors] */
    private fun parseSector(data: List<String>, gameServer: GameServer) {
        val id = data[0].toByte()
        val freq = data[1]
        val callsign = data[2]
        val polygon = ArrayList<Short>()
        for (i in 3 until data.size) {
            val pos = data[i].split(",")
            polygon.add(nmToPx(pos[0].toFloat()).toInt().toShort())
            polygon.add(nmToPx(pos[1].toFloat()).toInt().toShort())
        }
        gameServer.sectors.add(Sector(id, "", freq, callsign, polygon.toShortArray(), onClient = false))
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
                var labelX: Short? = null
                var labelY: Short? = null
                for (i in 3 until data.size) {
                    val pos = data[i].split(",")
                    if (pos[0] == "LABEL") {
                        labelX = nmToPx(pos[1].toFloat()).toInt().toShort()
                        labelY = nmToPx(pos[2].toFloat()).toInt().toShort()
                        continue
                    }
                    polygon.add(nmToPx(pos[0].toFloat()).toInt().toShort())
                    polygon.add(nmToPx(pos[1].toFloat()).toInt().toShort())
                }
                gameServer.minAltSectors.add(MinAltSector(minAlt, polygon.toShortArray(), labelX = labelX, labelY = labelY, restr = enforced, onClient = false))
            }
            "CIRCLE" -> {
                val pos = data[3].split(",")
                val posX = nmToPx(pos[0].toFloat()).toInt().toShort()
                val posY = nmToPx(pos[1].toFloat()).toInt().toShort()
                val radius = nmToPx(data[4].toFloat())
                gameServer.minAltSectors.add(MinAltSector(minAlt, null, posX, posY, radius, null, null, enforced, false))
            }
            else -> Gdx.app.log("GameLoader", "Unknown minAltSector type $type")
        }
    }

    /** Parse the given [data] into a [Shoreline], and adds it to [GameServer.shoreline] */
    private fun parseShoreline(data: List<String>, gameServer: GameServer) {
        val polygon = ArrayList<Short>()
        for (coord in data) {
            val pos = coord.split(",")
            polygon.add(nmToPx(pos[0].toFloat()).toInt().toShort())
            polygon.add(nmToPx(pos[1].toFloat()).toInt().toShort())
        }
        gameServer.shoreline.add(Shoreline(polygon.toShortArray()))
    }

    /** Parse the given [data] into a [PublishedHold], and adds it to [GameServer.shoreline] */
    private fun parseHold(data: List<String>, gameServer: GameServer) {
        val wptName = data[0]
        val wptId = gameServer.updatedWaypointMapping[wptName] ?: run {
            Gdx.app.log("GameLoader", "Unknown hold waypoint $wptName")
            return
        }
        val inboundHdg = data[1].toShort()
        val legDist = data[2].toByte()
        val maxSpdLower = data[3].toShort()
        val maxSpdHigher = data[4].toShort()
        val dir = when (data[5]) {
            "LEFT" -> CommandTarget.TURN_LEFT
            "RIGHT" -> CommandTarget.TURN_RIGHT
            else -> {
                Gdx.app.log("GameLoader", "Unknown hold direction ${data[5]} for $wptName")
                CommandTarget.TURN_RIGHT
            }
        }
        var minAlt: Int? = null
        var maxAlt: Int? = null
        if (data.size >= 7) data[6].let {
            val aboveAltRegex = "A(-?\\d+)".toRegex() // Altitude values of at least 1 digit, with "A" as a prefix
            val belowAltRegex = "B(-?\\d+)".toRegex() // Altitude values of at least 1 digit, with "B" as a prefix
            minAlt = aboveAltRegex.find(it)?.groupValues?.get(1)?.toInt()
            maxAlt = belowAltRegex.find(it)?.groupValues?.get(1)?.toInt()
        }
        gameServer.publishedHolds.put(wptName, PublishedHold(wptId, maxAlt, minAlt, maxSpdLower, maxSpdHigher, inboundHdg, legDist, dir, false))
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
        val posX = nmToPx(pos[0].toFloat())
        val posY = nmToPx(pos[1].toFloat())
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
        val posX = nmToPx(pos[0].toFloat())
        val posY = nmToPx(pos[1].toFloat())
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
        airport.setRunwayMapping(name, id)
    }

    /** Parse the given [data] into an [Approach], and adds it to the supplied [airport]'s [ApproachChildren] component
     *
     * Returns the constructed [Approach] or null if an invalid runway is specified
     * */
    private fun parseApproach(data: List<String>, airport: Airport): Approach? {
        val name = data[2].replace("-", " ")
        val dayNight = when (data[3]) {
            "DAY_NIGHT" -> UsabilityFilter.DAY_AND_NIGHT
            "DAY_ONLY" -> UsabilityFilter.DAY_ONLY
            "NIGHT_ONLY" -> UsabilityFilter.NIGHT_ONLY
            else -> {
                Gdx.app.log("GameLoader", "Unknown dayNight for SID $name: ${data[2]}")
                UsabilityFilter.DAY_AND_NIGHT
            }
        }
        val rwyId = airport.entity[RunwayChildren.mapper]?.updatedRwyMapping?.get(data[4]) ?: run {
            Gdx.app.log("GameLoader", "Runway ${data[4]} not found for approach $name")
            return null
        }
        val heading = data[5].toShort()
        val pos = data[6].split(",")
        val posX = nmToPx(pos[0].toFloat())
        val posY = nmToPx(pos[1].toFloat())
        val decisionAltitude = data[7].toShort()
        val rvr = data[8].toShort()
        val app = when (data[1]) {
            "ILS-GS" -> {
                val locDist = data[9].toByte()
                val glideAngle = data[10].toFloat()
                val gsOffset = data[11].toFloat()
                val maxInterceptAlt = data[12].toShort()
                val towerCallsign = data[13].replace("-", " ")
                val towerFreq = data[14]
                Approach.IlsGS(name, rwyId, towerCallsign, towerFreq, heading, posX, posY, locDist, glideAngle, gsOffset, maxInterceptAlt, decisionAltitude, rvr, dayNight)
            }
            "ILS-LOC-OFFSET" -> {
                val locDist = data[9].toByte()
                val towerCallsign = data[10].replace("-", " ")
                val towerFreq = data[11]
                val lineUpDist = data[12].toFloat()
                val steps = ArrayList<Pair<Float, Short>>()
                for (i in 13 until data.size) {
                    val step = data[i].split("@")
                    steps.add(Pair(step[1].toFloat(), step[0].toShort()))
                }
                Approach.IlsLOCOffset(name, rwyId, towerCallsign, towerFreq, heading, posX, posY, locDist, decisionAltitude, rvr, lineUpDist, steps.toTypedArray(), dayNight)
            }
            else -> {
                Gdx.app.log("GameLoader", "Unknown approach type ${data[1]} for $name")
                null
            }
        }
        app?.apply {
            airport.entity[ApproachChildren.mapper]?.approachMap?.put(name, this)
        }
        return app
    }

    /** Parse the given [data] into the route legs data, and adds it to the supplied [approach]'s [Approach.routeLegs] */
    private fun parseApproachRoute(data: List<String>, approach: Approach) {
        if (approach.routeLegs.legs.isNotEmpty()) {
            Gdx.app.log("GameLoader", "Multiple routes for approach: ${approach.entity[ApproachInfo.mapper]?.approachName}")
        }
        approach.routeLegs.extendRoute(parseLegs(data.subList(1, data.size), Route.Leg.APP))
    }

    /** Parse the given [data] into approach transition legs data, and adds it to the supplied [approach]'s [Approach.transitions] */
    private fun parseApproachTransition(data: List<String>, approach: Approach) {
        approach.transitions.add(Pair(data[1], parseLegs(data.subList(2, data.size), Route.Leg.APP_TRANS)))
    }

    /** Parse the given [data] into missed approach procedure legs data, and adds it to the supplied [approach]'s [Approach.missedLegs] */
    private fun parseApproachMissed(data: List<String>, approach: Approach) {
        if (approach.missedLegs.legs.isNotEmpty()) {
            Gdx.app.log("GameLoader", "Multiple missed approach procedures for approach: ${approach.entity[ApproachInfo.mapper]?.approachName}")
        }
        approach.missedLegs.extendRoute(parseLegs(data.subList(1, data.size), Route.Leg.MISSED_APP))
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
                    parseLeg(legType, dataStream, flightPhase)?.apply { legArray.legs.add(this) }
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
        parseLeg(legType, dataStream, flightPhase)?.apply { legArray.legs.add(this) }

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
        val belowAltRegex = " B(-?\\d+) ".toRegex() // Altitude values of at least 1 digit, with "B" as a prefix
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
            "HOLD" -> {
                val wptName = wptRegex.find(data)?.groupValues?.get(1) ?: return null
                val publishedHold = GAME.gameServer?.publishedHolds?.get(wptName)?.entity?.get(PublishedHoldInfo.mapper) ?: return null
                return Route.HoldLeg(wptName, publishedHold.maxAltFt, publishedHold.minAltFt, publishedHold.maxSpdKtLower, publishedHold.maxSpdKtHigher, publishedHold.inboundHdgDeg, publishedHold.legDistNm, publishedHold.turnDir)
            }
            else -> {
                if (legType.isNotEmpty()) Gdx.app.log("GameLoader", "Unknown leg type: $legType")
                return null
            }
        }
    }
}