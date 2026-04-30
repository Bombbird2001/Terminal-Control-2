package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.editor.io.AirportMapIO
import com.bombbird.terminalcontrol2.files.getExtDir
import com.bombbird.terminalcontrol2.global.AVAIL_AIRPORTS
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.UI_WIDTH
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.global.BUTTON_HEIGHT_BIG
import com.bombbird.terminalcontrol2.global.BUTTON_WIDTH_BIG
import com.bombbird.terminalcontrol2.global.BOTTOM_BUTTON_MARGIN
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.utilities.FileLog
import ktx.assets.toInternalFile
import ktx.scene2d.*

/** Menu screen to pick an airport and open the map editor. */
class MapEditorMenu : BasicUIScreen() {
    private var currSelectedAirport: KTextButton? = null
    private lateinit var start: KTextButton
    private lateinit var scrollPane: KScrollPane

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    label("Choose airport to edit:", "MenuHeader").cell(align = Align.center, padTop = 50f).setAlignment(Align.center)
                    row()
                    table {
                        scrollPane = scrollPane("NewGame") {
                            table {
                                for (icao in AVAIL_AIRPORTS) {
                                    textButton(icao.key, "NewLoadGameAirport").cell(growX = true, height = 150f).apply {
                                        addChangeListener { event, _ ->
                                            if (currSelectedAirport != this@apply) {
                                                currSelectedAirport?.isChecked = false
                                                currSelectedAirport = this@apply
                                                scrollPane.velocityY = 0f
                                                scrollPane.scrollY = 0f
                                            } else {
                                                currSelectedAirport = null
                                            }
                                            start.isVisible = currSelectedAirport != null
                                            event?.handle()
                                        }
                                    }
                                    row()
                                }
                            }
                            setOverscroll(false, false)
                        }.cell(align = Align.top, width = 300f, growY = true)
                        table {
                            table { }.cell(growY = true)
                            row().padTop(10f)
                            start = textButton("Edit", "NewLoadGameStart").cell(width = 400f, height = 100f).apply {
                                isVisible = false
                                addChangeListener { event, _ ->
                                    val icao = currSelectedAirport?.text?.toString()
                                    if (icao == null) return@addChangeListener
                                    try {
                                        val handle = getAirportArptFileHandle(icao)
                                        if (!handle.exists()) {
                                            CustomDialog("Missing file", "Could not find $icao.arpt", "", "Ok").show(stage)
                                            return@addChangeListener
                                        }
                                        val mapDef = AirportMapIO.parseArpt(handle.readString())

                                        // Replace any existing editor screen instance
                                        GAME.removeScreen<MapEditorScreen>()
                                        GAME.addScreen(MapEditorScreen(mapDef))
                                        GAME.setScreen<MapEditorScreen>()
                                    } catch (e: Exception) {
                                        FileLog.warn("MapEditorMenu", "Failed to open editor for $icao: ${e.message}")
                                        CustomDialog("Error", "Failed to open editor for $icao", "", "Ok").show(stage)
                                    }
                                    event?.handle()
                                }
                            }
                        }.cell(growY = true).align(Align.top)
                    }.cell(growY = true, padTop = 50f)
                    row().padTop(50f)
                    textButton("Back", "Menu").cell(width = BUTTON_WIDTH_BIG, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom)
                        .addChangeListener { _, _ -> GAME.setScreen<MainMenu>() }
                }
            }
        }
    }

    private fun getAirportArptFileHandle(icao: String): FileHandle {
        val upper = icao.uppercase()
        val customDir = getExtDir("Airports")
        val custom = customDir?.child("$upper.arpt")
        if (custom != null && custom.exists()) return custom
        return "Airports/$upper.arpt".toInternalFile()
    }
}

