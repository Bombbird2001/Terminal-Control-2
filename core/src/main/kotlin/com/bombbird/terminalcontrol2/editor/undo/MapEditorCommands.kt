package com.bombbird.terminalcontrol2.editor.undo

import com.bombbird.terminalcontrol2.editor.model.AirportDefinition
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.ApproachDefinition
import com.bombbird.terminalcontrol2.editor.model.CirclingDefinition
import com.bombbird.terminalcontrol2.editor.model.DEFAULT_APPROACH_VECTOR_TRANSITION_NAME
import com.bombbird.terminalcontrol2.editor.model.GlideslopeDefinition
import com.bombbird.terminalcontrol2.editor.model.LocalizerDefinition
import com.bombbird.terminalcontrol2.editor.model.RouteTokens
import com.bombbird.terminalcontrol2.editor.model.StepDownFixDefinition
import com.bombbird.terminalcontrol2.editor.model.ensureDefaultVectorTransition
import com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.NmPoint
import com.bombbird.terminalcontrol2.editor.model.RunwayConfigDefinition
import com.bombbird.terminalcontrol2.editor.model.RunwayDefinition
import com.bombbird.terminalcontrol2.editor.model.RunwayLabelPlacement
import com.bombbird.terminalcontrol2.editor.model.TimeSlot
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
    private val airport: AirportDefinition,
    private val runway: RunwayDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    private val affectedApproaches: List<ApproachDefinition> =
        airport.approaches.filter { it.runwayName == from }

    override fun execute() {
        runway.name = to
        for (ap in affectedApproaches) ap.runwayName = to
    }

    override fun undo() {
        runway.name = from
        for (ap in affectedApproaches) ap.runwayName = from
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

class SetApproachTimeSlotCommand(
    private val approach: ApproachDefinition,
    private val from: TimeSlot,
    private val to: TimeSlot,
) : EditorCommand {
    override fun execute() {
        approach.timeSlot = to
    }

    override fun undo() {
        approach.timeSlot = from
    }
}

class SetApproachRunwayNameCommand(
    private val approach: ApproachDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        approach.runwayName = to
    }

    override fun undo() {
        approach.runwayName = from
    }
}

class SetApproachDecisionAltitudeCommand(
    private val approach: ApproachDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        approach.decisionAltitudeFt = to
    }

    override fun undo() {
        approach.decisionAltitudeFt = from
    }
}

class SetApproachRvrCommand(
    private val approach: ApproachDefinition,
    private val from: Short,
    private val to: Short,
) : EditorCommand {
    override fun execute() {
        approach.rvrM = to
    }

    override fun undo() {
        approach.rvrM = from
    }
}

class SetApproachLineupDistanceCommand(
    private val approach: ApproachDefinition,
    private val from: Float?,
    private val to: Float?,
) : EditorCommand {
    override fun execute() {
        approach.lineupDistanceNm = to
    }

    override fun undo() {
        approach.lineupDistanceNm = from
    }
}

class SetApproachVisualAfterFafCommand(
    private val approach: ApproachDefinition,
    private val from: Boolean,
    private val to: Boolean,
) : EditorCommand {
    override fun execute() {
        approach.visualAfterFaf = to
    }

    override fun undo() {
        approach.visualAfterFaf = from
    }
}

class SetApproachLocalizerEnabledCommand(
    private val approach: ApproachDefinition,
    private val from: Boolean,
    private val to: Boolean,
) : EditorCommand {
    override fun execute() {
        approach.localizerEnabled = to
    }

    override fun undo() {
        approach.localizerEnabled = from
    }
}

class SetApproachLocalizerFieldsCommand(
    private val approach: ApproachDefinition,
    private val fromHdg: Float,
    private val fromDist: Byte,
    private val toHdg: Float,
    private val toDist: Byte,
) : EditorCommand {
    override fun execute() {
        approach.localizer = LocalizerDefinition(toHdg, toDist)
    }

    override fun undo() {
        approach.localizer = LocalizerDefinition(fromHdg, fromDist)
    }
}

class SetApproachGlideslopeEnabledCommand(
    private val approach: ApproachDefinition,
    private val from: Boolean,
    private val to: Boolean,
) : EditorCommand {
    override fun execute() {
        approach.glideslopeEnabled = to
    }

    override fun undo() {
        approach.glideslopeEnabled = from
    }
}

