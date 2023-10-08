package com.bombbird.terminalcontrol2.ui.panes

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ArrayMap.Entries
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.AIRPORT_SIZE
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.removeMouseScrollListeners
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import ktx.ashley.get
import ktx.ashley.has
import ktx.ashley.remove
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import ktx.scene2d.*

class MainInfoPane {
    private lateinit var scoreLabel: Label
    private lateinit var highScoreLabel: Label

    private lateinit var metarScroll: KScrollPane
    private lateinit var metarPane: KTableWidget
    private val metarExpandArray = GdxArray<Byte>(AIRPORT_SIZE)

    private lateinit var paneContainer: KContainer<Actor>

    private lateinit var buttonTable: KTableWidget
    private lateinit var commsButton: KTextButton
    private lateinit var statusButton: KTextButton
    private lateinit var sectorButton: KTextButton

    private val airportAtisButtonMap = GdxArrayMap<Byte, Pair<KTextButton, KTextButton>>(AIRPORT_SIZE)
    private val airportAtisMap = GdxArrayMap<Byte, Pair<String, String>>(AIRPORT_SIZE)

    private val airportRwyConfigMap = GdxArrayMap<Byte, KTableWidget>(AIRPORT_SIZE)
    private val airportRwySettingButtonMap = GdxArrayMap<Byte, KImageButton>(AIRPORT_SIZE)
    private val airportRwyConfigButtonsMap = GdxArrayMap<Byte, GdxArray<KTextButton>>(AIRPORT_SIZE)
    private val airportRwyConfigConfirmCancelMap = GdxArrayMap<Byte, Pair<KTextButton, KTextButton>>(AIRPORT_SIZE)
    private var buttonsBeingModified = false
    private var selectedArptId: Byte? = null

    val commsPaneObj = CommsPane()
    val statusPaneObj = StatusPane()
    val sectorPaneObj = SectorPane()

    /**
     * @param paneWidth will be used as the reference width of the UI pane when initialising the container
     * @return a [KContainer] used to contain a table with the elements of the main information pane, which has been added to the [KWidget]
     */
    @Scene2dDsl
    fun mainInfoPane(widget: KWidget<Actor>, paneWidth: Float): KContainer<Actor> {
        return widget.container {
            // debugAll()
            fill()
            setSize(paneWidth, UI_HEIGHT)
            table {
                // debugAll()
                table {
                    // debugAll()
                    scoreLabel = label("Score: Shiba", "Score").cell(align = Align.left, growX = true)
                    row()
                    highScoreLabel = label("High score: High shiba", "HighScore").cell(padTop = 10f, align = Align.left, growX = true)
                }.cell(padLeft = 20f, preferredWidth = paneWidth * 0.85f - 50)
                textButton("||", "Pause").cell(padRight = 30f, preferredWidth = paneWidth * 0.15f, height = UI_HEIGHT * 0.1f).addChangeListener { _, _ ->
                    // Pause the client
                    CLIENT_SCREEN?.pauseGame()
                }
                row()
                metarScroll = scrollPane("MetarPane") {
                    // debugAll()
                    metarPane = table {
                        align(Align.top)
                    }
                    setOverscroll(false, false)
                    removeMouseScrollListeners()
                }.cell(padTop = 20f, padBottom = 15f, align = Align.top, preferredWidth = paneWidth, height = UI_HEIGHT * 0.4f, growX = true, colspan = 2)
                row()
                buttonTable = table {
                    commsButton = textButton("Communications", "MenuPaneCategory").cell(preferredWidth = 0.3f * paneWidth, growY = true).apply {
                        isChecked = true
                        addChangeListener { _, _ ->
                            if (buttonsBeingModified) return@addChangeListener
                            buttonsBeingModified = true
                            if (!isChecked) isChecked = true
                            else {
                                paneContainer.actor = commsPaneObj.commsTable
                                selectedArptId = null
                                statusButton.isChecked = false
                                sectorButton.isChecked = false
                            }
                            buttonsBeingModified = false
                        }
                    }
                    statusButton = textButton("Status", "MenuPaneCategory").cell(preferredWidth = 0.15f * paneWidth, growY = true).apply {
                        addChangeListener { _, _ ->
                            if (buttonsBeingModified) return@addChangeListener
                            buttonsBeingModified = true
                            if (!isChecked) isChecked = true
                            else {
                                paneContainer.actor = statusPaneObj.statusTable
                                Gdx.app.postRunnable { statusPaneObj.refreshStatusMessages() }
                                selectedArptId = null
                                commsButton.isChecked = false
                                sectorButton.isChecked = false
                            }
                            buttonsBeingModified = false
                        }
                    }
                    sectorButton = textButton("Sectors", "MenuPaneCategory").cell(preferredWidth = 0.15f * paneWidth, growY = true).apply {
                        addChangeListener { _, _ ->
                            if (buttonsBeingModified) return@addChangeListener
                            buttonsBeingModified = true
                            if (!isChecked) isChecked = true
                            else {
                                paneContainer.actor = sectorPaneObj.sectorTable
                                selectedArptId = null
                                commsButton.isChecked = false
                                statusButton.isChecked = false
                                Gdx.app.postRunnable {
                                    CLIENT_SCREEN?.let {
                                        it.uiPane.sectorPane.selectedId = it.playerSector
                                        sectorPaneObj.updateSectorDisplay(it.sectors)
                                    }
                                }
                            }
                            buttonsBeingModified = false
                        }
                    }
                }.cell(align = Align.left, colspan = 2)
                row()
                paneContainer = container {
                    // debugAll()
                    commsPaneObj.commsPane(this, paneWidth)
                    actor = null
                    statusPaneObj.statusPane(this, paneWidth)
                    actor = null
                    sectorPaneObj.sectorPane(this, paneWidth)
                }.cell(align = Align.top, grow = true, colspan = 2)
            }
            isVisible = true
        }
    }

