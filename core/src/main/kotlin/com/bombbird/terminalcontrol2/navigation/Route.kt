package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.components.CommandTarget
import ktx.collections.GdxArray

/** Route class that contains aircraft route legs */
class Route {
    val routeLegs = GdxArray<Leg>(20)

    /** Abstract leg class that is extended to give specific leg functionality, contains [phase] which specifies which part of the flight the leg is part of */
    abstract class Leg(val phase: Byte) {
        companion object {
            const val NORMAL: Byte = 0 // Normal flight route
            const val TRANS: Byte = 1 // Approach transition
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

    }

    /** Defines a vector leg with the [heading] to fly
     *
     * Optional declaration of [phase]
     * */
    class VectorLeg(val heading: Short, phase: Byte = NORMAL): Leg(phase) {

    }

    /** Defines a route discontinuity leg
     *
     * In practice, this is a [VectorLeg] except the aircraft will maintain its present heading upon encountering this
     *
     * Optional declaration of [phase]
     * */
    class DiscontinuityLeg(phase: Byte = NORMAL): Leg(phase) {

    }

    /** Defines a holding leg - waypoint, altitude restrictions, speed restrictions, inbound heading and leg distance
     *
     * In practice, aircraft will hold indefinitely at the specified waypoint once it reaches there, till further clearance is given
     *
     * Optional declaration of [phase]
     * */
    class HoldLeg(val wptId: Short, val maxAltFt: Int?, val minAltFt: Int?, val maxSpdKt: Short?, val inboundHdg: Short, val legDist: Byte, phase: Byte = NORMAL): Leg(phase) {

    }
}