class SetApproachGlideslopeFieldsCommand(
    private val approach: ApproachDefinition,
    private val from: GlideslopeDefinition,
    private val to: GlideslopeDefinition,
) : EditorCommand {
    override fun execute() {
        approach.glideslope = GlideslopeDefinition(to.angleDeg, to.offsetNm, to.maxInterceptAltitudeFt)
    }

    override fun undo() {
        approach.glideslope = GlideslopeDefinition(from.angleDeg, from.offsetNm, from.maxInterceptAltitudeFt)
    }
}

class SetApproachStepDownEnabledCommand(
    private val approach: ApproachDefinition,
    private val from: Boolean,
    private val to: Boolean,
) : EditorCommand {
    override fun execute() {
        approach.stepDownEnabled = to
    }

    override fun undo() {
        approach.stepDownEnabled = from
    }
}

class ReplaceStepDownFixesCommand(
    private val approach: ApproachDefinition,
    private val from: List<StepDownFixDefinition>,
    private val to: List<StepDownFixDefinition>,
) : EditorCommand {
    override fun execute() {
        approach.stepDownFixes.clear()
        approach.stepDownFixes.addAll(to.map { StepDownFixDefinition(it.altitudeFt, it.distanceNm) })
    }

    override fun undo() {
        approach.stepDownFixes.clear()
        approach.stepDownFixes.addAll(from.map { StepDownFixDefinition(it.altitudeFt, it.distanceNm) })
    }
}

class SetApproachCirclingEnabledCommand(
    private val approach: ApproachDefinition,
    private val from: Boolean,
    private val to: Boolean,
) : EditorCommand {
    override fun execute() {
        approach.circlingEnabled = to
    }

    override fun undo() {
        approach.circlingEnabled = from
    }
}

class SetApproachCirclingFieldsCommand(
    private val approach: ApproachDefinition,
    private val from: CirclingDefinition,
    private val to: CirclingDefinition,
) : EditorCommand {
    override fun execute() {
        approach.circling = CirclingDefinition(to.minBreakoutAltFt, to.maxBreakoutAltFt, to.turnDirection)
    }

    override fun undo() {
        approach.circling = CirclingDefinition(from.minBreakoutAltFt, from.maxBreakoutAltFt, from.turnDirection)
    }
}

class SetApproachRouteTokensCommand(
    private val approach: ApproachDefinition,
    private val from: RouteTokens,
    private val to: RouteTokens,
) : EditorCommand {
    override fun execute() {
        approach.routeTokens = to
    }

    override fun undo() {
        approach.routeTokens = from
    }
}

class SetApproachMissedTokensCommand(
    private val approach: ApproachDefinition,
    private val from: RouteTokens,
    private val to: RouteTokens,
) : EditorCommand {
    override fun execute() {
        approach.missedApproachTokens = to
    }

    override fun undo() {
        approach.missedApproachTokens = from
    }
}

class SetApproachTransitionTokensCommand(
    private val approach: ApproachDefinition,
    private val transitionName: String,
    private val from: RouteTokens,
    private val to: RouteTokens,
) : EditorCommand {
    override fun execute() {
        approach.transitions[transitionName] = to
        approach.ensureDefaultVectorTransition()
    }

    override fun undo() {
        approach.transitions[transitionName] = from
        approach.ensureDefaultVectorTransition()
    }
}

class AddApproachTransitionCommand(
    private val approach: ApproachDefinition,
    private val name: String,
    private val tokens: RouteTokens,
) : EditorCommand {
    init {
        require(name != DEFAULT_APPROACH_VECTOR_TRANSITION_NAME) { "reserved transition name" }
        require(name.isNotBlank()) { "blank transition name" }
    }

    private var added = false

    override fun execute() {
        if (approach.transitions.containsKey(name)) return
        approach.transitions[name] = tokens
        added = true
        approach.ensureDefaultVectorTransition()
    }

    override fun undo() {
        if (added) approach.transitions.remove(name)
        approach.ensureDefaultVectorTransition()
    }
}

class RemoveApproachTransitionCommand(
    private val approach: ApproachDefinition,
    private val name: String,
) : EditorCommand {
    init {
        require(name != DEFAULT_APPROACH_VECTOR_TRANSITION_NAME) { "cannot remove vectors" }
    }

    private lateinit var removedTokens: RouteTokens

    override fun execute() {
        removedTokens = approach.transitions[name] ?: emptyList()
        approach.transitions.remove(name)
        approach.ensureDefaultVectorTransition()
    }

    override fun undo() {
        approach.transitions[name] = removedTokens
        approach.ensureDefaultVectorTransition()
    }
}

