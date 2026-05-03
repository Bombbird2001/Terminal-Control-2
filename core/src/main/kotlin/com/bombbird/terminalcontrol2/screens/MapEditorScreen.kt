package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.GeometryUtils
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Timer
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.bombbird.terminalcontrol2.editor.EditorLayer
import com.bombbird.terminalcontrol2.editor.model.AirportDefinition
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.ApproachDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltRestrictionType
import com.bombbird.terminalcontrol2.editor.model.MinAltSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.NmPoint
import com.bombbird.terminalcontrol2.editor.model.RunwayDefinition
import com.bombbird.terminalcontrol2.editor.model.SidDefinition
import com.bombbird.terminalcontrol2.editor.model.StarDefinition
import com.bombbird.terminalcontrol2.editor.model.WaypointDefinition
import com.bombbird.terminalcontrol2.editor.undo.MoveApproachPositionCommand
import com.bombbird.terminalcontrol2.editor.undo.AddMinAltSectorCommand
import com.bombbird.terminalcontrol2.editor.undo.InsertMinAltPolygonVertexCommand
import com.bombbird.terminalcontrol2.editor.undo.MoveMinAltCircleCenterCommand
import com.bombbird.terminalcontrol2.editor.undo.MoveMinAltPolygonVertexCommand
import com.bombbird.terminalcontrol2.editor.undo.RemoveMinAltPolygonVertexCommand
import com.bombbird.terminalcontrol2.editor.undo.RemoveMinAltSectorCommand
import com.bombbird.terminalcontrol2.editor.undo.SetMinAltCircleLabelPositionCommand
import com.bombbird.terminalcontrol2.editor.undo.SetMinAltCircleRadiusCommand
import com.bombbird.terminalcontrol2.editor.undo.SetMinAltPolygonLabelPositionCommand
import com.bombbird.terminalcontrol2.editor.undo.SetMinAltSectorMinAltitudeCommand
import com.bombbird.terminalcontrol2.editor.undo.MoveRunwayThresholdCommand
import com.bombbird.terminalcontrol2.editor.undo.MoveWaypointPositionCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameApproachCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameRunwayCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameSidCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameStarCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameWaypointCommand
import com.bombbird.terminalcontrol2.editor.undo.UndoRedoHistory
import com.bombbird.terminalcontrol2.editor.validation.AirportMapValidator
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.panes.MapEditorPropertiesPane
import com.bombbird.terminalcontrol2.ui.safeStage
import com.bombbird.terminalcontrol2.utilities.ShapeRendererBoundingBox
import com.bombbird.terminalcontrol2.utilities.mToPx
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.pxToNm
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.scene2d.KTextButton
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textArea
import ktx.scene2d.textButton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import ktx.graphics.moveTo
import ktx.scene2d.Scene2DSkin

/**
 * Map editor screen. UI/UX is modeled after [RadarScreen] (pan/zoom radar canvas + overlay UI stage).
 */
