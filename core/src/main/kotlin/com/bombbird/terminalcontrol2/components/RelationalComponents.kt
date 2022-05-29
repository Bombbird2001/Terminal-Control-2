package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.navigation.SidStar
import ktx.ashley.Mapper
import ktx.collections.GdxArrayMap

/** Component to keep track of an airport's runways (for O(1) access) as well as the mapping runway names to the most updated ID (for backwards compatibility) */
data class RunwayChildren(val rwyMap: GdxArrayMap<Byte, Airport.Runway> = GdxArrayMap(), val updatedRwyMapping: GdxArrayMap<String, Byte> = GdxArrayMap()): Component {
    companion object: Mapper<RunwayChildren>()
}

/** Component to keep track of an airport's SIDs (for O(1) access) */
data class SIDChildren(val sidMap: GdxArrayMap<String, SidStar.SID> = GdxArrayMap()): Component {
    companion object: Mapper<SIDChildren>()
}

/** Component to keep track of an airport's STARs (for O(1) access) */
data class STARChildren(val starMap: GdxArrayMap<String, SidStar.STAR> = GdxArrayMap()): Component {
    companion object: Mapper<STARChildren>()
}

/** Component to keep track of an airport's approaches (for O(1) access) */
data class ApproachChildren(val approachMap: GdxArrayMap<String, Approach> = GdxArrayMap()): Component {
    companion object: Mapper<ApproachChildren>()
}

/** Component to keep track of a runway's default generated visual approach */
data class VisualApproach(val visual: Entity = Entity()): Component {
    companion object: Mapper<VisualApproach>()
}
