package com.bombbird.terminalcontrol2.utilities

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.MAX_ALT
import com.bombbird.terminalcontrol2.navigation.ClearanceState
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.SidStar
import com.bombbird.terminalcontrol2.navigation.UsabilityFilter
import com.bombbird.terminalcontrol2.networking.GameServer
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.plusAssign
import ktx.collections.GdxArray
import kotlin.math.min

/**
 * Chooses a random SID from all SIDs available for the input runway
 *
 * This also takes into account any noise restrictions
 * @param rwy the runway to use
 * @return the [SidStar.SID] chosen
 * */
private fun randomSid(rwy: Entity): SidStar.SID? {
    val currentTime = UsabilityFilter.DAY_ONLY // TODO change depending on whether night operations are active
    val availableSids = GdxArray<SidStar.SID>()
    val rwyName = rwy[RunwayInfo.mapper]?.rwyName
    rwy[RunwayInfo.mapper]?.airport?.entity?.get(SIDChildren.mapper)?.sidMap?.values()?.forEach { sid ->
        // Add to list of eligible SIDs if both runway and time restriction checks passes
        if (!sid.rwyInitialClimbs.containsKey(rwyName)) return@forEach
        if (sid.timeRestriction != UsabilityFilter.DAY_AND_NIGHT && sid.timeRestriction != currentTime) return@forEach
        availableSids.add(sid)
    }

    if (availableSids.isEmpty) {
        Gdx.app.log("TrafficTools", "No SID available for runway $rwyName")
        return null
    }
    return availableSids.random()
}

/**
 * Creates a new departure aircraft on the input runway
 * @param rwy the runway the departure aircraft will be using
 * @param gs the gameServer to instantiate the aircraft in
 * */
fun createDeparture(rwy: Entity, gs: GameServer) {
    val rwyPos = rwy[Position.mapper]
    val rwyDir = rwy[Direction.mapper]

    gs.aircraft.put("SHIBA2", Aircraft("SHIBA2", rwyPos?.x ?: 10f, rwyPos?.y ?: -10f, rwy[Altitude.mapper]?.altitudeFt ?: 108f, "B77W", FlightType.DEPARTURE, false).apply {
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg((rwyDir?.trackUnitVector?.angleDeg() ?: 0f) - 90) // Runway heading
        // Calculate headwind component for takeoff
        val headwind = entity[Altitude.mapper]?.let { alt -> rwyDir?.let { dir -> entity[Position.mapper]?.let { pos ->
            val wind = getClosestAirportWindVector(pos.x, pos.y)
            calculateIASFromTAS(alt.altitudeFt, pxpsToKt(wind.dot(dir.trackUnitVector)))
        }}} ?: 0f
        entity[Speed.mapper]?.speedKts = -headwind
        val acPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: return@apply
        entity += TakeoffRoll(calculateRequiredAcceleration(0, (acPerf.vR + headwind).toInt().toShort(), ((rwy[RunwayInfo.mapper]?.lengthM ?: 3800) - 1000) * MathUtils.random(0.75f, 1f)))
        val sid = randomSid(rwy)
        val rwyName = rwy[RunwayInfo.mapper]?.rwyName ?: ""
        val initClimb = sid?.rwyInitialClimbs?.get(rwyName) ?: 3000
        entity += ClearanceAct(ClearanceState.ActingClearance(
                ClearanceState(sid?.name ?: "", sid?.getRandomSIDRouteForRunway(rwyName) ?: Route(), Route(),
            null, null, initClimb, acPerf.climbOutSpeed)
            ))
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = initClimb
            targetIasKt = acPerf.climbOutSpeed
            targetHdgDeg = convertWorldAndRenderDeg(rwyDir?.trackUnitVector?.angleDeg() ?: 90f) + MAG_HDG_DEV
        }
    })
}

/**
 * Chooses a random STAR from all STARs available for the input airport
 *
 * This also takes into account any noise restrictions
 * @param airport the airport to use
 * @return the [SidStar.STAR] chosen
 * */
private fun randomStar(airport: Entity): SidStar.STAR? {
    val currentTime = UsabilityFilter.DAY_ONLY // TODO change depending on whether night operations are active
    val availableStars = GdxArray<SidStar.STAR>()
    val runwaysAvailable = HashSet<String>()
    airport[RunwayChildren.mapper]?.rwyMap?.values()?.forEach { rwy ->
        // Add names of runways that are active for landing to set
        if (rwy.entity.has(ActiveLanding.mapper)) runwaysAvailable.add(rwy.entity[RunwayInfo.mapper]?.rwyName ?: return@forEach)
    }
    airport[STARChildren.mapper]?.starMap?.values()?.forEach { star ->
        // Add to list of eligible SIDs if both runway and time restriction checks passes
        if ((star.rwyLegs.keys() union runwaysAvailable).isEmpty()) return@forEach
        if (star.timeRestriction != UsabilityFilter.DAY_AND_NIGHT && star.timeRestriction != currentTime) return@forEach
        availableStars.add(star)
    }

    if (availableStars.isEmpty) {
        Gdx.app.log("TrafficTools", "No STAR available for ${airport[AirportInfo.mapper]?.name}")
        return null
    }
    return availableStars.random()
}

/**
 * Creates a new arrival aircraft for the input arrival
 * @param airport the airport the arrival is flying into
 * @param gs the gameServer to instantiate the aircraft in
 * */
fun createArrival(airport: Entity, gs: GameServer) {
    val randomStar = randomStar(airport)
    val starRoute = randomStar?.getRandomSTARRouteForRunway() ?: Route()
    val origStarRoute = Route().apply { setToRouteCopy(starRoute) }
    val spawnPos = calculateArrivalSpawnPoint(starRoute, gs.primarySector)

    gs.aircraft.put("SHIBA3", Aircraft("SHIBA3", spawnPos.first, spawnPos.second, 0f, "B77W", FlightType.ARRIVAL, false).apply {
        val alt = calculateArrivalSpawnAltitude(entity, airport, origStarRoute, spawnPos.first, spawnPos.second, starRoute)
        entity[Altitude.mapper]?.altitudeFt = alt
        entity[Direction.mapper]?.trackUnitVector?.rotateDeg(-spawnPos.third - 180)
        val aircraftPerf = entity[AircraftInfo.mapper]?.aircraftPerf ?: AircraftTypeData.AircraftPerfData()
        val ias = calculateArrivalSpawnIAS(origStarRoute, starRoute, alt, aircraftPerf)
        val tas = calculateTASFromIAS(alt, ias.toFloat())
        entity[Speed.mapper]?.apply {
            speedKts = tas
            vertSpdFpm = calculateMinVerticalSpd(aircraftPerf, alt, tas, 0f, approachExpedite = false, takingOff = false)
        }
        val clearedAlt = min(15000, (alt / 1000).toInt() * 1000)
        entity += ClearanceAct(ClearanceState.ActingClearance(
            ClearanceState(randomStar?.name ?: "", starRoute, Route(),
                if (starRoute.legs.isEmpty) (spawnPos.third + MAG_HDG_DEV).toInt().toShort() else null, null,
                clearedAlt, ias)
        ))
        entity[CommandTarget.mapper]?.apply {
            targetAltFt = clearedAlt
            targetIasKt = ias
            targetHdgDeg = modulateHeading(spawnPos.third + 180)
        }
        entity += ContactFromCentre(MAX_ALT + MathUtils.random(400, 1500))
        entity += InitialArrivalSpawn()
        if (alt > 10000) entity += DecelerateTo240kts()
    })
}
