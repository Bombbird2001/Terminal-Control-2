package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.navigation.SidStar
import ktx.ashley.Mapper
import ktx.collections.GdxArrayMap

/** Component to keep track of an airport's runways (for O(1) access) */
data class RunwayChildren(var rwyMap: GdxArrayMap<Byte, Airport.Runway> = GdxArrayMap()): Component {
    companion object: Mapper<RunwayChildren>()
}

/** Component to keep track of an airport's SIDs (for O(1) access) */
data class SIDChildren(var sidMap: GdxArrayMap<String, SidStar.SID> = GdxArrayMap()): Component {
    companion object: Mapper<SIDChildren>()
}

/** Component to keep track of an airport's STARs (for O(1) access) */
data class STARChildren(var starMap: GdxArrayMap<String, SidStar.STAR> = GdxArrayMap()): Component {
    companion object: Mapper<STARChildren>()
}
