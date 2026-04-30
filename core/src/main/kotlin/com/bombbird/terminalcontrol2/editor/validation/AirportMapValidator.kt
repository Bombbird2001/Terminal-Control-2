package com.bombbird.terminalcontrol2.editor.validation

import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import kotlin.math.abs
import kotlin.math.sqrt

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
        for (i in 0 until map.waypoints.size) {
            val a = map.waypoints[i]
            for (j in i + 1 until map.waypoints.size) {
                val b = map.waypoints[j]
                val dx = a.positionNm.xNm - b.positionNm.xNm
                val dy = a.positionNm.yNm - b.positionNm.yNm
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= 0.01f) warn("Waypoints too close: ${a.name} and ${b.name} (dist=${"%.3f".format(dist)}nm)")
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
        for (a in map.airports) {
            if (!airportIdSet.add(a.id)) err("Duplicate AIRPORT id: ${a.id}")
            if (!airportIcaoSet.add(a.icao)) err("Duplicate AIRPORT ICAO: ${a.icao}")
            if (!a.icao.matches(Regex("^[A-Z]{4}$"))) warn("AIRPORT ICAO should be 4 letters (got ${a.icao})")

            val runwayNames = a.runways.map { it.name }.toHashSet()
            for ((r1, r2) in a.dependentOppositeRunways) {
                if (r1 !in runwayNames || r2 !in runwayNames) err("DEPENDENT_OPPOSITE references missing runway(s): $r1 $r2")
            }
            for ((r1, r2) in a.crossingRunways) {
                if (r1 !in runwayNames || r2 !in runwayNames) err("CROSSING references missing runway(s): $r1 $r2")
            }
            for (cfg in a.runwayConfigs) {
                for (r in cfg.departureRunways) if (r !in runwayNames) err("CONFIG ${cfg.id} DEP references missing runway: $r")
                for (r in cfg.arrivalRunways) if (r !in runwayNames) err("CONFIG ${cfg.id} ARR references missing runway: $r")
            }
        }

        // Min-alt sectors basic sanity
        for (s in map.minAltSectors) {
            when (s) {
                is MinAltPolygonSectorDefinition -> if (s.verticesNm.size < 3) warn("MIN_ALT_SECTORS polygon has <3 vertices")
                is MinAltCircleSectorDefinition -> if (s.radiusNm <= 0f) warn("MIN_ALT_SECTORS circle radius must be > 0")
            }
        }

        return problems
    }
}

