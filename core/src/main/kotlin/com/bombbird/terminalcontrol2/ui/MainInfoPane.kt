package com.bombbird.terminalcontrol2.ui

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.utilities.addChangeListener
import com.bombbird.terminalcontrol2.utilities.removeMouseScrollListeners
import ktx.ashley.get
import ktx.ashley.has
import ktx.scene2d.*

lateinit var metarScroll: KScrollPane
lateinit var metarPane: KTableWidget
val metarExpandSet = HashSet<String>()

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
            metarScroll = scrollPane("MetarPane") {
                // debugAll()
                metarPane = table {
                    align(Align.top)
                }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(padTop = 20f, align = Align.top, preferredWidth = paneWidth, preferredHeight = UI_HEIGHT * 0.4f, growX = true)
            align(Align.top)
        }
        isVisible = true
    }
}

/** Updates the METAR pane displays (for new METAR information) */
fun updateMetarInformation() {
    metarPane.clear()
    CLIENT_SCREEN?.let {
        var padTop = false
        for (airport in it.airports.values()) {
            val airportInfo = airport.entity[AirportInfo.mapper] ?: continue
            val metarInfo = airport.entity[MetarInfo.mapper] ?: continue
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
                    Winds: ${if (metarInfo.windSpeedKt.toInt() == 0) "Calm" else "${if (metarInfo.windHeadingDeg == 0.toShort()) "VRB" else metarInfo.windHeadingDeg}@${metarInfo.windSpeedKt}kt ${if (metarInfo.windGustKt > 0) "gusting to ${metarInfo.windGustKt}kt" else ""}"}
                    Visibility: ${if (metarInfo.visibilityM == 9999.toShort()) 10000 else metarInfo.visibilityM}m
                    Ceiling: ${metarInfo.ceilingHundredFtAGL?.let { ceiling -> "${ceiling * 100} feet" } ?: "None"}
                    """.trimIndent() + if (metarInfo.windshear.isNotEmpty()) "\nWindshear: ${metarInfo.windshear}" else ""
            val linkedButton = metarPane.textButton(if (metarExpandSet.contains(airportInfo.icaoCode)) expandedText else text, "Metar").apply {
                label.setAlignment(Align.left)
                label.wrap = true
                cell(growX = true, padLeft = 20f, padRight = 10f, padTop = if (padTop) 30f else 0f, align = Align.left)
            }
            metarPane.textButton(if (metarExpandSet.contains(airportInfo.icaoCode)) "-" else "+", "MetarExpand").apply {
                cell(height = 50f, width = 50f, padLeft = 10f, padRight = 20f, padTop = if (padTop) 30f else 0f, align = Align.topRight)
                addChangeListener { _, _ ->
                    if (metarExpandSet.contains(airportInfo.icaoCode)) {
                        // Expanded, hide it
                        this@apply.label.setText("+")
                        linkedButton.label.setText(text)
                        metarExpandSet.remove(airportInfo.icaoCode)
                    } else {
                        // Hidden, expand it
                        this@apply.label.setText("-")
                        linkedButton.label.setText(expandedText)
                        metarExpandSet.add(airportInfo.icaoCode)
                    }
                }
            }
            metarPane.row()
            padTop = true
        }
    }
    metarPane.padBottom(20f)
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
