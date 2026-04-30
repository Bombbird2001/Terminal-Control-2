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
import com.bombbird.terminalcontrol2.editor.model.AirportMapDefinition
import com.bombbird.terminalcontrol2.editor.model.NmPoint
import com.bombbird.terminalcontrol2.editor.model.RunwayDefinition
import com.bombbird.terminalcontrol2.editor.model.WaypointDefinition
import com.bombbird.terminalcontrol2.editor.validation.AirportMapValidator
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.safeStage
import com.bombbird.terminalcontrol2.utilities.ShapeRendererBoundingBox
import com.bombbird.terminalcontrol2.utilities.nmToPx
import com.bombbird.terminalcontrol2.utilities.pxToNm
import ktx.app.KtxScreen
import ktx.assets.disposeSafely
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textField
import ktx.scene2d.Scene2DSkin
import kotlin.math.max

/**
 * Map editor screen. UI/UX is modeled after [RadarScreen] (pan/zoom radar canvas + overlay UI stage).
 *
 * This initial implementation focuses on rendering the loaded [AirportMapDefinition]; editing tools are layered on later.
 */
class MapEditorScreen(
    private var map: AirportMapDefinition,
) : KtxScreen, GestureListener, InputProcessor {

    private val radarDisplayStage = safeStage(GAME.batch)
    private val uiStage = safeStage(GAME.batch)
    private val shapeRenderer = ShapeRendererBoundingBox(6000)

    private val gestureDetector = GestureDetector(40f, 0.2f, 1.1f, 0.15f, this)
    private val inputMultiplexer = InputMultiplexer()

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

    // Properties UI widgets
    private var selectionTitle: com.badlogic.gdx.scenes.scene2d.ui.Label? = null
    private var nameField: com.badlogic.gdx.scenes.scene2d.ui.TextField? = null
    private var xNmField: com.badlogic.gdx.scenes.scene2d.ui.TextField? = null
    private var yNmField: com.badlogic.gdx.scenes.scene2d.ui.TextField? = null
    private var problemsField: com.badlogic.gdx.scenes.scene2d.ui.TextArea? = null
    private var needsValidation = true

    init {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            zoom = nmToPx(DEFAULT_ZOOM_NM) / UI_HEIGHT
            targetZoom = zoom
            position.set(0f, 0f, 0f)
            update()
        }
        shapeRenderer.projectionMatrix = radarDisplayStage.camera.combined

        buildPropertiesPane()
        buildProblemsPane()
    }

    fun setMap(newMap: AirportMapDefinition) {
        map = newMap
        setSelected(null)
        needsValidation = true
    }

    override fun show() {
        inputMultiplexer.clear()
        inputMultiplexer.addProcessor(uiStage)
        inputMultiplexer.addProcessor(radarDisplayStage)
        inputMultiplexer.addProcessor(gestureDetector)
        inputMultiplexer.addProcessor(this)
        Gdx.input.inputProcessor = inputMultiplexer
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        runCameraAnimations(delta)
        updateBoundingRect()

        // Draw world geometry
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
        radarDisplayStage.clear()
        uiStage.clear()
        radarDisplayStage.disposeSafely()
        uiStage.disposeSafely()
        shapeRenderer.disposeSafely()
    }

    // --- Camera helpers (adapted from RadarScreen) ---

    private fun updateBoundingRect() {
        val cam = radarDisplayStage.camera as OrthographicCamera
        val halfW = cam.viewportWidth * cam.zoom / 2f
        val halfH = cam.viewportHeight * cam.zoom / 2f
        shapeRenderer.setBoundingRect(cam.position.x - halfW, cam.position.y - halfH, halfW * 2f, halfH * 2f)
    }

    private fun unprojectFromRadarCamera(screenX: Float, screenY: Float): Vector2 {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            val scaleFactor = UI_HEIGHT / HEIGHT
            return Vector2(
                (screenX - WIDTH / 2) * zoom * scaleFactor + position.x,
                (HEIGHT / 2 - screenY) * zoom * scaleFactor + position.y
            )
        }
    }

    private fun clampUpdateCamera(deltaZoom: Float) {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            zoom += deltaZoom
            zoom = MathUtils.clamp(zoom, nmToPx(MIN_ZOOM_NM) / UI_HEIGHT, nmToPx(MAX_ZOOM_NM) / UI_HEIGHT)
            update()
            shapeRenderer.projectionMatrix = combined
        }
    }

    private fun initiateCameraAnimation(targetScreenX: Float, targetScreenY: Float) {
        (radarDisplayStage.camera as OrthographicCamera).apply {
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
        (radarDisplayStage.camera as OrthographicCamera).apply {
            if ((zoomRate < 0 && zoom > targetZoom) || (zoomRate > 0 && zoom < targetZoom)) {
                val neededDelta = max(0f, minOf((targetZoom - zoom) / zoomRate, delta))
                zoom += zoomRate * neededDelta
                translate(panRate.x * neededDelta, panRate.y * neededDelta)
                clampUpdateCamera(0f)
            } else cameraAnimating = false
        }
    }

    // --- GestureListener / InputProcessor ---

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (count == 2 && !cameraAnimating) initiateCameraAnimation(x, y)
        return true
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        (radarDisplayStage.camera as OrthographicCamera).apply {
            translate(-deltaX * zoom * UI_WIDTH / WIDTH, deltaY * zoom * UI_HEIGHT / HEIGHT)
        }
        clampUpdateCamera(0f)
        return true
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        if (initialDistance != prevInitDist) {
            prevInitDist = initialDistance
            prevZoom = (radarDisplayStage.camera as OrthographicCamera).zoom
        }
        val ratio = initialDistance / distance
        if (ratio < 0.95f || ratio > 1.05f) {
            clampUpdateCamera(prevZoom * ratio - (radarDisplayStage.camera as OrthographicCamera).zoom)
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
        if (draggingPointer == pointer) draggingPointer = null
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
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
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int) = false
    override fun mouseMoved(screenX: Int, screenY: Int) = false

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) return false
        clampUpdateCamera(amountY * 0.06f)
        return true
    }

    // --- Drawing ---

    private fun toPx(p: NmPoint): Vector2 = Vector2(nmToPx(p.xNm), nmToPx(p.yNm))

    private fun drawRunwaysAndWaypoints() {
        shapeRenderer.color = com.badlogic.gdx.graphics.Color.WHITE

        // Waypoints (small circles)
        for (wpt in map.waypoints) {
            val pos = toPx(wpt.positionNm)
            shapeRenderer.circle(pos.x, pos.y, 4f)
        }

        // Runways (simple line centered at threshold; refined later)
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
                nameField = textField("", "Settings").apply {
                    addChangeListener { _, _ -> applyNameField() }
                }
                row()

                label("X (nm)", "SettingsOption")
                xNmField = textField("", "Settings").apply {
                    addChangeListener { _, _ -> applyPositionFields() }
                }
                row()

                label("Y (nm)", "SettingsOption")
                yNmField = textField("", "Settings").apply {
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

                label("Problems", "MenuHeader")
                row()
                problemsField = com.badlogic.gdx.scenes.scene2d.ui.TextArea("", Scene2DSkin.defaultSkin).apply {
                    isDisabled = true
                    setSize(700f, 240f)
                }.also { add(it) }
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
        syncFieldsFromSelection()
    }

    private fun syncFieldsFromSelection() {
        val sel = selected
        when (sel) {
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
    }

    private fun applyNameField() {
        val sel = selected ?: return
        val text = nameField?.text?.trim().orEmpty()
        when (sel) {
            is Selected.Waypoint -> if (text.isNotEmpty()) sel.wpt.name = text
            is Selected.Runway -> if (text.isNotEmpty()) sel.rwy.name = text
        }
        syncFieldsFromSelection()
        needsValidation = true
    }

    private fun applyPositionFields() {
        val sel = selected ?: return
        val x = xNmField?.text?.toFloatOrNull() ?: return
        val y = yNmField?.text?.toFloatOrNull() ?: return
        when (sel) {
            is Selected.Waypoint -> sel.wpt.positionNm = NmPoint(x, y)
            is Selected.Runway -> sel.rwy.thresholdNm = NmPoint(x, y)
        }
        needsValidation = true
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button != Input.Buttons.LEFT) return false
        val worldPx = unprojectFromRadarCamera(screenX.toFloat(), screenY.toFloat())

        // Hit test waypoints first
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
            setSelected(best)
            draggingPointer = pointer
            return true
        }

        setSelected(null)
        return false
    }

    private fun drawSectors() {
        shapeRenderer.color = com.badlogic.gdx.graphics.Color(0f, 0.6f, 1f, 1f)
        map.sectorsByPlayerCount[1]?.forEach { sec ->
            val verts = sec.verticesNm
            for (i in 0 until verts.size - 1) {
                val a = toPx(verts[i])
                val b = toPx(verts[i + 1])
                shapeRenderer.line(a.x, a.y, b.x, b.y)
            }
        }
    }

    private fun drawMinAltSectors() {
        shapeRenderer.color = com.badlogic.gdx.graphics.Color(1f, 0.6f, 0f, 1f)
        for (sector in map.minAltSectors) {
            when (sector) {
                is com.bombbird.terminalcontrol2.editor.model.MinAltPolygonSectorDefinition -> {
                    val v = sector.verticesNm
                    for (i in 0 until v.size - 1) {
                        val a = toPx(v[i])
                        val b = toPx(v[i + 1])
                        shapeRenderer.line(a.x, a.y, b.x, b.y)
                    }
                }
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

