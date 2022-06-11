package com.bombbird.terminalcontrol2.files

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.navigation.UsabilityFilter
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.utilities.AircraftTypeData
import com.bombbird.terminalcontrol2.utilities.byte
import com.bombbird.terminalcontrol2.utilities.nmToPx
import ktx.ashley.get
import ktx.assets.toInternalFile
import ktx.collections.toGdxArray

/** Loads the "aircraft.perf" file located in the "Data" subfolder in the assets */
fun loadAircraftData() {
    "Data/aircraft.perf".toInternalFile().readString().split("\\r?\\n".toRegex()).toTypedArray().apply {
        for (line in this) {
            val lineData = line.trim().split(" ")
            val type = lineData[0]
            val wakeCat = lineData[1][0]
            val recat = lineData[2][0]
            val jetThrust = lineData[3].toInt()
            val propPower = lineData[4].toInt()
            val propArea = lineData[5].toFloat()
            val minCd0RefArea = lineData[6].toFloat()
            val maxCdRefArea = lineData[7].toFloat()
            val maxIas = lineData[8].toShort()
            val maxMach = lineData[9].toFloat()
            val typAppSpd = lineData[10].toShort()
            val typVr = lineData[11].toShort()
            val oew = lineData[12].toInt()
            val mtow = lineData[13].toInt()
            AircraftTypeData.addAircraftPerf(type, AircraftTypeData.AircraftPerfData(wakeCat, recat,
                jetThrust, propPower, propArea,
                minCd0RefArea, maxCdRefArea,
                maxIas, maxMach, typAppSpd, typVr,
                oew, mtow))
        }
    }
}

