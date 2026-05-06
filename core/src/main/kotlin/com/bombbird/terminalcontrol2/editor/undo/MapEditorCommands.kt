package com.bombbird.terminalcontrol2.editor.undo

import com.bombbird.terminalcontrol2.editor.model.AirportDefinition
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.ApproachDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.NmPoint
import com.bombbird.terminalcontrol2.editor.model.RunwayDefinition
import com.bombbird.terminalcontrol2.editor.model.RunwayLabelPlacement
import com.bombbird.terminalcontrol2.editor.model.SidDefinition
import com.bombbird.terminalcontrol2.editor.model.StarDefinition
import com.bombbird.terminalcontrol2.editor.model.WaypointDefinition

class MoveWaypointPositionCommand(
    private val waypoint: WaypointDefinition,
    private val from: NmPoint,
    private val to: NmPoint,
) : EditorCommand {
    override fun execute() {
        waypoint.positionNm = to
    }

    override fun undo() {
        waypoint.positionNm = from
    }
}

class MoveAirportPositionCommand(
    private val airport: AirportDefinition,
    private val from: NmPoint,
    private val to: NmPoint,
) : EditorCommand {
    override fun execute() {
        airport.positionNm = to
    }

    override fun undo() {
        airport.positionNm = from
    }
}

class MoveRunwayThresholdCommand(
    private val runway: RunwayDefinition,
    private val from: NmPoint,
    private val to: NmPoint,
) : EditorCommand {
    override fun execute() {
        runway.thresholdNm = to
    }

    override fun undo() {
        runway.thresholdNm = from
    }
}

class RenameWaypointCommand(
    private val waypoint: WaypointDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        waypoint.name = to
    }

    override fun undo() {
        waypoint.name = from
    }
}

class RenameRunwayCommand(
    private val runway: RunwayDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        runway.name = to
    }

    override fun undo() {
        runway.name = from
    }
}

class MoveApproachPositionCommand(
    private val approach: ApproachDefinition,
    private val from: NmPoint,
    private val to: NmPoint,
) : EditorCommand {
    override fun execute() {
        approach.positionNm = to
    }

    override fun undo() {
        approach.positionNm = from
    }
}

class MoveMinAltPolygonVertexCommand(
    private val sector: MinAltPolygonSectorDefinition,
    private val vertexIndex: Int,
    private val from: NmPoint,
    private val to: NmPoint,
) : EditorCommand {
    override fun execute() {
        sector.verticesNm[vertexIndex] = to
    }

    override fun undo() {
        sector.verticesNm[vertexIndex] = from
    }
}

class MoveMinAltCircleCenterCommand(
    private val sector: MinAltCircleSectorDefinition,
    private val from: NmPoint,
    private val to: NmPoint,
) : EditorCommand {
    override fun execute() {
        sector.centerNm = to
    }

    override fun undo() {
        sector.centerNm = from
    }
}

class RenameApproachCommand(
    private val approach: ApproachDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        approach.name = to
    }

    override fun undo() {
        approach.name = from
    }
}

class RenameSidCommand(
    private val sid: SidDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        sid.name = to
    }

    override fun undo() {
        sid.name = from
    }
}

class RenameStarCommand(
    private val star: StarDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        star.name = to
    }

    override fun undo() {
        star.name = from
    }
}

class SetMinAltSectorMinAltitudeCommand(
    private val sector: MinAltSectorDefinition,
    private val from: Int?,
    private val to: Int?,
) : EditorCommand {
    override fun execute() {
        sector.minAltitudeFt = to
    }

    override fun undo() {
        sector.minAltitudeFt = from
    }
}

class SetMinAltCircleRadiusCommand(
    private val sector: MinAltCircleSectorDefinition,
    private val from: Float,
    private val to: Float,
) : EditorCommand {
    override fun execute() {
        sector.radiusNm = to
    }

    override fun undo() {
        sector.radiusNm = from
    }
}

class SetMinAltPolygonLabelPositionCommand(
    private val sector: MinAltPolygonSectorDefinition,
    private val from: NmPoint?,
    private val to: NmPoint?,
) : EditorCommand {
    override fun execute() {
        sector.labelPositionNm = to
    }

    override fun undo() {
        sector.labelPositionNm = from
    }
}

class SetMinAltCircleLabelPositionCommand(
    private val sector: MinAltCircleSectorDefinition,
    private val from: NmPoint?,
    private val to: NmPoint?,
) : EditorCommand {
    override fun execute() {
        sector.labelPositionNm = to
    }

    override fun undo() {
        sector.labelPositionNm = from
    }
}

class InsertMinAltPolygonVertexCommand(
    private val sector: MinAltPolygonSectorDefinition,
    private val index: Int,
    private val point: NmPoint,
) : EditorCommand {
    override fun execute() {
        sector.verticesNm.add(index, point)
    }

    override fun undo() {
        sector.verticesNm.removeAt(index)
    }
}

class RemoveMinAltPolygonVertexCommand(
    private val sector: MinAltPolygonSectorDefinition,
    private val index: Int,
    private val removed: NmPoint,
) : EditorCommand {
    override fun execute() {
        sector.verticesNm.removeAt(index)
    }

    override fun undo() {
        sector.verticesNm.add(index, removed)
    }
}

class AddMinAltSectorCommand(
    private val list: MutableList<MinAltSectorDefinition>,
    private val sector: MinAltSectorDefinition,
    private val insertAt: Int,
) : EditorCommand {
    override fun execute() {
        list.add(insertAt, sector)
    }

    override fun undo() {
        list.removeAt(insertAt)
    }
}

