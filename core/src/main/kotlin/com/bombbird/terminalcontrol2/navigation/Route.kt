package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.components.WaypointInfo
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.json.BaseLegJSONInterface
import com.bombbird.terminalcontrol2.utilities.getServerOrClientWaypointMap
import com.squareup.moshi.JsonClass
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

    val size: Int
        get() = legs.size

    private val legs = GdxArray<Leg>(20)

    /** Secondary constructor for directly de-serialising from [SerialisedRoute] */
    constructor(newLegs: Array<Leg>): this() {
        for (leg in newLegs) legs.add(leg)
    }

    /**
     * Wrapper function for getting a leg from the route by its index
     * @param index the index of the leg to get
     * @return the leg at the index
     */
    operator fun get(index: Int): Leg {
        return legs[index]
    }

    /**
     * Wrapper function for checking if a specific leg exists in the route, identity equality not required
     * @param leg the leg to check
     * @return whether the leg exists in the route
     */
    fun contains(leg: Leg): Boolean {
        return legs.contains(leg, false)
    }

    /**
     * Wrapper function for finding the index of a specific leg exists in the route, identity equality not required
     * @param leg the leg to find
     * @return the index of the leg in the array, or -1 if none found
     */
    fun indexOf(leg: Leg): Int {
        return legs.indexOf(leg, false)
    }

    /**
     * Wrapper function for appending a new leg to the back of the route
     * @param leg the new leg to add to the route
     */
    fun add(leg: Leg) {
        legs.add(leg)
    }

    /**
     * Wrapper function for inserting a new leg at the specified index
     * @param index the index to insert the leg at
     * @param leg the leg to insert
     */
    fun insert(index: Int, leg: Leg) {
        legs.insert(index, leg)
    }

    /**
     * Wrapper function for removing a leg from the route by its index
     * @param index the index of the leg to remove
     */
    fun removeIndex(index: Int) {
        legs.removeIndex(index)
    }

    /**
     * Wrapper function for removing a range of legs from the route from [startIndex]
     * to [endIndex], both inclusive
     */
    fun removeRange(startIndex: Int, endIndex: Int) {
        legs.removeRange(startIndex, endIndex)
    }

    /**
     * Wrapper function for removing a leg from the route by its value, identity equality not required
     * @param leg the leg to remove
     */
    fun removeValue(leg: Leg) {
        legs.removeValue(leg, false)
    }

    /** Wrapper function for clearing the [legs] array */
    fun clear() {
        legs.clear()
    }

    /**
     * Adds all the legs in the provided [route] to the end of the leg array
     *
     * Note: This will directly refer to the leg object; any changes made to the leg object will be reflected in other
     * route legs using referring to the same object; this method should only be used when reading from save files or
     * de-serialising data, or if it is absolutely certain that two variables or properties are meant to refer to the
     * exact same leg objects
     * @param route the route used to extend this route (via reference)
     */
    fun extendRoute(route: Route) {
        legs.addAll(route.legs)
    }

    /**
     * Copies all the legs in the provided [route] to the end of the leg array
     * @param route the route used to extend this route (via copying)
     */
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
     */
    fun setToRoute(route: Route) {
        legs.clear()
        legs.addAll(route.legs)
    }

    /**
     * Clears the existing [legs] and copies all the legs in the provided [route]
     * @param route the route to set this route to (via copying)
     */
    fun setToRouteCopy(route: Route) {
        legs.clear()
        legs.addAll(route.legs.map { it.copyLeg() }.toGdxArray())
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
     */
    abstract class Leg {
        abstract var phase: Byte

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
     */
    @JsonClass(generateAdapter = true)
    data class WaypointLeg(val wptId: Short, val maxAltFt: Int?, val minAltFt: Int?, val maxSpdKt: Short?,
                      var legActive: Boolean, var altRestrActive: Boolean, var spdRestrActive: Boolean,
                      val flyOver: Boolean = false, val turnDir: Byte = CommandTarget.TURN_DEFAULT,
                      override var phase: Byte = NORMAL
    ): Leg(), BaseLegJSONInterface {
        override val legType = BaseLegJSONInterface.LegType.WAYPOINT_LEG

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
         */
        override fun copyLeg(): Leg {
            return WaypointLeg(wptId, maxAltFt, minAltFt, maxSpdKt, legActive, altRestrActive, spdRestrActive, flyOver, turnDir, phase)
        }

        /** Debug string representation */
        override fun toString(): String {
            val wptName = getServerOrClientWaypointMap()?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKt != null) "S$maxSpdKt" else ""} ${if (flyOver) "FLYOVER" else ""}"
        }
    }

    /**
     * Defines a vector leg with the [heading] to fly
     *
     * Optional declaration of [phase]
     */
    @JsonClass(generateAdapter = true)
    data class VectorLeg(var heading: Short, var turnDir: Byte = CommandTarget.TURN_DEFAULT, override var phase: Byte = NORMAL): Leg(), BaseLegJSONInterface {
        override val legType = BaseLegJSONInterface.LegType.VECTOR_LEG

        // No-arg constructor for Kryo serialisation
        constructor(): this(360)

        /**
         * Makes a copy of this vector leg and returns it
         * @return a new instance of this [VectorLeg]
         */
        override fun copyLeg(): Leg {
            return VectorLeg(heading, turnDir, phase)
        }

        /** Debug string representation */
        override fun toString(): String {
            return "HDG $heading $turnDir"
        }
    }

    /**
     * Defines an initial climb leg with the [heading] to fly, and the minimum altitude after which the aircraft will
     * continue to the next leg
     */
    @JsonClass(generateAdapter = true)
    data class InitClimbLeg(val heading: Short, val minAltFt: Int, override var phase: Byte = NORMAL): Leg(), BaseLegJSONInterface {
        override val legType = BaseLegJSONInterface.LegType.INIT_CLIMB_LEG

        // No-arg constructor for Kryo serialisation
        constructor(): this(360, 0)

        /**
         * Makes a copy of this initial climb leg and returns it
         * @return a new instance of this [InitClimbLeg]
         */
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
     */
    @JsonClass(generateAdapter = true)
    data class DiscontinuityLeg(override var phase: Byte = NORMAL): Leg(), BaseLegJSONInterface {
        override val legType = BaseLegJSONInterface.LegType.DISCONTINUITY_LEG

        /**
         * Makes a copy of this discontinuity leg and returns it
         * @return a new instance of this [DiscontinuityLeg]
         */
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
     */
    @JsonClass(generateAdapter = true)
    data class HoldLeg(var wptId: Short, var maxAltFt: Int?, var minAltFt: Int?, var maxSpdKtLower: Short?, var maxSpdKtHigher: Short?,
                       var inboundHdg: Short, var legDist: Byte, var turnDir: Byte, override var phase: Byte = NORMAL): Leg(), BaseLegJSONInterface {
        override val legType = BaseLegJSONInterface.LegType.HOLD_LEG

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
         */
        override fun copyLeg(): Leg {
            return HoldLeg(wptId, maxAltFt, minAltFt, maxSpdKtLower, maxSpdKtHigher, inboundHdg, legDist, turnDir, phase)
        }

        /** Debug string representation */
        override fun toString(): String {
            val wptName = getServerOrClientWaypointMap()?.get(wptId)?.entity?.get(WaypointInfo.mapper)?.wptName
            return "$wptId $wptName HDG $inboundHdg LEG $legDist ${if (maxAltFt != null) "B$maxAltFt" else ""} ${if (minAltFt != null) "A$minAltFt" else ""} ${if (maxSpdKtLower != null) "S$maxSpdKtLower" else ""} ${if (maxSpdKtHigher != null) "S$maxSpdKtHigher" else ""}"
        }
    }

    /**
     * Class for storing segments of the route; this is currently used for rendering the aircraft's lateral clearance state
     * in the UI
     *
     * Each instance of segment will store either 1 or 2 legs, with constructors provided only for the following valid
     * segment configurations:
     * 1. Aircraft -> Waypoint (null leg1, waypoint leg2)
     * 2. Waypoint -> waypoint (waypoint leg1, leg2)
     * 3. Hold (null leg1, hold leg2)
     * 4. Waypoint -> Vector (waypoint leg1, vector leg2)
     * 5. Hold -> Waypoint (hold leg1, waypoint leg2)
     */
    class LegSegment {

        var leg1: Leg? = null
        private set
        var leg2: Leg? = null
        private set
        var changed = false

        /**
         * Constructor for Aircraft -> Waypoint segment
         * @param leg2Wpt the waypoint the aircraft is flying to
         */
        constructor(leg2Wpt: WaypointLeg) {
            leg2  = leg2Wpt
        }

        /**
         * Constructor for Waypoint -> Waypoint segment
         * @param leg1Wpt the first waypoint
         * @param leg2Wpt the second waypoint
         */
        constructor(leg1Wpt: WaypointLeg, leg2Wpt: WaypointLeg) {
            leg1 = leg1Wpt
            leg2 = leg2Wpt
        }

        /**
         * Constructor for Hold segment
         * @param leg2Hold the hold leg
         */
        constructor(leg2Hold: HoldLeg) {
            leg2 = leg2Hold
        }

        /**
         * Constructor for Waypoint -> Vector segment
         * @param leg1Wpt the waypoint leg
         * @param leg2Vec the vector leg
         */
        constructor(leg1Wpt: WaypointLeg, leg2Vec: VectorLeg) {
            leg1 = leg1Wpt
            leg2 = leg2Vec
        }

        /**
         * Constructor for Hold -> Waypoint segment
         * @param leg1Hold the hold leg
         * @param leg2Wpt the waypoint leg after the hold
         */
        constructor(leg1Hold: HoldLeg, leg2Wpt: WaypointLeg) {
            leg1 = leg1Hold
            leg2 = leg2Wpt
        }

        /**
         * Overridden [equals] function to compare that the two legs of the segment are equal according to the
         * [compareLegEquality] function
         * @param other the other segment to compare this segment to
         * @return whether the other segment is the same as this segment
         */
        override fun equals(other: Any?): Boolean {
            if (other !is LegSegment) return false
            val finalLeg1 = leg1
            val otherLeg1 = other.leg1
            val finalLeg2 = leg2
            val otherLeg2 = other.leg2
            if ((finalLeg1 == null && otherLeg1 != null) || (finalLeg1 != null && otherLeg1 == null)) return false
            if ((finalLeg2 == null && otherLeg2 != null) || (finalLeg2 != null && otherLeg2 == null)) return false
            val leg1Equal = (finalLeg1 == null || otherLeg1 == null) || compareLegEquality(finalLeg1, otherLeg1)
            val leg2Equal = (finalLeg2 == null || otherLeg2 == null) || compareLegEquality(finalLeg2, otherLeg2)
            return leg1Equal && leg2Equal
        }

        override fun hashCode(): Int {
            var result = leg1?.hashCode() ?: 0
            result = 31 * result + (leg2?.hashCode() ?: 0)
            result = 31 * result + changed.hashCode()
            return result
        }
    }
}