package com.bombbird.terminalcontrol2.editor

/** Active editing layer: limits hit-testing / selection; all layers remain drawn. */
enum class EditorLayer(val displayName: String) {
    WAYPOINTS("Waypoints"),
    RUNWAYS("Runways"),
    TERRAIN_MVA("Terrain MVAs"),
    RESTRICTED("Restricted areas"),
    APPROACHES("Approaches"),
    SIDS("SIDs"),
    STARS("STARs");

    override fun toString(): String = displayName
}
