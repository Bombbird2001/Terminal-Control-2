package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Timer
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextArea
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition
import com.bombbird.terminalcontrol2.editor.model.NmPoint
import com.bombbird.terminalcontrol2.editor.model.RunwayDefinition
import com.bombbird.terminalcontrol2.editor.model.WaypointDefinition
import com.bombbird.terminalcontrol2.editor.undo.MoveRunwayThresholdCommand
import com.bombbird.terminalcontrol2.editor.undo.MoveWaypointPositionCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameRunwayCommand
import com.bombbird.terminalcontrol2.editor.undo.RenameWaypointCommand
import com.bombbird.terminalcontrol2.editor.undo.UndoRedoHistory
import com.bombbird.terminalcontrol2.editor.validation.AirportMapValidator
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.ui.addChangeListener
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
import ktx.scene2d.textField
import kotlin.math.hypot
import kotlin.math.max

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

    // Selection / drag state (MVP: waypoints + runway thresholds)
    private sealed interface Selected {
        data class Waypoint(val wpt: WaypointDefinition) : Selected
        data class Runway(val rwy: RunwayDefinition) : Selected
    }

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

    // Properties UI widgets
    private var selectionTitle: Label? = null
    private var nameField: TextField? = null
    private var xNmField: TextField? = null
    private var yNmField: TextField? = null
    private var problemsField: TextArea? = null
    private var needsValidation = true

    init {
        radarCam.apply {
            zoom = nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            targetZoom = zoom
            position.set(0f, 0f, 0f)
            update()
        }
        shapeRenderer.projectionMatrix = radarDisplayStage.camera.combined

        buildToolbar()
        buildPropertiesPane()
        buildProblemsPane()
    }

    fun hasUnsavedChanges(): Boolean = documentDirty

    /** TODO Placeholder until file I/O is implemented; clears the dirty flag. */
    fun dummySave() {
        documentDirty = false
    }

    fun setMap(newMap: AirportMapDefinition) {
        map = newMap
        history.clear()
        documentDirty = false
        cancelPendingSelect()
        setSelected(null)
        needsValidation = true
        updateUndoRedoButtons()
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
            zoom += deltaZoom
            zoom = MathUtils.clamp(zoom, nmToPx(MIN_ZOOM_NM) / UI_HEIGHT, nmToPx(MAX_ZOOM_NM) / UI_HEIGHT)
            update()
            shapeRenderer.projectionMatrix = combined
        }
    }

    private fun initiateCameraAnimation(targetScreenX: Float, targetScreenY: Float) {
        radarCam.apply {
            targetZoom = if (zoom > (nmToPx(ZOOM_THRESHOLD_NM) / UI_HEIGHT)) nmToPx(DEFAULT_ZOOM_IN_NM) / UI_HEIGHT
            else nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            val worldCoord = unprojectFromRadarCamera(targetScreenX, targetScreenY)
            targetCenter.set(worldCoord.x, worldCoord.y)
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
                textButton("Menu", "Pause").addChangeListener { _, _ ->
                    GAME.getScreen<MapEditorPauseScreen>().editor = this@MapEditorScreen
                    GAME.setScreen<MapEditorPauseScreen>()
                }
                undoButton = textButton("Undo", "Menu").apply {
                    addChangeListener { _, _ -> performUndo() }
                }
                redoButton = textButton("Redo", "Menu").apply {
                    addChangeListener { _, _ -> performRedo() }
                }
            }
        }
    }

    private fun performUndo() {
        history.undo()
        markDirty()
        syncFieldsFromSelection()
        needsValidation = true
        updateUndoRedoButtons()
    }

    private fun performRedo() {
        history.redo()
        markDirty()
        syncFieldsFromSelection()
        needsValidation = true
        updateUndoRedoButtons()
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
        }
        if (start.xNm == end.xNm && start.yNm == end.yNm) return
        when (sel) {
            is Selected.Waypoint -> history.execute(MoveWaypointPositionCommand(sel.wpt, start, end))
            is Selected.Runway -> history.execute(MoveRunwayThresholdCommand(sel.rwy, start, end))
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

        shapeRenderer.color = com.badlogic.gdx.graphics.Color.WHITE

        // Waypoints (small circles)
        for (wpt in map.waypoints) {
            val pos = toPx(wpt.positionNm)
            shapeRenderer.circle(pos.x, pos.y, 4f)
        }

        // Runways (simple line centered at threshold)
        for (arpt in map.airports) {
            for (rwy in arpt.runways) {
                val thr = toPx(rwy.thresholdNm)
                shapeRenderer.circle(thr.x, thr.y, 8f)
            }
        }

        // Selection highlight
        val sel = selected
        if (sel != null) {
            shapeRenderer.color = com.badlogic.gdx.graphics.Color.YELLOW
            val pos = when (sel) {
                is Selected.Waypoint -> toPx(sel.wpt.positionNm)
                is Selected.Runway -> toPx(sel.rwy.thresholdNm)
            }
            shapeRenderer.circle(pos.x, pos.y, 14f)
        }
    }

    private fun buildPropertiesPane() {
        uiStage.actors {
            table {
                setFillParent(true)
                right().top().pad(20f)
                defaults().pad(6f)

                selectionTitle = label("No selection", "MenuHeader")
                row()

                label("Name", "SettingsOption")
                nameField = textField("", "MapEditorProperties").apply {
                    addChangeListener { _, _ -> applyNameField() }
                }
                row()

                label("X (nm)", "SettingsOption")
                xNmField = textField("", "MapEditorProperties").apply {
                    addChangeListener { _, _ -> applyPositionFields() }
                }
                row()

                label("Y (nm)", "SettingsOption")
                yNmField = textField("", "MapEditorProperties").apply {
                    addChangeListener { _, _ -> applyPositionFields() }
                }
                row()
            }
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

        // Do not use [setSelected]: it clears drag state; here we commit selection + drag together.
        selected = sel
        draggingPointer = pointer
        isDraggingMapObject = true
        dragStartNm = when (sel) {
            is Selected.Waypoint -> NmPoint(sel.wpt.positionNm.xNm, sel.wpt.positionNm.yNm)
            is Selected.Runway -> NmPoint(sel.rwy.thresholdNm.xNm, sel.rwy.thresholdNm.yNm)
        }
        syncFieldsFromSelection()
    }

    private fun syncFieldsFromSelection() {
        syncingFields = true
        try {
            when (val sel = selected) {
                null -> {
                    selectionTitle?.setText("No selection")
                    nameField?.text = ""
                    xNmField?.text = ""
                    yNmField?.text = ""
                }
                is Selected.Waypoint -> {
                    selectionTitle?.setText("Waypoint ${sel.wpt.id}")
                    nameField?.text = sel.wpt.name
                    xNmField?.text = "%.3f".format(sel.wpt.positionNm.xNm)
                    yNmField?.text = "%.3f".format(sel.wpt.positionNm.yNm)
                }
                is Selected.Runway -> {
                    selectionTitle?.setText("Runway ${sel.rwy.name}")
                    nameField?.text = sel.rwy.name
                    xNmField?.text = "%.3f".format(sel.rwy.thresholdNm.xNm)
                    yNmField?.text = "%.3f".format(sel.rwy.thresholdNm.yNm)
                }
            }
        } finally {
            syncingFields = false
        }
    }

    private fun applyNameField() {
        if (syncingFields) return
        val sel = selected ?: return
        // Force uppercase
        val text = nameField?.text?.trim().orEmpty().uppercase()
        nameField?.text = text
        nameField?.cursorPosition = text.length
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
        }
        syncFieldsFromSelection()
        needsValidation = true
    }

    private fun applyPositionFields() {
        if (syncingFields) return
        val sel = selected ?: return
        val x = xNmField?.text?.toFloatOrNull() ?: return
        val y = yNmField?.text?.toFloatOrNull() ?: return
        val old = when (sel) {
            is Selected.Waypoint -> NmPoint(sel.wpt.positionNm.xNm, sel.wpt.positionNm.yNm)
            is Selected.Runway -> NmPoint(sel.rwy.thresholdNm.xNm, sel.rwy.thresholdNm.yNm)
        }
        val newPt = NmPoint(x, y)
        if (old.xNm == newPt.xNm && old.yNm == newPt.yNm) return
        when (sel) {
            is Selected.Waypoint -> history.execute(MoveWaypointPositionCommand(sel.wpt, old, newPt))
            is Selected.Runway -> history.execute(MoveRunwayThresholdCommand(sel.rwy, old, newPt))
        }
        markDirty()
        updateUndoRedoButtons()
        syncFieldsFromSelection()
        needsValidation = true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) return false
        val worldPx = unprojectFromRadarCamera(screenX.toFloat(), screenY.toFloat())

        var best: Selected? = null
        var bestDist2 = Float.POSITIVE_INFINITY

        for (wpt in map.waypoints) {
            val p = toPx(wpt.positionNm)
            val dx = p.x - worldPx.x
            val dy = p.y - worldPx.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = Selected.Waypoint(wpt)
            }
        }

        for (arpt in map.airports) {
            for (rwy in arpt.runways) {
                val p = toPx(rwy.thresholdNm)
                val dx = p.x - worldPx.x
                val dy = p.y - worldPx.y
                val d2 = dx * dx + dy * dy
                if (d2 < bestDist2) {
                    bestDist2 = d2
                    best = Selected.Runway(rwy)
                }
            }
        }

        // Only select if within threshold
        val thresholdPx = 16f
        if (best != null && bestDist2 <= thresholdPx * thresholdPx) {
            cancelPendingSelect()
            pendingSelect = best
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

    private fun drawSectors() {
        shapeRenderer.color = com.badlogic.gdx.graphics.Color(0f, 0.6f, 1f, 1f)
        map.sectorsByPlayerCount[1]?.forEach { sec ->
            drawClosedNmPolygon(sec.verticesNm)
        }
    }

    private fun drawMinAltSectors() {
        shapeRenderer.color = com.badlogic.gdx.graphics.Color(1f, 0.6f, 0f, 1f)
        for (sector in map.minAltSectors) {
            when (sector) {
                is MinAltPolygonSectorDefinition -> drawClosedNmPolygon(sector.verticesNm)
                is com.bombbird.terminalcontrol2.editor.model.MinAltCircleSectorDefinition -> {
                    val c = toPx(sector.centerNm)
                    shapeRenderer.circle(c.x, c.y, nmToPx(sector.radiusNm))
                }
            }
        }
    }

    private fun drawShoreline() {
        shapeRenderer.color = com.badlogic.gdx.graphics.Color(0.2f, 0.8f, 0.4f, 1f)
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
