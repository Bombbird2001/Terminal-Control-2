package com.bombbird.terminalcontrol2.editor.undo

import com.bombbird.terminalcontrol2.editor.model.ApproachDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.NmPoint
import com.bombbird.terminalcontrol2.editor.model.RunwayDefinition
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
