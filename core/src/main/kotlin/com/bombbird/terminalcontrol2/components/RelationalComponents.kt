package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.badlogic.ashley.core.Entity
import ktx.ashley.Mapper

/** Component to keep track of an airport's runways (for O(1) access) */
class RunwayChildren(val rwyMap: HashMap<String, Entity> = HashMap()): Component {
    companion object: Mapper<RunwayChildren>()
}
