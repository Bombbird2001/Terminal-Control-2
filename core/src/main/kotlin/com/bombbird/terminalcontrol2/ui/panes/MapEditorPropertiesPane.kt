package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.bombbird.terminalcontrol2.editor.EditorLayer
import com.bombbird.terminalcontrol2.editor.model.RunwayLabelPlacement
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
import ktx.scene2d.textButton
import ktx.scene2d.textField
import kotlin.math.max

/**
 * Right-docked properties strip for [MapEditorScreen], modeled after [UIPane]
 * (full-height background image + overlaid content container).
 */
class MapEditorPropertiesPane {

    companion object {
        const val EMPTY_SELECTION_HINT_GENERIC = "Hold to select an object on the map"
        const val EMPTY_SELECTION_HINT_WAYPOINTS = "Hold to select a waypoint on the map"
        const val EMPTY_SELECTION_HINT_AIRPORTS = "Hold to select an airport on the map"
        const val EMPTY_SELECTION_HINT_RUNWAYS = "Hold to select a runway threshold on the map"
        const val EMPTY_SELECTION_HINT_MVA_RESTR_AREA = "Hold polygon vertex or inside circle to select area"

        /** Must match vertical padding used for inner properties rows in [build] (`padVert * 2` on defaults). */
        private const val SCROLL_INNER_PAD_VERT = 5f

        private fun scrollInnerRowPadBottom(): Float = SCROLL_INNER_PAD_VERT * 2f

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

    lateinit var nameRowCaption: Label
    lateinit var nameField: TextField
    lateinit var xNmField: TextField
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
    )

    fun getRadarCameraOffsetForZoom(zoom: Float): Float = paneWidth / 2f * zoom

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
     * @param padTopWhenVisible applied when [visible] (e.g. spacing above the first button in a row).
     */
    private fun Actor.setTableRowSpaceAndVisibility(
        visible: Boolean,
        padBottomWhenVisible: Float,
        padTopWhenVisible: Float = 0f,
    ) {
        val table = parent as? Table ?: return
        val cell = table.getCell(this) ?: return
        if (visible) {
            isVisible = true
            val prefH = when (this) {
                is Label -> prefHeight
                is TextField -> prefHeight
                is TextButton -> prefHeight
                is SelectBox<*> -> max(prefHeight, 48f)
                else -> height
            }
            cell.height(prefH).padBottom(padBottomWhenVisible).padTop(padTopWhenVisible)
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
            setTableRowSpaceAndVisibility(visible, scrollInnerRowPadBottom())
            return
        }

        var rowActor: Actor = this
        while (rowActor.parent != null && rowActor.parent !== rootTable) {
            rowActor = rowActor.parent as? Actor ?: break
        }

        val rowCell = rootTable.getCell(rowActor) ?: run {
            setTableRowSpaceAndVisibility(visible, scrollInnerRowPadBottom())
            return
        }

        if (visible) {
            rowActor.isVisible = true
            val prefH = max(
                when (rowActor) {
                    is Layout -> rowActor.prefHeight
                    else -> rowActor.height
                },
                0f,
            )
            rowCell.height(prefH).padBottom(scrollInnerRowPadBottom()).padTop(0f)
        } else {
            rowCell.height(0f).padBottom(0f).padTop(0f)
            rowActor.isVisible = false
        }

        rootTable.invalidateHierarchy()
        (rootTable.parent as? ScrollPane)?.invalidateHierarchy()
    }

    /** Rows in the outer [contentRoot] table (empty-state layer UI). */
    private fun Actor.setOuterContentRowSpaceAndVisibility(visible: Boolean, padTopWhenVisible: Float = 0f) {
        setTableRowSpaceAndVisibility(visible, scrollInnerRowPadBottom(), padTopWhenVisible)
    }