class RenameApproachTransitionCommand(
    private val approach: ApproachDefinition,
    private val fromName: String,
    private val toName: String,
) : EditorCommand {
    init {
        require(fromName != DEFAULT_APPROACH_VECTOR_TRANSITION_NAME && toName != DEFAULT_APPROACH_VECTOR_TRANSITION_NAME) {
            "cannot rename vectors"
        }
    }

    override fun execute() {
        val t = approach.transitions.remove(fromName) ?: return
        approach.transitions[toName] = t
        approach.ensureDefaultVectorTransition()
    }

    override fun undo() {
        val t = approach.transitions.remove(toName) ?: return
        approach.transitions[fromName] = t
        approach.ensureDefaultVectorTransition()
    }
}

class AddApproachCommand(
    private val list: MutableList<ApproachDefinition>,
    private val approach: ApproachDefinition,
    private val insertAt: Int,
) : EditorCommand {
    override fun execute() {
        list.add(insertAt, approach)
    }

    override fun undo() {
        list.removeAt(insertAt)
    }
}

class RemoveApproachCommand(
    private val list: MutableList<ApproachDefinition>,
    private val approach: ApproachDefinition,
    private val index: Int,
) : EditorCommand {
    override fun execute() {
        list.removeAt(index)
    }

    override fun undo() {
        list.add(index, approach)
    }
}

enum class RunwayConfigListSection { DEPARTURE, ARRIVAL }

class AddRunwayConfigCommand(
    private val list: MutableList<RunwayConfigDefinition>,
    private val config: RunwayConfigDefinition,
    private val index: Int,
) : EditorCommand {
    override fun execute() {
        list.add(index, config)
    }

    override fun undo() {
        list.removeAt(index)
    }
}

class RemoveRunwayConfigCommand(
    private val list: MutableList<RunwayConfigDefinition>,
    private val config: RunwayConfigDefinition,
    private val index: Int,
) : EditorCommand {
    override fun execute() {
        list.removeAt(index)
    }

    override fun undo() {
        list.add(index, config)
    }
}

class SetRunwayConfigNameCommand(
    private val config: RunwayConfigDefinition,
    private val from: String,
    private val to: String,
) : EditorCommand {
    override fun execute() {
        config.name = to
    }

    override fun undo() {
        config.name = from
    }
}

class SetRunwayConfigTimeSlotCommand(
    private val config: RunwayConfigDefinition,
    private val from: TimeSlot,
    private val to: TimeSlot,
) : EditorCommand {
    override fun execute() {
        config.timeSlot = to
    }

    override fun undo() {
        config.timeSlot = from
    }
}

class AddRunwayNameToConfigSectionCommand(
    private val config: RunwayConfigDefinition,
    private val section: RunwayConfigListSection,
    private val runwayName: String,
    private val index: Int,
) : EditorCommand {
    private val list: MutableList<String>
        get() = when (section) {
            RunwayConfigListSection.DEPARTURE -> config.departureRunways
            RunwayConfigListSection.ARRIVAL -> config.arrivalRunways
        }

    override fun execute() {
        list.add(index, runwayName)
    }

    override fun undo() {
        list.removeAt(index)
    }
}

class RemoveRunwayNameFromConfigSectionCommand(
    private val config: RunwayConfigDefinition,
    private val section: RunwayConfigListSection,
    private val runwayName: String,
    private val index: Int,
) : EditorCommand {
    private val list: MutableList<String>
        get() = when (section) {
            RunwayConfigListSection.DEPARTURE -> config.departureRunways
            RunwayConfigListSection.ARRIVAL -> config.arrivalRunways
        }

    override fun execute() {
        list.removeAt(index)
    }

    override fun undo() {
        list.add(index, runwayName)
    }
}

/** Single undo/redo step that applies several commands in order (undo reverses). */
class CompositeEditorCommand(
    private val parts: List<EditorCommand>,
) : EditorCommand {
    init {
        require(parts.isNotEmpty()) { "CompositeEditorCommand requires at least one part" }
    }

    override fun execute() {
        for (c in parts) c.execute()
    }

    override fun undo() {
        for (c in parts.asReversed()) c.undo()
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

    /** Next unused runway configuration id within [airport] (`0..127`), or null if none available. */
    fun nextRunwayConfigId(airport: AirportDefinition): Byte? {
        val used = airport.runwayConfigs.map { it.id.toInt() and 0xff }.toHashSet()
        for (i in 0..127) if (i !in used) return i.toByte()
        return null
    }
}
