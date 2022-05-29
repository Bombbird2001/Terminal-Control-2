package com.bombbird.terminalcontrol2.navigation

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.global.getEngine
import com.bombbird.terminalcontrol2.navigation.Approach.IlsGS.SerialisedIlsGS
import com.bombbird.terminalcontrol2.navigation.Approach.IlsLOCOffset.SerialisedIlsLOCOffset
import com.bombbird.terminalcontrol2.utilities.Pronounceable
import com.bombbird.terminalcontrol2.utilities.convertWorldAndRenderDeg
import ktx.ashley.entity
import ktx.ashley.get
import ktx.ashley.plusAssign
import ktx.ashley.with
import ktx.collections.GdxArray
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
 *
 * This class is abstract and is extended by the various approach types: [IlsGS], [IlsLOCOffset]
 * */
abstract class Approach(name: String, arptId: Byte, runwayId: Byte, tower: String, towerFreq: String, onClient: Boolean = true,
               override val timeRestriction: Byte): UsabilityFilter, Pronounceable {
    override val pronunciation: String
        get() = "" // TODO implement pronunciation based on approach name (or I might change it to a user specified pronunciation)
    val entity = getEngine(onClient).entity {
        with<ApproachInfo> {
            approachName = name
            airportId = arptId
            rwyId = runwayId
            towerName = tower
            frequency = towerFreq
            GAME.gameServer?.airports?.get(arptId)?.entity?.get(RunwayChildren.mapper)?.rwyMap?.get(runwayId)?.let { rwyObj = it }
        }
    }

    val transitions = GdxArray<Pair<String, Route>>(6)
    val routeLegs = Route()
    val missedLegs = Route()

    /**
     * Gets a [SerialisedApproach] from current state
     *
     * This method is abstract and must be implemented by each individual approach class
     * */
    abstract fun getSerialisableObject(): SerialisedApproach

    companion object {
        /**
         * De-serialises a [SerialisedApproach] and creates a new [Approach] object from it
         *
         * Will invoke the specific [fromSerialisedObject] function for different type of approaches
         * @param serialisedApproach the serialised approach object to de-serialise
         * */
        fun fromSerialisedObject(serialisedApproach: SerialisedApproach): Approach {
            (serialisedApproach as? SerialisedIlsGS)?.let {
                return fromSerialisedObject(it)
            } ?: (serialisedApproach as? SerialisedIlsLOCOffset)?.let {
                return fromSerialisedObject(it)
            } ?: run {
                Gdx.app.log("Approach", "Unknown approach type provided for ${serialisedApproach.name}, returning an EmptyApproach")
                return EmptyApproach()
            }
        }

        /**
         * De-serialises a [SerialisedIlsGS] and creates a new [IlsGS] object from it
         * @param serialisedIlsGS the serialised ILS GS object to de-serialise
         * @return a new [IlsGS] object instance
         * */
        private fun fromSerialisedObject(serialisedIlsGS: SerialisedIlsGS): IlsGS {
            return IlsGS(serialisedIlsGS.name, serialisedIlsGS.arptId, serialisedIlsGS.rwyId, serialisedIlsGS.tower, serialisedIlsGS.towerFreq,
                serialisedIlsGS.heading, serialisedIlsGS.posX, serialisedIlsGS.posY, serialisedIlsGS.locDistNm,
                serialisedIlsGS.gsAngle, serialisedIlsGS.gsOffsetNm, serialisedIlsGS.gsMaxAlt,
                serialisedIlsGS.decisionAlt, serialisedIlsGS.rvr,
                serialisedIlsGS.timeRestriction).apply {
                setFromSerialisedObject(serialisedIlsGS)
            }
        }

        /**
         * De-serialises a [SerialisedIlsLOCOffset] and creates a new [IlsLOCOffset] object from it
         * @param serialisedIlsLOCOffset the serialised offset ILS LOC object to de-serialise
         * @return a new [IlsLOCOffset] object instance
         * */
        private fun fromSerialisedObject(serialisedIlsLOCOffset: SerialisedIlsLOCOffset): IlsLOCOffset {
            return IlsLOCOffset(serialisedIlsLOCOffset.name, serialisedIlsLOCOffset.arptId, serialisedIlsLOCOffset.rwyId, serialisedIlsLOCOffset.tower, serialisedIlsLOCOffset.towerFreq,
                serialisedIlsLOCOffset.heading, serialisedIlsLOCOffset.posX, serialisedIlsLOCOffset.posY, serialisedIlsLOCOffset.locDistNm,
                serialisedIlsLOCOffset.decisionAlt, serialisedIlsLOCOffset.rvr,
                serialisedIlsLOCOffset.centerlineInterceptDist, serialisedIlsLOCOffset.steps.map { Pair(it.dist, it.alt) }.toTypedArray(),
                serialisedIlsLOCOffset.timeRestriction).apply {
                setFromSerialisedObject(serialisedIlsLOCOffset)
            }
        }
    }

    /**
     * Object that contains [Approach] data to be serialised by Kryo
     *
     * This class is abstract and is extended by SerialisedIlsGS and SerialisedIlsLOCOffset
     * */
    abstract class SerialisedApproach(val name: String = "", val arptId: Byte = 0, val rwyId: Byte = 0, val tower: String = "", val towerFreq: String = "",
                                      val timeRestriction: Byte = 0,
                                      val transitions: Array<SerialisedTransition> = arrayOf(),
                                      val routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                                      val missedLegs: Route.SerialisedRoute = Route.SerialisedRoute())

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
    fun setFromSerialisedObject(serialisedApproach: SerialisedApproach) {
        transitions.clear()
        for (transLeg in serialisedApproach.transitions) transitions.add(Pair(transLeg.name, Route.fromSerialisedObject(transLeg.route)))
        routeLegs.setToRoute(Route.fromSerialisedObject(serialisedApproach.routeLegs))
        missedLegs.setToRoute(Route.fromSerialisedObject(serialisedApproach.missedLegs))
    }

    /** Empty approach that is used when de-serialising an unknown/un-implemented approach type */
    class EmptyApproach: Approach("", 0, 0, "", "", timeRestriction = UsabilityFilter.DAY_AND_NIGHT) {
        override fun getSerialisableObject(): SerialisedApproach {
            return SerialisedEmptyApproach()
        }

        /** Serialised class for [EmptyApproach]; it will likely not be used at all */
        class SerialisedEmptyApproach: SerialisedApproach()
    }

    /**
     * ILS class that stores all relevant data regarding the ILS (with glide slope) and utility functions
     *
     * [entity], [transitions], [routeLegs], [missedLegs], [UsabilityFilter] and [Pronounceable] are directly inherited from [Approach]
     * */
    class IlsGS(name: String, arptId: Byte, runwayId: Byte, tower: String, towerFreq: String,
                heading: Short, posX: Float, posY: Float, locDistNm: Byte, gsAngle: Float, gsOffsetNm: Float, gsMaxAlt: Short, decisionAlt: Short, rvr: Short,
                timeRestriction: Byte): Approach(name, arptId, runwayId, tower, towerFreq, timeRestriction = timeRestriction) {
        init {
            entity.apply {
                this += Position(posX, posY)
                this += Direction(Vector2(Vector2.Y).rotateDeg(180 - (heading - MAG_HDG_DEV)))
                this += Localizer(locDistNm)
                this += GlideSlope(gsAngle, gsOffsetNm, gsMaxAlt)
                this += Minimums(decisionAlt, rvr)
            }
        }

        /**
         * Gets a serialised ILS GS object from current state
         * @return a [SerialisedIlsGS] object representing the current state of this [IlsGS] object
         * */
        override fun getSerialisableObject(): SerialisedApproach {
            entity.apply {
                val appInfo = get(ApproachInfo.mapper) ?: return SerialisedIlsGS()
                val pos = get(Position.mapper) ?: return SerialisedIlsGS()
                val dir = get(Direction.mapper) ?: return SerialisedIlsGS()
                val loc = get(Localizer.mapper) ?: return SerialisedIlsGS()
                val gs = get(GlideSlope.mapper) ?: return SerialisedIlsGS()
                val mins = get(Minimums.mapper) ?: return SerialisedIlsGS()
                return SerialisedIlsGS(appInfo.approachName, appInfo.airportId, appInfo.rwyId, appInfo.towerName, appInfo.frequency,
                                       timeRestriction,
                                       transitions.map { SerialisedTransition(it.first, it.second.getSerialisedObject()) }.toTypedArray(),
                                       routeLegs.getSerialisedObject(),
                                       missedLegs.getSerialisedObject(),
                                       (convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) - 180 + MAG_HDG_DEV).roundToInt().toShort(), pos.x, pos.y, loc.maxDistNm,
                                       gs.glideAngle, gs.offsetNm, gs.maxInterceptAlt,
                                       mins.baroAltFt, mins.rvrM
                )
            }
        }

        /** Object that contains [IlsGS] data to be serialised by Kryo */
        class SerialisedIlsGS(name: String = "", arptId: Byte = 0, rwyId: Byte = 0, tower: String = "", towerFreq: String = "",
                              timeRestriction: Byte = 0,
                              transitions: Array<SerialisedTransition> = arrayOf(),
                              routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                              missedLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                              val heading: Short = 360, val posX: Float = 0f, val posY: Float = 0f, val locDistNm: Byte = 0,
                              val gsAngle: Float = 0f, val gsOffsetNm: Float = 0f, val gsMaxAlt: Short = 0,
                              val decisionAlt: Short = 0, val rvr: Short = 0): SerialisedApproach(name, rwyId, arptId, tower, towerFreq, timeRestriction, transitions, routeLegs, missedLegs)
    }

    /**
     * ILS class that stores all relevant data regarding the ILS (without glide slope, with localiser offset) and utility functions
     *
     * [entity], [transitions], [routeLegs], [missedLegs], [UsabilityFilter] and [Pronounceable] are directly inherited from [Approach]
     * */
    class IlsLOCOffset(name: String, arptId: Byte, runwayId: Byte, tower: String, towerFreq: String,
                       heading: Short, posX: Float, posY: Float, locDistNm: Byte, decisionAlt: Short, rvr: Short, centerlineInterceptDist: Float, steps: Array<Pair<Float, Short>>,
                       timeRestriction: Byte): Approach(name, arptId, runwayId, tower, towerFreq, timeRestriction = timeRestriction) {
        init {
            entity.apply {
                this += Position(posX, posY)
                this += Direction(Vector2(Vector2.Y).rotateDeg(180 - (heading - MAG_HDG_DEV)))
                this += Localizer(locDistNm)
                this += Minimums(decisionAlt, rvr)
                this += Offset(centerlineInterceptDist)
                this += StepDown(steps)
            }
        }

        /**
         * Gets a serialised offset ILS LOC object from current state
         * @return a [SerialisedIlsLOCOffset] object representing the current state of this [IlsLOCOffset] object
         * */
        override fun getSerialisableObject(): SerialisedApproach {
            entity.apply {
                val appInfo = get(ApproachInfo.mapper) ?: return SerialisedIlsLOCOffset()
                val pos = get(Position.mapper) ?: return SerialisedIlsLOCOffset()
                val dir = get(Direction.mapper) ?: return SerialisedIlsLOCOffset()
                val loc = get(Localizer.mapper) ?: return SerialisedIlsLOCOffset()
                val mins = get(Minimums.mapper) ?: return SerialisedIlsLOCOffset()
                val offset = get(Offset.mapper) ?: return SerialisedIlsLOCOffset()
                val steps = get(StepDown.mapper) ?: return SerialisedIlsLOCOffset()
                return SerialisedIlsLOCOffset(appInfo.approachName, appInfo.airportId, appInfo.rwyId, appInfo.towerName, appInfo.frequency,
                    timeRestriction,
                    transitions.map { SerialisedTransition(it.first, it.second.getSerialisedObject()) }.toTypedArray(),
                    routeLegs.getSerialisedObject(),
                    missedLegs.getSerialisedObject(),
                    (convertWorldAndRenderDeg(dir.trackUnitVector.angleDeg()) - 180 + MAG_HDG_DEV).roundToInt().toShort(), pos.x, pos.y, loc.maxDistNm,
                    mins.baroAltFt, mins.rvrM,
                    offset.lineUpDistNm, steps.altAtDist.map { SerialisedStep(it.first, it.second) }.toTypedArray()
                )
            }
        }

        /** Object that contains [IlsLOCOffset] data to be serialised by Kryo */
        class SerialisedIlsLOCOffset(name: String = "", arptId: Byte = 0, rwyId: Byte = 0, tower: String = "", towerFreq: String = "",
                              timeRestriction: Byte = 0,
                              transitions: Array<SerialisedTransition> = arrayOf(),
                              routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                              missedLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                              val heading: Short = 360, val posX: Float = 0f, val posY: Float = 0f, val locDistNm: Byte = 0,
                              val decisionAlt: Short = 0, val rvr: Short = 0,
                              val centerlineInterceptDist: Float = 0f, val steps: Array<SerialisedStep> = arrayOf()): SerialisedApproach(name, arptId, rwyId, tower, towerFreq, timeRestriction, transitions, routeLegs, missedLegs)
    }
}
