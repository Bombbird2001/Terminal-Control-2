package com.bombbird.terminalcontrol2.editor.validation

import com.bombbird.terminalcontrol2.editor.MapEditorFieldConstraints
import com.bombbird.terminalcontrol2.editor.undo.MapEditorByteIds
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import com.bombbird.terminalcontrol2.editor.route.collectRouteParseWarnings
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.utilities.calculateDistanceBetweenPoints

data class ValidationProblem(
    val severity: Severity,
    val message: String,
) {
    enum class Severity { ERROR, WARNING }
}

object AirportMapValidator {
    fun validate(map: AirportMapDefinition): List<ValidationProblem> {
        val problems = mutableListOf<ValidationProblem>()

        fun err(msg: String) = problems.add(ValidationProblem(ValidationProblem.Severity.ERROR, msg))
        fun warn(msg: String) = problems.add(ValidationProblem(ValidationProblem.Severity.WARNING, msg))

        // Globals (subset of DataFileTest constraints)
        val minAlt = map.globals.minAltFt
        val maxAlt = map.globals.maxAltFt
        if (minAlt !in 500..16500 || minAlt % 100 != 0) err("MIN_ALT must be 500..16500 and a multiple of 100 (got $minAlt)")
        if (maxAlt !in 15000..26000 || maxAlt % 100 != 0) err("MAX_ALT must be 15000..26000 and a multiple of 100 (got $maxAlt)")
        if (maxAlt <= minAlt) err("MAX_ALT must be greater than MIN_ALT (got $maxAlt <= $minAlt)")
        if (map.globals.transitionAltitudeFt !in minAlt..maxAlt || map.globals.transitionAltitudeFt % 100 != 0) {
            warn("TRANS_ALT should be within [MIN_ALT, MAX_ALT] and a multiple of 100 (got ${map.globals.transitionAltitudeFt})")
        }

        // Waypoints
        val idSet = HashSet<Short>()
        val nameSet = HashSet<String>()
        for (w in map.waypoints) {
            if (!idSet.add(w.id)) err("Duplicate waypoint id: ${w.id}")
            if (!nameSet.add(w.name)) err("Duplicate waypoint name: ${w.name}")
        }
        // Very rough proximity check (O(n^2), but map sizes are manageable)
        for ((i, wpt1) in map.waypoints.withIndex()) {
            for (j in i + 1 until map.waypoints.size) {
                val wpt2 = map.waypoints[j]
                val dist = calculateDistanceBetweenPoints(
                    wpt1.positionNm.xNm,
                    wpt1.positionNm.yNm,
                    wpt2.positionNm.xNm,
                    wpt2.positionNm.yNm,
                )
                if (dist <= 0.01f) warn("Waypoints too close: ${wpt1.name} and ${wpt2.name} (dist=${"%.3f".format(dist)}nm)")
            }
        }

        // Holds reference existing waypoint
        val wptNames = map.waypoints.map { it.name }.toHashSet()
        for (h in map.publishedHolds) {
            if (h.waypointName !in wptNames) err("Hold references unknown waypoint: ${h.waypointName}")
        }

        // Airports
        val airportIdSet = HashSet<Byte>()
        val airportIcaoSet = HashSet<String>()
        val airportNameSet = HashSet<String>()
        val towerFreqRegex = Regex("^1\\d{2}\\.\\d{1,3}$")
        if (MapEditorByteIds.nextAirportId(map) == null) err("Max airport count reached")

        val approachNameOccurrences = HashMap<String, MutableList<String>>()

        for (a in map.airports) {
            if (!airportIdSet.add(a.id)) err("Duplicate AIRPORT id: ${a.id}")
            if (!airportIcaoSet.add(a.icao)) err("Duplicate AIRPORT ICAO: ${a.icao}")
            if (!airportNameSet.add(a.name)) err("Duplicate AIRPORT name: ${a.name}")
            if (!a.icao.matches(Regex("^[A-Z]{4}$"))) warn("AIRPORT ICAO should be 4 letters (got ${a.icao})")
            if (a.elevationFt < MapEditorFieldConstraints.MIN_ELEVATION_FT) {
                err("AIRPORT ${a.icao} elevation must be >= ${MapEditorFieldConstraints.MIN_ELEVATION_FT} ft (got ${a.elevationFt})")
            }

            val runwayIds = HashSet<Byte>()
            val runwayNames = HashSet<String>()
            for (r in a.runways) {
                if (!runwayIds.add(r.id)) err("Duplicate runway id ${r.id} at airport ${a.icao}")
                if (!runwayNames.add(r.name)) err("Duplicate runway name '${r.name}' at airport ${a.icao}")
                if (r.lengthM !in MapEditorFieldConstraints.RUNWAY_LENGTH_M_RANGE) {
                    err("Runway ${r.name} at ${a.icao}: length must be in ${MapEditorFieldConstraints.RUNWAY_LENGTH_M_RANGE} m (got ${r.lengthM})")
                }
                if (r.displacedThresholdM < 0 || r.displacedThresholdM > r.lengthM) {
                    err("Runway ${r.name} at ${a.icao}: displaced threshold must be 0..length (${r.lengthM} m) (got ${r.displacedThresholdM})")
                }
                if (r.intersectionTakeoffLengthM < 0 || r.intersectionTakeoffLengthM > r.lengthM) {
                    err("Runway ${r.name} at ${a.icao}: intersection takeoff must be 0..length (${r.lengthM} m) (got ${r.intersectionTakeoffLengthM})")
                }
                if (!MapEditorFieldConstraints.isValidStoredRunwayTrueHeadingDeg(r.trueHeadingDeg)) {
                    err("Runway ${r.name} at ${a.icao}: true heading must be in (0, 360] deg (got ${r.trueHeadingDeg})")
                }
                if (r.thresholdElevationFt < MapEditorFieldConstraints.MIN_ELEVATION_FT) {
                    err("Runway ${r.name} at ${a.icao}: threshold elevation must be >= ${MapEditorFieldConstraints.MIN_ELEVATION_FT} ft (got ${r.thresholdElevationFt})")
                }
                if (!towerFreqRegex.matches(r.towerFrequency.trim())) {
                    warn("Tower frequency should match 1XX.d–ddd (got '${r.towerFrequency}' for ${a.icao} rwy ${r.name})")
                }
            }
            if (MapEditorByteIds.nextRunwayId(a) == null) warn("No free runway id (0–127) left for airport ${a.icao}")
            if (MapEditorByteIds.nextRunwayConfigId(a) == null) warn("No free runway configuration id (0–127) left for airport ${a.icao}")

            val runwayNamesForRefs = a.runways.map { it.name }.toHashSet()
            for ((r1, r2) in a.dependentOppositeRunways) {
                if (r1 !in runwayNamesForRefs || r2 !in runwayNamesForRefs) err("DEPENDENT_OPPOSITE references missing runway(s): $r1 $r2")
            }
            for ((r1, r2) in a.crossingRunways) {
                if (r1 !in runwayNamesForRefs || r2 !in runwayNamesForRefs) err("CROSSING references missing runway(s): $r1 $r2")
            }
            for (cfg in a.runwayConfigs) {
                for (r in cfg.departureRunways) {
                    if (r !in runwayNamesForRefs) warn("CONFIG ${cfg.id} DEP references unknown runway: $r at ${a.icao}")
                }
                for (r in cfg.arrivalRunways) {
                    if (r !in runwayNamesForRefs) warn("CONFIG ${cfg.id} ARR references unknown runway: $r at ${a.icao}")
                }
                val depSeen = HashSet<String>()
                for (r in cfg.departureRunways) {
                    if (!depSeen.add(r)) warn("CONFIG ${cfg.id} DEP lists duplicate runway: $r at ${a.icao}")
                }
                val arrSeen = HashSet<String>()
                for (r in cfg.arrivalRunways) {
                    if (!arrSeen.add(r)) warn("CONFIG ${cfg.id} ARR lists duplicate runway: $r at ${a.icao}")
                }
            }

            for (ap in a.approaches) {
                approachNameOccurrences.getOrPut(ap.name) { mutableListOf() }
                    .add("${a.icao} rwy ${ap.runwayName}")
                if (ap.runwayName !in runwayNamesForRefs) {
                    warn("Approach '${ap.name}' at ${a.icao}: runway '${ap.runwayName}' not found")
                }
                if (ap.glideslopeEnabled && ap.stepDownEnabled && ap.glideslope != null && ap.stepDownFixes.isNotEmpty()) {
                    warn("Approach '${ap.name}' at ${a.icao}: glideslope and step-down fixes are mutually exclusive")
                }
                fun reportRoute(ctx: String, tokens: List<String>, phase: Byte) {
                    if (tokens.isEmpty()) return
                    for (msg in collectRouteParseWarnings(tokens, phase, map)) {
                        warn("$ctx '${ap.name}' at ${a.icao}: $msg")
                    }
                }
                reportRoute("Approach route", ap.routeTokens, Route.Leg.APP)
                if (ap.missedApproachTokens.isNotEmpty()) {
                    reportRoute("Missed approach", ap.missedApproachTokens, Route.Leg.MISSED_APP)
                }
                for ((tName, tToks) in ap.transitions) {
                    if (tName.isBlank()) warn("Approach '${ap.name}' at ${a.icao}: blank transition name")
                    reportRoute("Transition $tName", tToks, Route.Leg.APP_TRANS)
                }
                val cfgIds = a.runwayConfigs.map { it.id }.toHashSet()
                for (cid in ap.allowedRunwayConfigIds) {
                    if (cid !in cfgIds) {
                        warn("Approach '${ap.name}' at ${a.icao}: ALLOWED_CONFIGS references unknown config id $cid")
                    }
                }
            }
        }

        for ((name, occurrences) in approachNameOccurrences) {
            if (occurrences.size > 1) {
                warn("Duplicate approach name '$name' at: ${occurrences.joinToString(", ")}")
            }
        }

        // Min-alt sectors basic sanity
        for (s in map.minAltSectors) {
            when (s) {
                is MinAltPolygonSectorDefinition -> {
                    if (s.verticesNm.size < 3) warn("MIN_ALT_SECTORS polygon has <3 vertices")
                    val alt = s.minAltitudeFt
                    if (alt != null && !MapEditorFieldConstraints.isValidMinAltSectorFt(alt)) {
                        err("MIN_ALT_SECTORS polygon min altitude must be ${MapEditorFieldConstraints.MIN_ALT_SECTOR_FT_MIN}..${MapEditorFieldConstraints.MIN_ALT_SECTOR_FT_MAX} ft and a multiple of 100 (got $alt)")
                    }
                }
                is MinAltCircleSectorDefinition -> {
                    if (s.radiusNm <= 0f) warn("MIN_ALT_SECTORS circle radius must be > 0")
                    val alt = s.minAltitudeFt
                    if (alt != null && !MapEditorFieldConstraints.isValidMinAltSectorFt(alt)) {
                        err("MIN_ALT_SECTORS circle min altitude must be ${MapEditorFieldConstraints.MIN_ALT_SECTOR_FT_MIN}..${MapEditorFieldConstraints.MIN_ALT_SECTOR_FT_MAX} ft and a multiple of 100 (got $alt)")
                    }
                }
            }
        }

        return problems
    }
}

