package com.bombbird.terminalcontrol2.editor.route

import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.files.HOLD_LEG
import com.bombbird.terminalcontrol2.files.testParseLegs
import java.util.HashMap

fun editorWaypointIdMap(map: AirportMapDefinition): HashMap<String, Short> =
    HashMap(map.waypoints.associate { it.name to it.id })

/** HOLD legs must reference a [AirportMapDefinition.publishedHolds] entry for the waypoint (editor has no game server). */
fun holdPublishedHoldWarnings(tokens: List<String>, map: AirportMapDefinition): List<String> {
    if (tokens.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until tokens.size - 1) {
        if (tokens[i] != HOLD_LEG) continue
        val wpt = tokens[i + 1]
        val hasHold = map.publishedHolds.any { it.waypointName.equals(wpt, ignoreCase = true) }
        if (!hasHold) {
            out.add("HOLD references waypoint '$wpt' with no matching published hold")
        }
    }
    return out
}

/** Returns parser warnings; empty means [testParseLegs] accepted the token stream. */
fun collectRouteParseWarnings(tokens: List<String>, phase: Byte, map: AirportMapDefinition): List<String> {
    val msgs = mutableListOf<String>()
    val wpts = editorWaypointIdMap(map)
    testParseLegs(tokens, wpts, phase) { _, msg -> msgs.add(msg) }
    msgs.addAll(holdPublishedHoldWarnings(tokens, map))
    return msgs
}
