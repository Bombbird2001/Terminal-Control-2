package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.Position
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.MAG_HDG_DEV
import com.bombbird.terminalcontrol2.utilities.compareLegEquality
import com.bombbird.terminalcontrol2.utilities.getRequiredTrack
import ktx.ashley.get
import ktx.collections.GdxArray
import ktx.collections.toGdxArray

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

    /**
     * Adds all the legs in the provided [route] to the end of the leg array
     *
     * Note: This will directly refer to the leg object; any changes made to the leg object will be reflected in other
     * route legs using referring to the same object; this method should only be used when reading from save files or
     * de-serialising data, or if it is absolutely certain that two variables or properties are meant to refer to the
     * exact same leg objects
     * @param route the route used to extend this route (via reference)
     * */
    fun extendRoute(route: Route) {
        legs.addAll(route.legs)
    }

    /**
     * Copies all the legs in the provided [route] to the end of the leg array
     * @param route the route used to extend this route (via copying)
     * */
    fun extendRouteCopy(route: Route) {
        legs.addAll(route.legs.map { it.copyLeg() }.toGdxArray())
    }

    /**
     * Clears the existing [legs] and adds all the legs in the provided [route]
     *
     * Note: This will directly refer to the leg object; any changes made to the leg object will be reflected in other
     * route legs using referring to the same object; this method should only be used when reading from save files or
     * de-serialising data, or if it is absolutely certain that two variables or properties are meant to refer to the
     * exact same leg objects
     * @param route the route to set this route to (via reference)
     * */
    fun setToRoute(route: Route) {
        legs.clear()
        legs.addAll(route.legs)
    }

    /**
     * Clears the existing [legs] and copies all the legs in the provided [route]
     * @param route the route to set this route to (via copying)
     * */
    fun setToRouteCopy(route: Route) {
        legs.clear()
        legs.addAll(route.legs.map { it.copyLeg() }.toGdxArray())
    }

    /** Returns the track and turn direction from the first to second waypoint, or null if there is no second waypoint leg */
    fun findNextWptLegTrackAndDirection(): Pair<Float, Byte>? {
        if (legs.size < 2) return null
        (legs[0] as? WaypointLeg)?.let { wpt1 ->
            (legs[1] as? WaypointLeg)?.let { wpt2 ->
                val w1 = GAME.gameServer?.waypoints?.get(wpt1.wptId)?.entity?.get(Position.mapper) ?: return null
                val w2 = GAME.gameServer?.waypoints?.get(wpt2.wptId)?.entity?.get(Position.mapper) ?: return null
                return Pair(getRequiredTrack(w1.x, w1.y, w2.x, w2.y), wpt2.turnDir)
            } ?: (legs[1] as? VectorLeg)?.let { return Pair(it.heading - MAG_HDG_DEV, CommandTarget.TURN_DEFAULT)}
        } ?: return null
    }

    /**
     * Gets the after waypoint vector leg; if no vector legs exist after the waypoint leg, null is returned
     * @param wpt the waypoint leg to check for a vector leg after
     * @return a [VectorLeg], or null if no vector leg found
     * */
    fun getAfterWptHdgLeg(wpt: WaypointLeg): VectorLeg? {
        for (i in 0 until legs.size) legs[i]?.apply {
            if (compareLegEquality(wpt, this)) {
                if (legs.size > i + 1) (legs[i + 1] as? VectorLeg)?.let { return it } ?: return null // If subsequent leg exists and is vector, return it
            }
        }
        return null
    }

    /**
     * Gets the after waypoint vector leg; if no vector legs exist after the waypoint leg, null is returned
     * @param wptName the waypoint name to check for a vector leg after
     * @return a [VectorLeg], or null if no vector leg found
     * */
    fun getAfterWptHdgLeg(wptName: String): VectorLeg? {
        for (i in 0 until legs.size) (legs[i] as? WaypointLeg)?.apply {
            if (GAME.gameClientScreen?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName == wptName) {
                if (legs.size > i + 1) (legs[i + 1] as? VectorLeg)?.let { return it } ?: return null // If subsequent leg exists and is vector, return it
            }
        }
        return null
    }

    /**
     * Gets the current direct if there is a vector leg immediately after; if no vector legs exist after the waypoint leg,
     * or current direct is not waypoint leg, null is returned
     * @return a [WaypointLeg], or null if no vector leg found
     * */
    fun getNextAfterWptHdgLeg(): WaypointLeg? {
        if (legs.size < 2) return null
        val firstLeg = legs[0]
        if (firstLeg !is WaypointLeg || legs[1] !is VectorLeg) return null
        return firstLeg
    }

    /**
     * Gets the first upcoming holding leg; if a non-waypoint leg is reached before finding any
     * hold legs, null is returned
     * @return a [HoldLeg], or null if no hold legs are found
     * */
    fun getNextHoldLeg(): HoldLeg? {
        for (i in 0 until legs.size) legs[i]?.apply {
            if (this is HoldLeg) return this
            else if (this !is WaypointLeg) return null
        }
        return null
    }

    /**
     * Gets the first upcoming hold leg with the input ID; if a non-waypoint/hold leg is reached before finding any hold
     * legs, null is returned
     *
     * If waypoint ID is -1, a search for present position hold leg in the first position is done instead, and returns it
     * if found
     * @param wptId the waypoint ID of the hold leg to search for in the route
     * @return a [HoldLeg], or null if no hold legs are found
     * */
    fun findFirstHoldLegWithID(wptId: Short): HoldLeg? {
        if (wptId <= -1) {
            // Searching for present position hold leg - only the first leg should be
            if (legs.size == 0) return null
            return (legs[0] as? HoldLeg)?.let {
                // If the first leg is hold and has a wptId of less than or equal to -1 (present position waypoints have custom IDs less than -1, or -1 if uninitialised)
                if (it.wptId <= -1) it else null
            }
        }

        for (i in 0 until legs.size) legs[i]?.apply {
            if (this is HoldLeg && this.wptId == wptId) return this
            else if (this !is WaypointLeg && this !is HoldLeg) return null
        }
        return null
    }

    /**
     * Gets the first upcoming waypoint leg with a speed restriction; if a non-waypoint leg is reached before finding any
     * waypoint legs with a restriction, null is returned
     * @return a [WaypointLeg], or null if no legs with a speed restriction are found
     * */
    fun getNextWaypointWithSpdRestr(): WaypointLeg? {
        for (i in 0 until legs.size) (legs[i] as? WaypointLeg)?.let { if (it.maxSpdKt != null && it.spdRestrActive) return it } ?: return null
        return null
    }

    /**
     * Gets the speed restriction active at the active leg in the current departure route
     * @return the max speed, or null if a speed restriction does not exist
     * */
    fun getNextMaxSpd(): Short? {
        for (i in 0 until legs.size) return (legs[i] as? WaypointLeg)?.let {
            if (it.legActive && it.spdRestrActive) it.maxSpdKt else null
        } ?: continue
        return null
    }

    /**
     * Gets the next minimum altitude restriction for the route
     * @return the minimum altitude, or null if a minimum altitude restriction does not exist
     * */
    fun getNextMinAlt(): Int? {
        for (i in 0 until legs.size) return (legs[i] as? WaypointLeg)?.let {
            if (it.legActive && it.altRestrActive) it.minAltFt else null
        } ?: continue
        return null
    }

    /**
     * Gets the next maximum altitude restriction for the route
     * @return the maximum altitude, or null if a maximum altitude restriction does not exist
     * */
    fun getNextMaxAlt(): Int? {
        for (i in 0 until legs.size) return (legs[i] as? WaypointLeg)?.let {
            if (it.legActive && it.altRestrActive) it.maxAltFt else null
        } ?: continue
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
     * Abstract leg class that is extended to give specific leg functionality, contains abstract property [phase] which
     * specifies which part of the flight the leg is part of
     * */
    abstract class Leg {
        abstract val phase: Byte

        /** Abstract function for implementation - makes a copy of the leg and returns it */
        abstract fun copyLeg(): Leg

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
    data class WaypointLeg(val wptId: Short, val maxAltFt: Int?, val minAltFt: Int?, val maxSpdKt: Short?,
                      var legActive: Boolean, var altRestrActive: Boolean, var spdRestrActive: Boolean,
                      val flyOver: Boolean = false, val turnDir: Byte = CommandTarget.TURN_DEFAULT,
                      override val phase: Byte = NORMAL
    ): Leg() {
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

        /**
         * Makes a copy of this waypoint leg and returns it
         * @return a new instance of this [WaypointLeg]
         * */
        override fun copyLeg(): Leg {
            return WaypointLeg(wptId, maxAltFt, minAltFt, maxSpdKt, legActive, altRestrActive, spdRestrActive, flyOver, turnDir, phase)
        }

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
    data class VectorLeg(var heading: Short, var turnDir: Byte = CommandTarget.TURN_DEFAULT, override val phase: Byte = NORMAL): Leg() {

        // No-arg constructor for Kryo serialisation
        constructor(): this(360)

        /**
         * Makes a copy of this vector leg and returns it
         * @return a new instance of this [VectorLeg]
         * */
        override fun copyLeg(): Leg {
            return VectorLeg(heading, turnDir, phase)
        }

        /** Debug string representation */
        override fun toString(): String {
            return "HDG $heading $turnDir"
        }
    }

    /** Defines an initial climb leg with the [heading] to fly, and the minimum altitude after which the aircraft will continue to the next leg */
    data class InitClimbLeg(val heading: Short, val minAltFt: Int, override val phase: Byte = NORMAL): Leg() {

        // No-arg constructor for Kryo serialisation
        constructor(): this(360, 0)

        /**
         * Makes a copy of this initial climb leg and returns it
         * @return a new instance of this [InitClimbLeg]
         * */
        override fun copyLeg(): Leg {
            return InitClimbLeg(heading, minAltFt, phase)
        }

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
    data class DiscontinuityLeg(override val phase: Byte = NORMAL): Leg() {

        /**
         * Makes a copy of this discontinuity leg and returns it
         * @return a new instance of this [DiscontinuityLeg]
         * */
        override fun copyLeg(): Leg {
            return DiscontinuityLeg(phase)
        }
    }

    /**
     * Defines a holding leg - waypoint, altitude restrictions, speed restrictions, inbound heading and leg distance
     *
     * In practice, aircraft will hold indefinitely at the specified waypoint once it reaches there, till further clearance is given
     *
     * Optional declaration of [phase]
     * */
    data class HoldLeg(var wptId: Short, var maxAltFt: Int?, var minAltFt: Int?, var maxSpdKtLower: Short?, var maxSpdKtHigher: Short?,
                       var inboundHdg: Short, var legDist: Byte, var turnDir: Byte, override val phase: Byte = NORMAL): Leg() {

        // No-arg constructor for Kryo serialisation
        constructor(): this(0, null, null, 230, 240, 360, 5, CommandTarget.TURN_RIGHT)

        /** Secondary constructor using the name of a waypoint instead of its ID - use only when loading from internal game files */
        constructor(wptName: String, maxAltFt: Int?, minAltFt: Int?, maxSpdKtLower: Short?, maxSpdKtHigher: Short?, inboundHdg: Short, legDist: Byte,
                    turnDir: Byte, phase: Byte = NORMAL): this(GAME.gameServer?.let {
            it.waypoints[it.updatedWaypointMapping[wptName]]?.entity?.get(WaypointInfo.mapper)?.wptId ?: -1
        } ?: throw RuntimeException("gameServer is non-existent when creating route in GameLoader context"), maxAltFt, minAltFt, maxSpdKtLower, maxSpdKtHigher, inboundHdg, legDist, turnDir, phase)

        /**
         * Makes a copy of this hold leg and returns it
         * @return a new instance of this [HoldLeg]
         * */
        override fun copyLeg(): Leg {
            return HoldLeg(wptId, maxAltFt, minAltFt, maxSpdKtLower, maxSpdKtHigher, inboundHdg, legDist, turnDir, phase)
        }

        /** Debug string representation */
        override fun toString(): String {
            val wptName = GAME.gameServer?.waypoints?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName HDG $inboundHdg LEG $legDist ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKtLower != null) "S$maxSpdKtLower" else ""} ${if (maxSpdKtHigher != null) "S$maxSpdKtHigher" else ""}"
        }
    }
}