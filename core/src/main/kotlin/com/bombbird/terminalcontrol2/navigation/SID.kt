package com.bombbird.terminalcontrol2.navigation

import com.bombbird.terminalcontrol2.utilities.Pronounceable
import ktx.collections.GdxArray

/** SID class that stores all relevant data regarding the SID and utility functions
 *
 * [rwyLegs] stores arrays of legs for departure from each eligible runway
 *
 * [routeLegs] stores the main legs array common to all runways (follows after [rwyLegs])
 *
 * [outboundLegs] stores arrays of legs for possible outbound routes from the SID (routes split after [routeLegs])
 *
 * Additionally, [UsabilityFilter] is implemented to provide filtering of suitable SIDs depending on conditions;
 * [Pronounceable] is implemented to provide adjustments to text for accurate pronunciation by TTS implementations
 * */
class SID(val name: String,
          override val timeRestriction: Byte,
          override val pronunciation: String): UsabilityFilter, Pronounceable {
    val rwyLegs = HashMap<String, GdxArray<Route.Leg>>(6)
    val routeLegs = GdxArray<Route.Leg>()
    val outboundLegs = GdxArray<GdxArray<Route.Leg>>(10)
}