package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array as GdxArray
import com.bombbird.terminalcontrol2.editor.EditorLayer
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
        const val EMPTY_SELECTION_HINT = "Hold and select an object on the map"

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

    /** Rows inside the properties [scrollPane] inner table. */
    private fun Actor.setScrollInnerRowSpaceAndVisibility(visible: Boolean) {
        setTableRowSpaceAndVisibility(visible, scrollInnerRowPadBottom())
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
        val showAdd = showLayerChrome &&
            (layerSelectBox.selected == EditorLayer.TERRAIN_MVA || layerSelectBox.selected == EditorLayer.RESTRICTED)
        addPolygonButton.setOuterContentRowSpaceAndVisibility(showAdd, padTopWhenVisible = 6f)
        addCircleButton.setOuterContentRowSpaceAndVisibility(showAdd, padTopWhenVisible = 6f)
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
        hideMinAltPropertyRows()
    }

    fun bindNoSelection() {
        selectionTitle.setText("No selection")
        emptySelectionHint.setText(EMPTY_SELECTION_HINT)
        setNoSelectionPropertiesLayout()
        nameField.text = ""
        xNmField.text = ""
        yNmField.text = ""
    }

    fun bindWaypoint(id: Short, name: String, xNm: Float, yNm: Float) {
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Waypoint $id")
        nameField.text = name
        setPositionNmFields(xNm, yNm)
    }

    fun bindRunway(name: String, thresholdXNm: Float, thresholdYNm: Float) {
        setHasSelectionPropertiesLayout()
        selectionTitle.setText("Runway $name")
        nameField.text = name
        setPositionNmFields(thresholdXNm, thresholdYNm)
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
                    selectionTitle = label("No selection", "MapEditorPropertiesPane").cell(padTop = 20f)
                    row()
                    emptySelectionHint = label(EMPTY_SELECTION_HINT, "SettingsOption").apply {
                        wrap = true
                        setAlignment(Align.top)
                    }.cell(width = pw - padSide * 2, padTop = 15f)
                    row()
                    layerCaptionLabel = label("Selection layer:", "SettingsOption").cell(padTop = 12f)
                    row()
                    layerSelectBox = selectBox<EditorLayer>("MapEditorLayerSelect").apply {
                        items = GdxArray<EditorLayer>().also { a ->
                            EditorLayer.entries.forEach { layer ->
                                if (layer != EditorLayer.SIDS && layer != EditorLayer.STARS) a.add(layer)
                            }
                        }
                        selected = EditorLayer.WAYPOINTS
                        addChangeListener { _, _ -> listeners.onEditorLayerChanged() }
                    }.cell(width = pw - padSide * 2, height = 48f)
                    row()
                    table {
                        defaults().pad(padVert).padLeft(padSide).padRight(padSide)
                        addPolygonButton = textButton("Add polygon", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddMinAltPolygon() }
                        }
                        addCircleButton = textButton("Add circle", "MapEditorPropertiesButton").apply {
                            addChangeListener { _, _ -> listeners.onAddMinAltCircle() }
                        }
                    }
                    syncEmptyStateToolVisibility(mapObjectSelected = false)
                    row().expandY().fillY()
                    scrollPane("SettingsPane") {
                        table {
                            debugAll()
                            defaults().padBottom(scrollInnerRowPadBottom()).padLeft(padSide).padRight(padSide)
                            insertVertexBeforeButton = textButton("Insert vertex\nbefore", "MapEditorPropertiesButton").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onInsertVertexBefore() }
                            }
                            insertVertexAfterButton = textButton("Insert vertex\nafter", "MapEditorPropertiesButton").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onInsertVertexAfter() }
                            }
                            row()

                            deleteVertexButton = textButton("Delete vertex", "MapEditorPropertiesButton").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onDeleteVertex() }
                            }
                            deleteSectorButton = textButton("Delete sector", "MapEditorPropertiesButton").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onDeleteSector() }
                            }
                            row()

                            label("Name", "SettingsOption")
                            nameField = textField("", "MapEditorProperties").apply {
                                addChangeListener { _, _ -> listeners.onNameChanged() }
                            }
                            row()

                            label("X (nm)", "SettingsOption")
                            xNmField = textField("", "MapEditorProperties").apply {
                                addChangeListener { _, _ -> listeners.onPositionChanged() }
                            }
                            row()

                            label("Y (nm)", "SettingsOption")
                            yNmField = textField("", "MapEditorProperties").apply {
                                addChangeListener { _, _ -> listeners.onPositionChanged() }
                            }
                            row()

                            minAltitudeCaption = label("Min altitude\n(ft / UNL)", "SettingsOption")
                            minAltitudeField = textField("", "MapEditorProperties").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onMinAltitudeChanged() }
                            }
                            row()

                            radiusNmCaption = label("Radius (nm)", "SettingsOption")
                            radiusNmField = textField("", "MapEditorProperties").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onRadiusChanged() }
                            }
                            row()

                            labelOffsetXCaption = label("Label offset X (nm)", "SettingsOption")
                            labelOffsetXNmField = textField("", "MapEditorProperties").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onLabelOffsetChanged() }
                            }
                            row()

                            labelOffsetYCaption = label("Label offset Y (nm)", "SettingsOption")
                            labelOffsetYNmField = textField("", "MapEditorProperties").apply {
                                isVisible = false
                                addChangeListener { _, _ -> listeners.onLabelOffsetChanged() }
                            }
                            row()
                            hideMinAltPropertyRows()
                        }
                        setOverscroll(false, false)
                        setFadeScrollBars(false)
                        setScrollingDisabled(true, false)
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