    /**
     * Updates the score display labels with the new score data
     * @param newScore the new score
     * @param newHighScore the new high score
     */
    fun updateScoreDisplay(newScore: Int, newHighScore: Int) {
        scoreLabel.setText("Score: $newScore")
        highScoreLabel.setText("High score: $newHighScore")
    }

    /** Initialises the ATIS pane displays for all airports */
    fun initializeAtisDisplay() {
        metarPane.clear()
        CLIENT_SCREEN?.let {
            var padTop = false
            for (airportEntry in Entries(it.airports)) {
                val airport = airportEntry.value
                val arptId = airport.entity[AirportInfo.mapper]?.arptId ?: continue
                val linkedButton = metarPane.textButton("", "Metar").apply {
                    label.setAlignment(Align.left)
                    label.wrap = true
                    cell(growX = true, padLeft = 20f, padRight = 10f, padTop = if (padTop) 30f else 0f, align = Align.left)
                }
                metarPane.table {
                    // debugAll()
                    val expandButton = textButton(if (metarExpandArray.contains(arptId, false)) "-" else "+", "MetarExpand").apply {
                        addChangeListener { _, _ ->
                            val texts = airportAtisMap[arptId] ?: Pair("", "")
                            if (metarExpandArray.contains(arptId)) {
                                // Expanded, hide it
                                this@apply.label.setText("+")
                                linkedButton.label.setText(texts.first)
                                metarExpandArray.removeValue(arptId, false)
                            } else {
                                // Hidden, expand it
                                this@apply.label.setText("-")
                                linkedButton.label.setText(texts.second)
                                metarExpandArray.add(arptId)
                            }
                        }
                    }.cell(height = 50f, width = 50f, padLeft = 10f, padRight = 20f, align = Align.topRight)
                    airportAtisButtonMap[arptId] = Pair(linkedButton, expandButton)
                    row()
                    airportRwySettingButtonMap[arptId] = imageButton("RunwayConfig").apply {
                        addChangeListener { _, _ ->
                            buttonsBeingModified = true
                            setPaneToAirportRwyConfig(airport)
                            commsButton.isChecked = false
                            statusButton.isChecked = false
                            buttonsBeingModified = false
                        }
                    }.cell(height = 75f, width = 50f, padLeft = 10f, padRight = 20f, padTop = 10f, align = Align.topRight)
                    align(Align.top)
                    cell(growY = true)
                    padTop(if (padTop) 30f else 0f)
                }
                metarPane.row()
                padTop = true
            }
        }
        metarPane.padBottom(35f)
    }