class MapEditorScreen(
    private var map: AirportMapDefinition,
) : KtxScreen, GestureListener, InputProcessor {

    companion object {
        /** Seconds to wait after touch-down on a pick before committing selection (reduces grab-while-panning). */
        private const val PICK_SELECT_DELAY_SEC = 0.15f
        /** If the finger moves farther than this (px) before the delay elapses, treat as pan — cancel pick. */
        private const val PICK_CANCEL_MOVE_PX = 14f
        private const val PICK_THRESHOLD_PX = 16f
        /** Screen-space radius for snapping a min-alt polygon vertex to vertices of other min-alt polygons while dragging. */
        private const val MIN_ALT_VERTEX_SNAP_RADIUS_PX = 16f
        private const val CIRCLE_EDGE_GRAB_PX = 16f
        private const val NEW_CIRCLE_RADIUS_NM = 3f
        private const val NEW_TRIANGLE_CIRCUMRADIUS_NM = 0.4f

        const val MAP_EDITOR_MIN_ALT_LABEL_MVA = "MinAltSector"
        const val MAP_EDITOR_MIN_ALT_LABEL_MVA_SELECTED = "MapEditorMinAltSectorSelected"
        const val MAP_EDITOR_MIN_ALT_LABEL_RESTR = "MinAltSectorRestr"
        const val MAP_EDITOR_MIN_ALT_LABEL_RESTR_SELECTED = "MapEditorMinAltSectorSelected"
    }

    private val radarDisplayStage = safeStage(GAME.batch)
    private val uiStage = safeStage(GAME.batch)
    private val shapeRenderer = ShapeRendererBoundingBox(6000)
    private val radarCam = radarDisplayStage.camera as OrthographicCamera

    private val gestureDetector = GestureDetector(40f, 0.2f, 1.1f, 0.15f, this)
    private val inputMultiplexer = InputMultiplexer()

    private val history = UndoRedoHistory()

    /** Cleared by [dummySave]; set when the document differs from the last saved baseline. */
    private var documentDirty = false

    // Camera parameters (mirrors RadarScreen style)
    private var cameraAnimating = false
    private var targetZoom: Float
    private var zoomRate = 0f
    private var targetCenter = Vector2()
    private var panRate = Vector2()
    private var prevInitDist = 0f
    private var prevZoom = 1f

    // Selection / drag state
    private sealed interface Selected {
        data class Waypoint(val wpt: WaypointDefinition) : Selected
        data class Runway(val rwy: RunwayDefinition) : Selected
        data class MinAltPolygonVertex(val sector: MinAltPolygonSectorDefinition, val vertexIndex: Int) : Selected
        data class MinAltCircle(val sector: MinAltCircleSectorDefinition) : Selected
        data class Approach(val airport: AirportDefinition, val approach: ApproachDefinition) : Selected
        data class Sid(val airport: AirportDefinition, val sid: SidDefinition) : Selected
        data class Star(val airport: AirportDefinition, val star: StarDefinition) : Selected
    }

    private val activeLayer: EditorLayer get() = propertiesPane.layerSelectBox.selected

    private var selected: Selected? = null
    private var draggingPointer: Int? = null
    /** True while the user has touch down on a selected map object (blocks radar pan/zoom). */
    private var isDraggingMapObject = false
    private var dragStartNm: NmPoint? = null

    /** Hit candidate waiting for [PICK_SELECT_DELAY_SEC] before [setSelected] (avoids accidental drags when panning). */
    private var pendingSelect: Selected? = null
    private var pendingSelectPointer = -1
    private var pickDownScreenX = 0
    private var pickDownScreenY = 0
    private var pendingSelectTask: Timer.Task? = null

    private var syncingFields = false

    private var undoButton: KTextButton? = null
    private var redoButton: KTextButton? = null

    private val propertiesPane = MapEditorPropertiesPane()

    private var problemsField: TextArea? = null
    private var needsValidation = true

    private val polygonCentroidScratch = FloatArray(64)
    private val centroidVec2 = Vector2()
    private val savedBatchProjection = Matrix4()

    init {
        propertiesPane.build(
            uiStage,
            MapEditorPropertiesPane.Listeners(
                onInsertVertexBefore = { insertPolygonVertexBefore() },
                onInsertVertexAfter = { insertPolygonVertexAfter() },
                onDeleteVertex = { deleteSelectedPolygonVertex() },
                onDeleteSector = { deleteSelectedMinAltSector() },
                onNameChanged = { applyNameField() },
                onPositionChanged = { applyPositionFields() },
                onMinAltitudeChanged = { applyMinAltitudeField() },
                onRadiusChanged = { applyRadiusNmField() },
                onLabelOffsetChanged = { applyLabelOffsetFields() },
                onEditorLayerChanged = { onEditorLayerChanged() },
                onAddMinAltPolygon = { addNewMinAltPolygonAtScreenCenter() },
                onAddMinAltCircle = { addNewMinAltCircleAtScreenCenter() },
            ),
        )
        buildToolbar()
        buildProblemsPane()
        radarCam.apply {
            zoom = nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            targetZoom = zoom
            moveTo(Vector2(0f, 0f), propertiesPane.getRadarCameraOffsetForZoom(zoom))
            update()
        }
        shapeRenderer.projectionMatrix = radarDisplayStage.camera.combined
        syncFieldsFromSelection()
    }

    fun hasUnsavedChanges(): Boolean = documentDirty

    /** TODO Placeholder until file I/O is implemented; clears the dirty flag. */
    fun dummySave() {
        documentDirty = false
    }

    override fun show() {
        inputMultiplexer.clear()
        inputMultiplexer.addProcessor(uiStage)
        inputMultiplexer.addProcessor(radarDisplayStage)
        inputMultiplexer.addProcessor(gestureDetector)
        inputMultiplexer.addProcessor(this)
        Gdx.input.inputProcessor = inputMultiplexer
        updateUndoRedoButtons()
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        runCameraAnimations(delta)
        updateBoundingRect()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        drawSectors()
        drawShoreline()
        drawMinAltSectors()
        drawRunwaysAndWaypoints()
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        drawSelectedMinAltPolygonVertexHandles()
        shapeRenderer.end()

        drawMinAltSectorLabelsOnRadar()

        if (needsValidation) {
            updateProblems()
            needsValidation = false
        }

        uiStage.act(delta)
        uiStage.draw()
    }

    override fun resize(width: Int, height: Int) {
        ScreenSize.updateScreenSizeParameters(width, height)
        radarDisplayStage.viewport.setWorldSize(UI_WIDTH, UI_HEIGHT)
        radarDisplayStage.viewport.update(width, height)
        radarDisplayStage.camera.update()
        uiStage.viewport.setWorldSize(UI_WIDTH, UI_HEIGHT)
        uiStage.viewport.update(width, height, true)
        uiStage.camera.update()
        propertiesPane.resize()
        shapeRenderer.projectionMatrix = radarDisplayStage.camera.combined
    }

    override fun dispose() {
        cancelPendingSelect()
        radarDisplayStage.clear()
        uiStage.clear()
        radarDisplayStage.disposeSafely()
        uiStage.disposeSafely()
        shapeRenderer.disposeSafely()
    }

    private fun markDirty() {
        documentDirty = true
    }

    private fun updateBoundingRect() {
        val halfW = radarCam.viewportWidth * radarCam.zoom / 2f
        val halfH = radarCam.viewportHeight * radarCam.zoom / 2f
        shapeRenderer.setBoundingRect(radarCam.position.x - halfW, radarCam.position.y - halfH, halfW * 2f, halfH * 2f)
    }

    private fun unprojectFromRadarCamera(screenX: Float, screenY: Float): Vector2 {
        radarCam.apply {
            val scaleFactor = UI_HEIGHT / HEIGHT
            return Vector2(
                (screenX - WIDTH / 2) * zoom * scaleFactor + position.x,
                (HEIGHT / 2 - screenY) * zoom * scaleFactor + position.y
            )
        }
    }

    private fun clampUpdateCamera(deltaZoom: Float) {
        radarCam.apply {
            val oldZoom = zoom
            zoom += deltaZoom
            zoom = MathUtils.clamp(zoom, nmToPx(MIN_ZOOM_NM) / UI_HEIGHT, nmToPx(MAX_ZOOM_NM) / UI_HEIGHT)
            translate(propertiesPane.getRadarCameraOffsetForZoom(zoom - oldZoom), 0f)
            update()
            shapeRenderer.projectionMatrix = combined
        }
    }

    private fun initiateCameraAnimation(targetScreenX: Float, targetScreenY: Float) {
        radarCam.apply {
            targetZoom = if (zoom > (nmToPx(ZOOM_THRESHOLD_NM) / UI_HEIGHT)) nmToPx(DEFAULT_ZOOM_IN_NM) / UI_HEIGHT
            else nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            val worldCoord = unprojectFromRadarCamera(targetScreenX, targetScreenY)
            targetCenter.set(worldCoord.x + propertiesPane.getRadarCameraOffsetForZoom(targetZoom), worldCoord.y)
            zoomRate = (targetZoom - zoom) / CAM_ANIM_TIME
            panRate.set((targetCenter.x - position.x) / CAM_ANIM_TIME, (targetCenter.y - position.y) / CAM_ANIM_TIME)
            cameraAnimating = true
        }
    }

    private fun runCameraAnimations(delta: Float) {
        if (!cameraAnimating) return
        radarCam.apply {
            if ((zoomRate < 0 && zoom > targetZoom) || (zoomRate > 0 && zoom < targetZoom)) {
                val neededDelta = max(0f, minOf((targetZoom - zoom) / zoomRate, delta))
                zoom += zoomRate * neededDelta
                translate(panRate.x * neededDelta, panRate.y * neededDelta)
                clampUpdateCamera(0f)
            } else cameraAnimating = false
        }
    }

    private fun buildToolbar() {
        uiStage.actors {
            table {
                setFillParent(true)
                top().left().pad(20f)
                defaults().padRight(8f)
                textButton("Menu", "MapEditorMenuButton").addChangeListener { _, _ ->
                    GAME.getScreen<MapEditorPauseScreen>().editor = this@MapEditorScreen
                    GAME.setScreen<MapEditorPauseScreen>()
                }
                undoButton = textButton("Undo", "MapEditorMenuButton").apply {
                    addChangeListener { _, _ -> performUndo() }
                }
                redoButton = textButton("Redo", "MapEditorMenuButton").apply {
                    addChangeListener { _, _ -> performRedo() }
                }
            }
        }
    }

    private fun onEditorLayerChanged() {
        cancelPendingSelect()
        setSelected(null)
        needsValidation = true
    }

    private fun performUndo() {
        history.undo()
        discardStaleMinAltSelectionIfNeeded()
        markDirty()
        syncFieldsFromSelection()
        needsValidation = true
        updateUndoRedoButtons()
    }

    private fun performRedo() {
        history.redo()
        discardStaleMinAltSelectionIfNeeded()
        markDirty()
        syncFieldsFromSelection()
        needsValidation = true
        updateUndoRedoButtons()
    }

    /** After undo/redo, clear min-alt selection if the sector was removed or the vertex index is no longer valid. */
    private fun discardStaleMinAltSelectionIfNeeded() {
        when (val sel = selected) {
            is Selected.MinAltPolygonVertex -> {
                val sectorStillInMap = map.minAltSectors.any { it === sel.sector }
                val indexOk = sectorStillInMap && sel.vertexIndex >= 0 && sel.vertexIndex < sel.sector.verticesNm.size
                if (!sectorStillInMap || !indexOk) setSelected(null)
            }
            is Selected.MinAltCircle -> {
                if (map.minAltSectors.none { it === sel.sector }) setSelected(null)
            }
            else -> Unit
        }
    }

    private fun updateUndoRedoButtons() {
        undoButton?.isDisabled = !history.canUndo()
        redoButton?.isDisabled = !history.canRedo()
    }

    private fun finalizeDragIfNeeded() {
        val sel = selected ?: return
        val start = dragStartNm ?: return
        val end = when (sel) {
            is Selected.Waypoint -> NmPoint(sel.wpt.positionNm.xNm, sel.wpt.positionNm.yNm)
            is Selected.Runway -> NmPoint(sel.rwy.thresholdNm.xNm, sel.rwy.thresholdNm.yNm)
            is Selected.MinAltPolygonVertex -> {
                val v = sel.sector.verticesNm[sel.vertexIndex]
                NmPoint(v.xNm, v.yNm)
            }
            is Selected.MinAltCircle -> NmPoint(sel.sector.centerNm.xNm, sel.sector.centerNm.yNm)
            is Selected.Approach -> NmPoint(sel.approach.positionNm.xNm, sel.approach.positionNm.yNm)
            is Selected.Sid, is Selected.Star -> return
        }
        if (start.xNm == end.xNm && start.yNm == end.yNm) return
        when (sel) {
            is Selected.Waypoint -> history.execute(MoveWaypointPositionCommand(sel.wpt, start, end))
            is Selected.Runway -> history.execute(MoveRunwayThresholdCommand(sel.rwy, start, end))
            is Selected.MinAltPolygonVertex -> history.execute(
                MoveMinAltPolygonVertexCommand(sel.sector, sel.vertexIndex, start, end),
            )
            is Selected.MinAltCircle -> history.execute(MoveMinAltCircleCenterCommand(sel.sector, start, end))
            is Selected.Approach -> history.execute(MoveApproachPositionCommand(sel.approach, start, end))
            is Selected.Sid, is Selected.Star -> Unit
        }
        markDirty()
        updateUndoRedoButtons()
    }

    private fun revertDragInProgress() {
        val start = dragStartNm ?: return
        val sel = selected ?: return
        when (sel) {
            is Selected.Waypoint -> sel.wpt.positionNm = start
            is Selected.Runway -> sel.rwy.thresholdNm = start
            is Selected.MinAltPolygonVertex -> sel.sector.verticesNm[sel.vertexIndex] = start
            is Selected.MinAltCircle -> sel.sector.centerNm = start
            is Selected.Approach -> sel.approach.positionNm = start
            is Selected.Sid, is Selected.Star -> Unit
        }
        syncFieldsFromSelection()
        needsValidation = true
    }

    private fun endDragForPointer(pointer: Int, applyCommand: Boolean) {
        if (draggingPointer != pointer) return
        if (applyCommand) finalizeDragIfNeeded()
        isDraggingMapObject = false
        draggingPointer = null
        dragStartNm = null
    }

    // --- GestureListener / InputProcessor ---

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        // Must not return true here while dragging: [GestureDetector.touchUp] would short-circuit
        // [InputMultiplexer] and [MapEditorScreen.touchUp] would never run (stuck drag state).
        if (isDraggingMapObject) return false
        if (count == 2 && !cameraAnimating) initiateCameraAnimation(x, y)
        return true
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        // When false, [GestureDetector.touchDragged] returns false so multiplexer still delivers
        // touchDragged to [MapEditorScreen] after the finger leaves the gesture tap square (~40px).
        if (isDraggingMapObject) return false
        val z = radarCam.zoom
        radarCam.translate(-deltaX * z * UI_WIDTH / WIDTH, deltaY * z * UI_HEIGHT / HEIGHT)
        clampUpdateCamera(0f)
        return true
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        if (isDraggingMapObject) return false
        if (initialDistance != prevInitDist) {
            prevInitDist = initialDistance
            prevZoom = radarCam.zoom
        }
        val ratio = initialDistance / distance
        if (ratio !in 0.95f..1.05f) {
            clampUpdateCamera(prevZoom * ratio - radarCam.zoom)
        }
        return true
    }

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int) = false
    override fun longPress(x: Float, y: Float) = false
    override fun fling(velocityX: Float, velocityY: Float, button: Int) = false
    override fun panStop(x: Float, y: Float, pointer: Int, button: Int) = false
    override fun pinch(initialPointer1: Vector2?, initialPointer2: Vector2?, pointer1: Vector2?, pointer2: Vector2?) = false
    override fun pinchStop() = Unit

    override fun keyDown(keycode: Int) = false
    override fun keyUp(keycode: Int) = false
    override fun keyTyped(character: Char) = false

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pendingSelect != null && pendingSelectPointer == pointer) {
            cancelPendingSelect()
        }
        endDragForPointer(pointer, applyCommand = true)
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pendingSelect != null && pendingSelectPointer == pointer) {
            cancelPendingSelect()
        }
        revertDragInProgress()
        endDragForPointer(pointer, applyCommand = false)
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (pendingSelect != null && pendingSelectPointer == pointer) {
            val move = hypot(
                (screenX - pickDownScreenX).toDouble(),
                (screenY - pickDownScreenY).toDouble(),
            ).toFloat()
            if (move > PICK_CANCEL_MOVE_PX) cancelPendingSelect()
        }
        if (draggingPointer != pointer) return false
        val sel = selected ?: return false
        val worldPx = unprojectFromRadarCamera(screenX.toFloat(), screenY.toFloat())
        val xNm = pxToNm(worldPx.x)
        val yNm = pxToNm(worldPx.y)
        when (sel) {
            is Selected.Waypoint -> sel.wpt.positionNm = NmPoint(xNm, yNm)
            is Selected.Runway -> sel.rwy.thresholdNm = NmPoint(xNm, yNm)
            is Selected.MinAltPolygonVertex ->
                sel.sector.verticesNm[sel.vertexIndex] = snapMinAltPolygonVertexNm(sel, worldPx)
            is Selected.MinAltCircle -> sel.sector.centerNm = NmPoint(xNm, yNm)
            is Selected.Approach -> sel.approach.positionNm = NmPoint(xNm, yNm)
            is Selected.Sid, is Selected.Star -> return false
        }
        syncFieldsFromSelection()
        needsValidation = true
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int) = false

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (isDraggingMapObject) return true
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) return false
        clampUpdateCamera(amountY * 0.06f)
        return true
    }

    // --- Drawing ---

    private fun toPx(p: NmPoint): Vector2 = Vector2(nmToPx(p.xNm), nmToPx(p.yNm))

    private fun drawClosedNmPolygon(vertices: List<NmPoint>) {
        if (vertices.size < 2) return
        val n = vertices.size
        for (i in vertices.indices) {
            val a = toPx(vertices[i])
            val b = toPx(vertices[(i + 1) % n])
            shapeRenderer.line(a.x, a.y, b.x, b.y)
        }
    }

    private fun drawRunwaysAndWaypoints() {
        val rwyWidthPx = RWY_WIDTH_PX_ZOOM_1 + (radarCam.zoom - 1f) * RWY_WIDTH_CHANGE_PX_PER_ZOOM

        shapeRenderer.color = RUNWAY_INACTIVE
        for (arpt in map.airports) {
            for (rwy in arpt.runways) {
                val thr = toPx(rwy.thresholdNm)
                val lengthPx = mToPx(rwy.lengthM.toFloat())
                val angleDeg = Vector2(Vector2.Y).rotateDeg(-rwy.trueHeadingDeg).angleDeg()
                shapeRenderer.rect(thr.x, thr.y - rwyWidthPx / 2f, 0f, rwyWidthPx / 2f, lengthPx, rwyWidthPx, 1f, 1f, angleDeg)
            }
        }

        shapeRenderer.color = Color.GRAY
        for (wpt in map.waypoints) {
            val pos = toPx(wpt.positionNm)
            shapeRenderer.circle(pos.x, pos.y, 4f)
        }

        shapeRenderer.color = Color.WHITE
        for (arpt in map.airports) {
            for (rwy in arpt.runways) {
                val thr = toPx(rwy.thresholdNm)
                shapeRenderer.circle(thr.x, thr.y, 8f)
            }
        }

        // Selection highlight
        val sel = selected
        if (sel != null) {
            shapeRenderer.color = Color.YELLOW
            val pos = when (sel) {
                is Selected.Waypoint -> toPx(sel.wpt.positionNm)
                is Selected.Runway -> toPx(sel.rwy.thresholdNm)
                is Selected.MinAltPolygonVertex -> toPx(sel.sector.verticesNm[sel.vertexIndex])
                is Selected.MinAltCircle -> toPx(sel.sector.centerNm)
                is Selected.Approach -> toPx(sel.approach.positionNm)
                is Selected.Sid -> {
                    val i = sel.airport.sids.indexOf(sel.sid)
                    toPx(if (i >= 0) sidPickNm(sel.airport, i, sel.airport.sids.size) else sel.airport.positionNm)
                }
                is Selected.Star -> {
                    val i = sel.airport.stars.indexOf(sel.star)
                    toPx(if (i >= 0) starPickNm(sel.airport, i, sel.airport.stars.size) else sel.airport.positionNm)
                }
            }
            shapeRenderer.circle(pos.x, pos.y, 14f)
        }
    }

    private fun buildProblemsPane() {
        uiStage.actors {
            table {
                setFillParent(true)
                left().bottom().pad(20f)
                defaults().pad(6f)

                label("Problems", "MenuHeader").cell(padLeft = 20f)
                row()
                problemsField = textArea("", "MapEditorProblems").cell(width = 500f, height = 300f).apply {
                    isDisabled = true
                }
            }
        }
    }

    private fun updateProblems() {
        val probs = AirportMapValidator.validate(map)
        val text = if (probs.isEmpty()) "No problems found."
        else probs.take(50).joinToString("\n") { "${it.severity}: ${it.message}" } +
            if (probs.size > 50) "\n... (${probs.size - 50} more)" else ""
        problemsField?.text = text
    }

    private fun setSelected(newSelection: Selected?) {
        selected = newSelection
        draggingPointer = null
        isDraggingMapObject = false
        dragStartNm = null
        syncFieldsFromSelection()
    }

    private fun cancelPendingSelect() {
        pendingSelectTask?.cancel()
        pendingSelectTask = null
        pendingSelect = null
        pendingSelectPointer = -1
    }

    /** Commits [pendingSelect] after delay if the finger is still down (see [PICK_SELECT_DELAY_SEC]). */
    private fun commitPendingSelectIfStillValid() {
        val sel = pendingSelect ?: return
        val pointer = pendingSelectPointer
        if (pointer < 0 || !Gdx.input.isTouched(pointer)) {
            cancelPendingSelect()
            return
        }
        pendingSelect = null
        pendingSelectTask = null
        pendingSelectPointer = -1

        when (sel) {
            is Selected.Sid, is Selected.Star -> {
                selected = sel
                draggingPointer = null
                isDraggingMapObject = false
                dragStartNm = null
                syncFieldsFromSelection()
            }
            else -> {
                selected = sel
                draggingPointer = pointer
                isDraggingMapObject = true
                dragStartNm = when (sel) {
                    is Selected.Waypoint -> NmPoint(sel.wpt.positionNm.xNm, sel.wpt.positionNm.yNm)
                    is Selected.Runway -> NmPoint(sel.rwy.thresholdNm.xNm, sel.rwy.thresholdNm.yNm)
                    is Selected.MinAltPolygonVertex -> {
                        val v = sel.sector.verticesNm[sel.vertexIndex]
                        NmPoint(v.xNm, v.yNm)
                    }
                    is Selected.MinAltCircle -> NmPoint(sel.sector.centerNm.xNm, sel.sector.centerNm.yNm)
                    is Selected.Approach -> NmPoint(sel.approach.positionNm.xNm, sel.approach.positionNm.yNm)
                    is Selected.Sid, is Selected.Star -> null
                }
                syncFieldsFromSelection()
            }
        }
    }

    private fun syncFieldsFromSelection() {
        syncingFields = true
        try {
            propertiesPane.prepareSelectionSync()
            when (val sel = selected) {
                null -> propertiesPane.bindNoSelection()
                is Selected.Waypoint -> propertiesPane.bindWaypoint(
                    id = sel.wpt.id,
                    name = sel.wpt.name,
                    xNm = sel.wpt.positionNm.xNm,
                    yNm = sel.wpt.positionNm.yNm,
                )
                is Selected.Runway -> propertiesPane.bindRunway(
                    name = sel.rwy.name,
                    thresholdXNm = sel.rwy.thresholdNm.xNm,
                    thresholdYNm = sel.rwy.thresholdNm.yNm,
                )
                is Selected.MinAltPolygonVertex -> {
                    val sector = sel.sector
                    val t = sector.restrictionType
                    val title =
                        "${if (t == MinAltRestrictionType.MVA) "MVA" else "Restricted"} polygon vertex ${sel.vertexIndex + 1}/${sector.verticesNm.size}"
                    val v = sector.verticesNm[sel.vertexIndex]
                    val anchor = minAltGeometricAnchorNm(sector)
                    val lp = sector.labelPositionNm
                    propertiesPane.bindMinAltPolygonVertex(
                        title = title,
                        vertexXNm = v.xNm,
                        vertexYNm = v.yNm,
                        minAltitudeDisplay = sector.minAltitudeFt?.toString() ?: "UNL",
                        labelOffsetXNm = (lp?.xNm ?: anchor.xNm) - anchor.xNm,
                        labelOffsetYNm = (lp?.yNm ?: anchor.yNm) - anchor.yNm,
                        disableDeleteVertexBecauseMinPolygonSize = sector.verticesNm.size <= 3,
                    )
                }
                is Selected.MinAltCircle -> {
                    val sector = sel.sector
                    val t = sector.restrictionType
                    val title = "${if (t == MinAltRestrictionType.MVA) "MVA" else "Restricted"} circle center"
                    val anchor = sector.centerNm
                    val lp = sector.labelPositionNm
                    propertiesPane.bindMinAltCircleCenter(
                        title = title,
                        centerXNm = sector.centerNm.xNm,
                        centerYNm = sector.centerNm.yNm,
                        minAltitudeDisplay = sector.minAltitudeFt?.toString() ?: "UNL",
                        radiusNm = sector.radiusNm,
                        labelOffsetXNm = (lp?.xNm ?: anchor.xNm) - anchor.xNm,
                        labelOffsetYNm = (lp?.yNm ?: anchor.yNm) - anchor.yNm,
                    )
                }
                is Selected.Approach -> propertiesPane.bindApproach(
                    icao = sel.airport.icao,
                    name = sel.approach.name,
                    xNm = sel.approach.positionNm.xNm,
                    yNm = sel.approach.positionNm.yNm,
                )
                is Selected.Sid -> {
                    val i = sel.airport.sids.indexOf(sel.sid)
                    val p = if (i >= 0) sidPickNm(sel.airport, i, sel.airport.sids.size) else sel.airport.positionNm
                    propertiesPane.bindSid(
                        icao = sel.airport.icao,
                        name = sel.sid.name,
                        xNm = p.xNm,
                        yNm = p.yNm,
                    )
                }
                is Selected.Star -> {
                    val i = sel.airport.stars.indexOf(sel.star)
                    val p = if (i >= 0) starPickNm(sel.airport, i, sel.airport.stars.size) else sel.airport.positionNm
                    propertiesPane.bindStar(
                        icao = sel.airport.icao,
                        name = sel.star.name,
                        xNm = p.xNm,
                        yNm = p.yNm,
                    )
                }
            }
        } finally {
            syncingFields = false
            propertiesPane.syncEmptyStateToolVisibility(mapObjectSelected = selected != null)
        }
    }

    private fun applyNameField() {
        if (syncingFields) return
        val sel = selected ?: return
        if (propertiesPane.nameField.isDisabled) return
        // Force uppercase
        val text = propertiesPane.nameField.text.trim().uppercase()
        propertiesPane.nameField.text = text
        propertiesPane.nameField.cursorPosition = text.length
        when (sel) {
            is Selected.Waypoint -> {
                if (text.isEmpty()) return
                val old = sel.wpt.name
                if (old == text) return
                history.execute(RenameWaypointCommand(sel.wpt, old, text))
                markDirty()
                updateUndoRedoButtons()
            }
            is Selected.Runway -> {
                if (text.isEmpty()) return
                val old = sel.rwy.name
                if (old == text) return
                history.execute(RenameRunwayCommand(sel.rwy, old, text))
                markDirty()
                updateUndoRedoButtons()
            }
            is Selected.Approach -> {
                if (text.isEmpty()) return
                val old = sel.approach.name
                if (old == text) return
                history.execute(RenameApproachCommand(sel.approach, old, text))
                markDirty()
                updateUndoRedoButtons()
            }
            is Selected.Sid -> {
                if (text.isEmpty()) return
                val old = sel.sid.name
                if (old == text) return
                history.execute(RenameSidCommand(sel.sid, old, text))
                markDirty()
                updateUndoRedoButtons()
            }
            is Selected.Star -> {
                if (text.isEmpty()) return
                val old = sel.star.name
                if (old == text) return
                history.execute(RenameStarCommand(sel.star, old, text))
                markDirty()
                updateUndoRedoButtons()
            }
            is Selected.MinAltPolygonVertex, is Selected.MinAltCircle -> Unit
        }
        syncFieldsFromSelection()
        needsValidation = true
    }

    private fun applyPositionFields() {
        if (syncingFields) return
        if (propertiesPane.xNmField.isDisabled || propertiesPane.yNmField.isDisabled) return
        val sel = selected ?: return
        val x = propertiesPane.xNmField.text.toFloatOrNull() ?: return
        val y = propertiesPane.yNmField.text.toFloatOrNull() ?: return
        val newPt = NmPoint(x, y)
        when (sel) {
            is Selected.Waypoint -> {
                val old = NmPoint(sel.wpt.positionNm.xNm, sel.wpt.positionNm.yNm)
                if (old.xNm == newPt.xNm && old.yNm == newPt.yNm) return
                history.execute(MoveWaypointPositionCommand(sel.wpt, old, newPt))
            }
            is Selected.Runway -> {
                val old = NmPoint(sel.rwy.thresholdNm.xNm, sel.rwy.thresholdNm.yNm)
                if (old.xNm == newPt.xNm && old.yNm == newPt.yNm) return
                history.execute(MoveRunwayThresholdCommand(sel.rwy, old, newPt))
            }
            is Selected.MinAltPolygonVertex -> {
                val old = sel.sector.verticesNm[sel.vertexIndex]
                val oldPt = NmPoint(old.xNm, old.yNm)
                if (oldPt.xNm == newPt.xNm && oldPt.yNm == newPt.yNm) return
                history.execute(MoveMinAltPolygonVertexCommand(sel.sector, sel.vertexIndex, oldPt, newPt))
            }
            is Selected.MinAltCircle -> {
                val old = NmPoint(sel.sector.centerNm.xNm, sel.sector.centerNm.yNm)
                if (old.xNm == newPt.xNm && old.yNm == newPt.yNm) return
                history.execute(MoveMinAltCircleCenterCommand(sel.sector, old, newPt))
            }
            is Selected.Approach -> {
                val old = NmPoint(sel.approach.positionNm.xNm, sel.approach.positionNm.yNm)
                if (old.xNm == newPt.xNm && old.yNm == newPt.yNm) return
                history.execute(MoveApproachPositionCommand(sel.approach, old, newPt))
            }
            is Selected.Sid, is Selected.Star -> return
        }
        markDirty()
        updateUndoRedoButtons()
        syncFieldsFromSelection()
        needsValidation = true
    }

    private fun dist2Px(a: Vector2, b: Vector2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    /** If [worldPx] is within [MIN_ALT_VERTEX_SNAP_RADIUS_PX] of a vertex on another min-alt polygon, returns that vertex in NM; otherwise NM under the pointer. */
    private fun snapMinAltPolygonVertexNm(sel: Selected.MinAltPolygonVertex, worldPx: Vector2): NmPoint {
        val r2 = MIN_ALT_VERTEX_SNAP_RADIUS_PX * MIN_ALT_VERTEX_SNAP_RADIUS_PX
        var bestD2 = Float.POSITIVE_INFINITY
        var best: NmPoint? = null
        for (sector in map.minAltSectors) {
            if (sector !is MinAltPolygonSectorDefinition || sector === sel.sector) continue
            for (v in sector.verticesNm) {
                val d2 = dist2Px(toPx(v), worldPx)
                if (d2 <= r2 && d2 < bestD2) {
                    bestD2 = d2
                    best = NmPoint(v.xNm, v.yNm)
                }
            }
        }
        return best ?: NmPoint(pxToNm(worldPx.x), pxToNm(worldPx.y))
    }

    /** Picks for SIDs: spiral around airport ARP so handles do not coincide. */
    private fun sidPickNm(arpt: AirportDefinition, index: Int, count: Int): NmPoint {
        if (count <= 1) return arpt.positionNm
        val angle = (index.toFloat() / count) * MathUtils.PI2
        val r = 0.35f + index * 0.1f
        return NmPoint(
            arpt.positionNm.xNm + MathUtils.cos(angle) * r,
            arpt.positionNm.yNm + MathUtils.sin(angle) * r,
        )
    }

    /** Picks for STARs: different phase/radius from SIDs at the same airport. */
    private fun starPickNm(arpt: AirportDefinition, index: Int, count: Int): NmPoint {
        if (count <= 1) return NmPoint(arpt.positionNm.xNm + 0.25f, arpt.positionNm.yNm + 0.25f)
        val angle = (index.toFloat() / count) * MathUtils.PI2 + MathUtils.PI / 3f
        val r = 0.5f + index * 0.09f
        return NmPoint(
            arpt.positionNm.xNm + MathUtils.cos(angle) * r,
            arpt.positionNm.yNm + MathUtils.sin(angle) * r,
        )
    }

    private data class PickHit(val selected: Selected, val metricPx: Float)

    private fun pickClosestHit(worldPx: Vector2): PickHit? {
        var best: PickHit? = null
        fun offer(sel: Selected, metricPx: Float) {
            if (best == null || metricPx < best!!.metricPx) best = PickHit(sel, metricPx)
        }
        when (activeLayer) {
            EditorLayer.WAYPOINTS -> {
                for (wpt in map.waypoints) {
                    val d = sqrt(dist2Px(toPx(wpt.positionNm), worldPx))
                    if (d <= PICK_THRESHOLD_PX) offer(Selected.Waypoint(wpt), d)
                }
            }
            EditorLayer.RUNWAYS -> {
                for (arpt in map.airports) {
                    for (rwy in arpt.runways) {
                        val d = sqrt(dist2Px(toPx(rwy.thresholdNm), worldPx))
                        if (d <= PICK_THRESHOLD_PX) offer(Selected.Runway(rwy), d)
                    }
                }
            }
            EditorLayer.TERRAIN_MVA -> pickMinAltHits(worldPx, MinAltRestrictionType.MVA, ::offer)
            EditorLayer.RESTRICTED -> pickMinAltHits(worldPx, MinAltRestrictionType.RESTR, ::offer)
            EditorLayer.APPROACHES -> {
                for (arpt in map.airports) {
                    for (apch in arpt.approaches) {
                        val d = sqrt(dist2Px(toPx(apch.positionNm), worldPx))
                        if (d <= PICK_THRESHOLD_PX) offer(Selected.Approach(arpt, apch), d)
                    }
                }
            }
            EditorLayer.SIDS, EditorLayer.STARS -> Unit
        }
        return best
    }

    private fun pickMinAltHits(worldPx: Vector2, want: MinAltRestrictionType, offer: (Selected, Float) -> Unit) {
        for (sector in map.minAltSectors) {
            when (sector) {
                is MinAltPolygonSectorDefinition -> if (sector.restrictionType == want) {
                    sector.verticesNm.forEachIndexed { i, v ->
                        val d = sqrt(dist2Px(toPx(v), worldPx))
                        if (d <= PICK_THRESHOLD_PX) offer(Selected.MinAltPolygonVertex(sector, i), d)
                    }
                }
                is MinAltCircleSectorDefinition -> if (sector.restrictionType == want) {
                    val c = toPx(sector.centerNm)
                    val dx = worldPx.x - c.x
                    val dy = worldPx.y - c.y
                    val d = sqrt(dx * dx + dy * dy)
                    val rPx = nmToPx(sector.radiusNm)
                    when {
                        d <= rPx -> offer(Selected.MinAltCircle(sector), d)
                        d - rPx <= CIRCLE_EDGE_GRAB_PX -> offer(Selected.MinAltCircle(sector), d - rPx)
                    }
                }
            }
        }
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) return false
        val worldPx = unprojectFromRadarCamera(screenX.toFloat(), screenY.toFloat())

        val hit = pickClosestHit(worldPx)
        if (hit != null) {
            cancelPendingSelect()
            pendingSelect = hit.selected
            pendingSelectPointer = pointer
            pickDownScreenX = screenX
            pickDownScreenY = screenY
            pendingSelectTask = Timer.schedule(object : Timer.Task() {
                override fun run() {
                    commitPendingSelectIfStillValid()
                }
            }, PICK_SELECT_DELAY_SEC)
            return true
        }

        cancelPendingSelect()
        setSelected(null)
        return false
    }

    private fun polygonCentroidNm(vertices: List<NmPoint>): NmPoint {
        val n = vertices.size
        if (n == 0) return NmPoint(0f, 0f)
        if (n == 1) return vertices[0]
        val need = n * 2
        val fa = if (need <= polygonCentroidScratch.size) polygonCentroidScratch else FloatArray(need)
        for (i in vertices.indices) {
            fa[i * 2] = nmToPx(vertices[i].xNm)
            fa[i * 2 + 1] = nmToPx(vertices[i].yNm)
        }
        GeometryUtils.polygonCentroid(fa, 0, need, centroidVec2)
        return NmPoint(pxToNm(centroidVec2.x), pxToNm(centroidVec2.y))
    }

    private fun minAltGeometricAnchorNm(sector: MinAltSectorDefinition): NmPoint = when (sector) {
        is MinAltPolygonSectorDefinition -> polygonCentroidNm(sector.verticesNm)
        is MinAltCircleSectorDefinition -> sector.centerNm
    }

    private fun minAltLabelAnchorNm(sector: MinAltSectorDefinition): NmPoint = when (sector) {
        is MinAltPolygonSectorDefinition -> sector.labelPositionNm ?: polygonCentroidNm(sector.verticesNm)
        is MinAltCircleSectorDefinition -> sector.labelPositionNm ?: sector.centerNm
    }

    private fun formatMinAltEditorLabelText(ft: Int?): String =
        if (ft == null) "UNL" else (ft / 100f).roundToInt().toString()

    private fun isMinAltSectorSelected(sector: MinAltSectorDefinition): Boolean {
        val sel = selected ?: return false
        return when (sel) {
            is Selected.MinAltPolygonVertex -> sel.sector === sector
            is Selected.MinAltCircle -> sel.sector === sector
            else -> false
        }
    }

    private fun mapEditorMinAltLabelStyle(restr: MinAltRestrictionType, sectorSelected: Boolean): String = when (restr) {
        MinAltRestrictionType.MVA ->
            if (sectorSelected) MAP_EDITOR_MIN_ALT_LABEL_MVA_SELECTED else MAP_EDITOR_MIN_ALT_LABEL_MVA
        MinAltRestrictionType.RESTR ->
            if (sectorSelected) MAP_EDITOR_MIN_ALT_LABEL_RESTR_SELECTED else MAP_EDITOR_MIN_ALT_LABEL_RESTR
    }

    private fun labelStyleOrDefault(styleName: String): LabelStyle {
        val skin = Scene2DSkin.defaultSkin
        return if (skin.has(styleName, LabelStyle::class.java)) skin.get(styleName, LabelStyle::class.java)
        else skin.get(LabelStyle::class.java)
    }

    private fun drawSelectedMinAltPolygonVertexHandles() {
        val sel = selected as? Selected.MinAltPolygonVertex ?: return
        val verts = sel.sector.verticesNm
        val n = verts.size
        if (n < 2) return
        val iSel = sel.vertexIndex
        val iPrev = (iSel - 1 + n) % n
        val iNext = (iSel + 1) % n
        for (i in verts.indices) {
            val p = toPx(verts[i])
            shapeRenderer.color = when (i) {
                iSel -> Color.YELLOW
                iPrev -> Color.RED
                iNext -> Color.GREEN
                else -> Color.WHITE
            }
            val r = if (i == iSel) 6f else 5f
            shapeRenderer.circle(p.x, p.y, r)
        }
    }

    private fun drawMinAltSectorLabelsOnRadar() {
        val batch = GAME.batch
        savedBatchProjection.set(batch.projectionMatrix)
        batch.projectionMatrix = radarCam.combined
        batch.begin()
        for (sector in map.minAltSectors) {
            val anchor = minAltLabelAnchorNm(sector)
            val wx = nmToPx(anchor.xNm)
            val wy = nmToPx(anchor.yNm)
            val text = formatMinAltEditorLabelText(sector.minAltitudeFt)
            val styleName = mapEditorMinAltLabelStyle(sector.restrictionType, isMinAltSectorSelected(sector))
            val lbl = Label(text, labelStyleOrDefault(styleName))
            lbl.validate()
            lbl.setPosition(wx - lbl.prefWidth / 2f, wy - lbl.prefHeight / 2f)
            lbl.draw(batch, 1f)
        }
        batch.end()
        batch.projectionMatrix.set(savedBatchProjection)
    }

    private fun activeMinAltRestrictionType(): MinAltRestrictionType? = when (activeLayer) {
        EditorLayer.TERRAIN_MVA -> MinAltRestrictionType.MVA
        EditorLayer.RESTRICTED -> MinAltRestrictionType.RESTR
        else -> null
    }

    private fun radarCenterNm(): NmPoint {
        val x = radarCam.position.x
        val y = radarCam.position.y
        return NmPoint(pxToNm(x), pxToNm(y))
    }

    private fun equilateralTriangleAround(center: NmPoint, radiusNm: Float): MutableList<NmPoint> {
        val out = mutableListOf<NmPoint>()
        for (i in 0 until 3) {
            val ang = MathUtils.PI2 / 3f * i + MathUtils.PI / 2f
            out.add(
                NmPoint(
                    center.xNm + MathUtils.cos(ang) * radiusNm,
                    center.yNm + MathUtils.sin(ang) * radiusNm,
                ),
            )
        }
        return out
    }

    private fun addNewMinAltPolygonAtScreenCenter() {
        val rType = activeMinAltRestrictionType() ?: return
        val sector = MinAltPolygonSectorDefinition(
            rType,
            map.globals.minAltFt,
            equilateralTriangleAround(radarCenterNm(), NEW_TRIANGLE_CIRCUMRADIUS_NM),
        )
        val idx = map.minAltSectors.size
        history.execute(AddMinAltSectorCommand(map.minAltSectors, sector, idx))
        markDirty()
        updateUndoRedoButtons()
        setSelected(Selected.MinAltPolygonVertex(sector, 0))
        needsValidation = true
    }

    private fun addNewMinAltCircleAtScreenCenter() {
        val rType = activeMinAltRestrictionType() ?: return
        val sector = MinAltCircleSectorDefinition(rType, map.globals.minAltFt, radarCenterNm(), NEW_CIRCLE_RADIUS_NM)
        val idx = map.minAltSectors.size
        history.execute(AddMinAltSectorCommand(map.minAltSectors, sector, idx))
        markDirty()
        updateUndoRedoButtons()
        setSelected(Selected.MinAltCircle(sector))
        needsValidation = true
    }

    private fun insertPolygonVertexBefore() {
        val sel = selected as? Selected.MinAltPolygonVertex ?: return
        val sector = sel.sector
        val i = sel.vertexIndex
        val n = sector.verticesNm.size
        val iPrev = (i - 1 + n) % n
        val prev = sector.verticesNm[iPrev]
        val cur = sector.verticesNm[i]
        val mid = NmPoint((prev.xNm + cur.xNm) / 2f, (prev.yNm + cur.yNm) / 2f)
        history.execute(InsertMinAltPolygonVertexCommand(sector, i, mid))
        markDirty()
        updateUndoRedoButtons()
        setSelected(Selected.MinAltPolygonVertex(sector, i))
        needsValidation = true
    }

    private fun insertPolygonVertexAfter() {
        val sel = selected as? Selected.MinAltPolygonVertex ?: return
        val sector = sel.sector
        val i = sel.vertexIndex
        val n = sector.verticesNm.size
        val iNext = (i + 1) % n
        val cur = sector.verticesNm[i]
        val next = sector.verticesNm[iNext]
        val mid = NmPoint((cur.xNm + next.xNm) / 2f, (cur.yNm + next.yNm) / 2f)
        val insertAt = i + 1
        history.execute(InsertMinAltPolygonVertexCommand(sector, insertAt, mid))
        markDirty()
        updateUndoRedoButtons()
        setSelected(Selected.MinAltPolygonVertex(sector, insertAt))
        needsValidation = true
    }

    private fun deleteSelectedPolygonVertex() {
        val sel = selected as? Selected.MinAltPolygonVertex ?: return
        val sector = sel.sector
        if (sector.verticesNm.size <= 3) return
        val i = sel.vertexIndex
        val removed = sector.verticesNm[i]
        history.execute(RemoveMinAltPolygonVertexCommand(sector, i, NmPoint(removed.xNm, removed.yNm)))
        markDirty()
        updateUndoRedoButtons()
        val newIdx = min(i, sector.verticesNm.lastIndex)
        setSelected(Selected.MinAltPolygonVertex(sector, newIdx))
        needsValidation = true
    }

    private fun deleteSelectedMinAltSector() {
        val sel = selected ?: return
        val sector: MinAltSectorDefinition = when (sel) {
            is Selected.MinAltPolygonVertex -> sel.sector
            is Selected.MinAltCircle -> sel.sector
            else -> return
        }
        val idx = map.minAltSectors.indexOf(sector)
        if (idx < 0) return
        history.execute(RemoveMinAltSectorCommand(map.minAltSectors, sector, idx))
        markDirty()
        updateUndoRedoButtons()
        setSelected(null)
        needsValidation = true
    }

    private fun applyMinAltitudeField() {
        if (syncingFields) return
        val sel = selected ?: return
        val sector: MinAltSectorDefinition = when (sel) {
            is Selected.MinAltPolygonVertex -> sel.sector
            is Selected.MinAltCircle -> sel.sector
            else -> return
        }
        val raw = propertiesPane.minAltitudeField.text.trim()
        val newAlt = when {
            raw.isEmpty() || raw.equals("UNL", ignoreCase = true) -> null
            else -> raw.toIntOrNull() ?: return
        }
        val old = sector.minAltitudeFt
        if (old == newAlt) return
        history.execute(SetMinAltSectorMinAltitudeCommand(sector, old, newAlt))
        markDirty()
        updateUndoRedoButtons()
        needsValidation = true
    }

    private fun applyRadiusNmField() {
        if (syncingFields) return
        val sel = selected as? Selected.MinAltCircle ?: return
        val r = propertiesPane.radiusNmField.text.toFloatOrNull() ?: return
        if (r <= 0f) return
        val old = sel.sector.radiusNm
        if (old == r) return
        history.execute(SetMinAltCircleRadiusCommand(sel.sector, old, r))
        markDirty()
        updateUndoRedoButtons()
        needsValidation = true
    }

    private fun applyLabelOffsetFields() {
        if (syncingFields) return
        val ox = propertiesPane.labelOffsetXNmField.text.toFloatOrNull() ?: 0f
        val oy = propertiesPane.labelOffsetYNmField.text.toFloatOrNull() ?: 0f
        when (val sel = selected) {
            is Selected.MinAltPolygonVertex -> {
                val sector = sel.sector
                val anchor = minAltGeometricAnchorNm(sector)
                val old = sector.labelPositionNm
                val newPos = if (ox == 0f && oy == 0f) null else NmPoint(anchor.xNm + ox, anchor.yNm + oy)
                if (old?.xNm == newPos?.xNm && old?.yNm == newPos?.yNm) return
                history.execute(SetMinAltPolygonLabelPositionCommand(sector, old, newPos))
            }
            is Selected.MinAltCircle -> {
                val sector = sel.sector
                val anchor = sector.centerNm
                val old = sector.labelPositionNm
                val newPos = if (ox == 0f && oy == 0f) null else NmPoint(anchor.xNm + ox, anchor.yNm + oy)
                if (old?.xNm == newPos?.xNm && old?.yNm == newPos?.yNm) return
                history.execute(SetMinAltCircleLabelPositionCommand(sector, old, newPos))
            }
            else -> return
        }
        markDirty()
        updateUndoRedoButtons()
        syncFieldsFromSelection()
        needsValidation = true
    }

    private fun drawSectors() {
        shapeRenderer.color = SECTOR_GREEN
        map.sectorsByPlayerCount[1]?.forEach { sec ->
            drawClosedNmPolygon(sec.verticesNm)
        }
    }

    private fun drawMinAltSectors() {
        for (sector in map.minAltSectors) {
            shapeRenderer.color = when (sector.restrictionType) {
                MinAltRestrictionType.MVA -> Color.GRAY
                MinAltRestrictionType.RESTR -> Color.ORANGE
            }
            when (sector) {
                is MinAltPolygonSectorDefinition -> drawClosedNmPolygon(sector.verticesNm)
                is MinAltCircleSectorDefinition -> {
                    val c = toPx(sector.centerNm)
                    shapeRenderer.circle(c.x, c.y, nmToPx(sector.radiusNm))
                }
            }
        }
    }

    private fun drawShoreline() {
        shapeRenderer.color = Color.BROWN
        for (pl in map.shorelinePolylines) {
            val pts = pl.pointsNm
            for (i in 0 until pts.size - 1) {
                val a = toPx(pts[i])
                val b = toPx(pts[i + 1])
                shapeRenderer.line(a.x, a.y, b.x, b.y)
            }
        }
    }
}
