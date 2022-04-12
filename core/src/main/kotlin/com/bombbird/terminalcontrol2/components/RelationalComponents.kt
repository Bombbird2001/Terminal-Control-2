package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import ktx.ashley.Mapper

/** Component to keep track of an airport's runways (for O(1) access) */
class RunwayChildren(var rwyMap: HashMap<Int, Airport.Runway> = HashMap()): Component {
    companion object: Mapper<RunwayChildren>()
}