    /** Updates the ATIS pane display label (for new METAR/runway information change) for all airports */
    fun updateAtisInformation() {
        CLIENT_SCREEN?.let {
            for (airportEntry in Entries(it.airports)) {
                val airport = airportEntry.value
                val airportInfo = airport.entity[AirportInfo.mapper] ?: continue
                val metarInfo = airport.entity[MetarInfo.mapper] ?: continue
                val rwyDisplay = getRunwayInformationDisplay(airport.entity)
                val text = """
                    ${airportInfo.icaoCode} - ${metarInfo.letterCode}
                    $rwyDisplay
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    """.trimIndent()
                val expandedText = """
                    ${airportInfo.icaoCode} - ${metarInfo.letterCode}
                    $rwyDisplay
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    Winds: ${if (metarInfo.windSpeedKt.toInt() == 0) "Calm" else "${if (metarInfo.windHeadingDeg == 0.toShort()) "VRB" else metarInfo.windHeadingDeg}@${metarInfo.windSpeedKt}kt ${if (metarInfo.windGustKt > 0) "gusting ${metarInfo.windGustKt}kt" else ""}"}
                    Visibility: ${if (metarInfo.visibilityM >= 9999.toShort()) 10000 else metarInfo.visibilityM}m
                    Ceiling: ${if (metarInfo.rawMetar?.contains("CAVOK") == true) "CAVOK"
                else metarInfo.ceilingHundredFtAGL?.let { ceiling -> "${ceiling * 100} feet" } ?: "None"}
                    """.trimIndent() + if (metarInfo.windshear.isNotEmpty()) "\nWindshear: ${metarInfo.windshear}" else ""
                airportAtisMap[airportInfo.arptId] = Pair(text, expandedText)
                airportAtisButtonMap[airportInfo.arptId]?.first?.setText(if (metarExpandArray.contains(airportInfo.arptId, false)) expandedText else text)
            }
        }
    }

    /**
     * Gets the arrival, departure runway display string in the format of:
     *
     * DEP - XXX, XXX     ARR - XXX, XXX
     * @return a string representing the arrival, departure runways in the above format
     */
    private fun getRunwayInformationDisplay(airport: Entity): String {
        var dep = "DEP - "
        var depAdded = false
        var arr = "ARR - "
        var arrAdded = false
        val rwyMap = airport[RunwayChildren.mapper]?.rwyMap ?: return "$dep      $arr"
        Entries(rwyMap).forEach {
            val rwy = it.value
            if (rwy.entity.has(ActiveTakeoff.mapper)) {
                if (depAdded) dep += ", " else depAdded = true
                dep += rwy.entity[RunwayInfo.mapper]?.rwyName
            }
            if (rwy.entity.has(ActiveLanding.mapper)) {
                if (arrAdded) arr += ", " else arrAdded = true
                arr += rwy.entity[RunwayInfo.mapper]?.rwyName
            }
        }
        return "$dep      $arr"
    }

