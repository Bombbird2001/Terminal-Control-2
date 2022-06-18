package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.AIRPORT_SIZE
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import ktx.ashley.get
import ktx.ashley.has
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.collections.set
import ktx.scene2d.*

lateinit var metarScroll: KScrollPane
lateinit var metarPane: KTableWidget
val metarExpandArray = GdxArray<String>(AIRPORT_SIZE)

lateinit var paneContainer: KContainer<Actor>

lateinit var commsOuterTable: KTableWidget
lateinit var commsTable: KTableWidget
lateinit var statusOuterTable: KTableWidget
lateinit var statusTable: KTableWidget
lateinit var buttonTable: KTableWidget
lateinit var commsButton: KTextButton
lateinit var statusButton: KTextButton

lateinit var confirmRwyChangeButton: KTextButton
lateinit var cancelRwyChangeButton: KTextButton
val airportRwyConfigMap = GdxArrayMap<Byte, KTableWidget>(AIRPORT_SIZE)
val airportRwySettingButtonMap = GdxArrayMap<Byte, KImageButton>(AIRPORT_SIZE)
val airportRwyConfigButtonsMap = GdxArrayMap<Byte, GdxArray<KTextButton>>(AIRPORT_SIZE)
var buttonsBeingModified = false
var selectedArptId: Byte? = null

/**
 * @param paneWidth will be used as the reference width of the UI pane when initialising the container
 * @return a [KContainer] used to contain a table with the elements of the main information pane, which has been added to the [KWidget]
 * */
@Scene2dDsl
fun <S> KWidget<S>.mainInfoPane(paneWidth: Float): KContainer<Actor> {
    return container {
        // debugAll()
        fill()
        setSize(paneWidth, UI_HEIGHT)
        table {
            // debugAll()
            table {
                // debugAll()
                label("Score: Shiba", "Score").cell(align = Align.left, growX = true)
                row()
                label("High score: High shiba", "HighScore").cell(padTop = 10f, align = Align.left, growX = true)
            }.cell(padLeft = 20f, preferredWidth = paneWidth * 0.85f - 50)
            textButton("||", "Pause").cell(padRight = 30f, preferredWidth = paneWidth * 0.15f, height = UI_HEIGHT * 0.1f).addChangeListener { _, _ ->
                // Pause the client
                GAME.gameClientScreen?.pauseGame()
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
                    addChangeListener { _, _ ->
                        if (buttonsBeingModified) return@addChangeListener
                        buttonsBeingModified = true
                        if (!isChecked) isChecked = true
                        else {
                            paneContainer.actor = commsOuterTable
                            selectedArptId = null
                            statusButton.isChecked = false
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
                            paneContainer.actor = statusOuterTable
                            selectedArptId = null
                            commsButton.isChecked = false
                        }
                        buttonsBeingModified = false
                    }
                }
            }.cell(align = Align.left, colspan = 2)
            row()
            paneContainer = container {
                // debugAll()
                commsOuterTable = table {
                    scrollPane("MenuPane") {
                        // debugAll()
                        commsTable = table {
                            label("Shiba shiba shiba", "HighScore")
                        }
                        setOverscroll(false, false)
                        removeMouseScrollListeners()
                    }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT, grow = true)
                }
                actor = null
                statusOuterTable = table {
                    scrollPane("MenuPane") {
                        // debugAll()
                        statusTable = table {
                            label("Nom nom nom", "HighScore")
                        }
                        setOverscroll(false, false)
                        removeMouseScrollListeners()
                    }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT, grow = true)
                }
            }.cell(align = Align.top, grow = true, colspan = 2)
        }
        isVisible = true
    }
}

