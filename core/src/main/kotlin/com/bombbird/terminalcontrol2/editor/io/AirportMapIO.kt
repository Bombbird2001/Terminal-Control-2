package com.bombbird.terminalcontrol2.editor.io

import com.bombbird.terminalcontrol2.editor.model.*
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.toLines
import kotlin.math.max

/**
 * Parser/serializer for the legacy `.arpt` + `.desc` airport map formats.
 *
 * This is editor-focused: it produces an [AirportMapDefinition] that keeps coordinates in nm.
 *
 * Notes:
 * - String parsing is intentionally tolerant (to support in-progress edits).
 * - For now, procedure routes are stored as token streams (see [RouteTokens]).
 */
object AirportMapIO {
    fun parseArpt(text: String, onWarning: (String) -> Unit = { FileLog.info("AirportMapIO", it) }): AirportMapDefinition {
        val map = AirportMapDefinition()
        var parseMode: String? = null
        var currAirport: AirportDefinition? = null
        var currConfig: RunwayConfigDefinition? = null
        var currSid: SidDefinition? = null
        var currStar: StarDefinition? = null
        var currApproach: ApproachDefinition? = null
        var currNozGroup: ApproachNozGroupDefinition? = null
        var currSectorCount: Byte = 0

        fun warn(msg: String) = onWarning(msg)

        for ((lineNo, rawLine) in text.toLines().withIndex()) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) continue

            val parts = line.split(" ")
            val head = parts[0]