    /** Initializes the runway configuration panes for all the airports */
    fun initializeAirportRwyConfigPanes() {
        val airports = GAME.gameServer?.airports ?: CLIENT_SCREEN?.airports ?: return
        for (airportEntry in Entries(airports)) {
            val airport = airportEntry.value
            val arptId = airport.entity[AirportInfo.mapper]?.arptId ?: return
            paneContainer.actor = null
            val newTable = paneContainer.table {
                // debugAll()
                table {
                    label("Select runway configuration", "MenuPaneRunwayConfigLabel")
                    cell(colspan = 2, padBottom = 15f)
                }.cell(growX = true)
                row()
                scrollPane("MenuPane") {
                    table configTable@ {
                        // debugAll()
                        val buttonArray = GdxArray<KTextButton>()
                        val configs = airport.entity[RunwayConfigurationChildren.mapper]?.rwyConfigs ?: return
                        for (configEntry in Entries(configs)) {
                            val config = configEntry.value
                            val arrRwys = config.arrRwys
                            val depRwys = config.depRwys
                            val sb = StringBuilder()
                            if (config.timeRestriction == UsabilityFilter.DAY_ONLY) sb.append("Day only\n")
                            else if (config.timeRestriction == UsabilityFilter.NIGHT_ONLY) sb.append("Night only\n")
                            sb.append("DEP: ")
                            for (j in 0 until depRwys.size) {
                                sb.append(depRwys[j].entity[RunwayInfo.mapper]?.rwyName)
                                if (j < depRwys.size - 1) sb.append(", ")
                            }
                            sb.append("\nARR: ")
                            for (j in 0 until arrRwys.size) {
                                sb.append(arrRwys[j].entity[RunwayInfo.mapper]?.rwyName)
                                if (j < arrRwys.size - 1) sb.append(", ")
                            }
                            buttonArray.add(this@configTable.textButton(sb.toString(), "MenuPaneRunwayConfiguration").apply {
                                isChecked = airport.entity[ActiveRunwayConfig.mapper]?.configId == config.id
                                name = config.id.toString()
                                addChangeListener { _, _ ->
                                    if (buttonsBeingModified) return@addChangeListener
                                    buttonsBeingModified = true
                                    for (i in 0 until buttonArray.size) buttonArray[i].isChecked = false
                                    isChecked = true
                                    updateConfigButtonStates(arptId, airport.entity[ActiveRunwayConfig.mapper]?.configId, config.id)
                                    updateConfirmCancelButtons(airport.entity)
                                    buttonsBeingModified = false
                                }
                            }.cell(padLeft = 5f, padTop = 5f, padBottom = 5f, padRight = 20f, growX = true))
                            row()
                        }
                        airportRwyConfigButtonsMap[arptId] = buttonArray
                        align(Align.topLeft)
                    }
                    setOverscroll(false, false)
                    removeMouseScrollListeners()
                }.cell(preferredWidth = 0.6f * paneContainer.width, preferredHeight = paneContainer.height, align = Align.left, growY = true)
                table {
                    // debugAll()
                    val cfmButton = textButton("Already\nactive", "MenuPaneRunwayChange").cell(padRight = 20f, padBottom = 20f, growX = true).apply {
                        addChangeListener { _, _ ->
                            // Send the update request, ID has been stored in the name field of the button
                            val configIdToUse = if (name != null) name.toByte() else return@addChangeListener
                            GAME.gameServer?.also { server ->
                                server.airports[arptId]?.let { arpt ->
                                    if ((arpt.entity[RunwayConfigurationChildren.mapper]?.rwyConfigs?.get(configIdToUse)?.rwyAvailabilityScore ?: return@addChangeListener) == 0)
                                        return@addChangeListener
                                    arpt.activateRunwayConfig(configIdToUse)
                                    server.sendActiveRunwayUpdateToAll(arptId, configIdToUse)
                                    server.sendPendingRunwayUpdateToAll(arptId, null)
                                    arpt.entity.remove<PendingRunwayConfig>()
                                }
                            }
                        }
                        isVisible = false
                    }
                    row()
                    val cancelButton = textButton("Close\nPane", "MenuPaneRunwayChange").cell(padRight = 20f, growX = true).apply {
                        name = "Close" // When this is getting initialized, it will always start with the active runway config
                        addChangeListener { _, _ ->
                            // Reset to the current state
                            if (name == "") setAirportRunwayConfigPaneState(airport.entity)
                            else if (name == "Close") commsButton.isChecked = true
                        }
                    }
                    setBackground("ListBackground")
                    airportRwyConfigConfirmCancelMap[arptId] = Pair(cfmButton, cancelButton)
                }.cell(preferredWidth = 0.4f * paneContainer.width, align = Align.right, growY = true)
                setFillParent(true)
            }
            airportRwyConfigMap[arptId] = newTable
        }
        paneContainer.actor = commsPaneObj.commsTable
    }

    /**
     * Updates the main info pane to display runway configuration selections
     * @param airport the airport to display runway configurations for
     */
    private fun setPaneToAirportRwyConfig(airport: Airport) {
        val arptId = airport.entity[AirportInfo.mapper]?.arptId ?: return
        selectedArptId = arptId
        airportRwyConfigMap[arptId]?.let {
            paneContainer.actor = it
            setAirportRunwayConfigPaneState(airport.entity)
            return
        }
    }

