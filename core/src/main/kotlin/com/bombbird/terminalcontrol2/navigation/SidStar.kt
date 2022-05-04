package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.utilities.Pronounceable
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap

/** SID/STAR class that stores all relevant data regarding the SID/STAR and utility functions
 *
 * [rwyLegs] stores arrays of legs for departure from/arrival to each eligible runway
 *
 * [routeLegs] stores the main legs array common to all runways
 *
 * [inOutboundLegs] stores arrays of legs for possible outbound routes from the SID/inbound routes from the STAR
 *
 * Additionally, [UsabilityFilter] is implemented to provide filtering of suitable SIDs depending on conditions;
 * [Pronounceable] is implemented to provide adjustments to text for accurate pronunciation by TTS implementations
 *
 * This class is abstract and is extended by [SID] and [STAR]
 * */
abstract class SidStar(val name: String,
                       override val timeRestriction: Byte,
                       override val pronunciation: String): UsabilityFilter, Pronounceable {
    val routeLegs = Route()
    val rwyLegs = GdxArrayMap<String, Route>(6)
    protected val inOutboundLegs = GdxArray<Route>(10)

    /** Adds the supplied array of legs into [inOutboundLegs] */
    fun addToInboundOutboundLegs(newLegs: Route) {
        inOutboundLegs.add(newLegs)
    }

    /** Sets [routeLegs], [rwyLegs] an [inOutboundLegs] from the supplied [serialisedSidStar]
     *
     * This will clear any existing route data (there should not be any data in the route to begin with, should this function be used)
     * */
    fun setFromSerialisedObject(serialisedSidStar: SerialisedSidStar) {
        routeLegs.legs.clear()
        rwyLegs.clear()
        inOutboundLegs.clear()
        routeLegs.extendRoute(Route.fromSerialisedObject(serialisedSidStar.routeLegs))
        for (rwyLeg in serialisedSidStar.rwyLegs) rwyLegs.put(rwyLeg.rwy, Route.fromSerialisedObject(rwyLeg.route))
        for (inOutboundLeg in serialisedSidStar.inOutboundLegs) inOutboundLegs.add(Route.fromSerialisedObject(inOutboundLeg))
    }

    /** Object that contains [SidStar] data to be serialised by Kryo
     *
     * This class is abstract and is extended by SerialisedSID and SerialisedSTAR
     * */
    abstract class SerialisedSidStar(val name: String = "",
                                     val timeRestriction: Byte = 0,
                                     val pronunciation: String = "",
                                     val routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                                     val rwyLegs: Array<SerialisedRwyLegs> = arrayOf(),
                                     val inOutboundLegs: Array<Route.SerialisedRoute> = arrayOf())

    /** Object that contains [rwyLegs] data to be serialised by Kryo */
    class SerialisedRwyLegs(val rwy: String = "", val route: Route.SerialisedRoute = Route.SerialisedRoute())

    /** SID class that stores all relevant data regarding the SID and utility functions
     *
     * [rwyLegs], [routeLegs], [UsabilityFilter] and [Pronounceable] are directly inherited from [SidStar]
     *
     * [outboundLegs] utilises a getter for [inOutboundLegs]
     *
     * [rwyInitialClimbs] stores the initial climb altitude for each available runway
     * */
    class SID(name: String,
              timeRestriction: Byte,
              pronunciation: String): SidStar(name, timeRestriction, pronunciation), UsabilityFilter, Pronounceable {

        companion object {
            /** De-serialises a [SerialisedSID] and creates a new [SID] object from it */
            fun fromSerialisedObject(serialisedSID: SerialisedSID): SID {
                return SID(serialisedSID.name, serialisedSID.timeRestriction, serialisedSID.pronunciation).apply {
                    setFromSerialisedObject(serialisedSID)
                    for (initClimb in serialisedSID.rwyInitialClimbs) rwyInitialClimbs.put(initClimb.rwy, initClimb.altFt)
                }
            }
        }

        val rwyInitialClimbs = GdxArrayMap<String, Int>(6)
        val outboundLegs: GdxArray<Route>
            get() = inOutboundLegs

        /** Object that contains [SID] data to be serialised by Kryo */
        class SerialisedSID(name: String = "",
                            timeRestriction: Byte = 0,
                            pronunciation: String = "",
                            routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                            rwyLegs: Array<SerialisedRwyLegs> = arrayOf(),
                            inOutboundLegs: Array<Route.SerialisedRoute> = arrayOf(),
                            val rwyInitialClimbs: Array<SerialisedRwyInitClimb> = arrayOf()
        ): SerialisedSidStar(name, timeRestriction, pronunciation, routeLegs, rwyLegs, inOutboundLegs)

        /** Object that contains [rwyInitialClimbs] data to be serialised by Kryo */
        class SerialisedRwyInitClimb(val rwy: String = "", val altFt: Int = 3000)

        /** Gets a [SerialisedSID] from current state */
        fun getSerialisedObject(): SerialisedSID {
            return SerialisedSID(name, timeRestriction, pronunciation, routeLegs.getSerialisedObject(),
                                 rwyLegs.entries().map { SerialisedRwyLegs(it.key, it.value.getSerialisedObject()) }.toTypedArray(),
                                 inOutboundLegs.map { it.getSerialisedObject() }.toTypedArray(),
                                 rwyInitialClimbs.entries().map { SerialisedRwyInitClimb(it.key, it.value) }.toTypedArray())
        }
    }

    /** STAR class that stores all relevant data regarding the STAR and utility functions
     *
     * [rwyLegs], [routeLegs], [UsabilityFilter] and [Pronounceable] are directly inherited from [SidStar]
     *
     * [inboundLegs] utilises a getter for [inOutboundLegs]
     * */
    class STAR(name: String,
              timeRestriction: Byte,
              pronunciation: String): SidStar(name, timeRestriction, pronunciation), UsabilityFilter, Pronounceable {

        companion object {
            /** De-serialises a [SerialisedSTAR] and creates a new [STAR] object from it */
            fun fromSerialisedObject(serialisedSTAR: SerialisedSTAR): STAR {
                return STAR(serialisedSTAR.name, serialisedSTAR.timeRestriction, serialisedSTAR.pronunciation).apply {
                    setFromSerialisedObject(serialisedSTAR)
                }
            }
        }

        val inboundLegs: GdxArray<Route>
            get() = inOutboundLegs

        /** Object that contains [STAR] data to be serialised by Kryo */
        class SerialisedSTAR(name: String = "",
                             timeRestriction: Byte = 0,
                             pronunciation: String = "",
                             routeLegs: Route.SerialisedRoute = Route.SerialisedRoute(),
                             rwyLegs: Array<SerialisedRwyLegs> = arrayOf(),
                             inOutboundLegs: Array<Route.SerialisedRoute> = arrayOf()):
            SerialisedSidStar(name, timeRestriction, pronunciation, routeLegs, rwyLegs, inOutboundLegs)

        /** Gets a [SerialisedSTAR] from current state */
        fun getSerialisedObject(): SerialisedSTAR {
            return SerialisedSTAR(name, timeRestriction, pronunciation, routeLegs.getSerialisedObject(),
                rwyLegs.entries().map { SerialisedRwyLegs(it.key, it.value.getSerialisedObject()) }.toTypedArray(),
                inOutboundLegs.map { it.getSerialisedObject() }.toTypedArray())
        }
    }
}