            when (head) {
                "MAX_PLAYERS" -> map.globals.maxPlayers = parts.getOrNull(1)?.toIntOrNull() ?: map.globals.maxPlayers
                "MIN_ALT" -> map.globals.minAltFt = parts.getOrNull(1)?.toIntOrNull() ?: map.globals.minAltFt
                "MAX_ALT" -> map.globals.maxAltFt = parts.getOrNull(1)?.toIntOrNull() ?: map.globals.maxAltFt
                "INTERMEDIATE_ALTS" -> {
                    map.globals.intermediateAltitudesFt.clear()
                    for (i in 1 until parts.size) parts[i].toIntOrNull()?.let { map.globals.intermediateAltitudesFt.add(it) }
                }
                "TRANS_ALT" -> map.globals.transitionAltitudeFt = parts.getOrNull(1)?.toIntOrNull() ?: map.globals.transitionAltitudeFt
                "TRANS_LVL" -> map.globals.transitionLevelFl = parts.getOrNull(1)?.toIntOrNull() ?: map.globals.transitionLevelFl
                "MIN_SEP" -> map.globals.minSepNm = parts.getOrNull(1)?.toFloatOrNull() ?: map.globals.minSepNm
                "MAG_HDG_DEV" -> map.globals.magHdgDevDeg = parts.getOrNull(1)?.toFloatOrNull() ?: map.globals.magHdgDevDeg
                "MAX_ARRIVALS" -> map.globals.maxArrivals = parts.getOrNull(1)?.toIntOrNull() ?: map.globals.maxArrivals

                // Block open / close
                "WAYPOINTS/" -> parseMode = "WAYPOINTS"
                "/WAYPOINTS" -> parseMode = null
                "SECTORS/" -> parseMode = "SECTORS"
                "/SECTORS" -> {
                    parseMode = null
                    currSectorCount = 0
                }
                "ACC_SECTORS/" -> parseMode = "ACC_SECTORS"
                "/ACC_SECTORS" -> parseMode = null
                "MIN_ALT_SECTORS/" -> parseMode = "MIN_ALT_SECTORS"
                "/MIN_ALT_SECTORS" -> parseMode = null
                "SHORELINE/" -> parseMode = "SHORELINE"
                "/SHORELINE" -> parseMode = null
                "HOLDS/" -> parseMode = "HOLDS"
                "/HOLDS" -> parseMode = null

                // Airport blocks
                "AIRPORT/" -> {
                    // AIRPORT/ <id> <icao> <name> <ratio> <maxAdvanceDeps> <x>,<y> <elev> <realLifeIcao>
                    if (parts.size < 9) {
                        warn("Line ${lineNo + 1}: AIRPORT header has ${parts.size} tokens, expected 9+")
                        continue
                    }
                    val idParsed = parts[1].toByteOrNull()
                    if (idParsed == null) {
                        warn("Line ${lineNo + 1}: invalid airport id")
                        continue
                    }
                    val id = idParsed
                    val icao = parts[2]
                    val name = parts[3]
                    val ratio = parts[4].toByteOrNull() ?: 0
                    val maxAdv = parts[5].toIntOrNull() ?: 0
                    val pos = parseNmPoint(parts[6], warn = { warn("Line ${lineNo + 1}: $it") })
                    if (pos == null) continue
                    val elev = parts[7].toShortOrNull() ?: 0
                    val weatherIcao = parts[8]
                    currAirport = AirportDefinition(id, icao, name, ratio, maxAdv, pos, elev, weatherIcao)
                    map.airports.add(currAirport!!)
                    currConfig = null
                    currSid = null
                    currStar = null
                    currApproach = null
                    currNozGroup = null
                }
                "/AIRPORT" -> {
                    currAirport = null
                    currConfig = null
                    currSid = null
                    currStar = null
                    currApproach = null
                    currNozGroup = null
                }

                // Airport-local block modes
                "RWYS/" -> parseMode = "RWYS"
                "/RWYS" -> parseMode = null
                "DEPENDENT_OPPOSITE/" -> parseMode = "DEPENDENT_OPPOSITE"
                "/DEPENDENT_OPPOSITE" -> parseMode = null
                "CROSSING/" -> parseMode = "CROSSING"
                "/CROSSING" -> parseMode = null
                "DEPARTURE_DEPEND/" -> parseMode = "DEPARTURE_DEPEND"
                "/DEPARTURE_DEPEND" -> parseMode = null
                "DEP_NOZ/" -> parseMode = "DEP_NOZ"
                "/DEP_NOZ" -> parseMode = null
                "TRAFFIC/" -> parseMode = "TRAFFIC"
                "/TRAFFIC" -> parseMode = null

                // Config blocks
                "CONFIG/" -> {
                    if (parts.size < 3) {
                        warn("Line ${lineNo + 1}: CONFIG header too short")
                        continue
                    }
                    val arpt = currAirport
                    if (arpt == null) {
                        warn("Line ${lineNo + 1}: CONFIG outside AIRPORT")
                        continue
                    }
                    val id = parts[1].toByteOrNull() ?: 0
                    val timeSlot = parseTimeSlot(parts[2], warn = { warn("Line ${lineNo + 1}: $it") })
                    currConfig = RunwayConfigDefinition(id, timeSlot)
                    arpt.runwayConfigs.add(currConfig!!)
                }
                "/CONFIG" -> currConfig = null
                "NAME" -> currConfig?.name = parts.drop(1).joinToString(" ").trim()
                "DEP" -> currConfig?.departureRunways?.apply { clear(); addAll(parts.drop(1)) }
                "ARR" -> currConfig?.arrivalRunways?.apply { clear(); addAll(parts.drop(1)) }
                "NTZ" -> {
                    val cfg = currConfig ?: continue
                    if (parts.size < 5) {
                        warn("Line ${lineNo + 1}: NTZ too short")
                        continue
                    }
                    val pos = parseNmPoint(parts[1], warn = { warn("Line ${lineNo + 1}: $it") })
                    if (pos == null) continue
                    val hdg = parts[2].toFloatOrNull() ?: 0f
                    val width = parts[3].toFloatOrNull() ?: 0f
                    val len = parts[4].toFloatOrNull() ?: 0f
                    cfg.ntz.add(NoTransgressionZoneDefinition(pos, hdg, width, len))
                }
                "DEPENDENT_PARALLEL_DEP" -> {
                    val cfg = currConfig ?: continue
                    if (parts.size < 3) {
                        warn("Line ${lineNo + 1}: DEPENDENT_PARALLEL_DEP too short")
                        continue
                    }
                    cfg.dependentParallelDeparturePairs.add(Pair(parts[1], parts[2]))
                }

                // SID/STAR/APCH blocks
                "SID/" -> {
                    if (parts.size < 4) {
                        warn("Line ${lineNo + 1}: SID header too short")
                        continue
                    }
                    val arpt = currAirport
                    if (arpt == null) {
                        warn("Line ${lineNo + 1}: SID outside AIRPORT")
                        continue
                    }
                    val name = parts[1]
                    val timeSlot = parseTimeSlot(parts[2], warn = { warn("Line ${lineNo + 1}: $it") })
                    val pron = parts.drop(3).joinToString(" ").replace("-", " ").trim()
                    currSid = SidDefinition(name, timeSlot, pron)
                    arpt.sids.add(currSid!!)
                }
                "/SID" -> currSid = null
                "STAR/" -> {
                    if (parts.size < 4) {
                        warn("Line ${lineNo + 1}: STAR header too short")
                        continue
                    }
                    val arpt = currAirport
                    if (arpt == null) {
                        warn("Line ${lineNo + 1}: STAR outside AIRPORT")
                        continue
                    }
                    val name = parts[1]
                    val timeSlot = parseTimeSlot(parts[2], warn = { warn("Line ${lineNo + 1}: $it") })
                    val pron = parts.drop(3).joinToString(" ").replace("-", " ").trim()
                    currStar = StarDefinition(name, timeSlot, pron)
                    arpt.stars.add(currStar!!)
                }
                "/STAR" -> currStar = null
                "APCH/" -> {
                    if (parts.size < 7) {
                        warn("Line ${lineNo + 1}: APCH header too short")
                        continue
                    }
                    val arpt = currAirport
                    if (arpt == null) {
                        warn("Line ${lineNo + 1}: APCH outside AIRPORT")
                        continue
                    }
                    val name = parts[1].replace("-", " ")
                    val timeSlot = parseTimeSlot(parts[2], warn = { warn("Line ${lineNo + 1}: $it") })
                    val rwy = parts[3]
                    val pos = parseNmPoint(parts[4], warn = { warn("Line ${lineNo + 1}: $it") })
                    if (pos == null) continue
                    val da = parts[5].toShortOrNull() ?: 0
                    val rvr = parts[6].toShortOrNull() ?: 0
                    currApproach = ApproachDefinition(name, timeSlot, rwy, pos, da, rvr)
                    arpt.approaches.add(currApproach!!)
                }
                "/APCH" -> currApproach = null

                // Approach NOZ groups
                "APP_NOZ/" -> {
                    val arpt = currAirport
                    if (arpt == null) {
                        warn("Line ${lineNo + 1}: APP_NOZ outside AIRPORT")
                        continue
                    }
                    currNozGroup = ApproachNozGroupDefinition()
                    arpt.approachNozGroups.add(currNozGroup!!)
                }
                "/APP_NOZ" -> currNozGroup = null
                "ZONE" -> {
                    val group = currNozGroup
                    if (group == null) {
                        warn("Line ${lineNo + 1}: ZONE outside APP_NOZ")
                        continue
                    }
                    if (parts.size < 6) {
                        warn("Line ${lineNo + 1}: ZONE too short")
                        continue
                    }
                    val pos = parseNmPoint(parts[1], warn = { warn("Line ${lineNo + 1}: $it") })
                    if (pos == null) continue
                    val hdg = parts[2].toFloatOrNull() ?: 0f
                    val width = parts[3].toFloatOrNull() ?: 0f
                    val len = parts[4].toFloatOrNull() ?: 0f
                    val appNames = parts.drop(5).map { it.replace("-", " ") }.toMutableList()
                    group.zones.add(ApproachNozZoneDefinition(pos, hdg, width, len, appNames))
                }

                // Airport-local scalar headers
                "WINDDIR" -> currAirport?.metar?.windDirCumulative?.apply { clear(); addAll(parts.drop(1).mapNotNull { it.toFloatOrNull() }) }
                "WINDSPD" -> currAirport?.metar?.windSpdCumulative?.apply { clear(); addAll(parts.drop(1).mapNotNull { it.toFloatOrNull() }) }
                "VISIBILITY" -> currAirport?.metar?.visibilityCumulative?.apply { clear(); addAll(parts.drop(1).mapNotNull { it.toFloatOrNull() }) }
                "CEILING" -> currAirport?.metar?.ceilingCumulative?.apply { clear(); addAll(parts.drop(1).mapNotNull { it.toFloatOrNull() }) }
                "WINDSHEAR" -> {
                    val a = parts.getOrNull(1)?.toFloatOrNull()
                    val b = parts.getOrNull(2)?.toFloatOrNull()
                    if (a != null && b != null) currAirport?.metar?.windshearLogCoefficients = Pair(a, b)
                }
                "CUSTOM_APP_SEP" -> {
                    val arpt = currAirport
                    if (arpt == null) {
                        warn("Line ${lineNo + 1}: CUSTOM_APP_SEP outside AIRPORT")
                        continue
                    }
                    if (parts.size < 4) {
                        warn("Line ${lineNo + 1}: CUSTOM_APP_SEP too short")
                        continue
                    }
                    val g1 = parts[1].split(",").map { it.replace("-", " ") }
                    val g2 = parts[2].split(",").map { it.replace("-", " ") }
                    val sep = parts[3].toFloatOrNull() ?: 0f
                    arpt.customApproachSeparations.add(CustomApproachSeparationDefinition(g1, g2, sep))
                }
                "DEPRECATED" -> {
                    // Apply to current most specific scope; we only model this at airport/config/runway/procedure/approach level.
                    currApproach?.deprecated = true
                    currSid?.deprecated = true
                    currStar?.deprecated = true
                    currConfig?.deprecated = true
                    currAirport?.deprecated = true
                }

                // Procedure inner statements
                "RWY" -> {
                    if (currSid != null) {
                        if (parts.size < 4) {
                            warn("Line ${lineNo + 1}: SID RWY line too short")
                            continue
                        }
                        val rwy = parts[1]
                        val initClimb = parts[2].toIntOrNull() ?: 0
                        val tokens = parts.drop(3)
                        currSid!!.runwaySegments[rwy] = SidStarRunwaySegmentDefinition(initClimb, tokens)
                    } else if (currStar != null) {
                        val rwy = parts.getOrNull(1) ?: continue
                        currStar!!.runwayAvailability.add(rwy)
                    } else {
                        // Approaches use RWY in header, not inside.
                    }
                }
                "ROUTE" -> {
                    val tokens = parts.drop(1)
                    when {
                        currSid != null -> currSid!!.routeTokens = tokens
                        currStar != null -> currStar!!.routeTokens = tokens
                        currApproach != null -> currApproach!!.routeTokens = tokens
                    }
                }
                "OUTBOUND" -> currSid?.let { it.outboundTokens = parts.drop(1) }
                "INBOUND" -> currStar?.let { it.inboundTokens = parts.drop(1) }
                "ALLOWED_CONFIGS" -> {
                    val ids = parts.drop(1).mapNotNull { it.toByteOrNull() }
                    when {
                        currSid != null -> currSid!!.allowedRunwayConfigIds.apply { clear(); addAll(ids) }
                        currStar != null -> currStar!!.allowedRunwayConfigIds.apply { clear(); addAll(ids) }
                        currApproach != null -> currApproach!!.allowedRunwayConfigIds.apply { clear(); addAll(ids) }
                    }
                }
                "LOC" -> currApproach?.let {
                    if (parts.size >= 3) {
                        val hdg = parts[1].toFloatOrNull() ?: 0f
                        val dist = parts[2].toByteOrNull() ?: 0
                        it.localizer = LocalizerDefinition(hdg, dist)
                    }
                }
                "GS" -> currApproach?.let {
                    if (parts.size >= 4) {
                        val angle = parts[1].toFloatOrNull() ?: 0f
                        val offset = parts[2].toFloatOrNull() ?: 0f
                        val maxAlt = parts[3].toShortOrNull() ?: 0
                        it.glideslope = GlideslopeDefinition(angle, offset, maxAlt)
                    }
                }
                "STEPDOWN" -> currApproach?.let {
                    for (i in 1 until parts.size) {
                        val seg = parts[i].split("@", limit = 2)
                        if (seg.size != 2) continue
                        val alt = seg[0].toShortOrNull() ?: continue
                        val dist = seg[1].toFloatOrNull() ?: continue
                        it.stepDownFixes.add(StepDownFixDefinition(alt, dist))
                    }
                    it.stepDownFixes.sortBy { s -> s.distanceNm }
                }
                "LINEUP" -> currApproach?.lineupDistanceNm = parts.getOrNull(1)?.toFloatOrNull()
                "CIRCLING" -> currApproach?.let {
                    if (parts.size >= 4) {
                        val minAlt = parts[1].toIntOrNull() ?: 0
                        val maxAlt = parts[2].toIntOrNull() ?: 0
                        val dir = parseTurnDir(parts[3], warn = { warn("Line ${lineNo + 1}: $it") })
                        it.circling = CirclingDefinition(minAlt, maxAlt, dir)
                    }
                }
                "TRANSITION" -> currApproach?.let {
                    if (parts.size >= 3) it.transitions[parts[1]] = parts.drop(2)
                }
                "MISSED" -> currApproach?.missedApproachTokens = parts.drop(1)
                "WAKE_INHIBIT" -> currApproach?.wakeInhibitApproachNames?.apply {
                    clear()
                    addAll(parts.drop(1).map { it.replace("-", " ") })
                }
                "PARALLEL_WAKE_AFFECTS" -> currApproach?.let {
                    if (parts.size >= 3) it.parallelWakeAffects.add(ParallelWakeAffectsDefinition(parts[1].replace("-", " "), parts[2].toFloatOrNull() ?: 0f))
                }
                "VIS_AFTER_FAF" -> currApproach?.visualAfterFaf = true

                else -> {
                    // Dispatch by parse mode for data lines.
                    when (parseMode) {
                        "WAYPOINTS" -> {
                            // <id> <name> <x>,<y>
                            if (parts.size < 3) {
                                warn("Line ${lineNo + 1}: waypoint too short")
                                continue
                            }
                            val id = parts[0].toShortOrNull() ?: continue
                            val name = parts[1]
                            val pos = parseNmPoint(parts[2], warn = { warn("Line ${lineNo + 1}: $it") }) ?: continue
                            map.waypoints.add(WaypointDefinition(id, name, pos))
                        }
                        "SECTORS" -> {
                            // Nested sector count tags like "1/" and "/1"
                            if (head.endsWith("/") && head.dropLast(1).toByteOrNull() != null) {
                                currSectorCount = head.dropLast(1).toByte()
                                continue
                            }
                            if (head.startsWith("/") && head.drop(1).toByteOrNull() != null) {
                                currSectorCount = 0
                                continue
                            }
                            if (currSectorCount == 0.toByte()) continue
                            if (parts.size < 6) {
                                warn("Line ${lineNo + 1}: sector too short")
                                continue
                            }
                            val freq = parts[0]
                            val arrCs = parts[1].replace("-", " ")
                            val depCs = parts[2].replace("-", " ")
                            val verts = parts.drop(3).mapNotNull { parseNmPoint(it, warn = { warn("Line ${lineNo + 1}: $it") }) }.toMutableList()
                            map.sectorsByPlayerCount.getOrPut(currSectorCount) { mutableListOf() }.add(SectorDefinition(freq, arrCs, depCs, verts))
                        }
                        "ACC_SECTORS" -> {
                            if (parts.size < 4) {
                                warn("Line ${lineNo + 1}: ACC sector too short")
                                continue
                            }
                            val freq = parts[0]
                            val callsign = parts[1].replace("-", " ")
                            val verts = parts.drop(2).mapNotNull { parseNmPoint(it, warn = { warn("Line ${lineNo + 1}: $it") }) }.toMutableList()
                            map.accSectors.add(AccSectorDefinition(freq, callsign, verts))
                        }
                        "MIN_ALT_SECTORS" -> {
                            // POLYGON|CIRCLE MVA|RESTR <alt|UNL> ...
                            if (parts.size < 4) {
                                warn("Line ${lineNo + 1}: min alt sector too short")
                                continue
                            }
                            val shape = parts[0]
                            val rType = when (parts[1]) {
                                "MVA" -> MinAltRestrictionType.MVA
                                "RESTR" -> MinAltRestrictionType.RESTR
                                else -> {
                                    warn("Line ${lineNo + 1}: unknown restriction type ${parts[1]}")
                                    MinAltRestrictionType.MVA
                                }
                            }
                            val alt = if (parts[2] == "UNL") null else parts[2].toIntOrNull()
                            when (shape) {
                                "POLYGON" -> {
                                    var label: NmPoint? = null
                                    val verts = mutableListOf<NmPoint>()
                                    for (i in 3 until parts.size) {
                                        val tok = parts[i]
                                        if (tok.startsWith("LABEL,")) {
                                            val labelParts = tok.split(",")
                                            if (labelParts.size == 3) {
                                                val lx = labelParts[1].toFloatOrNull()
                                                val ly = labelParts[2].toFloatOrNull()
                                                if (lx != null && ly != null) label = NmPoint(lx, ly)
                                            }
                                            continue
                                        }
                                        parseNmPoint(tok, warn = { warn("Line ${lineNo + 1}: $it") })?.let { verts.add(it) }
                                    }
                                    map.minAltSectors.add(MinAltPolygonSectorDefinition(rType, alt, verts, label))
                                }
                                "CIRCLE" -> {
                                    if (parts.size < 5) {
                                        warn("Line ${lineNo + 1}: circle min alt sector too short")
                                        continue
                                    }
                                    val center = parseNmPoint(parts[3], warn = { warn("Line ${lineNo + 1}: $it") }) ?: continue
                                    val radius = parts[4].toFloatOrNull() ?: 0f
                                    map.minAltSectors.add(MinAltCircleSectorDefinition(rType, alt, center, radius))
                                }
                                else -> warn("Line ${lineNo + 1}: unknown min alt sector shape $shape")
                            }
                        }
                        "SHORELINE" -> {
                            val pts = parts.mapNotNull { parseNmPoint(it, warn = { warn("Line ${lineNo + 1}: $it") }) }.toMutableList()
                            if (pts.isNotEmpty()) map.shorelinePolylines.add(PolylineDefinition(pts))
                        }
                        "HOLDS" -> {
                            if (parts.size < 6) {
                                warn("Line ${lineNo + 1}: hold too short")
                                continue
                            }
                            val wpt = parts[0]
                            val inbound = parts[1].toShortOrNull() ?: 0
                            val legDist = parts[2].toByteOrNull() ?: 0
                            val spdLo = parts[3].toShortOrNull() ?: 0
                            val spdHi = parts[4].toShortOrNull() ?: 0
                            val dir = parseTurnDir(parts[5], warn = { warn("Line ${lineNo + 1}: $it") })
                            val altTok = parts.getOrNull(6)
                            map.publishedHolds.add(HoldDefinition(wpt, inbound, legDist, spdLo, spdHi, dir, altTok))
                        }
                        "RWYS" -> {
                            val arpt = currAirport ?: continue
                            if (parts.size < 11) {
                                warn("Line ${lineNo + 1}: runway too short")
                                continue
                            }
                            val id = parts[0].toByteOrNull() ?: 0
                            val name = parts[1]
                            val pos = parseNmPoint(parts[2], warn = { warn("Line ${lineNo + 1}: $it") }) ?: continue
                            val hdg = parts[3].toFloatOrNull() ?: 0f
                            val lenM = parts[4].toShortOrNull() ?: 0
                            val disp = parts[5].toShortOrNull() ?: 0
                            val intTo = parts[6].toShortOrNull() ?: 0
                            val elev = parts[7].toShortOrNull() ?: 0
                            val label = parseLabel(parts[8], warn = { warn("Line ${lineNo + 1}: $it") })
                            val towerCs = parts[9].replace("-", " ")
                            val towerFreq = parts[10]
                            arpt.runways.add(RunwayDefinition(id, name, pos, hdg, lenM, disp, intTo, elev, label, towerCs, towerFreq))
                        }
                        "DEPENDENT_OPPOSITE" -> currAirport?.dependentOppositeRunways?.add(Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: ""))
                        "CROSSING" -> if (parts.size >= 2) currAirport?.crossingRunways?.add(Pair(parts[0], parts[1]))
                        "DEPARTURE_DEPEND" -> {
                            val arpt = currAirport ?: continue
                            if (parts.isEmpty()) continue
                            val depRwy = parts[0]
                            arpt.departureDependencies.add(DepartureDependencyDefinition(depRwy, parts.drop(1).toMutableList()))
                        }
                        "DEP_NOZ" -> {
                            val arpt = currAirport ?: continue
                            if (parts.size < 5) continue
                            val rwy = parts[0]
                            val pos = parseNmPoint(parts[1], warn = { warn("Line ${lineNo + 1}: $it") }) ?: continue
                            val hdg = parts[2].toShortOrNull() ?: 0
                            val width = parts[3].toFloatOrNull() ?: 0f
                            val len = parts[4].toFloatOrNull() ?: 0f
                            arpt.departureNoz.add(DepartureNozDefinition(rwy, pos, hdg, width, len))
                        }
                        "TRAFFIC" -> currAirport?.trafficLines?.add(line)
                        null -> {
                            // Unknown top-level token
                        }
                        else -> warn("Line ${lineNo + 1}: unknown parse mode $parseMode for line '$line'")
                    }
                }
            }
        }

        // Normalize: sort intermediate alts.
        map.globals.intermediateAltitudesFt.sort()
        return map
    }

    fun serializeArpt(map: AirportMapDefinition): String {
        val out = StringBuilder()
        fun ln(s: String = "") { out.append(s).append('\n') }

        ln("MAX_PLAYERS ${map.globals.maxPlayers}")
        ln("MIN_ALT ${map.globals.minAltFt}")
        ln("MAX_ALT ${map.globals.maxAltFt}")
        ln(
            "INTERMEDIATE_ALTS" + if (map.globals.intermediateAltitudesFt.isEmpty()) "" else " " + map.globals.intermediateAltitudesFt.joinToString(" ")
        )
        ln("TRANS_ALT ${map.globals.transitionAltitudeFt}")
        ln("TRANS_LVL ${map.globals.transitionLevelFl}")
        ln("MIN_SEP ${trimFloat(map.globals.minSepNm)}")
        ln("MAG_HDG_DEV ${trimFloat(map.globals.magHdgDevDeg)}")
        ln("MAX_ARRIVALS ${map.globals.maxArrivals}")
        ln()

        ln("WAYPOINTS/")
        map.waypoints.sortedBy { it.id }.forEach {
            ln("${it.id} ${it.name} ${formatNmPoint(it.positionNm)}")
        }
        ln("/WAYPOINTS")
        ln()

        ln("SECTORS/")
        map.sectorsByPlayerCount.keys.sorted().forEach { cnt ->
            ln("$cnt/")
            map.sectorsByPlayerCount[cnt].orEmpty().forEach { sec ->
                ln("${sec.frequency} ${sec.arrivalCallsign.replace(" ", "-")} ${sec.departureCallsign.replace(" ", "-")} " +
                    sec.verticesNm.joinToString(" ") { formatNmPoint(it) })
            }
            ln("/$cnt")
        }
        ln("/SECTORS")
        ln()

        ln("ACC_SECTORS/")
        map.accSectors.forEach { sec ->
            ln("${sec.frequency} ${sec.callsign.replace(" ", "-")} " + sec.verticesNm.joinToString(" ") { formatNmPoint(it) })
        }
        ln("/ACC_SECTORS")
        ln()

        ln("MIN_ALT_SECTORS/")
        map.minAltSectors.forEach { sector ->
            when (sector) {
                is MinAltPolygonSectorDefinition -> {
                    val altTok = sector.minAltitudeFt?.toString() ?: "UNL"
                    val labelTok = sector.labelPositionNm?.let { "LABEL,${trimFloat(it.xNm)},${trimFloat(it.yNm)} " } ?: ""
                    ln("POLYGON ${sector.restrictionType.name} $altTok $labelTok" +
                        sector.verticesNm.joinToString(" ") { formatNmPoint(it) })
                }
                is MinAltCircleSectorDefinition -> {
                    val altTok = sector.minAltitudeFt?.toString() ?: "UNL"
                    ln("CIRCLE ${sector.restrictionType.name} $altTok ${formatNmPoint(sector.centerNm)} ${trimFloat(sector.radiusNm)}")
                }
            }
        }
        ln("/MIN_ALT_SECTORS")
        ln()

        ln("SHORELINE/")
        map.shorelinePolylines.forEach { pl ->
            ln(pl.pointsNm.joinToString(" ") { formatNmPoint(it) })
        }
        ln("/SHORELINE")
        ln()

        ln("HOLDS/")
        map.publishedHolds.forEach { h ->
            val altTok = h.altitudeRestrictionToken?.let { " $it" } ?: ""
            ln("${h.waypointName} ${h.inboundHeadingDeg} ${h.legDistanceNm} ${h.maxSpeedLowerKt} ${h.maxSpeedHigherKt} ${h.turnDirection.name}$altTok")
        }
        ln("/HOLDS")
        ln()

        for (arpt in map.airports.sortedBy { it.id }) {
            ln("AIRPORT/ ${arpt.id} ${arpt.icao} ${arpt.name} ${arpt.ratio} ${arpt.maxAdvanceDepartures} ${formatNmPoint(arpt.positionNm)} ${arpt.elevationFt} ${arpt.realLifeWeatherIcao}")
            ln()

            if (arpt.metar.windDirCumulative.isNotEmpty()) ln("WINDDIR ${arpt.metar.windDirCumulative.joinToString(" ") { trimFloat(it) }}")
            if (arpt.metar.windSpdCumulative.isNotEmpty()) ln("WINDSPD ${arpt.metar.windSpdCumulative.joinToString(" ") { trimFloat(it) }}")
            if (arpt.metar.visibilityCumulative.isNotEmpty()) ln("VISIBILITY ${arpt.metar.visibilityCumulative.joinToString(" ") { trimFloat(it) }}")
            if (arpt.metar.ceilingCumulative.isNotEmpty()) ln("CEILING ${arpt.metar.ceilingCumulative.joinToString(" ") { trimFloat(it) }}")
            arpt.metar.windshearLogCoefficients?.let { (a, b) -> ln("WINDSHEAR ${trimFloat(a)} ${trimFloat(b)}") }
            ln()

            ln("RWYS/")
            arpt.runways.sortedBy { it.id }.forEach { r ->
                ln("${r.id} ${r.name} ${formatNmPoint(r.thresholdNm)} ${trimFloat(r.trueHeadingDeg)} ${r.lengthM} ${r.displacedThresholdM} ${r.intersectionTakeoffLengthM} ${r.thresholdElevationFt} ${r.labelPlacement.name} ${r.towerCallsign.replace(" ", "-")} ${r.towerFrequency}")
            }
            ln("/RWYS")
            ln()

            ln("DEPENDENT_OPPOSITE/")
            arpt.dependentOppositeRunways.forEach { (a, b) -> ln("$a $b") }
            ln("/DEPENDENT_OPPOSITE")
            ln()

            ln("CROSSING/")
            arpt.crossingRunways.forEach { (a, b) -> ln("$a $b") }
            ln("/CROSSING")
            ln()

            ln("DEPARTURE_DEPEND/")
            arpt.departureDependencies.forEach { dep ->
                ln(dep.departureRunwayName + (if (dep.rules.isEmpty()) "" else " " + dep.rules.joinToString(" ")))
            }
            ln("/DEPARTURE_DEPEND")
            ln()

            ln("DEP_NOZ/")
            arpt.departureNoz.forEach { noz ->
                ln("${noz.runwayName} ${formatNmPoint(noz.positionNm)} ${noz.headingDeg} ${trimFloat(noz.widthNm)} ${trimFloat(noz.lengthNm)}")
            }
            ln("/DEP_NOZ")
            ln()

            arpt.runwayConfigs.sortedBy { it.id }.forEach { cfg ->
                ln("CONFIG/ ${cfg.id} ${cfg.timeSlot.name}")
                if (cfg.deprecated) ln("DEPRECATED")
                ln("NAME ${cfg.name}")
                ln("DEP ${cfg.departureRunways.joinToString(" ")}")
                ln("ARR ${cfg.arrivalRunways.joinToString(" ")}")
                cfg.ntz.forEach { ntz ->
                    ln("NTZ ${formatNmPoint(ntz.positionNm)} ${trimFloat(ntz.headingDeg)} ${trimFloat(ntz.widthNm)} ${trimFloat(ntz.lengthNm)}")
                }
                cfg.dependentParallelDeparturePairs.forEach { (a, b) -> ln("DEPENDENT_PARALLEL_DEP $a $b") }
                ln("/CONFIG")
                ln()
            }

            arpt.sids.forEach { sid ->
                ln("SID/ ${sid.name} ${sid.timeSlot.name} ${sid.pronunciation.replace(" ", "-")}")
                if (sid.deprecated) ln("DEPRECATED")
                sid.runwaySegments.forEach { (rwy, seg) ->
                    ln("RWY $rwy ${seg.initialClimbFt} " + seg.routeTokens.joinToString(" "))
                }
                ln("ROUTE " + sid.routeTokens.joinToString(" "))
                ln("OUTBOUND " + sid.outboundTokens.joinToString(" "))
                ln("ALLOWED_CONFIGS " + sid.allowedRunwayConfigIds.joinToString(" "))
                ln("/SID")
                ln()
            }

            arpt.stars.forEach { star ->
                ln("STAR/ ${star.name} ${star.timeSlot.name} ${star.pronunciation.replace(" ", "-")}")
                if (star.deprecated) ln("DEPRECATED")
                star.runwayAvailability.forEach { rwy -> ln("RWY $rwy") }
                ln("ROUTE " + star.routeTokens.joinToString(" "))
                ln("INBOUND " + star.inboundTokens.joinToString(" "))
                ln("ALLOWED_CONFIGS " + star.allowedRunwayConfigIds.joinToString(" "))
                ln("/STAR")
                ln()
            }

            arpt.approaches.forEach { ap ->
                ln("APCH/ ${ap.name.replace(" ", "-")} ${ap.timeSlot.name} ${ap.runwayName} ${formatNmPoint(ap.positionNm)} ${ap.decisionAltitudeFt} ${ap.rvrM}")
                if (ap.deprecated) ln("DEPRECATED")
                ap.localizer?.let { ln("LOC ${trimFloat(it.headingDeg)} ${it.distanceNm}") }
                ap.glideslope?.let { ln("GS ${trimFloat(it.angleDeg)} ${trimFloat(it.offsetNm)} ${it.maxInterceptAltitudeFt}") }
                if (ap.stepDownFixes.isNotEmpty()) {
                    ln("STEPDOWN " + ap.stepDownFixes.joinToString(" ") { "${it.altitudeFt}@${trimFloat(it.distanceNm)}" })
                }
                ap.lineupDistanceNm?.let { ln("LINEUP ${trimFloat(it)}") }
                ap.circling?.let { ln("CIRCLING ${it.minBreakoutAltFt} ${it.maxBreakoutAltFt} ${it.turnDirection.name}") }
                ln("ROUTE " + ap.routeTokens.joinToString(" "))
                ap.transitions.forEach { (name, toks) -> ln("TRANSITION $name " + toks.joinToString(" ")) }
                if (ap.missedApproachTokens.isNotEmpty()) ln("MISSED " + ap.missedApproachTokens.joinToString(" "))
                if (ap.wakeInhibitApproachNames.isNotEmpty()) ln("WAKE_INHIBIT " + ap.wakeInhibitApproachNames.joinToString(" ") { it.replace(" ", "-") })
                ap.parallelWakeAffects.forEach { p -> ln("PARALLEL_WAKE_AFFECTS ${p.otherApproachName.replace(" ", "-")} ${trimFloat(p.distanceNm)}") }
                if (ap.visualAfterFaf) ln("VIS_AFTER_FAF")
                if (ap.allowedRunwayConfigIds.isNotEmpty()) ln("ALLOWED_CONFIGS " + ap.allowedRunwayConfigIds.joinToString(" "))
                ln("/APCH")
                ln()
            }

            arpt.approachNozGroups.forEach { g ->
                ln("APP_NOZ/")
                g.zones.forEach { z ->
                    ln("ZONE ${formatNmPoint(z.positionNm)} ${trimFloat(z.headingDeg)} ${trimFloat(z.widthNm)} ${trimFloat(z.lengthNm)} " +
                        z.approachNames.joinToString(" ") { it.replace(" ", "-") })
                }
                ln("/APP_NOZ")
                ln()
            }

            arpt.customApproachSeparations.forEach { sep ->
                val g1 = sep.group1ApproachNames.joinToString(",") { it.replace(" ", "-") }
                val g2 = sep.group2ApproachNames.joinToString(",") { it.replace(" ", "-") }
                ln("CUSTOM_APP_SEP $g1 $g2 ${trimFloat(sep.separationNm)}")
            }
            if (arpt.customApproachSeparations.isNotEmpty()) ln()

            ln("TRAFFIC/")
            arpt.trafficLines.forEach { ln(it) }
            ln("/TRAFFIC")
            ln()

            if (arpt.deprecated) ln("DEPRECATED")
            ln("/AIRPORT")
            ln()
        }

        return out.toString().trimEnd() + "\n"
    }

    fun parseDesc(text: String): String = text.trim()
    fun serializeDesc(text: String): String = text.trim() + "\n"

    private fun parseNmPoint(token: String, warn: (String) -> Unit): NmPoint? {
        val coords = token.split(",")
        if (coords.size != 2) {
            warn("Invalid coord token '$token'")
            return null
        }
        val x = coords[0].toFloatOrNull()
        val y = coords[1].toFloatOrNull()
        if (x == null || y == null) {
            warn("Invalid coord token '$token'")
            return null
        }
        return NmPoint(x, y)
    }

    private fun formatNmPoint(p: NmPoint): String = "${trimFloat(p.xNm)},${trimFloat(p.yNm)}"

    private fun parseLabel(token: String, warn: (String) -> Unit): RunwayLabelPlacement {
        return try {
            RunwayLabelPlacement.valueOf(token)
        } catch (_: IllegalArgumentException) {
            warn("Unknown runway label '$token'")
            RunwayLabelPlacement.LABEL_BEFORE
        }
    }

    private fun parseTimeSlot(token: String, warn: (String) -> Unit): TimeSlot {
        return try {
            TimeSlot.valueOf(token)
        } catch (_: IllegalArgumentException) {
            warn("Unknown time slot '$token'")
            TimeSlot.DAY_NIGHT
        }
    }

    private fun parseTurnDir(token: String, warn: (String) -> Unit): TurnDirection {
        return try {
            TurnDirection.valueOf(token)
        } catch (_: IllegalArgumentException) {
            warn("Unknown turn direction '$token'")
            TurnDirection.RIGHT
        }
    }

    private fun trimFloat(v: Float): String {
        // Keep output compact but stable.
        val s = "%.6f".format(v)
        return s.trimEnd('0').trimEnd('.').ifBlank { "0" }
    }
}