    /**
     * Updates the UI to reflect the current runway configuration state
     * @param airport the airport entity to update for
     */
    fun setAirportRunwayConfigPaneState(airport: Entity) {
        val arptId = airport[AirportInfo.mapper]?.arptId ?: return
        val activeId = airport[ActiveRunwayConfig.mapper]?.configId
        val pendingId = airport[PendingRunwayConfig.mapper]?.pendingId
        airportRwySettingButtonMap[arptId]?.style = Scene2DSkin.defaultSkin["RunwayConfig${if (pendingId != null) "Pending" else ""}", ImageButtonStyle::class.java]
        airportRwyConfigButtonsMap[arptId]?.apply { for (i in 0 until size) { get(i).let { button ->
            button.isChecked = button.name.toByte() == (pendingId ?: activeId)
        }}}
        updateConfigButtonStates(arptId, activeId, pendingId)
        updateConfirmCancelButtons(airport)
    }

    /**
     * Updates all the buttons in the UI after a selection has been made
     * @param arptId the ID of the airport to update
     * @param currConfigId the ID of the current runway configuration
     * @param selectedConfigId the ID of the user selected runway configuration
     */
    private fun updateConfigButtonStates(arptId: Byte, currConfigId: Byte?, selectedConfigId: Byte?) {
        val configButtons = airportRwyConfigButtonsMap[arptId] ?: return
        for (i in 0 until configButtons.size) { configButtons[i]?.apply {
            val buttonId = name.toByte()
            val changed = (currConfigId != null && selectedConfigId != null && buttonId == currConfigId && currConfigId != selectedConfigId) ||
                    (selectedConfigId != null && buttonId == selectedConfigId && selectedConfigId != currConfigId)
            style = Scene2DSkin.defaultSkin["MenuPaneRunwayConfiguration${if (changed) "Changed" else ""}", TextButtonStyle::class.java]
        }}
    }

    /**
     * Updates the confirm and cancel buttons to reflect the usability of the runway configurations
     * @param airport the airport entity to update for
     */
    private fun updateConfirmCancelButtons(airport: Entity) {
        val arptId = airport[AirportInfo.mapper]?.arptId ?: return
        val cfmButton = airportRwyConfigConfirmCancelMap[arptId]?.first ?: return
        val cancelButton = airportRwyConfigConfirmCancelMap[arptId]?.second ?: return
        cfmButton.name = null
        cfmButton.style = Scene2DSkin.defaultSkin["MenuPaneRunwayChange", TextButtonStyle::class.java]
        val activeId = airport[ActiveRunwayConfig.mapper]?.configId
        val pendingId = airport[PendingRunwayConfig.mapper]?.pendingId

        val configButtons = airportRwyConfigButtonsMap[arptId] ?: return
        var selectedId: Byte? = null
        for (i in 0 until configButtons.size) { configButtons[i]?.apply {
            if (isChecked) selectedId = name.toByte()
        }}

        val selectedConfig = airport[RunwayConfigurationChildren.mapper]?.rwyConfigs?.get(selectedId)
        // Only show this button if selected ID is different from active ID
        cfmButton.isVisible = selectedId != activeId && selectedConfig != null
        // Only show this button if selected ID is different from active ID, and the selected ID is not equal to the ID of any pending runway changes
        val cancel = selectedId != activeId && selectedId != pendingId && selectedConfig != null
        cancelButton.name = if (cancel) "" else "Close"
        cancelButton.setText(if (cancel) "Cancel" else "Close\npane")
        if (selectedConfig != null) {
            val isNight = UsabilityFilter.isNight()
            cfmButton.setText(when {
                selectedConfig.timeRestriction == UsabilityFilter.DAY_ONLY && isNight -> "Only allowed\nin day"
                selectedConfig.timeRestriction == UsabilityFilter.NIGHT_ONLY && !isNight -> "Only allowed\nat night"
                selectedConfig.rwyAvailabilityScore == 0 -> "Not allowed\ndue to winds"
                GAME.gameServer == null -> "Only host\ncan change\nrunway"
                else -> {
                    cfmButton.name = selectedId?.toString()
                    cfmButton.style = Scene2DSkin.defaultSkin["MenuPaneRunwayChangeAllowed", TextButtonStyle::class.java]
                    if (selectedId == activeId) "Already\nactive" else "Confirm\nrunway\nchange"
                }
            })

        }
    }

    /**
     * Checks whether the currently selected pane is the status pane
     * @return true if status pane button selected, else false
     */
    fun isStatusPaneSelected(): Boolean { return statusButton.isChecked }
}
