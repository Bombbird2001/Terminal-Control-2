package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Value
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.bombbird.terminalcontrol2.editor.EditorLayer
import com.bombbird.terminalcontrol2.editor.RouteEditTarget
import com.bombbird.terminalcontrol2.editor.model.AirportDefinition
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.ApproachDefinition
import com.bombbird.terminalcontrol2.editor.model.DEFAULT_APPROACH_VECTOR_TRANSITION_NAME
import com.bombbird.terminalcontrol2.editor.model.RunwayConfigDefinition
import com.bombbird.terminalcontrol2.editor.model.RunwayLabelPlacement
import com.bombbird.terminalcontrol2.editor.model.StepDownFixDefinition
import com.bombbird.terminalcontrol2.editor.model.TimeSlot
import com.bombbird.terminalcontrol2.editor.model.TurnDirection
import com.bombbird.terminalcontrol2.editor.undo.RunwayConfigListSection
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.global.UI_WIDTH
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import ktx.scene2d.KContainer
import ktx.scene2d.KImageButton
import ktx.scene2d.KScrollPane
import ktx.scene2d.KSelectBox
import ktx.scene2d.KTextButton
import ktx.scene2d.actors
import ktx.scene2d.container
import ktx.scene2d.imageButton
import ktx.scene2d.label
import ktx.scene2d.scrollPane
import ktx.scene2d.selectBox
import ktx.scene2d.table
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.checkBox
import ktx.scene2d.textButton
import ktx.scene2d.textField
import kotlin.math.max

/**
 * Right-docked properties strip for [MapEditorScreen], modeled after [UIPane]
 * (full-height background image + overlaid content container).
 */
class MapEditorPropertiesPane {

    private lateinit var editorListeners: Listeners

    companion object {
        const val EMPTY_SELECTION_HINT_GENERIC = "Hold to select an object on the map"
        const val EMPTY_SELECTION_HINT_WAYPOINTS = "Hold to select a waypoint on the map"
        const val EMPTY_SELECTION_HINT_AIRPORTS = "Hold to select an airport on the map"
        const val EMPTY_SELECTION_HINT_RUNWAYS = "Hold to select a runway threshold on the map"
        const val EMPTY_SELECTION_HINT_MVA_RESTR_AREA = "Hold polygon vertex or inside circle to select area"
        const val EMPTY_SELECTION_HINT_APPROACHES = "Hold to select an approach on the map"

        /** Must match vertical padding used for inner properties rows in [build] (`padVert * 2` on defaults). */
        private const val SCROLL_INNER_PAD_VERT = 5f
        private const val PAD_HORIZONTAL = 20f

        /**
         * Subtracted from the pane when sizing [mainPropertiesTable] / approach rebuild columns so minimum
         * widths plus padding stay under the scroll viewport (vertical scrollbar gutter, table rounding).
         */
        private const val SCROLL_PROPERTIES_HORIZONTAL_RESERVE = 14f

        private fun scrollInnerRowPadVert(): Float = SCROLL_INNER_PAD_VERT

        private fun formatPositionNm(value: Float): String = "%.2f".format(value)

        private fun formatRadiusNm(value: Float): String = "%.1f".format(value)

        private fun formatLabelOffsetNm(value: Float): String = "%.1f".format(value)
    }

    val paneWidth: Float
        get() = max(UI_WIDTH * 0.28f, 460f)

    lateinit var paneImage: KImageButton
    lateinit var contentRoot: KContainer<Actor>

    lateinit var selectionTitle: Label
    lateinit var emptySelectionHint: Label
    lateinit var propertiesScrollPane: KScrollPane

    lateinit var mainPropertiesTable: Table
    lateinit var runwayConfigIdField: TextField
    lateinit var runwayConfigTimeSlotSelectBox: SelectBox<String>
    lateinit var runwayConfigNameField: TextField

    private lateinit var runwayConfigListTable: Table
    private lateinit var runwayConfigListCaptionLabel: Label
    lateinit var runwayConfigListBackButton: KTextButton
    lateinit var runwayConfigBackButton: KTextButton
    lateinit var runwayConfigDeleteButton: KTextButton
    private lateinit var runwayConfigDepTable: Table
    private lateinit var runwayConfigArrTable: Table
    private lateinit var runwayConfigPickHintLabel: Label
    private lateinit var runwayConfigIdCaptionLabel: Label
    private lateinit var runwayConfigNameCaptionLabel: Label
    private lateinit var runwayConfigTimeSlotCaptionLabel: Label
    private lateinit var runwayConfigDepHeaderLabel: Label
    private lateinit var runwayConfigArrHeaderLabel: Label
    private lateinit var runwayConfigEditNtzButton: TextButton
    private lateinit var runwayConfigEditParallelDepsButton: TextButton
    private lateinit var runwayConfigAddDepButton: TextButton
    private lateinit var runwayConfigAddArrButton: TextButton

    lateinit var nameRowCaption: Label
    lateinit var nameField: TextField
    lateinit var xNmCaption: Label
    lateinit var xNmField: TextField
    lateinit var yNmCaption: Label
    lateinit var yNmField: TextField

    lateinit var minAltitudeCaption: Label
    lateinit var minAltitudeField: TextField

    lateinit var radiusNmCaption: Label
    lateinit var radiusNmField: TextField

    lateinit var labelOffsetXCaption: Label
    lateinit var labelOffsetXNmField: TextField
    lateinit var labelOffsetYCaption: Label
    lateinit var labelOffsetYNmField: TextField

    lateinit var insertVertexBeforeButton: KTextButton
    lateinit var insertVertexAfterButton: KTextButton
    lateinit var deleteVertexButton: KTextButton
    lateinit var deleteSectorButton: KTextButton

    lateinit var layerCaptionLabel: Label
    lateinit var layerSelectBox: KSelectBox<EditorLayer>
    lateinit var addPolygonButton: KTextButton
    lateinit var addCircleButton: KTextButton
    lateinit var addAirportButton: KTextButton
    lateinit var addRunwayButton: KTextButton
    lateinit var addRunwayConfigButton: KTextButton

    lateinit var airportIdCaption: Label
    lateinit var airportIdField: TextField
    lateinit var airportIcaoCaption: Label
    lateinit var airportIcaoField: TextField
    lateinit var airportDisplayNameCaption: Label
    lateinit var airportDisplayNameField: TextField
    lateinit var airportRatioCaption: Label
    lateinit var airportRatioField: TextField
    lateinit var airportMaxAdvCaption: Label
    lateinit var airportMaxAdvField: TextField
    lateinit var airportElevCaption: Label
    lateinit var airportElevField: TextField
    lateinit var airportWeatherButton: KTextButton
    lateinit var airportTrafficButton: KTextButton
    lateinit var airportRwyConfigButton: KTextButton
    lateinit var deleteAirportButton: KTextButton

    lateinit var runwayParentIcaoCaption: Label
    lateinit var runwayParentIcaoSelectBox: KSelectBox<String>
    lateinit var runwayLengthCaption: Label
    lateinit var runwayLengthField: TextField
    lateinit var runwayTrackCaption: Label
    lateinit var runwayTrackField: TextField
    lateinit var runwayDisplacedCaption: Label
    lateinit var runwayDisplacedField: TextField
    lateinit var runwayIntersectionCaption: Label
    lateinit var runwayIntersectionField: TextField
    lateinit var runwayThrElevCaption: Label
    lateinit var runwayThrElevField: TextField
    lateinit var runwayLabelPlacementCaption: Label
    lateinit var runwayLabelPlacementSelectBox: KSelectBox<String>
    lateinit var runwayTowerCsCaption: Label
    lateinit var runwayTowerCsField: TextField
    lateinit var runwayTowerFreqCaption: Label
    lateinit var runwayTowerFreqField: TextField
    lateinit var deleteRunwayButton: KTextButton

    lateinit var addApproachRowTable: Table
    lateinit var addApproachButton: KTextButton
    lateinit var addApproachRunwaySelectBox: KSelectBox<String>

    lateinit var approachRootTable: Table
    lateinit var approachTimeSlotSelectBox: KSelectBox<String>
    lateinit var approachRunwaySelectBox: KSelectBox<String>
    lateinit var approachDaField: TextField
    lateinit var approachRvrField: TextField
    lateinit var approachLineupField: TextField
    lateinit var approachVisualAfterFafCheckBox: CheckBox
    lateinit var approachLocEnabledCheckBox: CheckBox
    lateinit var approachLocHdgField: TextField
    lateinit var approachLocDistField: TextField
    lateinit var approachGsEnabledCheckBox: CheckBox
    lateinit var approachGsAngleField: TextField
    lateinit var approachGsOffsetField: TextField
    lateinit var approachGsMaxAltField: TextField
    lateinit var approachStepDownEnabledCheckBox: CheckBox
    lateinit var approachStepDownTable: Table
    lateinit var approachStepDownBlockTable: Table
    lateinit var approachStepDownAddButton: KTextButton
    lateinit var approachLocFieldsTable: Table
    lateinit var approachGsFieldsTable: Table
    lateinit var approachCirclingFieldsTable: Table
    lateinit var approachCirclingEnabledCheckBox: CheckBox
    lateinit var approachCirclingMinField: TextField
    lateinit var approachCirclingMaxField: TextField
    lateinit var approachCirclingTurnSelectBox: KSelectBox<String>
    lateinit var approachEditMainRouteButton: KTextButton
    lateinit var approachEditMissedButton: KTextButton
    lateinit var approachTransitionsTable: Table
    lateinit var approachAddTransitionNameField: TextField
    lateinit var approachAddTransitionButton: KTextButton
    lateinit var approachAllowedConfigsTable: Table
    lateinit var approachReadOnlyMetaTable: Table
    lateinit var deleteApproachButton: KTextButton