/** Loads the "[mainName].arpt" file located in the "Airports" subfolder in the assets */
fun loadWorldData(mainName: String, gameServer: GameServer) {
    "Airports/$mainName.arpt".toInternalFile().readString().split("\\r?\\n".toRegex()).toTypedArray().apply {
        var parseMode = ""
        var currAirport: Airport? = null
        var currSid: SidStar.SID? = null
        var currStar: SidStar.STAR? = null
        var currSectorCount = 0.byte
        var currApp: Approach? = null
        for (line in this) {
            val lineData = line.trim().split(" ")
            when (lineData[0]) {
                "AIRPORT" -> currAirport = parseAirport(lineData, gameServer)
                "/AIRPORT" -> {
                    currAirport = null
                    currSid = null
                }
                "WINDDIR" -> if (currAirport != null) parseWindDir(lineData, currAirport)
                "WINDSPD" -> if (currAirport != null) parseWindSpd(lineData, currAirport)
                "VISIBILITY" -> if (currAirport != null) parseVisibility(lineData, currAirport)
                "CEILING" -> if (currAirport != null) parseCeiling(lineData, currAirport)
                "WINDSHEAR" -> if (currAirport != null) parseWindshear(lineData, currAirport)
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
                "LOC" -> if (currApp != null) parseAppLocalizer(lineData, currApp)
                "GS" -> if (currApp != null) parseAppGlideslope(lineData, currApp)
                "STEPDOWN" -> if (currApp != null) parseAppStepDown(lineData, currApp)
                "LINEUP" -> if (currApp != null) parseAppLineUp(lineData, currApp)
                "CIRCLING" -> if (currApp != null) parseCircling(lineData, currApp)
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
                            if (currSectorCount == 0.byte) currSectorCount = lineData[0].toByte()
                            else parseSector(lineData, currSectorCount, gameServer)
                        }
                        "" -> when (lineData[0]) {
                            "MIN_ALT" -> MIN_ALT = lineData[1].toInt()
                            "MAX_ALT" -> MAX_ALT = lineData[1].toInt()
                            "INTERMEDIATE_ALTS" -> INTERMEDIATE_ALTS.apply {
                                clear()
                                addAll(lineData.subList(1, lineData.size).map { it.toInt() }.toGdxArray())
                                sort()
                            }
                            "TRANS_ALT" -> TRANS_ALT = lineData[1].toInt()
                            "TRANS_LVL" -> TRANS_LVL = lineData[1].toInt()
                            "MIN_SEP" -> MIN_SEP = lineData[1].toFloat()
                            "MAG_HDG_DEV" -> MAG_HDG_DEV = lineData[1].toFloat()
                            else -> {
                                if (lineData[0] in arrayOf("WAYPOINTS", "HOLDS", "MIN_ALT_SECTORS", "SHORELINE", "RWYS", "SECTORS"))
                                    parseMode = lineData[0]
                                else if (lineData[0] != "") Gdx.app.log("GameLoader", "Unknown parse mode ${lineData[0]}")
                            }
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

/**
 * Parse the given [data] into a [Sector], and adds it to [GameServer.sectors]
 * @param data the line array for the sector
 * @param currSectorCount the number of players this sector configuration is for
 * @param gameServer the [GameServer] to add this sector to
 * */
private fun parseSector(data: List<String>, currSectorCount: Byte, gameServer: GameServer) {
    val id = data[0].toByte()
    val freq = data[1]
    val callsign = data[2]
    val polygon = ArrayList<Short>()
    for (i in 3 until data.size) {
        val pos = data[i].split(",")
        polygon.add(nmToPx(pos[0].toFloat()).toInt().toShort())
        polygon.add(nmToPx(pos[1].toFloat()).toInt().toShort())
    }
    val sector = Sector(id, "", freq, callsign, polygon.toShortArray(), onClient = false)
    if (currSectorCount == 1.byte) gameServer.primarySector.vertices = polygon.map { it.toFloat() }.toFloatArray()
    if (!gameServer.sectors.containsKey(currSectorCount)) gameServer.sectors.put(currSectorCount, Array<Sector>().apply { add(sector) })
    else gameServer.sectors[currSectorCount].add(sector)
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
    gameServer.shoreline.add(Shoreline(polygon.toShortArray(), false))
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
    if (data.size != 8) Gdx.app.log("GameLoader", "Airport data has ${data.size} elements instead of 8")
    val id = data[1].toByte()
    val icao = data[2]
    val name = data[3]
    val ratio = data[4].toByte()
    val pos = data[5].split(",")
    val posX = nmToPx(pos[0].toFloat())
    val posY = nmToPx(pos[1].toFloat())
    val elevation = data[6].toShort()
    val realLifeIcao = data[7]
    val arpt = Airport(id, icao, name, ratio, posX, posY, elevation, false).apply {
        setMetarRealLifeIcao(realLifeIcao)
    }
    gameServer.airports.put(id, arpt)
    gameServer.updatedAirportMapping.put(icao, id)
    return arpt
}

/** Parse the given [data] into a runway, and adds it to the supplied [airport] */
private fun parseRunway(data: List<String>, airport: Airport) {
    if (data.size != 10) Gdx.app.log("GameLoader", "Windshear data has ${data.size} elements instead of 10")
    val id = data[0].toByte()
    val name = data[1]
    val pos = data[2].split(",")
    val posX = nmToPx(pos[0].toFloat())
    val posY = nmToPx(pos[1].toFloat())
    val trueHdg = data[3].toFloat()
    val rwyLengthM = data[4].toShort()
    val displacedThresholdLengthM = data[5].toShort()
    val elevation = data[6].toShort()
    val labelPos = when (data[7]) {
        "LABEL_BEFORE" -> RunwayLabel.BEFORE
        "LABEL_RIGHT" -> RunwayLabel.RIGHT
        "LABEL_LEFT" -> RunwayLabel.LEFT
        else -> {
            Gdx.app.log("GameLoader", "Unknown runway label placement for runway $name: ${data[6]}")
            RunwayLabel.BEFORE
        }
    }
    val towerCallsign = data[8].replace("-", " ")
    val towerFreq = data[9]
    airport.addRunway(id, name, posX, posY, trueHdg, rwyLengthM, displacedThresholdLengthM, elevation, towerCallsign, towerFreq, labelPos)
    airport.setRunwayMapping(name, id)
}

/** Parse the given [data] into an [Approach], and adds it to the supplied [airport]'s [ApproachChildren] component
 *
 * Returns the constructed [Approach] or null if an invalid runway is specified
 * */
private fun parseApproach(data: List<String>, airport: Airport): Approach? {
    if (data.size != 7) Gdx.app.log("GameLoader", "Approach data has ${data.size} elements instead of 7")
    val name = data[1].replace("-", " ")
    val dayNight = when (data[2]) {
        "DAY_NIGHT" -> UsabilityFilter.DAY_AND_NIGHT
        "DAY_ONLY" -> UsabilityFilter.DAY_ONLY
        "NIGHT_ONLY" -> UsabilityFilter.NIGHT_ONLY
        else -> {
            Gdx.app.log("GameLoader", "Unknown dayNight for SID $name: ${data[2]}")
            UsabilityFilter.DAY_AND_NIGHT
        }
    }
    val arptId = airport.entity[AirportInfo.mapper]?.arptId ?: return null
    val rwyId = airport.entity[RunwayChildren.mapper]?.updatedRwyMapping?.get(data[3]) ?: run {
        Gdx.app.log("GameLoader", "Runway ${data[3]} not found for approach $name")
        return null
    }
    val pos = data[4].split(",")
    val posX = nmToPx(pos[0].toFloat())
    val posY = nmToPx(pos[1].toFloat())
    val decisionAltitude = data[5].toShort()
    val rvr = data[6].toShort()
    val app = Approach(name, arptId, rwyId, posX, posY, decisionAltitude, rvr, false, dayNight)
    airport.entity[ApproachChildren.mapper]?.approachMap?.put(name, app)
    return app
}

/**
 * Parse the given data into localizer data, and adds it to the input approach
 * @param data the data containing localizer information
 * @param approach the approach to add the localizer to
 * */
private fun parseAppLocalizer(data: List<String>, approach: Approach) {
    if (data.size != 3) Gdx.app.log("GameLoader", "Localizer data has ${data.size} elements instead of 3")
    val heading = data[1].toShort()
    val locDistNm = data[2].toByte()
    approach.addLocalizer(heading, locDistNm)
}

/**
 * Parse the given data into glideslope data, and adds it to the input approach
 * @param data the data containing glideslope information
 * @param approach the approach to add the glideslope to
 * */
private fun parseAppGlideslope(data: List<String>, approach: Approach) {
    if (data.size != 4) Gdx.app.log("GameLoader", "Glideslope data has ${data.size} elements instead of 4")
    val angleDeg = data[1].toFloat()
    val offsetNm = data[2].toFloat()
    val maxInterceptAltFt = data[3].toShort()
    approach.addGlideslope(angleDeg, offsetNm, maxInterceptAltFt)
}

/**
 * Parse the given data into step-down procedure data, and adds it to the input approach
 * @param data the data containing step-down information
 * @param approach the approach to add the step-down procedure to
 * */
private fun parseAppStepDown(data: List<String>, approach: Approach) {
    val steps = ArrayList<Pair<Float, Short>>()
    for (i in 1 until data.size) {
        val step = data[i].split("@")
        steps.add(Pair(step[1].toFloat(), step[0].toShort()))
    }
    steps.sortBy { it.first }
    approach.addStepDown(steps.toTypedArray())
}

/**
 * Parse the given data into line-up distance data, and adds it to the input approach
 * @param data the data containing line-up distance data
 * @param approach the approach to add the line-up distance to
 */
private fun parseAppLineUp(data: List<String>, approach: Approach) {
    if (data.size != 2) Gdx.app.log("GameLoader", "Lineup data has ${data.size} elements instead of 2")
    approach.addLineUpDist(data[1].toFloat())
}

/**
 * Parse the given data into circling approach data, and adds it to the input approach
 * @param data the data containing the circling approach data
 * @param approach the approach to add the circling approach data to
 */
private fun parseCircling(data: List<String>, approach: Approach) {
    if (data.size != 4) Gdx.app.log("GameLoader", "Circling data has ${data.size} elements instead of 4")
    val minBreakoutAlt = data[1].toInt()
    val maxBreakoutAlt = data[2].toInt()
    val turnDir = when (data[3]) {
        "LEFT" -> CommandTarget.TURN_LEFT
        "RIGHT" -> CommandTarget.TURN_RIGHT
        else -> {
            Gdx.app.log("GameLoader", "Unknown circling breakout turn direction for ${data[0]}")
            CommandTarget.TURN_LEFT
        }
    }
    approach.addCircling(minBreakoutAlt, maxBreakoutAlt, turnDir)
}

/** Parse the given [data] into the route legs data, and adds it to the supplied [approach]'s [Approach.routeLegs] */
private fun parseApproachRoute(data: List<String>, approach: Approach) {
    if (approach.routeLegs.size > 0) {
        Gdx.app.log("GameLoader", "Multiple routes for approach: ${approach.entity[ApproachInfo.mapper]?.approachName}")
    }
    approach.routeLegs.extendRoute(parseLegs(data.subList(1, data.size), Route.Leg.APP))
}

/** Parse the given [data] into approach transition legs data, and adds it to the supplied [approach]'s [Approach.transitions] */
private fun parseApproachTransition(data: List<String>, approach: Approach) {
    approach.transitions.put(data[1], parseLegs(data.subList(2, data.size), Route.Leg.APP_TRANS))
}

/**
 * Parse the given [data] into missed approach procedure legs data, and adds it to the supplied [approach]'s [Approach.missedLegs]
 * @param data the line array for the missed approach legs
 * @param approach the [Approach] to add the legs to
 * */
private fun parseApproachMissed(data: List<String>, approach: Approach) {
    if (approach.missedLegs.size > 0) {
        Gdx.app.log("GameLoader", "Multiple missed approach procedures for approach: ${approach.entity[ApproachInfo.mapper]?.approachName}")
    }
    approach.missedLegs.add(Route.DiscontinuityLeg(Route.Leg.MISSED_APP))
    approach.missedLegs.extendRoute(parseLegs(data.subList(1, data.size), Route.Leg.MISSED_APP))
}

/**
 * Parse the given [data] into a [SidStar.SID], and adds it to the supplied [airport]'s [SIDChildren] component
 * @param data the line array for the legs
 * @return the constructed [SidStar.SID]
 * */
private fun parseSID(data: List<String>, airport: Airport): SidStar.SID {
    if (data.size != 4) Gdx.app.log("GameLoader", "SID data has ${data.size} elements instead of 4")
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

/**
 * Parse the given [data] into a [SidStar.STAR], and adds it to the supplied [airport]'s [STARChildren] component
 * @param data the line array for the legs
 * @param airport the airport that this STAR belongs to
 * @return the constructed [SidStar.STAR]
 * */
private fun parseSTAR(data: List<String>, airport: Airport): SidStar.STAR {
    if (data.size != 4) Gdx.app.log("GameLoader", "STAR data has ${data.size} elements instead of 4")
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

/**
 * Parse the given [data] into runway availability to the STAR, and adds it to the supplied STAR's runway legs
 *
 * [SidStar.STAR.rwyLegs]'s values will contain an empty route, since this is used only to mark the runways where
 * this STAR can be used
 * @param data the line array for the sector
 * @param star the STAR to add the runway to
 * */
private fun parseSTARRwyRoute(data: List<String>, star: SidStar.STAR) {
    val rwy = data[1]
    star.rwyLegs.put(rwy, Route())
}

/**
 * Parse the given [data] into route legs data, and adds it to the supplied [sidStar]'s [SidStar.routeLegs]
 * @param data the line array for the legs
 * @param sidStar the [SidStar] to add the legs to
 * */
private fun parseSIDSTARRoute(data: List<String>, sidStar: SidStar) {
    if (sidStar.routeLegs.size > 0) {
        Gdx.app.log("GameLoader", "Multiple routes for SID/STAR: ${sidStar.name}")
    }
    sidStar.routeLegs.extendRoute(parseLegs(data.subList(1, data.size), Route.Leg.NORMAL))
}

/** Parse the given [data] into the route data for the inbound/outbound segment of the SID/STAR respectively, and adds it to the supplied [sidStar]'s [SidStar.routeLegs] */
private fun parseSIDSTARinOutboundRoute(data: List<String>, sidStar: SidStar) {
    sidStar.addToInboundOutboundLegs(parseLegs(data.subList(1, data.size), Route.Leg.NORMAL))
}

/**
 * Parse the given [data], [flightPhase] into a route and returns it
 * @param data the line array for the legs
 * @param flightPhase the phase of flight for this specific set of legs
 * @return a [Route] containing the legs
 * */
private fun parseLegs(data: List<String>, flightPhase: Byte): Route {
    val route = Route()
    var legType = ""
    var dataStream = ""
    for (part in data) {
        when (part) {
            "INITCLIMB", "HDNG", "WYPT", "HOLD" -> {
                parseLeg(legType, dataStream, flightPhase)?.apply { route.add(this) }
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
    parseLeg(legType, dataStream, flightPhase)?.apply { route.add(this) }

    return route
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
            return Route.InitClimbLeg(hdg, minAlt, flightPhase)
        }
        "HDNG" -> {
            val hdg = hdgRegex.find(data)?.groupValues?.get(1)?.toInt()?.toShort() ?: return null
            val turnDir = dirRegex.find(data)?.let {
                when (it.groupValues[1]) {
                    "LEFT" -> CommandTarget.TURN_LEFT
                    "RIGHT" -> CommandTarget.TURN_RIGHT
                    else -> {
                        Gdx.app.log("GameLoader", "Unknown turn direction for HDG ${it.groupValues[0]}")
                        CommandTarget.TURN_DEFAULT
                    }
                }
            } ?: CommandTarget.TURN_DEFAULT
            return Route.VectorLeg(hdg, turnDir, flightPhase)
        }
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
            return Route.HoldLeg(wptName, publishedHold.maxAltFt, publishedHold.minAltFt, publishedHold.maxSpdKtLower, publishedHold.maxSpdKtHigher, publishedHold.inboundHdgDeg, publishedHold.legDistNm, publishedHold.turnDir, flightPhase)
        }
        else -> {
            if (legType.isNotEmpty()) Gdx.app.log("GameLoader", "Unknown leg type: $legType")
            return null
        }
    }
}

/**
 * Parse the given data into wind direction chances data for the input airport, and adds it to the airport entity's
 * [RandomMetarInfo] component
 * @param data the line array containing wind direction cumulative distribution data
 * @param airport the airport to add the data to
 * */
private fun parseWindDir(data: List<String>, airport: Airport) {
    if (data.size != 38) Gdx.app.log("GameLoader", "Wind direction data has ${data.size} elements instead of 38")
    airport.entity[RandomMetarInfo.mapper]?.apply {
        for (i in 1 until data.size) {
            windDirDist.add((i * 10 - 10).toShort(), data[i].toFloat())
        }
        windDirDist.generateNormalized()
    }
}

/**
 * Parse the given data into wind speed chances data for the input airport, and adds it to the airport entity's
 * [RandomMetarInfo] component
 * @param data the line array containing wind speed cumulative distribution data
 * @param airport the airport to add the data to
 * */
private fun parseWindSpd(data: List<String>, airport: Airport) {
    if (data.size < 32) Gdx.app.log("GameLoader", "Wind speed data has only ${data.size} elements; recommended at least 32")
    airport.entity[RandomMetarInfo.mapper]?.apply {
        for (i in 1 until data.size) {
            windSpdDist.add((i - 1).toShort(), data[i].toFloat())
        }
        windSpdDist.generateNormalized()
    }
}

/**
 * Parse the given data into visibility chances data for the input airport, and adds it to the airport entity's
 * [RandomMetarInfo] component
 * @param data the line array containing visibility cumulative distribution data
 * @param airport the airport to add the data to
 * */
private fun parseVisibility(data: List<String>, airport: Airport) {
    if (data.size != 21) Gdx.app.log("GameLoader", "Visibility data has ${data.size} elements instead of 21")
    airport.entity[RandomMetarInfo.mapper]?.apply {
        for (i in 1 until data.size) {
            visibilityDist.add((i * 500).toShort(), data[i].toFloat())
        }
        visibilityDist.generateNormalized()
    }
}

/**
 * Parse the given data into ceiling chances data for the input airport, and adds it to the airport entity's
 * [RandomMetarInfo] component
 * @param data the line array containing ceiling cumulative distribution data
 * @param airport the airport to add the data to
 */
private fun parseCeiling(data: List<String>, airport: Airport) {
    if (data.size != 16) Gdx.app.log("GameLoader", "Ceiling data has ${data.size} elements instead of 16")
    airport.entity[RandomMetarInfo.mapper]?.apply {
        for (i in 1 until data.size) {
            val hundredFtDist = shortArrayOf(-1, 0, 1, 2, 5, 10, 20, 30, 50, 80, 120, 170, 230, 300, 380)
            ceilingDist.add(hundredFtDist[i - 1], data[i].toFloat())
        }
        ceilingDist.generateNormalized()
    }
}

/**
 * Parse the given data into windshear chance data for the input airport, and adds it to the airport entity's
 * [RandomMetarInfo] component
 * @param data the line array containing windshear logistic curve coefficients
 * @param airport the airport to add the data to
 * */
private fun parseWindshear(data: List<String>, airport: Airport) {
    if (data.size != 3) Gdx.app.log("GameLoader", "Windshear data has ${data.size} elements instead of 3")
    airport.entity[RandomMetarInfo.mapper]?.apply {
        windshearLogCoefficients = Pair(data[1].toFloat(), data[2].toFloat())
    }
}
