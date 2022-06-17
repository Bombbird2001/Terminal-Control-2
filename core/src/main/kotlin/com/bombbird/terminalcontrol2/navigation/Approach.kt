package com.bombbird.terminalcontrol2.navigation

import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.utilities.Pronounceable
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.with
import ktx.collections.GdxArrayMap
import kotlin.math.roundToInt

/**
 * Approach class that stores all relevant data regarding the approach and utility functions
 *
 * [transitions] stores arrays of legs for possible transitions onto the approach
 *
 * [routeLegs] stores the main legs array common to all runways
 *
 * [missedLegs] stores the legs to be flown in the event of a go around or missed approach
 *
 * Additionally, [UsabilityFilter] is implemented to provide filtering of suitable SIDs depending on conditions;
 * [Pronounceable] is implemented to provide adjustments to text for accurate pronunciation by TTS implementations
 * */
class Approach(name: String, arptId: Byte, runwayId: Byte, posX: Float, posY: Float, decisionAlt: Short, rvr: Short,
               onClient: Boolean = true, override val timeRestriction: Byte): UsabilityFilter, Pronounceable {
    override val pronunciation: String
        get() = "" // TODO implement pronunciation based on approach name (or I might change it to a user specified pronunciation)
    val entity = getEngine(onClient).entity {
        with<ApproachInfo> {
            approachName = name
            airportId = arptId
            rwyId = runwayId
            GAME.gameServer?.airports?.get(arptId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(runwayId)?.let { rwyObj = it }
        }
        with<Position> {
            x = posX
            y = posY
        }
        with<Minimums> {
            baroAltFt = decisionAlt
            rvrM = rvr
        }
    }

    val transitions = GdxArrayMap<String, Route>(6)
    val routeLegs = Route()
    val missedLegs = Route()

    /**
     * Gets a [SerialisedApproach] from current state
     *
     * This method is abstract and must be implemented by each individual approach class
     * */
    fun getSerialisableObject(): SerialisedApproach {
        val appInfo = entity[ApproachInfo.mapper] ?: return SerialisedApproach()
        val pos = entity[Position.mapper] ?: return SerialisedApproach()
        val dir = entity[Direction.mapper]
        val loc = entity[Localizer.mapper]
        val gs = entity[GlideSlope.mapper]
        val stepDown = entity[StepDown.mapper]
        return SerialisedApproach(
            appInfo.approachName, appInfo.airportId, appInfo.rwyId, pos.x, pos.y,
            timeRestriction,
            transitions.entries().map { SerialisedTransition(it.key, it.value.getSerialisedObject()) }.toTypedArray(),
            routeLegs.getSerialisedObject(),
            missedLegs.getSerialisedObject(),
            if (loc != null && dir != null) (convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) + 180 + MAG_HDG_DEV).roundToInt().toShort() else null, loc?.maxDistNm,
            gs?.glideAngle, gs?.offsetNm, gs?.maxInterceptAlt,
            stepDown?.altAtDist?.map { SerialisedStep(it.first, it.second) }?.toTypedArray()
        )
    }

    companion object {
        /**
         * De-serialises a [SerialisedApproach] and creates a new [Approach] object from it
         * @param serialisedApproach the serialised approach object to de-serialise
         * */
        fun fromSerialisedObject(serialisedApproach: SerialisedApproach): Approach {
            return Approach(
                serialisedApproach.name, serialisedApproach.arptId, serialisedApproach.rwyId, serialisedApproach.posX, serialisedApproach.posy,
                0, 0,
                timeRestriction = serialisedApproach.timeRestriction
            ).apply {
                setLegsFromSerialisedObject(serialisedApproach)
                val locHdg = serialisedApproach.locHdg
                val locDistNm = serialisedApproach.locDistNm
                if (locHdg != null && locDistNm != null) {
                    entity += Direction(Vector2(Vector2.Y).rotateDeg(180 - (locHdg - MAG_HDG_DEV)))
                    entity += Localizer(locDistNm)
                }
                val gsAngleDeg = serialisedApproach.gsAngleDeg
                val gsOffsetNm = serialisedApproach.gsOffsetNm
                val gsMaxAlt = serialisedApproach.maxGsAlt
                val steps = serialisedApproach.steps
                if (gsAngleDeg != null && gsOffsetNm != null && gsMaxAlt != null) entity += GlideSlope(gsAngleDeg, gsOffsetNm, gsMaxAlt)
                else if (steps != null) entity += StepDown(steps.map { Pair(it.dist, it.alt) }.toTypedArray())
            }
        }
    }

    /**
     * Object that contains [Approach] data to be serialised by Kryo
     *
     * This class is abstract and is extended by SerialisedIlsGS and SerialisedIlsLOCOffset
     * */
    class SerialisedApproach(val name: String = "", val arptId: Byte = 0, val rwyId: Byte = 0, val posX: Float = 0f, val posy: Float = 0f,
                             val timeRestriction: Byte = 0,
                             val transitions: Array<SerialisedTransition> = arrayOf(),
                             val routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                             val missedLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                             val locHdg: Short? = null, val locDistNm: Byte? = null,
                             val gsAngleDeg: Float? = null, val gsOffsetNm: Float? = null, val maxGsAlt: Short? = null,
                             val steps: Array<SerialisedStep>? = null)

    /** Object that contains [transitions] data to be serialised by Kryo */
    class SerialisedTransition(val name: String = "", val route: Route.SerialisedRoute = Route.SerialisedRoute())

    /** Object that contains non-precision approach step down data to be serialised by Kryo */
    class SerialisedStep(val dist: Float = 0f, val alt: Short = 0)

    /**
     * Sets [transitions], [routeLegs] and [missedLegs] from the supplied [serialisedApproach]
     *
     * This will clear any existing route data (there should not be any data in the route to begin with, should this function be used)
     * @param serialisedApproach the serialised approach object to refer to
     * */
    fun setLegsFromSerialisedObject(serialisedApproach: SerialisedApproach) {
        transitions.clear()
        for (transLeg in serialisedApproach.transitions) transitions.put(transLeg.name, Route.fromSerialisedObject(transLeg.route))
        routeLegs.setToRoute(Route.fromSerialisedObject(serialisedApproach.routeLegs))
        missedLegs.setToRoute(Route.fromSerialisedObject(serialisedApproach.missedLegs))
    }

    /**
     * Adds a localizer to the entity of this approach
     * @param heading the track of the localizer
     * @param locDistNm the maximum localizer distance
     * */
    fun addLocalizer(heading: Short, locDistNm: Byte) {
        entity += Direction(Vector2(Vector2.Y).rotateDeg(180 - (heading - MAG_HDG_DEV)))
        entity += Localizer(locDistNm)
    }

    /**
     * Adds a glideslope to the entity of this approach
     * @param angleDeg the slope degree of the glideslope
     * @param offsetDistNm the maximum localizer distance
     * @param maxInterceptAltFt the maximum glideslope intercept altitude
     * */
    fun addGlideslope(angleDeg: Float, offsetDistNm: Float, maxInterceptAltFt: Short) {
        entity += GlideSlope(angleDeg, offsetDistNm, maxInterceptAltFt)
    }

    /**
     * Adds a glideslope to the entity of this approach
     * @param steps the sorted list of step down altitudes at distances from the localizer origin
     * */
    fun addStepDown(steps: Array<Pair<Float, Short>>) {
        entity += StepDown(steps)
    }

    /**
     * Adds a line-up distance component for the approach; should be used when the approach is offset from the runway
     * @param lineUpDist the distance from the runway threshold to start turning the plane towards the runway
     */
    fun addLineUpDist(lineUpDist: Float) {
        entity += LineUpDist(lineUpDist)
    }

    /**
     * Adds a circling approach component for the approach
     * @param minBreakoutAlt the minimum altitude for the breakout to be initiated
     * @param maxBreakoutAlt the maximum altitude for the breakout to be initiated
     * @param breakoutDir the direction of the breakout (left or right)
     */
    fun addCircling(minBreakoutAlt: Int, maxBreakoutAlt: Int, breakoutDir: Byte) {
        entity += Circling(minBreakoutAlt, maxBreakoutAlt, breakoutDir)
    }
}