    lateinit var routeEditTable: Table
    lateinit var routeEditTitleLabel: Label
    lateinit var routeEditPreviewLabel: Label
    lateinit var routeEditErrorLabel: Label
    lateinit var routeEditHdgField: TextField
    lateinit var routeEditInitHdgField: TextField
    lateinit var routeEditInitAltField: TextField
    lateinit var routeEditBackButton: TextButton
    lateinit var routeEditApplyButton: TextButton
    lateinit var routeEditAddWyptButton: TextButton
    lateinit var routeEditAddHoldButton: TextButton
    lateinit var routeEditRemoveLastButton: TextButton
    lateinit var routeEditAddHdngButton: TextButton
    lateinit var routeEditAddInitButton: TextButton

    private val approachStepDownFieldRows = mutableListOf<Pair<TextField, TextField>>()

    data class Listeners(
        val onInsertVertexBefore: () -> Unit,
        val onInsertVertexAfter: () -> Unit,
        val onDeleteVertex: () -> Unit,
        val onDeleteSector: () -> Unit,
        val onNameChanged: () -> Unit,
        val onPositionChanged: () -> Unit,
        val onMinAltitudeChanged: () -> Unit,
        val onRadiusChanged: () -> Unit,
        val onLabelOffsetChanged: () -> Unit,
        val onEditorLayerChanged: () -> Unit,
        val onAddMinAltPolygon: () -> Unit,
        val onAddMinAltCircle: () -> Unit,
        val onAddAirport: () -> Unit,
        val onAddRunway: () -> Unit,
        val onAirportIcaoChanged: () -> Unit,
        val onAirportDisplayNameChanged: () -> Unit,
        val onAirportRatioChanged: () -> Unit,
        val onAirportMaxAdvChanged: () -> Unit,
        val onAirportElevChanged: () -> Unit,
        val onAirportWeatherDummy: () -> Unit,
        val onAirportTrafficDummy: () -> Unit,
        val onDeleteAirport: () -> Unit,
        val onRunwayParentIcaoChanged: () -> Unit,
        val onRunwayLengthChanged: () -> Unit,
        val onRunwayTrackChanged: () -> Unit,
        val onRunwayDisplacedChanged: () -> Unit,
        val onRunwayIntersectionChanged: () -> Unit,
        val onRunwayThrElevChanged: () -> Unit,
        val onRunwayLabelPlacementChanged: () -> Unit,
        val onRunwayTowerCsChanged: () -> Unit,
        val onRunwayTowerFreqChanged: () -> Unit,
        val onDeleteRunway: () -> Unit,
        val onOpenRunwayConfigList: () -> Unit,
        val onRunwayConfigListBack: () -> Unit,
        val onRunwayConfigSelected: (RunwayConfigDefinition) -> Unit,
        val onRunwayConfigDetailBack: () -> Unit,
        val onAddRunwayConfiguration: () -> Unit,
        val onDeleteRunwayConfiguration: () -> Unit,
        val onRunwayConfigNameChanged: () -> Unit,
        val onRunwayConfigTimeSlotChanged: () -> Unit,
        val onRunwayConfigRemoveDepRunway: (Int) -> Unit,
        val onRunwayConfigRemoveArrRunway: (Int) -> Unit,
        val onRunwayConfigStartPickDepRunway: () -> Unit,
        val onRunwayConfigStartPickArrRunway: () -> Unit,
        val onRunwayConfigEditNtzPlaceholder: () -> Unit,
        val onRunwayConfigEditParallelDepsPlaceholder: () -> Unit,
        val onAddApproach: () -> Unit,
        val onDeleteApproach: () -> Unit,
        val onCommitApproachProperties: () -> Unit,
        val onApproachStepDownAdd: () -> Unit,
        val onApproachStepDownRemove: (Int) -> Unit,
        val onApproachTransitionAdd: () -> Unit,
        val onApproachTransitionRemove: (String) -> Unit,
        val onApproachTransitionRename: (String, String) -> Unit,
        val onApproachEditRoute: (RouteEditTarget) -> Unit,
        val onRouteEditBack: () -> Unit,
        val onRouteEditApply: () -> Unit,
        val onRouteEditPickWaypoint: (Boolean) -> Unit,
        val onRouteEditRemoveLastToken: () -> Unit,
        val onRouteEditAddHdng: () -> Unit,
        val onRouteEditAddInitClimb: () -> Unit,
    )

    fun getRadarCameraOffsetForZoom(zoom: Float): Float = paneWidth / 2f * zoom

    /**
     * Width budget for fixed columns and full-width labels inside [propertiesScrollPane].
     * Slightly less than `paneWidth - 2*pad` so content does not exceed the viewport when the vertical bar shows.
     */
    private fun propertiesScrollInnerContentWidth(): Float =
        max(200f, paneWidth - PAD_HORIZONTAL * 2f - SCROLL_PROPERTIES_HORIZONTAL_RESERVE)

    /**
     * Child area inside [routeEditTable], which applies [PAD_HORIZONTAL] left+right padding on each row.
     */
    private fun routeEditInnerContentWidth(): Float =
        max(160f, propertiesScrollInnerContentWidth() - 2f * PAD_HORIZONTAL)

    private fun routeEditHalfRowElementW(): Float =
        (routeEditInnerContentWidth() - PAD_HORIZONTAL * 3f) / 2f - 1f

    /** Matches [build] `halfRowElementW` for caption/control pairs at the current [paneWidth]. */
    private fun halfRowCaptionControlWidth(): Float =
        (propertiesScrollInnerContentWidth() - PAD_HORIZONTAL * 3f) / 2f - 1f

    private fun thirdRowCaptionControlWidth(): Float =
        (propertiesScrollInnerContentWidth() - PAD_HORIZONTAL * 3f) / 3f - 1f

    fun setNoSelectionPropertiesLayout() {
        emptySelectionHint.isVisible = true
        propertiesScrollPane.isVisible = false
    }

    fun setHasSelectionPropertiesLayout() {
        emptySelectionHint.isVisible = false
        propertiesScrollPane.isVisible = true
    }

    /**
     * Collapses or expands a table cell so hidden actors do not reserve vertical space.
     * @param padVertWhenVisible applied when [visible]
     */
    private fun Actor.setTableRowSpaceAndVisibility(
        visible: Boolean,
        padVertWhenVisible: Float,
        customPadTop: Float? = null
    ) {
        val table = parent as? Table ?: return
        val cell = table.getCell(this) ?: return
        if (visible) {
            isVisible = true
            val padTop = customPadTop ?: padVertWhenVisible
            // Prefer [Value.prefHeight] for [Layout] rows so nested [Table]s (e.g. approach sub-blocks) keep
            // correct height after dynamic rebuilds; a fixed float snapshot is often too small and overlaps.
            when {
                this is SelectBox<*> ->
                    cell.height(max(prefHeight, 48f)).padBottom(padVertWhenVisible).padTop(padTop)
                this is Layout ->
                    cell.height(Value.prefHeight).padBottom(padVertWhenVisible).padTop(padTop)
                else ->
                    cell.height(height).padBottom(padVertWhenVisible).padTop(padTop)
            }
        } else {
            cell.height(0f).padBottom(0f).padTop(0f)
            isVisible = false
        }
        table.invalidate()
    }

    /**
     * Collapses or expands a row inside the properties scrollpane.
     */
    private fun Actor.setScrollInnerRowSpaceAndVisibility(visible: Boolean) {
        // Rows are nested as tables for better width management; collapse the outer row cell in the
        // scrollpane's root table so the row immediately appears/disappears without requiring an
        // unrelated input event to trigger layout.
        isVisible = visible

        var root: Actor = this
        while (root.parent != null && root.parent !is ScrollPane) {
            root = root.parent as Actor
        }
        val rootTable = root as? Table ?: run {
            setTableRowSpaceAndVisibility(visible, scrollInnerRowPadVert())
            return
        }

        var rowActor: Actor = this
        while (rowActor.parent != null && rowActor.parent !== rootTable) {
            rowActor = rowActor.parent as? Actor ?: break
        }

        val rowCell = rootTable.getCell(rowActor) ?: run {
            setTableRowSpaceAndVisibility(visible, scrollInnerRowPadVert())
            return
        }

        if (visible) {
            rowActor.isVisible = true
            when (rowActor) {
                is SelectBox<*> ->
                    rowCell.height(max(rowActor.prefHeight, 48f))
                        .padBottom(scrollInnerRowPadVert())
                        .padTop(scrollInnerRowPadVert())
                is Layout ->
                    rowCell.height(Value.prefHeight)
                        .padBottom(scrollInnerRowPadVert())
                        .padTop(scrollInnerRowPadVert())
                else ->
                    rowCell.height(max(rowActor.height, 0f))
                        .padBottom(scrollInnerRowPadVert())
                        .padTop(scrollInnerRowPadVert())
            }
        } else {
            rowCell.height(0f).padBottom(0f).padTop(0f)
            rowActor.isVisible = false
        }

        rootTable.invalidateHierarchy()
        (rootTable.parent as? ScrollPane)?.invalidateHierarchy()
    }