    /**
     * Shows layer picker (and optionally sector-add buttons) only when [mapObjectSelected] is false.
     * Sector-add buttons require [EditorLayer.TERRAIN_MVA] or [EditorLayer.RESTRICTED].
     */
    fun syncEmptyStateToolVisibility(mapObjectSelected: Boolean) {
        val showLayerChrome = !mapObjectSelected
        layerCaptionLabel.setOuterContentRowSpaceAndVisibility(showLayerChrome, padTopWhenVisible = 12f)
        layerSelectBox.setOuterContentRowSpaceAndVisibility(showLayerChrome)
        val showAddMinAlt = showLayerChrome &&
            (layerSelectBox.selected == EditorLayer.TERRAIN_MVA || layerSelectBox.selected == EditorLayer.RESTRICTED)
        addPolygonButton.setOuterContentRowSpaceAndVisibility(showAddMinAlt, padTopWhenVisible = 6f)
        addCircleButton.setOuterContentRowSpaceAndVisibility(showAddMinAlt, padTopWhenVisible = 6f)
        val showAddAirport = showLayerChrome && layerSelectBox.selected == EditorLayer.AIRPORTS
        addAirportButton.setOuterContentRowSpaceAndVisibility(showAddAirport, padTopWhenVisible = 6f)
        val showAddRunway = showLayerChrome && layerSelectBox.selected == EditorLayer.RUNWAYS
        addRunwayButton.setOuterContentRowSpaceAndVisibility(showAddRunway, padTopWhenVisible = 6f)
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
        airportWeatherButton.setScrollInnerRowSpaceAndVisibility(false)
        airportTrafficButton.setScrollInnerRowSpaceAndVisibility(false)
        deleteAirportButton.setScrollInnerRowSpaceAndVisibility(false)
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
    }

