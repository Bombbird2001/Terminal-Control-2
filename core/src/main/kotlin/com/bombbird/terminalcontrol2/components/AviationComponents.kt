package com.bombbird.terminalcontrol2.components

import com.badlogic.ashley.core.Component
import com.bombbird.terminalcontrol2.entities.Airport
import ktx.ashley.Mapper

/** Component for tagging airport related information */
class AirportInfo(var icaoCode: String = "", var name: String = "", val rwys: RunwayChildren = RunwayChildren()): Component {
    // TODO Add other airport related components
    companion object: Mapper<AirportInfo>()
}

/** Component for tagging runway related information */
class RunwayInfo(var rwyName: String = ""): Component {
    lateinit var airport: Airport
    // TODO Add other runway related components
    companion object: Mapper<RunwayInfo>()
}