    private fun Actor.setOuterContentRowSpaceAndVisibility(visible: Boolean) {
        setTableRowSpaceAndVisibility(visible, scrollInnerRowPadVert())
        (parent.parent as? Layout)?.invalidateHierarchy()
    }

    /** Rows in the outer [contentRoot] table (empty-state layer UI). */
    private fun Actor.setOuterContentSpaceAndVisibility(visible: Boolean, customPadTop: Float? = null) {
        setTableRowSpaceAndVisibility(visible, scrollInnerRowPadVert(), customPadTop)
    }

    /**
     * Shows layer picker (and optionally sector-add buttons) only when [mapObjectSelected] is false.
     * Sector-add buttons require [EditorLayer.TERRAIN_MVA] or [EditorLayer.RESTRICTED].
     */
    fun syncEmptyStateToolVisibility(
        mapObjectSelected: Boolean,
        runwayConfigListActive: Boolean = false,
        runwayConfigDetailsActive: Boolean = false,
    ) {
        val showLayerChrome = !mapObjectSelected
        layerCaptionLabel.setOuterContentSpaceAndVisibility(showLayerChrome, customPadTop = 20f)
        layerSelectBox.setOuterContentSpaceAndVisibility(showLayerChrome)
        val showAddMinAlt = showLayerChrome &&
            (layerSelectBox.selected == EditorLayer.TERRAIN_MVA || layerSelectBox.selected == EditorLayer.RESTRICTED)
        addPolygonButton.setOuterContentRowSpaceAndVisibility(showAddMinAlt)
        addCircleButton.setOuterContentRowSpaceAndVisibility(showAddMinAlt)
        val showAddAirport = showLayerChrome && layerSelectBox.selected == EditorLayer.AIRPORTS
        addAirportButton.setOuterContentRowSpaceAndVisibility(showAddAirport)
        val showAddRunway = showLayerChrome && layerSelectBox.selected == EditorLayer.RUNWAYS
        addRunwayButton.setOuterContentRowSpaceAndVisibility(showAddRunway)
        val showAddApproach = showLayerChrome && layerSelectBox.selected == EditorLayer.APPROACHES
        addApproachRowTable.setOuterContentRowSpaceAndVisibility(showAddApproach)
        addRunwayConfigButton.setOuterContentRowSpaceAndVisibility(runwayConfigListActive)
        runwayConfigListBackButton.setOuterContentRowSpaceAndVisibility(runwayConfigListActive)
        runwayConfigBackButton.setOuterContentRowSpaceAndVisibility(runwayConfigDetailsActive)
        runwayConfigDeleteButton.setOuterContentRowSpaceAndVisibility(runwayConfigDetailsActive)
    }

    private fun setRunwayConfigListRowsVisible(visible: Boolean) {
        runwayConfigListCaptionLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigListTable.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigListTable.invalidateHierarchy()
    }