/** Updates the ATIS pane displays (for new METAR/runway information) */
fun updateAtisInformation() {
    metarPane.clear()
    CLIENT_SCREEN?.let {
        var padTop = false
        for (airport in it.airports.values()) {
            val airportInfo = airport.entity[AirportInfo.mapper] ?: continue
            val metarInfo = airport.entity[MetarInfo.mapper] ?: continue
            val arptId = airport.entity[AirportInfo.mapper]?.arptId ?: continue
            val rwyDisplay = getRunwayInformationDisplay(airport.entity)
            val text =
                """
                    ${airportInfo.icaoCode} - ${metarInfo.letterCode}
                    $rwyDisplay
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    """.trimIndent()
            val expandedText = """
                    ${airportInfo.icaoCode} - ${metarInfo.letterCode}
                    $rwyDisplay
                    ${metarInfo.rawMetar ?: "Loading METAR..."}
                    Winds: ${if (metarInfo.windSpeedKt.toInt() == 0) "Calm" else "${if (metarInfo.windHeadingDeg == 0.toShort()) "VRB" else metarInfo.windHeadingDeg}@${metarInfo.windSpeedKt}kt ${if (metarInfo.windGustKt > 0) "gusting ${metarInfo.windGustKt}kt" else ""}"}
                    Visibility: ${if (metarInfo.visibilityM == 9999.toShort()) 10000 else metarInfo.visibilityM}m
                    Ceiling: ${if (metarInfo.rawMetar?.contains("CAVOK") == true) "CAVOK"
                    else metarInfo.ceilingHundredFtAGL?.let { ceiling -> "${ceiling * 100} feet" } ?: "None"}
                    """.trimIndent() + if (metarInfo.windshear.isNotEmpty()) "\nWindshear: ${metarInfo.windshear}" else ""
            val linkedButton = metarPane.textButton(if (metarExpandArray.contains(airportInfo.icaoCode, false)) expandedText else text, "Metar").apply {
                label.setAlignment(Align.left)
                label.wrap = true
                cell(growX = true, padLeft = 20f, padRight = 10f, padTop = if (padTop) 30f else 0f, align = Align.left)
            }
            metarPane.table {
                // debugAll()
                textButton(if (metarExpandArray.contains(airportInfo.icaoCode, false)) "-" else "+", "MetarExpand").apply {
                    addChangeListener { _, _ ->
                        if (metarExpandArray.contains(airportInfo.icaoCode)) {
                            // Expanded, hide it
                            this@apply.label.setText("+")
                            linkedButton.label.setText(text)
                            metarExpandArray.removeValue(airportInfo.icaoCode, false)
                        } else {
                            // Hidden, expand it
                            this@apply.label.setText("-")
                            linkedButton.label.setText(expandedText)
                            metarExpandArray.add(airportInfo.icaoCode)
                        }
                    }
                }.cell(height = 50f, width = 50f, padLeft = 10f, padRight = 20f, align = Align.topRight)
                row()
                airportRwySettingButtonMap[arptId] = imageButton("RunwayConfig").apply {
                    addChangeListener { _, _ ->
                        setPaneToAirportRwyConfig(airport)
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

/**
 * Gets the arrival, departure runway display string in the format of:
 *
 * DEP - XXX, XXX     ARR - XXX, XXX
 * @return a string representing the arrival, departure runways in the above format
 * */
fun getRunwayInformationDisplay(airport: Entity): String {
    var dep = "DEP - "
    var depAdded = false
    var arr = "ARR - "
    var arrAdded = false
    airport[RunwayChildren.mapper]?.rwyMap?.values()?.forEach {
        if (it.entity.has(ActiveTakeoff.mapper)) {
            if (depAdded) dep += ", " else depAdded = true
            dep += it.entity[RunwayInfo.mapper]?.rwyName
        }
        if (it.entity.has(ActiveLanding.mapper)) {
            if (arrAdded) arr += ", " else arrAdded = true
            arr += it.entity[RunwayInfo.mapper]?.rwyName
        }
    }
    return "$dep      $arr"
}

/**
 * Updates the main info pane to display runway configuration selections
 * @param airport the airport to display runway configurations for
 */
fun setPaneToAirportRwyConfig(airport: Airport) {
    val arptId = airport.entity[AirportInfo.mapper]?.arptId ?: return
    selectedArptId = arptId
    airportRwyConfigMap[arptId]?.let {
        paneContainer.actor = it
        setAirportRunwayConfigState(airport.entity)
        return
    }
    paneContainer.actor = null
    val newTable = paneContainer.table {
        // debugAll()
        val configs = airport.entity[RunwayConfigurationChildren.mapper]?.rwyConfigs ?: return
        table {
            label("Select runway configuration", "MenuPaneRunwayConfigLabel")
            cell(colspan = 2, padBottom = 15f)
        }.cell(growX = true)
        row()
        scrollPane("MenuPane") {
            table configTable@ {
                // debugAll()
                val buttonArray = GdxArray<KTextButton>()
                for (config in configs.values()) {
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
            confirmRwyChangeButton = textButton("Confirm\nrunway\nchange", "MenuPaneRunwayChange").cell(padRight = 20f, padBottom = 20f, growX = true).apply {
                addChangeListener { _, _ ->
                    // Send the update request, ID has been stored in the name field of the button
                    val configIdToUse = if (name != null) name.toByte() else return@addChangeListener
                    GAME.gameServer?.also { server ->
                        server.airports[arptId]?.let { arpt ->
                            if ((arpt.entity[RunwayConfigurationChildren.mapper]?.rwyConfigs?.get(configIdToUse)?.rwyAvailabilityScore ?: return@addChangeListener) == 0)
                                return@addChangeListener
                            arpt.activateRunwayConfig(configIdToUse)
                            server.sendActiveRunwayUpdateToAll(arptId, configIdToUse)
                        }
                    }
                }
            }
            row()
            cancelRwyChangeButton = textButton("Cancel", "MenuPaneRunwayChange").cell(padRight = 20f, growX = true).apply {
                addChangeListener { _, _ ->
                    // Reset to the current state
                    setAirportRunwayConfigState(airport.entity)
                }
            }
            setBackground("ListBackground")
        }.cell(preferredWidth = 0.4f * paneContainer.width, align = Align.right, growY = true)
        setFillParent(true)
    }
    airportRwyConfigMap[arptId] = newTable
}

/**
 * Updates the UI to reflect the current runway configuration state
 * @param airport the airport entity to update for
 * */
fun setAirportRunwayConfigState(airport: Entity) {
    val arptId = airport[AirportInfo.mapper]?.arptId ?: return
    val activeId = airport[ActiveRunwayConfig.mapper]?.configId
    val pendingId = airport[PendingRunwayConfig.mapper]?.pendingId
    airportRwySettingButtonMap[arptId]?.style = Scene2DSkin.defaultSkin["RunwayConfig${if (pendingId != null) "Pending" else ""}", ImageButtonStyle::class.java]
    airportRwyConfigButtonsMap[arptId]?.apply { for (i in 0 until size) { get(i).let { button ->
        button.isChecked = button.name.toByte() == activeId
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

private fun updateConfirmCancelButtons(airport: Entity) {
    confirmRwyChangeButton.name = null
    confirmRwyChangeButton.style = Scene2DSkin.defaultSkin["MenuPaneRunwayChange", TextButtonStyle::class.java]
    val arptId = airport[AirportInfo.mapper]?.arptId ?: return
    val activeId = airport[ActiveRunwayConfig.mapper]?.configId
    val pendingId = airport[PendingRunwayConfig.mapper]?.pendingId

    val configButtons = airportRwyConfigButtonsMap[arptId] ?: return
    var selectedId: Byte? = null
    for (i in 0 until configButtons.size) { configButtons[i]?.apply {
        if (isChecked) selectedId = name.toByte()
    }}

    val selectedConfig = airport[RunwayConfigurationChildren.mapper]?.rwyConfigs?.get(selectedId)
    // Only show this button if selected ID is different from active ID
    confirmRwyChangeButton.isVisible = selectedId != activeId && selectedConfig != null
    // Only show this button if selected ID is different from active ID, and the selected ID is not equal to the ID of any pending runway changes
    cancelRwyChangeButton.isVisible = selectedId != activeId && selectedId != pendingId && selectedConfig != null
    if (selectedConfig != null) {
        val currTime = UsabilityFilter.DAY_ONLY // TODO Change depending on time of day
        confirmRwyChangeButton.setText(when {
            selectedConfig.rwyAvailabilityScore == 0 -> "Not allowed\ndue to winds"
            selectedConfig.timeRestriction == UsabilityFilter.DAY_ONLY && currTime == UsabilityFilter.NIGHT_ONLY -> "Only used\nin day"
            selectedConfig.timeRestriction == UsabilityFilter.NIGHT_ONLY && currTime == UsabilityFilter.DAY_ONLY -> "Only used\nat night"
            GAME.gameServer == null -> "Only host\ncan change\nrunway"
            else -> {
                confirmRwyChangeButton.name = selectedId?.toString()
                confirmRwyChangeButton.style = Scene2DSkin.defaultSkin["MenuPaneRunwayChangeAllowed", TextButtonStyle::class.java]
                "Confirm\nrunway\nchange"
            }
        })

    }
}

/**
 * Updates the active selected runway configuration pane if the input airport matches
 *
 * Call when the airport runway configuration has changed
 */
fun checkUpdateRunwayPaneState(airport: Airport) {
    selectedArptId.let {
        if (airport.entity[AirportInfo.mapper]?.arptId == it)
            setAirportRunwayConfigState(airport.entity)
    }
}
