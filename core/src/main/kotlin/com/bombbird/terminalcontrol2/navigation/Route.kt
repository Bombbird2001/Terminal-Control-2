package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.utilities.getRequiredTrack
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

    /** Adds all the legs in the provided [route] to the end of the leg array */
    fun extendRoute(route: Route) {
        legs.addAll(route.legs)
    }

    /** Clears the existing [legs] and adds all the legs in the provided [route] */
    fun setToRoute(route: Route) {
        legs.clear()
        legs.addAll(route.legs)
    }

    /** Returns the track and turn direction from the first to second waypoint, or null if there is no second waypoint leg */
    fun findNextWptLegTrackAndDirection(): Pair<Float, Byte>? {
        if (legs.size < 2) return null
        (legs[0] as? WaypointLeg)?.let { wpt1 -> (legs[1] as? WaypointLeg)?.let { wpt2 ->
            val w1 = GAME.gameServer?.waypoints?.get(wpt1.wptId)?.entity?.get(Position.mapper) ?: return null
            val w2 = GAME.gameServer?.waypoints?.get(wpt2.wptId)?.entity?.get(Position.mapper) ?: return null
            return Pair(getRequiredTrack(w1.x, w1.y, w2.x, w2.y), wpt2.turnDir)
        }} ?: return null
    }

    /**
     * Gets the speed restriction active at the active leg in the current departure route
     *
     * Returns the max speed, or null if a speed restriction does not exist
     * */
    fun getMaxSpdAtCurrLegDep(): Short? {
        for (i in 0 until legs.size) return (legs[i] as? WaypointLeg)?.maxSpdKt ?: continue
        return null
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

    /**
     * Abstract leg class that is extended to give specific leg functionality, contains abstract property[phase] which
     * specifies which part of the flight the leg is part of
     * */
    abstract class Leg(val phase: Byte) {
        companion object {
            const val NORMAL: Byte = 0 // Normal flight route
            const val APP_TRANS: Byte = 1 // Approach transition
            const val APP: Byte = 2 // Approach
            const val MISSED_APP: Byte = 3 // Missed approach procedure
        }
    }

    /**
     * Defines a waypoint leg - waypoint, altitude restrictions and speed restrictions
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
                    phase: Byte = NORMAL): this(GAME.gameServer?.let {
                        it.waypoints[it.updatedWaypointMapping[wptName]]?.entity?.get(WaypointInfo.mapper)?.wptId ?: -1
                    } ?: throw RuntimeException("gameServer is non-existent when creating route in GameLoader context"),
                    maxAltFt, minAltFt, maxSpdKt, legActive, altRestrActive, spdRestrActive, flyOver, turnDir, phase)

        // No-arg constructor for Kryo serialisation
        constructor(): this(0, null, null, null, true, true, true)

        /** Debug string representation */
        override fun toString(): String {
            val wptName = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKt != null) "S$maxSpdKt" else ""} ${if (flyOver) "FLYOVER" else ""}"
        }
    }

    /**
     * Defines a vector leg with the [heading] to fly
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

    /**
     * Defines a route discontinuity leg
     *
     * In practice, this is a [VectorLeg] except the aircraft will maintain its present heading upon encountering this
     *
     * Optional declaration of [phase]
     * */
    class DiscontinuityLeg(phase: Byte = NORMAL): Leg(phase)

    /**
     * Defines a holding leg - waypoint, altitude restrictions, speed restrictions, inbound heading and leg distance
     *
     * In practice, aircraft will hold indefinitely at the specified waypoint once it reaches there, till further clearance is given
     *
     * Optional declaration of [phase]
     * */
    class HoldLeg(val wptId: Short, val maxAltFt: Int?, val minAltFt: Int?, val maxSpdKtLower: Short?, val maxSpdKtHigher: Short?, val inboundHdg: Short, val legDist: Byte, val turnDir: Byte, phase: Byte = NORMAL): Leg(phase) {

        // No-arg constructor for Kryo serialisation
        constructor(): this(0, null, null, 230, 240, 360, 5, CommandTarget.TURN_RIGHT)

        /** Secondary constructor using the name of a waypoint instead of its ID - use only when loading from internal game files */
        constructor(wptName: String, maxAltFt: Int?, minAltFt: Int?, maxSpdKtLower: Short?, maxSpdKtHigher: Short?, inboundHdg: Short, legDist: Byte,
                    turnDir: Byte, phase: Byte = NORMAL): this(GAME.gameServer?.let {
            it.waypoints[it.updatedWaypointMapping[wptName]]?.entity?.get(WaypointInfo.mapper)?.wptId ?: -1
        } ?: throw RuntimeException("gameServer is non-existent when creating route in GameLoader context"), maxAltFt, minAltFt, maxSpdKtLower, maxSpdKtHigher, inboundHdg, legDist, turnDir, phase)

        /** Debug string representation */
        override fun toString(): String {
            val wptName = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName HDG $inboundHdg LEG $legDist ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKtLower != null) "S$maxSpdKtLower" else ""} ${if (maxSpdKtHigher != null) "S$maxSpdKtHigher" else ""}"
        }
    }
}