    private fun setRunwayConfigDetailRowsVisible(visible: Boolean) {
        runwayConfigPickHintLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigIdCaptionLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigIdField.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigNameCaptionLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigNameField.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigTimeSlotCaptionLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigTimeSlotSelectBox.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigDepHeaderLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigDepTable.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigAddDepButton.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigArrHeaderLabel.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigArrTable.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigAddArrButton.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigEditNtzButton.setScrollInnerRowSpaceAndVisibility(visible)
        runwayConfigEditParallelDepsButton.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun setMinAltitudeRowVisible(visible: Boolean) {
        minAltitudeCaption.setScrollInnerRowSpaceAndVisibility(visible)
        minAltitudeField.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun setRadiusRowVisible(visible: Boolean) {
        radiusNmCaption.setScrollInnerRowSpaceAndVisibility(visible)
        radiusNmField.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun setLabelOffsetRowsVisible(visible: Boolean) {
        labelOffsetXCaption.setScrollInnerRowSpaceAndVisibility(visible)
        labelOffsetXNmField.setScrollInnerRowSpaceAndVisibility(visible)
        labelOffsetYCaption.setScrollInnerRowSpaceAndVisibility(visible)
        labelOffsetYNmField.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun setVertexEditButtonsVisible(visible: Boolean) {
        insertVertexBeforeButton.setScrollInnerRowSpaceAndVisibility(visible)
        insertVertexAfterButton.setScrollInnerRowSpaceAndVisibility(visible)
        deleteVertexButton.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun setDeleteSectorButtonVisible(visible: Boolean) {
        deleteSectorButton.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun setDesignatorNameRowVisible(visible: Boolean) {
        nameRowCaption.setScrollInnerRowSpaceAndVisibility(visible)
        nameField.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun hideAirportPropertyRows() {
        airportIdCaption.setScrollInnerRowSpaceAndVisibility(false)
        airportIdField.setScrollInnerRowSpaceAndVisibility(false)
        airportIcaoCaption.setScrollInnerRowSpaceAndVisibility(false)
        airportIcaoField.setScrollInnerRowSpaceAndVisibility(false)
        airportDisplayNameCaption.setScrollInnerRowSpaceAndVisibility(false)
        airportDisplayNameField.setScrollInnerRowSpaceAndVisibility(false)
        airportRatioCaption.setScrollInnerRowSpaceAndVisibility(false)
        airportRatioField.setScrollInnerRowSpaceAndVisibility(false)
        airportMaxAdvCaption.setScrollInnerRowSpaceAndVisibility(false)
        airportMaxAdvField.setScrollInnerRowSpaceAndVisibility(false)
        airportElevCaption.setScrollInnerRowSpaceAndVisibility(false)
        airportElevField.setScrollInnerRowSpaceAndVisibility(false)
        airportWeatherButton.setOuterContentRowSpaceAndVisibility(false)
        airportTrafficButton.setOuterContentRowSpaceAndVisibility(false)
        airportRwyConfigButton.setOuterContentRowSpaceAndVisibility(false)
        deleteAirportButton.setOuterContentRowSpaceAndVisibility(false)
    }

    fun hideRunwayExtendedRows() {
        runwayParentIcaoCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayParentIcaoSelectBox.setScrollInnerRowSpaceAndVisibility(false)
        runwayLengthCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayLengthField.setScrollInnerRowSpaceAndVisibility(false)
        runwayTrackCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayTrackField.setScrollInnerRowSpaceAndVisibility(false)
        runwayDisplacedCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayDisplacedField.setScrollInnerRowSpaceAndVisibility(false)
        runwayIntersectionCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayIntersectionField.setScrollInnerRowSpaceAndVisibility(false)
        runwayThrElevCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayThrElevField.setScrollInnerRowSpaceAndVisibility(false)
        runwayLabelPlacementCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayLabelPlacementSelectBox.setScrollInnerRowSpaceAndVisibility(false)
        runwayTowerCsCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayTowerCsField.setScrollInnerRowSpaceAndVisibility(false)
        runwayTowerFreqCaption.setScrollInnerRowSpaceAndVisibility(false)
        runwayTowerFreqField.setScrollInnerRowSpaceAndVisibility(false)
        deleteRunwayButton.setScrollInnerRowSpaceAndVisibility(false)
    }

    /** Hides min-alt sector–specific rows and tools; call before branching on selection type. */
    fun hideMinAltPropertyRows() {
        setMinAltitudeRowVisible(false)
        setRadiusRowVisible(false)
        setLabelOffsetRowsVisible(false)
        setVertexEditButtonsVisible(false)
        setDeleteSectorButtonVisible(false)
    }

    /** Call at the start of each selection sync before a [bind]* method. */
    fun prepareSelectionSync() {
        nameField.isDisabled = false
        xNmField.isDisabled = false
        yNmField.isDisabled = false
        airportIdField.isDisabled = true
        hideMinAltPropertyRows()
        hideAirportPropertyRows()
        hideRunwayExtendedRows()
        setDesignatorNameRowVisible(true)
        setPositionNmRowsVisible(true)
        setRunwayConfigListRowsVisible(false)
        setRunwayConfigDetailRowsVisible(false)
        hideApproachPropertyRows()
    }

    private fun hideApproachPropertyRows() {
        approachRootTable.setScrollInnerRowSpaceAndVisibility(false)
    }

    fun bindNoSelection() {
        showMainPropertiesScroll()
        selectionTitle.setText("No selection")
        applyEmptyHintForActiveLayer()
        setNoSelectionPropertiesLayout()
        nameField.text = ""
        xNmField.text = ""
        yNmField.text = ""
    }

    fun applyEmptyHintForActiveLayer() {
        emptySelectionHint.setText(
            when (layerSelectBox.selected) {
                EditorLayer.WAYPOINTS -> EMPTY_SELECTION_HINT_WAYPOINTS
                EditorLayer.AIRPORTS -> EMPTY_SELECTION_HINT_AIRPORTS
                EditorLayer.RUNWAYS -> EMPTY_SELECTION_HINT_RUNWAYS
                EditorLayer.RESTRICTED, EditorLayer.TERRAIN_MVA -> EMPTY_SELECTION_HINT_MVA_RESTR_AREA
                EditorLayer.APPROACHES -> EMPTY_SELECTION_HINT_APPROACHES
                else -> EMPTY_SELECTION_HINT_GENERIC
            },
        )
    }

    fun bindWaypoint(id: Short, name: String, xNm: Float, yNm: Float) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Waypoint $id")
        nameField.text = name
        setPositionNmFields(xNm, yNm)
    }

    fun bindRunway(
        parentIcaoList: List<String>,
        parentIcao: String,
        designator: String,
        thresholdXNm: Float,
        thresholdYNm: Float,
        lengthM: Short,
        trackDeg: Float,
        displacedM: Short,
        intersectionM: Short,
        thresholdElevFt: Short,
        labelPlacement: RunwayLabelPlacement,
        towerCallsign: String,
        towerFrequency: String,
    ) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Runway $designator ($parentIcao)")
        nameField.text = designator
        setPositionNmFields(thresholdXNm, thresholdYNm)
        fillRunwayParentIcaoSelect(parentIcaoList, parentIcao)
        runwayLengthField.text = lengthM.toString()
        runwayTrackField.text = formatTrackDeg(trackDeg)
        runwayDisplacedField.text = displacedM.toString()
        runwayIntersectionField.text = intersectionM.toString()
        runwayThrElevField.text = thresholdElevFt.toString()
        runwayLabelPlacementSelectBox.selected = runwayLabelUiString(labelPlacement)
        runwayTowerCsField.text = towerCallsign
        runwayTowerFreqField.text = towerFrequency
        runwayParentIcaoCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayParentIcaoSelectBox.setScrollInnerRowSpaceAndVisibility(true)
        runwayLengthCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayLengthField.setScrollInnerRowSpaceAndVisibility(true)
        runwayTrackCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayTrackField.setScrollInnerRowSpaceAndVisibility(true)
        runwayDisplacedCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayDisplacedField.setScrollInnerRowSpaceAndVisibility(true)
        runwayIntersectionCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayIntersectionField.setScrollInnerRowSpaceAndVisibility(true)
        runwayThrElevCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayThrElevField.setScrollInnerRowSpaceAndVisibility(true)
        runwayLabelPlacementCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayLabelPlacementSelectBox.setScrollInnerRowSpaceAndVisibility(true)
        runwayTowerCsCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayTowerCsField.setScrollInnerRowSpaceAndVisibility(true)
        runwayTowerFreqCaption.setScrollInnerRowSpaceAndVisibility(true)
        runwayTowerFreqField.setScrollInnerRowSpaceAndVisibility(true)
        deleteRunwayButton.setScrollInnerRowSpaceAndVisibility(true)
    }

    fun bindAirport(
        id: Byte,
        icao: String,
        displayName: String,
        ratio: Byte,
        maxAdvanceDepartures: Int,
        elevationFt: Short,
        xNm: Float,
        yNm: Float,
    ) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        setDesignatorNameRowVisible(false)
        selectionTitle.setText("Airport $icao")
        airportIdField.text = id.toString()
        airportIcaoField.text = icao
        airportDisplayNameField.text = displayName
        airportRatioField.text = ratio.toString()
        airportMaxAdvField.text = maxAdvanceDepartures.toString()
        airportElevField.text = elevationFt.toString()
        setPositionNmFields(xNm, yNm)
        airportIdCaption.setScrollInnerRowSpaceAndVisibility(true)
        airportIdField.setScrollInnerRowSpaceAndVisibility(true)
        airportIcaoCaption.setScrollInnerRowSpaceAndVisibility(true)
        airportIcaoField.setScrollInnerRowSpaceAndVisibility(true)
        airportDisplayNameCaption.setScrollInnerRowSpaceAndVisibility(true)
        airportDisplayNameField.setScrollInnerRowSpaceAndVisibility(true)
        airportRatioCaption.setScrollInnerRowSpaceAndVisibility(true)
        airportRatioField.setScrollInnerRowSpaceAndVisibility(true)
        airportMaxAdvCaption.setScrollInnerRowSpaceAndVisibility(true)
        airportMaxAdvField.setScrollInnerRowSpaceAndVisibility(true)
        airportElevCaption.setScrollInnerRowSpaceAndVisibility(true)
        airportElevField.setScrollInnerRowSpaceAndVisibility(true)
        airportWeatherButton.setOuterContentRowSpaceAndVisibility(true)
        airportTrafficButton.setOuterContentRowSpaceAndVisibility(true)
        airportRwyConfigButton.setOuterContentRowSpaceAndVisibility(true)
        deleteAirportButton.setOuterContentRowSpaceAndVisibility(true)
    }

    private fun formatTrackDeg(v: Float): String {
        val t = "%.2f".format(v).trimEnd('0').trimEnd('.')
        return t.ifEmpty { "0" }
    }

    private fun runwayLabelUiString(p: RunwayLabelPlacement): String = when (p) {
        RunwayLabelPlacement.LABEL_LEFT -> "Left"
        RunwayLabelPlacement.LABEL_RIGHT -> "Right"
        RunwayLabelPlacement.LABEL_BEFORE -> "Before"
    }

    fun runwayLabelPlacementFromUi(s: String): RunwayLabelPlacement = when (s) {
        "Right" -> RunwayLabelPlacement.LABEL_RIGHT
        "Before" -> RunwayLabelPlacement.LABEL_BEFORE
        else -> RunwayLabelPlacement.LABEL_LEFT
    }

    fun fillRunwayParentIcaoSelect(icaos: List<String>, selectedIcao: String) {
        val arr = GdxArray<String>()
        icaos.sorted().forEach { arr.add(it) }
        runwayParentIcaoSelectBox.items = arr
        if (selectedIcao in icaos) runwayParentIcaoSelectBox.selected = selectedIcao
        else if (arr.size > 0) runwayParentIcaoSelectBox.selected = arr[0]
    }

    fun bindMinAltPolygonVertex(
        title: String,
        vertexXNm: Float,
        vertexYNm: Float,
        minAltitudeDisplay: String,
        labelOffsetXNm: Float,
        labelOffsetYNm: Float,
        disableDeleteVertexBecauseMinPolygonSize: Boolean,
    ) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        nameField.isDisabled = true
        nameField.text = ""
        selectionTitle.setText(title)
        setPositionNmFields(vertexXNm, vertexYNm)
        setVertexEditButtonsVisible(true)
        setDeleteSectorButtonVisible(true)
        setMinAltitudeRowVisible(true)
        minAltitudeField.text = minAltitudeDisplay
        setLabelOffsetRowsVisible(true)
        labelOffsetXNmField.text = formatLabelOffsetNm(labelOffsetXNm)
        labelOffsetYNmField.text = formatLabelOffsetNm(labelOffsetYNm)
        deleteVertexButton.isDisabled = disableDeleteVertexBecauseMinPolygonSize
    }

    fun bindMinAltCircleCenter(
        title: String,
        centerXNm: Float,
        centerYNm: Float,
        minAltitudeDisplay: String,
        radiusNm: Float,
        labelOffsetXNm: Float,
        labelOffsetYNm: Float,
    ) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        nameField.isDisabled = true
        nameField.text = ""
        selectionTitle.setText(title)
        setPositionNmFields(centerXNm, centerYNm)
        setDeleteSectorButtonVisible(true)
        setMinAltitudeRowVisible(true)
        minAltitudeField.text = minAltitudeDisplay
        setRadiusRowVisible(true)
        radiusNmField.text = formatRadiusNm(radiusNm)
        setLabelOffsetRowsVisible(true)
        labelOffsetXNmField.text = formatLabelOffsetNm(labelOffsetXNm)
        labelOffsetYNmField.text = formatLabelOffsetNm(labelOffsetYNm)
    }

    fun fillAddApproachRunwaySelect(map: AirportMapDefinition) {
        val keys = map.airports.sortedBy { it.icao }.flatMap { a ->
            a.runways.sortedBy { it.name }.map { r -> "${a.icao} ${r.name}" }
        }
        val arr = GdxArray<String>()
        keys.forEach { arr.add(it) }
        addApproachRunwaySelectBox.items = arr
        if (arr.size > 0) {
            val cur = addApproachRunwaySelectBox.selected
            if (cur.isNullOrBlank() || cur !in keys) addApproachRunwaySelectBox.selected = arr[0]
        }
    }

    fun collectStepDownFixesFromUi(): List<StepDownFixDefinition>? {
        val out = mutableListOf<StepDownFixDefinition>()
        for ((a, d) in approachStepDownFieldRows) {
            val at = a.text.trim()
            val dt = d.text.trim()
            if (at.isEmpty() && dt.isEmpty()) continue
            val alt = at.toShortOrNull() ?: return null
            val dist = dt.toFloatOrNull() ?: return null
            out.add(StepDownFixDefinition(alt, dist))
        }
        return out
    }

    fun showRouteEditScroll() {
        propertiesScrollPane.actor = routeEditTable
        propertiesScrollPane.invalidateHierarchy()
    }

    fun clearRouteEditError() {
        routeEditErrorLabel.setText("")
    }

    fun setRouteEditError(message: String) {
        routeEditErrorLabel.setText(message)
    }

    /**
     * @param pickWaypointActive when true, map pick mode is active; auxiliary route buttons are disabled.
     */
    fun refreshRouteEditUi(
        paneSubtitle: String,
        routeTitle: String,
        tokenPreview: String,
        pickWaypointActive: Boolean,
    ) {
        showRouteEditScroll()
        setHasSelectionPropertiesLayout()
        emptySelectionHint.isVisible = false
        propertiesScrollPane.isVisible = true
        selectionTitle.setText(paneSubtitle)
        routeEditTitleLabel.setText(routeTitle)
        routeEditPreviewLabel.setText(tokenPreview)
        clearRouteEditError()
        val dis = pickWaypointActive
        routeEditHdgField.isDisabled = dis
        routeEditInitHdgField.isDisabled = dis
        routeEditInitAltField.isDisabled = dis
        routeEditAddWyptButton.isDisabled = dis
        routeEditAddHoldButton.isDisabled = dis
        routeEditRemoveLastButton.isDisabled = dis
        routeEditAddHdngButton.isDisabled = dis
        routeEditAddInitButton.isDisabled = dis
        routeEditApplyButton.isDisabled = dis
    }

    fun bindApproach(airport: AirportDefinition, approach: ApproachDefinition, map: AirportMapDefinition) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        approachRootTable.setScrollInnerRowSpaceAndVisibility(true)
        selectionTitle.setText("Approach ${approach.name} (${airport.icao})")
        nameField.text = approach.name
        setPositionNmFields(approach.positionNm.xNm, approach.positionNm.yNm)

        approachTimeSlotSelectBox.selected = approach.timeSlot.name

        val rwyKeys = airport.runways.sortedBy { it.name }.map { "${airport.icao} ${it.name}" }
        val rwyArr = GdxArray<String>()
        rwyKeys.forEach { rwyArr.add(it) }
        approachRunwaySelectBox.items = rwyArr
        val wantRwy = "${airport.icao} ${approach.runwayName}"
        approachRunwaySelectBox.selected = if (wantRwy in rwyKeys) wantRwy else rwyKeys.firstOrNull() ?: wantRwy

        approachDaField.text = approach.decisionAltitudeFt.toString()
        approachRvrField.text = approach.rvrM.toString()
        approachLineupField.text = approach.lineupDistanceNm?.toString() ?: ""
        approachVisualAfterFafCheckBox.isChecked = approach.visualAfterFaf

        approachLocEnabledCheckBox.isChecked = approach.localizerEnabled
        approachLocHdgField.text = approach.localizer?.headingDeg?.let { formatTrackDeg(it) } ?: ""
        approachLocDistField.text = approach.localizer?.distanceNm?.toString() ?: ""

        approachGsEnabledCheckBox.isChecked = approach.glideslopeEnabled
        approachGsAngleField.text = approach.glideslope?.angleDeg?.toString() ?: ""
        approachGsOffsetField.text = approach.glideslope?.offsetNm?.toString() ?: ""
        approachGsMaxAltField.text = approach.glideslope?.maxInterceptAltitudeFt?.toString() ?: ""

        approachStepDownEnabledCheckBox.isChecked = approach.stepDownEnabled
        rebuildApproachStepDownFields(approach)

        approachCirclingEnabledCheckBox.isChecked = approach.circlingEnabled
        val c = approach.circling
        approachCirclingMinField.text = c?.minBreakoutAltFt?.toString() ?: ""
        approachCirclingMaxField.text = c?.maxBreakoutAltFt?.toString() ?: ""
        approachCirclingTurnSelectBox.selected = when (c?.turnDirection) {
            TurnDirection.LEFT -> "LEFT"
            else -> "RIGHT"
        }

        rebuildApproachTransitionRows(approach)
        rebuildApproachAllowedConfigs(airport, approach)
        rebuildApproachReadOnlyMeta(approach)
        refreshApproachComponentRowVisibility(approach)
        fillAddApproachRunwaySelect(map)
        mainPropertiesTable.invalidateHierarchy()
        propertiesScrollPane.invalidateHierarchy()
    }

    private fun refreshApproachComponentRowVisibility(a: ApproachDefinition) {
        // Sub-blocks live inside [approachRootTable]; use [setTableRowSpaceAndVisibility] only — the scroll helper
        // walks up to [mainPropertiesTable] and would collapse the entire approach pane.
        approachLocFieldsTable.setTableRowSpaceAndVisibility(a.localizerEnabled, scrollInnerRowPadVert())
        approachGsFieldsTable.setTableRowSpaceAndVisibility(a.glideslopeEnabled, scrollInnerRowPadVert())
        approachStepDownBlockTable.setTableRowSpaceAndVisibility(a.stepDownEnabled, scrollInnerRowPadVert())
        approachCirclingFieldsTable.setTableRowSpaceAndVisibility(a.circlingEnabled, scrollInnerRowPadVert())
        mainPropertiesTable.invalidateHierarchy()
        propertiesScrollPane.invalidateHierarchy()
    }

    private fun rebuildApproachStepDownFields(approach: ApproachDefinition) {
        approachStepDownFieldRows.clear()
        approachStepDownTable.clearChildren()
        val skin = Scene2DSkin.defaultSkin
        val lblStyle = skin.get("SettingsOption", LabelStyle::class.java)
        val hw = halfRowCaptionControlWidth()
        val pv = scrollInnerRowPadVert()
        approach.stepDownFixes.forEachIndexed { index, fix ->
            val altF = TextField(fix.altitudeFt.toString(), skin, "MapEditorProperties").apply {
                addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
            }
            val distF = TextField(fix.distanceNm.toString(), skin, "MapEditorProperties").apply {
                addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
            }
            approachStepDownFieldRows.add(altF to distF)
            approachStepDownTable.add(
                Label("Fix ${index + 1} alt (ft)", lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).expandX().width(hw).padRight(PAD_HORIZONTAL).padTop(pv).padBottom(pv)
            approachStepDownTable.add(altF).growX().width(hw).padTop(pv).padBottom(pv).row()
            approachStepDownTable.add(
                Label("Fix ${index + 1} dist (nm)", lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).expandX().width(hw).padRight(PAD_HORIZONTAL).padTop(pv).padBottom(pv)
            approachStepDownTable.add(distF).growX().width(hw).padTop(pv).padBottom(pv).row()
            val rm = TextButton("Remove fix", skin, "MapEditorPropertiesButton")
            rm.addChangeListener { _, _ -> editorListeners.onApproachStepDownRemove(index) }
            approachStepDownTable.add(rm).colspan(2).growX().padTop(pv).padBottom(pv).row()
        }
        approachStepDownTable.invalidateHierarchy()
    }

    private fun rebuildApproachTransitionRows(approach: ApproachDefinition) {
        val skin = Scene2DSkin.defaultSkin
        val hw = halfRowCaptionControlWidth()
        val tw = thirdRowCaptionControlWidth()
        val pv = scrollInnerRowPadVert()
        approachTransitionsTable.clearChildren()
        val ordered = buildList {
            add(DEFAULT_APPROACH_VECTOR_TRANSITION_NAME)
            approach.transitions.keys.filter { it != DEFAULT_APPROACH_VECTOR_TRANSITION_NAME }.sorted().forEach { add(it) }
        }
        for (name in ordered) {
            val isVectors = name == DEFAULT_APPROACH_VECTOR_TRANSITION_NAME
            val block = Table().apply {
                val transitionName = if (!isVectors) TextField(name, skin, "MapEditorProperties").apply {
                    addChangeListener { _, _ ->
                        val to = text.trim()
                        if (to.isNotBlank() && to != name) editorListeners.onApproachTransitionRename(name, to.uppercase())
                    }
                }
                else Label("Vectors", skin, "SettingsOption")
                add(transitionName).growX().width(tw).padRight(PAD_HORIZONTAL / 2).padTop(pv).padBottom(pv)
            }
            val ed = TextButton("Edit route", skin, "MapEditorPropertiesButton")
            ed.addChangeListener { _, _ ->
                editorListeners.onApproachEditRoute(RouteEditTarget.ApproachTransition(name))
            }
            if (isVectors) {
                block.add(ed).growX().width(hw).padTop(pv).padBottom(pv).row()
            } else {
                val rm = TextButton("Delete", skin, "MapEditorPropertiesButton")
                rm.addChangeListener { _, _ -> editorListeners.onApproachTransitionRemove(name) }
                block.add(ed).growX().width(tw).padRight(PAD_HORIZONTAL / 2).padTop(pv).padBottom(pv)
                block.add(rm).growX().width(tw).padTop(pv).padBottom(pv).row()
            }
            approachTransitionsTable.add(block).growX().padTop(pv).padBottom(pv).row()
        }
        approachTransitionsTable.invalidateHierarchy()
    }

    private fun rebuildApproachAllowedConfigs(airport: AirportDefinition, approach: ApproachDefinition) {
        val skin = Scene2DSkin.defaultSkin
        val lblStyle = skin.get("SettingsOption", LabelStyle::class.java)
        val pv = scrollInnerRowPadVert()
        approachAllowedConfigsTable.clearChildren()
        if (approach.allowedRunwayConfigIds.isEmpty()) {
            approachAllowedConfigsTable.add(
                Label("(none — all configs)", lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).growX().padTop(pv).padBottom(pv).row()
        } else {
            for (cid in approach.allowedRunwayConfigIds.sortedBy { it }) {
                val cfg = airport.runwayConfigs.find { it.id == cid } ?: continue
                val line = "Config $cid${if (cfg.name.isBlank()) "" else " - ${cfg.name}"}"
                approachAllowedConfigsTable.add(
                    Label(line, lblStyle).apply {
                        wrap = true
                        setAlignment(Align.center)
                    },
                ).growX().padRight(PAD_HORIZONTAL).padTop(pv).padBottom(pv).row()
            }
        }
        approachAllowedConfigsTable.invalidateHierarchy()
    }

    /** File-backed fields with no editor commands yet; listed so map data is visible. */
    private fun rebuildApproachReadOnlyMeta(approach: ApproachDefinition) {
        val skin = Scene2DSkin.defaultSkin
        val lblStyle = skin.get("SettingsOption", LabelStyle::class.java)
        val pv = scrollInnerRowPadVert()
        approachReadOnlyMetaTable.clearChildren()
        val lines = buildList {
            if (approach.deprecated) {
                add("Deprecated: set in file (not editable in map editor)")
            }
            if (approach.wakeInhibitApproachNames.isNotEmpty()) {
                add("Wake inhibit: ${approach.wakeInhibitApproachNames.joinToString(", ")}")
            }
            approach.parallelWakeAffects.forEach { p ->
                add("Parallel wake affects: ${p.otherApproachName} — ${p.distanceNm} nm")
            }
        }
        if (lines.isEmpty()) {
            approachReadOnlyMetaTable.setTableRowSpaceAndVisibility(false, pv)
            approachReadOnlyMetaTable.invalidateHierarchy()
            return
        }
        approachReadOnlyMetaTable.setTableRowSpaceAndVisibility(true, pv)
        approachReadOnlyMetaTable.add(
            Label("File-only metadata", lblStyle).apply {
                wrap = true
                setAlignment(Align.center)
            },
        ).growX().padRight(PAD_HORIZONTAL).padTop(pv).padBottom(pv).row()
        lines.forEach { text ->
            approachReadOnlyMetaTable.add(
                Label(text, lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).growX().padRight(PAD_HORIZONTAL).padTop(pv).padBottom(pv).row()
        }
        approachReadOnlyMetaTable.invalidateHierarchy()
    }

    fun bindSid(icao: String, name: String, xNm: Float, yNm: Float) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        xNmField.isDisabled = true
        yNmField.isDisabled = true
        selectionTitle.setText("SID $name ($icao)")
        nameField.text = name
        setPositionNmFields(xNm, yNm)
    }

    fun bindStar(icao: String, name: String, xNm: Float, yNm: Float) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        xNmField.isDisabled = true
        yNmField.isDisabled = true
        selectionTitle.setText("STAR $name ($icao)")
        nameField.text = name
        setPositionNmFields(xNm, yNm)
    }

    private fun setPositionNmFields(xNm: Float, yNm: Float) {
        xNmField.text = formatPositionNm(xNm)
        yNmField.text = formatPositionNm(yNm)
    }

    private fun setPositionNmRowsVisible(visible: Boolean) {
        xNmCaption.setScrollInnerRowSpaceAndVisibility(visible)
        xNmField.setScrollInnerRowSpaceAndVisibility(visible)
        yNmCaption.setScrollInnerRowSpaceAndVisibility(visible)
        yNmField.setScrollInnerRowSpaceAndVisibility(visible)
    }

    fun build(uiStage: Stage, listeners: Listeners) {
        this.editorListeners = listeners
        val pw = paneWidth
        val scrollInnerW = propertiesScrollInnerContentWidth()
        val halfRowElementW = (scrollInnerW - PAD_HORIZONTAL * 3f) / 2f - 1
        val routeEditInnerW = routeEditInnerContentWidth()
        val routeEditHalfW = routeEditHalfRowElementW()
        uiStage.actors {
            paneImage = imageButton("UIPane") {
                setSize(pw, UI_HEIGHT)
                setPosition(UI_WIDTH - pw, 0f)
                addChangeListener { event, _ -> event?.handle() }
            }
            contentRoot = container {
                fill()
                setSize(pw, UI_HEIGHT)
                setPosition(UI_WIDTH - pw, 0f)
                table {
                    debugAll()
                    align(Align.top)
                    defaults().pad(scrollInnerRowPadVert()).padLeft(PAD_HORIZONTAL).padRight(PAD_HORIZONTAL)
                    layerCaptionLabel = label("Selection layer:", "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.center)
                    }.cell(growX = true, padTop = 20f)
                    row()
                    layerSelectBox = selectBox<EditorLayer>("MapEditorLayerSelect").apply {
                        items = GdxArray<EditorLayer>().also { a ->
                            EditorLayer.entries.forEach { layer ->
                                if (layer != EditorLayer.SIDS && layer != EditorLayer.STARS) a.add(layer)
                            }
                        }
                        selected = EditorLayer.WAYPOINTS
                        addChangeListener { _, _ ->
                            listeners.onEditorLayerChanged()
                            applyEmptyHintForActiveLayer()
                            syncEmptyStateToolVisibility(false)
                        }
                    }.cell(width = pw - PAD_HORIZONTAL * 2, height = 48f)
                    row()
                    selectionTitle = label("No selection", "MapEditorPropertiesPane").cell(padTop = 20f)
                    row()
                    emptySelectionHint = label(EMPTY_SELECTION_HINT_GENERIC, "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.top)
                    }.cell(width = pw - PAD_HORIZONTAL * 2, padTop = 15f)
                    row()
                    table {
                        debugAll()
                        table {
                            addPolygonButton = textButton("Add polygon", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAddMinAltPolygon() }
                            }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                            addCircleButton = textButton("Add circle", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAddMinAltCircle() }
                            }.cell(growX = true, width = halfRowElementW)
                        }
                        row()
                        table {
                            addAirportButton = textButton("Add airport", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAddAirport() }
                            }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                            addRunwayButton = textButton("Add runway", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAddRunway() }
                            }.cell(growX = true, width = halfRowElementW)
                        }
                        row()
                        addApproachRowTable = table {
                            addApproachButton = textButton("Add approach", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAddApproach() }
                            }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                            addApproachRunwaySelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                            }.cell(growX = true, width = halfRowElementW)
                        }.cell(growX = true)
                        row()
                        table {
                            airportWeatherButton = textButton("Weather (later)", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAirportWeatherDummy() }
                            }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                            airportTrafficButton = textButton("Traffic (later)", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onAirportTrafficDummy() }
                            }.cell(growX = true, width = halfRowElementW)
                        }
                        row()
                        table {
                            airportRwyConfigButton = textButton("Runway config", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onOpenRunwayConfigList() }
                            }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                            deleteAirportButton = textButton("Delete airport", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> listeners.onDeleteAirport() }
                            }.cell(growX = true, width = halfRowElementW)
                        }
                    }
                    row()
                    table {
                        defaults().pad(SCROLL_INNER_PAD_VERT)
                        runwayConfigListBackButton = textButton("Back", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onRunwayConfigListBack() }
                        }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                        addRunwayConfigButton = textButton("Add config", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddRunwayConfiguration() }
                        }.cell(growX = true, width = halfRowElementW)
                    }.cell(growX = true)
                    row()
                    table {
                        runwayConfigBackButton = textButton("Back", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onRunwayConfigDetailBack() }
                        }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                        runwayConfigDeleteButton = textButton("Delete config", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onDeleteRunwayConfiguration() }
                        }.cell(growX = true, width = halfRowElementW)
                    }.cell(growX = true)
                    row()
                    table {
                        insertVertexBeforeButton = textButton("Insert vertex\nbefore", "MapEditorPropertiesButton").apply {
                            isVisible = false
                            addChangeListener { _, _ -> listeners.onInsertVertexBefore() }
                        }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                        insertVertexAfterButton = textButton("Insert vertex\nafter", "MapEditorPropertiesButton").apply {
                            isVisible = false
                            addChangeListener { _, _ -> listeners.onInsertVertexAfter() }
                        }.cell(growX = true, width = halfRowElementW)
                    }.cell(growX = true)
                    row()
                    table {
                        deleteVertexButton = textButton("Delete vertex", "MapEditorPropertiesButton").apply {
                            isVisible = false
                            addChangeListener { _, _ -> listeners.onDeleteVertex() }
                        }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                        deleteSectorButton = textButton("Delete sector", "MapEditorPropertiesButton").apply {
                            isVisible = false
                            addChangeListener { _, _ -> listeners.onDeleteSector() }
                        }.cell(growX = true, width = halfRowElementW)
                    }.cell(growX = true)
                    row()
                    runwayConfigListCaptionLabel = label("", "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.center)
                    }.cell(expandX = true, width = pw - 2 * PAD_HORIZONTAL)
                    row()
                    runwayConfigPickHintLabel = label("", "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.center)
                    }.cell(expandX = true, width = pw - 2 * PAD_HORIZONTAL)
                    row()
                    syncEmptyStateToolVisibility(mapObjectSelected = false)
                    row().expandY().fillY()
                    scrollPane("SettingsPane") {
                        mainPropertiesTable = table {
                            debugAll()
                            defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())

                            table {
                                nameRowCaption = label("Name", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                nameField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onNameChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                xNmCaption = label("X (nm)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                xNmField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onPositionChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                yNmCaption = label("Y (nm)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                yNmField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onPositionChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                minAltitudeCaption =
                                    label("Min altitude\n(ft / UNL)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                minAltitudeField = textField("", "MapEditorProperties").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onMinAltitudeChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                radiusNmCaption = label("Radius (nm)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                radiusNmField = textField("", "MapEditorProperties").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onRadiusChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                labelOffsetXCaption =
                                    label("Label offset X (nm)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                labelOffsetXNmField = textField("", "MapEditorProperties").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onLabelOffsetChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                labelOffsetYCaption =
                                    label("Label offset Y (nm)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                labelOffsetYNmField = textField("", "MapEditorProperties").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onLabelOffsetChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                airportIdCaption = label("Airport ID", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                airportIdField =
                                    textField("", "MapEditorProperties").apply { isDisabled = true }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportIcaoCaption = label("ICAO", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                airportIcaoField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportIcaoChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportDisplayNameCaption = label("Airport name", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                airportDisplayNameField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportDisplayNameChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportRatioCaption = label("Traffic ratio", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                airportRatioField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportRatioChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportMaxAdvCaption = label("Max advance\ndepartures", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                airportMaxAdvField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportMaxAdvChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportElevCaption = label("Elevation (ft)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                airportElevField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportElevChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                runwayParentIcaoCaption =
                                    label("Airport (ICAO)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayParentIcaoSelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayParentIcaoChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayLengthCaption = label("Length (m)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayLengthField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayLengthChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayTrackCaption = label("Track (deg)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayTrackField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayTrackChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayDisplacedCaption =
                                    label("Displaced\nthreshold (m)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayDisplacedField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayDisplacedChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayIntersectionCaption = label("Intersection takeoff (m)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayIntersectionField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayIntersectionChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayThrElevCaption =
                                    label("Threshold elevation (ft)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayThrElevField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayThrElevChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayLabelPlacementCaption =
                                    label("Label placement", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayLabelPlacementSelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                    items = GdxArray<String>().also { a ->
                                        a.add("Left")
                                        a.add("Right")
                                        a.add("Before")
                                    }
                                    selected = "Left"
                                    addChangeListener { _, _ -> listeners.onRunwayLabelPlacementChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayTowerCsCaption =
                                    label("Tower callsign", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayTowerCsField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayTowerCsChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayTowerFreqCaption = label("Tower frequency", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayTowerFreqField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayTowerFreqChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                deleteRunwayButton = textButton("Delete runway", "MapEditorPropertiesButton").apply {
                                    addChangeListener { _, _ -> listeners.onDeleteRunway() }
                                }.cell(growX = true)
                            }.cell(growX = true)
                            row()

                            approachRootTable = table {
                                defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())
                                table {
                                    label("Approach time slot", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachTimeSlotSelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                        items = GdxArray<String>().also { a ->
                                            TimeSlot.entries.forEach { s -> a.add(s.name) }
                                        }
                                        selected = TimeSlot.DAY_NIGHT.name
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("Runway (ICAO name)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachRunwaySelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("DA (ft)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachDaField = textField("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("RVR (m)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachRvrField = textField("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("Lineup distance (nm)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachLineupField = textField("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("Visual after FAF", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachVisualAfterFafCheckBox = checkBox("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("Localizer (LOC)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachLocEnabledCheckBox = checkBox("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ ->
                                            editorListeners.onCommitApproachProperties()
                                        }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                approachLocFieldsTable = table {
                                    defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())
                                    table {
                                        label("LOC hdg (deg)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachLocHdgField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                    row()
                                    table {
                                        label("LOC dist (nm)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachLocDistField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                }.cell(growX = true)
                                row()
                                table {
                                    defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())
                                    label("Glideslope (GS)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachGsEnabledCheckBox = checkBox("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                approachGsFieldsTable = table {
                                    defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())
                                    table {
                                        label("GS angle (deg)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachGsAngleField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                    row()
                                    table {
                                        label("GS offset (nm)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachGsOffsetField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                    row()
                                    table {
                                        label("GS max alt (ft)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachGsMaxAltField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("Step-down fixes", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachStepDownEnabledCheckBox = checkBox("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                approachStepDownBlockTable = table {
                                    approachStepDownTable = table { }.cell(growX = true)
                                    row()
                                    approachStepDownAddButton = textButton("Add step-down fix", "MapEditorPropertiesButton").apply {
                                        addChangeListener { _, _ -> editorListeners.onApproachStepDownAdd() }
                                    }.cell(growX = true)
                                }.cell(growX = true)
                                row()
                                table {
                                    label("Circling", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachCirclingEnabledCheckBox = checkBox("", "MapEditorProperties").apply {
                                        addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                approachCirclingFieldsTable = table {
                                    defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())
                                    table {
                                        label("Circling min alt (ft)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachCirclingMinField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                    row()
                                    table {
                                        label("Circling max alt (ft)", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachCirclingMaxField = textField("", "MapEditorProperties").apply {
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                    row()
                                    table {
                                        label("Circling turn", "SettingsOption").apply {
                                            wrap = true
                                            setAlignment(Align.center)
                                        }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                        approachCirclingTurnSelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                            items = GdxArray<String>().also { a ->
                                                a.add("RIGHT")
                                                a.add("LEFT")
                                            }
                                            selected = "RIGHT"
                                            addChangeListener { _, _ -> editorListeners.onCommitApproachProperties() }
                                        }.cell(growX = true, width = halfRowElementW)
                                    }.cell(growX = true)
                                }.cell(growX = true)
                                row()
                                table {
                                    approachEditMainRouteButton = textButton("Edit main route", "MapEditorPropertiesButton").apply {
                                        addChangeListener { _, _ ->
                                            editorListeners.onApproachEditRoute(RouteEditTarget.ApproachMainRoute)
                                        }
                                    }.cell(growX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                    approachEditMissedButton = textButton("Edit missed", "MapEditorPropertiesButton").apply {
                                        addChangeListener { _, _ ->
                                            editorListeners.onApproachEditRoute(RouteEditTarget.ApproachMissed)
                                        }
                                    }.cell(growX = true, width = halfRowElementW)
                                }.cell(growX = true)
                                row()
                                label("Transitions", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = scrollInnerW)
                                row()
                                approachTransitionsTable = table { }.cell(growX = true)
                                row()
                                table {
                                    approachAddTransitionNameField = textField("", "MapEditorProperties").apply {
                                    }.cell(growX = true, width = thirdRowCaptionControlWidth(), padRight = PAD_HORIZONTAL / 2)
                                    approachAddTransitionButton = textButton("Add transition", "MapEditorPropertiesButton").apply {
                                        addChangeListener { _, _ -> editorListeners.onApproachTransitionAdd() }
                                    }.cell(growX = true, width = thirdRowCaptionControlWidth() * 2 + PAD_HORIZONTAL / 2)
                                }.cell(growX = true)
                                row()
                                label("Allowed runway configs", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = scrollInnerW)
                                row()
                                approachAllowedConfigsTable = table { }.cell(growX = true)
                                row()
                                approachReadOnlyMetaTable = table { }.cell(growX = true)
                                row()
                                table {
                                    deleteApproachButton = textButton("Delete approach", "MapEditorPropertiesButton").apply {
                                        addChangeListener { _, _ -> editorListeners.onDeleteApproach() }
                                    }.cell(growX = true)
                                }.cell(growX = true)
                            }.cell(growX = true)
                            row()

                            runwayConfigListTable = table {  }
                            row()
                            table {
                                runwayConfigIdCaptionLabel = label("Config ID", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayConfigIdField = textField("", "MapEditorProperties").apply {
                                    isDisabled = true
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayConfigNameCaptionLabel = label("Config name", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayConfigNameField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> editorListeners.onRunwayConfigNameChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }
                            row()
                            table {
                                runwayConfigTimeSlotCaptionLabel = label("Time slot", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = PAD_HORIZONTAL)
                                runwayConfigTimeSlotSelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                    items = GdxArray<String>().also { a -> TimeSlot.entries.forEach { slot -> a.add(slot.name) } }
                                    selected = TimeSlot.DAY_NIGHT.name
                                    addChangeListener { _, _ -> editorListeners.onRunwayConfigTimeSlotChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            runwayConfigDepHeaderLabel = label("Departure runways", "SettingsOption").apply {
                                wrap = true
                                setAlignment(Align.center)
                            }.cell(growX = true)
                            row()
                            runwayConfigDepTable = table {

                            }.cell(growX = true)
                            row()
                            runwayConfigAddDepButton = textButton("Add departure runway", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> editorListeners.onRunwayConfigStartPickDepRunway() }
                            }.cell(growX = true)
                            row()

                            runwayConfigArrHeaderLabel = label("Arrival runways", "SettingsOption").apply {
                                wrap = true
                                setAlignment(Align.center)
                            }.cell(growX = true)
                            row()
                            runwayConfigArrTable = table {

                            }.cell(growX = true)
                            row()
                            runwayConfigAddArrButton = textButton("Add arrival runway", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> editorListeners.onRunwayConfigStartPickArrRunway() }
                            }.cell(growX = true)
                            row()

                            runwayConfigEditNtzButton = textButton("Edit NTZ (later)", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> editorListeners.onRunwayConfigEditNtzPlaceholder() }
                            }.cell(growX = true)
                            row()
                            runwayConfigEditParallelDepsButton = textButton("Edit parallel deps (later)", "MapEditorPropertiesButton").apply {
                                addChangeListener { _, _ -> editorListeners.onRunwayConfigEditParallelDepsPlaceholder() }
                            }.cell(growX = true)
                            row()

                            setRunwayConfigListRowsVisible(false)
                            setRunwayConfigDetailRowsVisible(false)

                            hideMinAltPropertyRows()
                            hideAirportPropertyRows()
                            hideRunwayExtendedRows()
                            hideApproachPropertyRows()
                        }
                        setOverscroll(false, false)
                        setFadeScrollBars(false)
                        setScrollingDisabled(false, false)
                        removeMouseScrollListeners()
                    }.also { propertiesScrollPane = it }.cell(width = pw - PAD_HORIZONTAL * 2f, growY = true, align = Align.top)
                }
            }
        }
        val skin = Scene2DSkin.defaultSkin
        val lblStyle = skin.get("SettingsOption", LabelStyle::class.java)
        routeEditTable = Table().apply {
            defaults().padBottom(scrollInnerRowPadVert()).padTop(scrollInnerRowPadVert())
                .padLeft(PAD_HORIZONTAL).padRight(PAD_HORIZONTAL)
            routeEditTitleLabel = Label("Route", skin, "MapEditorPropertiesPane")
            add(routeEditTitleLabel).growX().row()
            routeEditPreviewLabel = Label("", lblStyle).apply {
                wrap = true
                setAlignment(Align.topLeft)
            }
            add(routeEditPreviewLabel).growX().width(routeEditInnerW).row()
            routeEditErrorLabel = Label("", lblStyle).apply {
                wrap = true
                setAlignment(Align.topLeft)
            }
            add(routeEditErrorLabel).growX().width(routeEditInnerW).row()
            val hdgRow = Table()
            hdgRow.add(
                Label("HDG leg (deg)", lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).expandX().width(routeEditHalfW).padRight(PAD_HORIZONTAL)
            routeEditHdgField = TextField("", skin, "MapEditorProperties")
            hdgRow.add(routeEditHdgField).growX().width(routeEditHalfW)
            add(hdgRow).growX().row()
            val initRow = Table()
            initRow.add(
                Label("INIT hdg / A alt", lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).expandX().width(routeEditHalfW / 2f).padRight(PAD_HORIZONTAL)
            routeEditInitHdgField = TextField("", skin, "MapEditorProperties")
            routeEditInitAltField = TextField("", skin, "MapEditorProperties")
            initRow.add(routeEditInitHdgField).growX().width(routeEditHalfW / 2f - PAD_HORIZONTAL / 2f).padRight(PAD_HORIZONTAL)
            initRow.add(routeEditInitAltField).growX().width(routeEditHalfW / 2f - PAD_HORIZONTAL / 2f)
            add(initRow).growX().row()
            val wyptRow = Table()
            routeEditAddWyptButton = TextButton("Add WYPT (map)", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditPickWaypoint(false) }
            }
            routeEditAddHoldButton = TextButton("Add HOLD (map)", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditPickWaypoint(true) }
            }
            wyptRow.add(routeEditAddWyptButton).growX().width(routeEditHalfW).padRight(PAD_HORIZONTAL)
            wyptRow.add(routeEditAddHoldButton).growX().width(routeEditHalfW)
            add(wyptRow).growX().row()
            val rmRow = Table()
            routeEditRemoveLastButton = TextButton("Remove last leg", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditRemoveLastToken() }
            }
            routeEditAddHdngButton = TextButton("Add HDG", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditAddHdng() }
            }
            rmRow.add(routeEditRemoveLastButton).growX().width(routeEditHalfW).padRight(PAD_HORIZONTAL)
            rmRow.add(routeEditAddHdngButton).growX().width(routeEditHalfW)
            add(rmRow).growX().row()
            routeEditAddInitButton = TextButton("Add INITCLIMB", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditAddInitClimb() }
            }
            add(routeEditAddInitButton).growX().row()
            val backRow = Table()
            routeEditBackButton = TextButton("Back", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditBack() }
            }
            routeEditApplyButton = TextButton("Apply", skin, "MapEditorPropertiesButton").apply {
                addChangeListener { _, _ -> editorListeners.onRouteEditApply() }
            }
            backRow.add(routeEditBackButton).growX().width(routeEditHalfW).padRight(PAD_HORIZONTAL)
            backRow.add(routeEditApplyButton).growX().width(routeEditHalfW)
            add(backRow).growX().row()
        }
    }

    fun showMainPropertiesScroll() {
        propertiesScrollPane.actor = mainPropertiesTable
        propertiesScrollPane.invalidateHierarchy()
    }

    fun bindRunwayConfigList(airport: AirportDefinition) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Runway configs (${airport.icao})")
        setDesignatorNameRowVisible(false)
        setPositionNmRowsVisible(false)
        runwayConfigListCaptionLabel.setText(
            if (airport.runwayConfigs.isNotEmpty()) "Select config to edit:" else "No configs added",
        )
        setRunwayConfigDetailRowsVisible(false)
        setRunwayConfigListRowsVisible(true)
        runwayConfigListTable.clearChildren()
        val skin = Scene2DSkin.defaultSkin
        airport.runwayConfigs
            .sortedBy { it.id.toInt() and 0xff }
            .forEach { cfg ->
                val labelText = cfg.name.ifBlank { "Config ${cfg.id}" }
                val btn = TextButton(labelText, skin, "MapEditorPropertiesButton")
                btn.addChangeListener { _, _ -> editorListeners.onRunwayConfigSelected(cfg) }
                runwayConfigListTable.add(btn).growX().padTop(SCROLL_INNER_PAD_VERT).padBottom(SCROLL_INNER_PAD_VERT).row()
            }
        // Force the label/table to get a real width/height on first show.
        runwayConfigListCaptionLabel.setOuterContentSpaceAndVisibility(true)
        runwayConfigListTable.invalidateHierarchy()
        runwayConfigListTable.setScrollInnerRowSpaceAndVisibility(true)
        mainPropertiesTable.invalidateHierarchy()
        propertiesScrollPane.invalidateHierarchy()
    }

    fun bindRunwayConfigDetail(icao: String, cfg: RunwayConfigDefinition, pickSection: RunwayConfigListSection?) {
        showMainPropertiesScroll()
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Runway config ${cfg.id} ($icao)")
        setDesignatorNameRowVisible(false)
        setPositionNmRowsVisible(false)
        setRunwayConfigListRowsVisible(false)
        setRunwayConfigDetailRowsVisible(true)
        runwayConfigPickHintLabel.setScrollInnerRowSpaceAndVisibility(pickSection != null)
        runwayConfigPickHintLabel.setText(
            when (pickSection) {
                RunwayConfigListSection.DEPARTURE -> "Hold a departure runway threshold on the map (same airport)."
                RunwayConfigListSection.ARRIVAL -> "Hold an arrival runway threshold on the map (same airport)."
                null -> ""
            },
        )
        runwayConfigIdField.text = cfg.id.toString()
        runwayConfigNameField.text = cfg.name
        runwayConfigTimeSlotSelectBox.selected = cfg.timeSlot.name
        rebuildRunwayConfigDepRows(cfg)
        rebuildRunwayConfigArrRows(cfg)
        runwayConfigDepTable.invalidateHierarchy()
        runwayConfigArrTable.invalidateHierarchy()
        (runwayConfigDepTable.parent as? Layout)?.invalidateHierarchy()
        (runwayConfigArrTable.parent as? Layout)?.invalidateHierarchy()
        mainPropertiesTable.invalidateHierarchy()
        propertiesScrollPane.invalidateHierarchy()
    }

    private fun rebuildRunwayConfigDepRows(cfg: RunwayConfigDefinition) {
        val skin = Scene2DSkin.defaultSkin
        val lblStyle = skin.get("SettingsOption", LabelStyle::class.java)
        runwayConfigDepTable.clearChildren()
        cfg.departureRunways.forEachIndexed { index, name ->
            runwayConfigDepTable.add(
                Label(name, lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).growX().padRight(PAD_HORIZONTAL).padTop(scrollInnerRowPadVert()).padBottom(scrollInnerRowPadVert())
            val rm = TextButton("Remove", skin, "MapEditorPropertiesButton")
            rm.addChangeListener { _, _ -> editorListeners.onRunwayConfigRemoveDepRunway(index) }
            runwayConfigDepTable.add(rm).growX().padTop(scrollInnerRowPadVert()).padBottom(scrollInnerRowPadVert()).row()
        }
        runwayConfigDepTable.invalidateHierarchy()
        (runwayConfigDepTable.parent as? Layout)?.invalidateHierarchy()
        if (runwayConfigDepTable.isVisible) {
            runwayConfigDepTable.setScrollInnerRowSpaceAndVisibility(true)
        }
        mainPropertiesTable.invalidateHierarchy()
        propertiesScrollPane.invalidateHierarchy()
    }

    private fun rebuildRunwayConfigArrRows(cfg: RunwayConfigDefinition) {
        val skin = Scene2DSkin.defaultSkin
        val lblStyle = skin.get("SettingsOption", LabelStyle::class.java)
        runwayConfigArrTable.clearChildren()
        cfg.arrivalRunways.forEachIndexed { index, name ->
            runwayConfigArrTable.add(
                Label(name, lblStyle).apply {
                    wrap = true
                    setAlignment(Align.center)
                },
            ).growX().padRight(PAD_HORIZONTAL).padTop(scrollInnerRowPadVert()).padBottom(scrollInnerRowPadVert())
            val rm = TextButton("Remove", skin, "MapEditorPropertiesButton")
            rm.addChangeListener { _, _ -> editorListeners.onRunwayConfigRemoveArrRunway(index) }
            runwayConfigArrTable.add(rm).growX().padTop(scrollInnerRowPadVert()).padBottom(scrollInnerRowPadVert()).row()
        }
        runwayConfigArrTable.invalidateHierarchy()
        (runwayConfigArrTable.parent as? Layout)?.invalidateHierarchy()
        if (runwayConfigArrTable.isVisible) {
            runwayConfigArrTable.setScrollInnerRowSpaceAndVisibility(true)
        }
        mainPropertiesTable.invalidateHierarchy()
        propertiesScrollPane.invalidateHierarchy()
    }

    fun resize() {
        val pw = paneWidth
        paneImage.setSize(pw, UI_HEIGHT)
        paneImage.setPosition(UI_WIDTH - pw, 0f)
        contentRoot.setSize(pw, UI_HEIGHT)
        contentRoot.setPosition(UI_WIDTH - pw, 0f)
    }
}