class RemoveMinAltSectorCommand(
    private val list: MutableList<MinAltSectorDefinition>,
    private val sector: MinAltSectorDefinition,
    private val index: Int,
) : EditorCommand {
    override fun execute() {
        list.removeAt(index)
    }

    override fun undo() {
        list.add(index, sector)
    }
}

class AddAirportCommand(
    private val list: MutableList<AirportDefinition>,
    private val airport: AirportDefinition,
    private val insertAt: Int,
) : EditorCommand {
    override fun execute() {
        list.add(insertAt, airport)
    }

    override fun undo() {
        list.removeAt(insertAt)
    }
}

class RemoveAirportCommand(
    private val list: MutableList<AirportDefinition>,
    private val airport: AirportDefinition,
    private val index: Int,
) : EditorCommand {
    override fun execute() {
        list.removeAt(index)
    }

    override fun undo() {
        list.add(index, airport)
    }
}

class AddRunwayCommand(
    private val airport: AirportDefinition,
    private val runway: RunwayDefinition,
    private val insertAt: Int,
) : EditorCommand {
    override fun execute() {
        airport.runways.add(insertAt, runway)
    }

    override fun undo() {
        airport.runways.removeAt(insertAt)
    }
}

class RemoveRunwayCommand(
    private val airport: AirportDefinition,
    private val runway: RunwayDefinition,
    private val index: Int,
) : EditorCommand {
    override fun execute() {
        airport.runways.removeAt(index)
    }

    override fun undo() {
        airport.runways.add(index, runway)
    }
}

class MoveRunwayToAirportCommand(
    private val runway: RunwayDefinition,
    private val fromAirport: AirportDefinition,
    private val toAirport: AirportDefinition,
) : EditorCommand {
    override fun execute() {
        fromAirport.runways.remove(runway)
        toAirport.runways.add(runway)
    }

    override fun undo() {
        toAirport.runways.remove(runway)
        fromAirport.runways.add(runway)
    }
}

class SetAirportIcaoCommand(
    private val airport: AirportDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        airport.icao = to
    }

    override fun undo() {
        airport.icao = from
    }
}

class SetAirportDisplayNameCommand(
    private val airport: AirportDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        airport.name = to
    }

    override fun undo() {
        airport.name = from
    }
}

class SetAirportRatioCommand(
    private val airport: AirportDefinition,
    private val from: Byte,
    private val to: Byte,
) : EditorCommand {
    override fun execute() {
        airport.ratio = to
    }

    override fun undo() {
        airport.ratio = from
    }
}

class SetAirportMaxAdvanceDeparturesCommand(
    private val airport: AirportDefinition,
    private val from: Int,
    private val to: Int,
) : EditorCommand {
    override fun execute() {
        airport.maxAdvanceDepartures = to
    }

    override fun undo() {
        airport.maxAdvanceDepartures = from
    }
}

class SetAirportElevationCommand(
    private val airport: AirportDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        airport.elevationFt = to
    }

    override fun undo() {
        airport.elevationFt = from
    }
}

class SetRunwayLengthCommand(
    private val runway: RunwayDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        runway.lengthM = to
    }

    override fun undo() {
        runway.lengthM = from
    }
}

class SetRunwayTrueHeadingCommand(
    private val runway: RunwayDefinition,
    private val from: Float,
    private val to: Float,
) : EditorCommand {
    override fun execute() {
        runway.trueHeadingDeg = to
    }

    override fun undo() {
        runway.trueHeadingDeg = from
    }
}

class SetRunwayDisplacedThresholdCommand(
    private val runway: RunwayDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        runway.displacedThresholdM = to
    }

    override fun undo() {
        runway.displacedThresholdM = from
    }
}

class SetRunwayIntersectionTakeoffCommand(
    private val runway: RunwayDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        runway.intersectionTakeoffLengthM = to
    }

    override fun undo() {
        runway.intersectionTakeoffLengthM = from
    }
}

class SetRunwayThresholdElevationCommand(
    private val runway: RunwayDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        runway.thresholdElevationFt = to
    }

    override fun undo() {
        runway.thresholdElevationFt = from
    }
}

class SetRunwayLabelPlacementCommand(
    private val runway: RunwayDefinition,
    private val from: RunwayLabelPlacement,
    private val to: RunwayLabelPlacement,
) : EditorCommand {
    override fun execute() {
        runway.labelPlacement = to
    }

    override fun undo() {
        runway.labelPlacement = from
    }
}

class SetRunwayTowerCallsignCommand(
    private val runway: RunwayDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        runway.towerCallsign = to
    }

    override fun undo() {
        runway.towerCallsign = from
    }
}

class SetRunwayTowerFrequencyCommand(
    private val runway: RunwayDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        runway.towerFrequency = to
    }

    override fun undo() {
        runway.towerFrequency = from
    }
}

object MapEditorByteIds {
    /** Next unused airport id in `0..127`, or null if none available. */
    fun nextAirportId(map: AirportMapDefinition): Byte? {
        val used = map.airports.map { it.id.toInt() and 0xff }.toHashSet()
        for (i in 0..127) if (i !in used) return i.toByte()
        return null
    }

    /** Next unused runway id within [airport], or null if none available. */
    fun nextRunwayId(airport: AirportDefinition): Byte? {
        val used = airport.runways.map { it.id.toInt() and 0xff }.toHashSet()
        for (i in 0..127) if (i !in used) return i.toByte()
        return null
    }
}