    fun bindNoSelection() {
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
                else -> EMPTY_SELECTION_HINT_GENERIC
            },
        )
    }

    fun bindWaypoint(id: Short, name: String, xNm: Float, yNm: Float) {
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
        airportWeatherButton.setScrollInnerRowSpaceAndVisibility(true)
        airportTrafficButton.setScrollInnerRowSpaceAndVisibility(true)
        deleteAirportButton.setScrollInnerRowSpaceAndVisibility(true)
    }

    private fun formatTrackDeg(v: Float): String {
        val t = "%.2f".format(v).trimEnd('0').trimEnd('.')
        return if (t.isEmpty()) "0" else t
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

    fun bindApproach(icao: String, name: String, xNm: Float, yNm: Float) {
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Approach $name ($icao)")
        nameField.text = name
        setPositionNmFields(xNm, yNm)
    }

    fun bindSid(icao: String, name: String, xNm: Float, yNm: Float) {
        setHasSelectionPropertiesLayout()
        xNmField.isDisabled = true
        yNmField.isDisabled = true
        selectionTitle.setText("SID $name ($icao)")
        nameField.text = name
        setPositionNmFields(xNm, yNm)
    }

    fun bindStar(icao: String, name: String, xNm: Float, yNm: Float) {
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

    fun build(uiStage: Stage, listeners: Listeners) {
        val pw = paneWidth
        val padVert = SCROLL_INNER_PAD_VERT
        val padSide = 20f
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
                    defaults().pad(padVert).padLeft(padSide).padRight(padSide)
                    layerCaptionLabel = label("Selection layer:", "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.center)
                    }.cell(padTop = 12f, growX = true)
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
                        }
                    }.cell(width = pw - padSide * 2, height = 48f)
                    row()
                    selectionTitle = label("No selection", "MapEditorPropertiesPane").cell(padTop = 20f)
                    row()
                    emptySelectionHint = label(EMPTY_SELECTION_HINT_GENERIC, "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.top)
                    }.cell(width = pw - padSide * 2, padTop = 15f)
                    row()
                    table {
                        defaults().pad(padVert).padLeft(padSide).padRight(padSide)
                        addPolygonButton = textButton("Add polygon", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddMinAltPolygon() }
                        }
                        addCircleButton = textButton("Add circle", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddMinAltCircle() }
                        }
                        row()
                        addAirportButton = textButton("Add airport", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddAirport() }
                        }
                        addRunwayButton = textButton("Add runway", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddRunway() }
                        }
                    }
                    syncEmptyStateToolVisibility(mapObjectSelected = false)
                    row().expandY().fillY()
                    val halfRowElementW = (pw - padSide * 3f) / 2f - 1
                    scrollPane("SettingsPane") {
                        table {
                            debugAll()
                            defaults().padBottom(scrollInnerRowPadBottom())
                            table {
                                insertVertexBeforeButton = textButton("Insert vertex\nbefore", "MapEditorPropertiesButton").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onInsertVertexBefore() }
                                }.cell(growX = true, width = halfRowElementW, padRight = padSide)
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
                                }.cell(growX = true, width = halfRowElementW, padRight = padSide)
                                deleteSectorButton = textButton("Delete sector", "MapEditorPropertiesButton").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onDeleteSector() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                debugAll()
                                nameRowCaption = label("Name", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                nameField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onNameChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                label("X (nm)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                xNmField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onPositionChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                label("Y (nm)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                labelOffsetYNmField = textField("", "MapEditorProperties").apply {
                                    isVisible = false
                                    addChangeListener { _, _ -> listeners.onLabelOffsetChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()

                            table {
                                airportIdCaption = label("Airport id", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                airportIdField =
                                    textField("", "MapEditorProperties").apply { isDisabled = true }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportIcaoCaption = label("ICAO", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                airportIcaoField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportIcaoChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportDisplayNameCaption = label("Airport name", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                airportDisplayNameField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportDisplayNameChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportRatioCaption = label("Traffic ratio", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                airportRatioField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportRatioChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportMaxAdvCaption = label("Max advance\ndepartures", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                airportMaxAdvField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportMaxAdvChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportElevCaption = label("Elevation (ft)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                airportElevField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onAirportElevChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                airportWeatherButton = textButton("Weather (later)", "MapEditorPropertiesButton").apply {
                                    addChangeListener { _, _ -> listeners.onAirportWeatherDummy() }
                                }.cell(growX = true, width = halfRowElementW, padRight = padSide)
                                airportTrafficButton = textButton("Traffic (later)", "MapEditorPropertiesButton").apply {
                                    addChangeListener { _, _ -> listeners.onAirportTrafficDummy() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                deleteAirportButton = textButton("Delete airport", "MapEditorPropertiesButton").apply {
                                    addChangeListener { _, _ -> listeners.onDeleteAirport() }
                                }.cell(growX = true)
                            }.cell(growX = true)
                            row()

                            table {
                                runwayParentIcaoCaption =
                                    label("Airport (ICAO)", "SettingsOption").apply {
                                        wrap = true
                                        setAlignment(Align.center)
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                runwayParentIcaoSelectBox = selectBox<String>("MapEditorLayerSelect").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayParentIcaoChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayLengthCaption = label("Length (m)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                runwayLengthField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayLengthChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayTrackCaption = label("Track (deg)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                runwayDisplacedField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayDisplacedChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayIntersectionCaption = label("Intersection takeoff (m)", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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
                                    }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
                                runwayTowerCsField = textField("", "MapEditorProperties").apply {
                                    addChangeListener { _, _ -> listeners.onRunwayTowerCsChanged() }
                                }.cell(growX = true, width = halfRowElementW)
                            }.cell(growX = true)
                            row()
                            table {
                                runwayTowerFreqCaption = label("Tower frequency", "SettingsOption").apply {
                                    wrap = true
                                    setAlignment(Align.center)
                                }.cell(expandX = true, width = halfRowElementW, padRight = padSide)
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

                            hideMinAltPropertyRows()
                            hideAirportPropertyRows()
                            hideRunwayExtendedRows()
                        }
                        setOverscroll(false, false)
                        setFadeScrollBars(false)
                        setScrollingDisabled(false, false)
                        removeMouseScrollListeners()
                    }.also { propertiesScrollPane = it }.cell(width = pw - padSide * 2f, growY = true, align = Align.top)
                }
            }
        }
    }

    fun resize() {
        val pw = paneWidth
        paneImage.setSize(pw, UI_HEIGHT)
        paneImage.setPosition(UI_WIDTH - pw, 0f)
        contentRoot.setSize(pw, UI_HEIGHT)
        contentRoot.setPosition(UI_WIDTH - pw, 0f)
    }
}
