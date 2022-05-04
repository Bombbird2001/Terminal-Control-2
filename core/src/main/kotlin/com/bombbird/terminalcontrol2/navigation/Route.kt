package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.Constants
import ktx.ashley.get
import ktx.collections.GdxArray

/** Route class that contains aircraft route legs */
class Route() {
    companion object {
        /** De-serialises a [SerialisedRoute] and creates a new [Route] object from it  */
        fun fromSerialisedObject(serialisedRoute: SerialisedRoute): Route {
            return Route(serialisedRoute.legs)
        }
    }

    val legs = GdxArray<Leg>(20)

    /** Secondary constructor for directly de-serialising from [SerialisedRoute] */
    constructor(newLegs: Array<Leg>): this() {
        for (leg in newLegs) legs.add(leg)
    }

    /** Adds a new leg to the leg array */
    fun addLeg(leg: Leg) {
        legs.add(leg)
    }

    /** Adds all the legs in the provided [route] to thee end of the leg array */
    fun extendRoute(route: Route) {
        legs.addAll(route.legs)
    }

    /** Debug string representation */
    override fun toString(): String {
        return legs.toString()
    }

    /** Object that contains [Route] data to be serialised by Kryo */
    class SerialisedRoute(val legs: Array<Leg> = arrayOf())

    /** Gets a [SerialisedRoute] from current stat */
    fun getSerialisedObject(): SerialisedRoute {
        return SerialisedRoute(legs.map { it }.toTypedArray())
    }

    /** Abstract leg class that is extended to give specific leg functionality, contains [phase] which specifies which part of the flight the leg is part of */
    abstract class Leg(val phase: Byte) {
        companion object {
            const val NORMAL: Byte = 0 // Normal flight route
            const val APP_TRANS: Byte = 1 // Approach transition
            const val APP: Byte = 2 // Approach
            const val MISSED_APP: Byte = 3 // Missed approach procedure
        }
    }

    /** Defines a waypoint leg - waypoint, altitude restrictions and speed restrictions
     *
     * Optional declaration of [flyOver], [turnDir], [phase]
     * */
    class WaypointLeg(val wptId: Short, val maxAltFt: Int?, val minAltFt: Int?, val maxSpdKt: Short?,
                      var legActive: Boolean, var altRestrActive: Boolean, var spdRestrActive: Boolean,
                      val flyOver: Boolean = false, val turnDir: Byte = CommandTarget.TURN_DEFAULT,
                      phase: Byte = NORMAL
    ): Leg(phase) {
        /** Secondary constructor using the name of a waypoint instead of its ID - use only when loading from internal game files */
        constructor(wptName: String, maxAltFt: Int?, minAltFt: Int?, maxSpdKt: Short?,
                    legActive: Boolean, altRestrActive: Boolean, spdRestrActive: Boolean,
                    flyOver: Boolean = false, turnDir: Byte = CommandTarget.TURN_DEFAULT,
                    phase: Byte = NORMAL): this(Constants.GAME.gameServer?.let {
                        it.waypoints[it.updatedWaypointMapping[wptName]]?.entity?.get(WaypointInfo.mapper)?.wptId ?: -1
                    } ?: throw RuntimeException("gameServer is non-existent when creating route in GameLoader context"),
                    maxAltFt, minAltFt, maxSpdKt, legActive, altRestrActive, spdRestrActive, flyOver, turnDir, phase)

        // No-arg constructor for Kryo serialisation
        constructor(): this(0, null, null, null, true, true, true)

        /** Debug string representation */
        override fun toString(): String {
            val wptName = Constants.GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKt != null) "S$maxSpdKt" else ""} ${if (flyOver) "FLYOVER" else ""}"
        }
    }

    /** Defines a vector leg with the [heading] to fly
     *
     * Optional declaration of [phase]
     * */
    class VectorLeg(val heading: Short, phase: Byte = NORMAL): Leg(phase) {

        // No-arg constructor for Kryo serialisation
        constructor(): this(360)

        /** Debug string representation */
        override fun toString(): String {
            return "HDG $heading"
        }
    }

    /** Defines an initial climb leg with the [heading] to fly, and the minimum altitude after which the aircraft will continue to the next leg */
    class InitClimbLeg(val heading: Short, val minAltFt: Int, phase: Byte = NORMAL): Leg(phase) {

        // No-arg constructor for Kryo serialisation
        constructor(): this(360, 0)

        /** Debug string representation */
        override fun toString(): String {
            return "HDG $heading till A$minAltFt"
        }
    }

    /** Defines a route discontinuity leg
     *
     * In practice, this is a [VectorLeg] except the aircraft will maintain its present heading upon encountering this
     *
     * Optional declaration of [phase]
     * */
    class DiscontinuityLeg(phase: Byte = NORMAL): Leg(phase)

    /** Defines a holding leg - waypoint, altitude restrictions, speed restrictions, inbound heading and leg distance
     *
     * In practice, aircraft will hold indefinitely at the specified waypoint once it reaches there, till further clearance is given
     *
     * Optional declaration of [phase]
     * */
    class HoldLeg(val wptId: Short, val maxAltFt: Int?, val minAltFt: Int?, val maxSpdKt: Short?, val inboundHdg: Short, val legDist: Byte, phase: Byte = NORMAL): Leg(phase) {

        // No-arg constructor for Kryo serialisation
        constructor(): this(0, null, null, null, 360, 5)

        /** Secondary constructor using the name of a waypoint instead of its ID - use only when loading from internal game files */
        constructor(wptName: String, maxAltFt: Int?, minAltFt: Int?, maxSpdKt: Short?, inboundHdg: Short, legDist: Byte,
                    phase: Byte = NORMAL): this(Constants.GAME.gameServer?.let {
            it.waypoints[it.updatedWaypointMapping[wptName]]?.entity?.get(WaypointInfo.mapper)?.wptId ?: -1
        } ?: throw RuntimeException("gameServer is non-existent when creating route in GameLoader context"), maxAltFt, minAltFt, maxSpdKt, inboundHdg, legDist, phase)

        /** Debug string representation */
        override fun toString(): String {
            val wptName = Constants.GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName HDG $inboundHdg LEG $legDist ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKt != null) "S$maxSpdKt" else ""}"
        }
    }
}