package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.SECTOR_COUNT_SIZE
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.utilities.byte
import ktx.collections.GdxArray
import ktx.scene2d.*

class SectorPane {
    lateinit var sectorTable: KTableWidget
    private lateinit var sectorButtonTable: KTableWidget
    private lateinit var swapButton: KTextButton

    private val sectorButtonArray = GdxArray<KTextButton>(SECTOR_COUNT_SIZE)

    private var selectedId: Byte = -1
    private var containerWidth = 0f
    private var buttonsBeingModified = false

    /**
     * @param widget the widget to add the sector pane table to
     * @param paneWidth will be used as the reference width of the UI pane when initialising the pane
     * @return a [KTableWidget] used to contain elements of the sector pane
     */
    @Scene2dDsl
    fun sectorPane(widget: KWidget<Actor>, paneWidth: Float): KTableWidget {
        containerWidth = paneWidth
        sectorTable = widget.table {
            // debugAll()
            setBackground("ListBackground")
            scrollPane("MenuPane") {
                table {
                    debugAll()
                    sectorButtonTable = table { }.cell(grow = true)
                    swapButton = textButton("Request\nswap", "MenuPaneSectorChange").apply {
                        addChangeListener { _, _ ->
                            GAME.gameClientScreen?.let {
                                if (it.swapSectorRequest != null) {
                                    // If an active request is pending
                                    // TODO Cancel swap request
                                    return@addChangeListener
                                }
                                // If no selected request, return
                                if (selectedId.toInt() == -1) return@addChangeListener
                                // If the selected ID is the same as player sector, return
                                if (it.playerSector == selectedId) return@addChangeListener
                                // TODO Send swap request
                            }
                        }
                    }.cell(padLeft = 10f, padRight = 10f, preferredWidth = 0.3f * paneWidth)
                }
                setOverscroll(false, false)
                removeMouseScrollListeners()
            }.cell(preferredWidth = paneWidth, preferredHeight = 0.5f * UI_HEIGHT, grow = true)
        }
        return sectorTable
    }

    /**
     * Updates the sector pane buttons with the sectors in the game, this player's sector ID, outgoing sector request and
     * any incoming sector requests
     * @param sectors all sectors in the game currently
     */
    fun updateSectorDisplay(sectors: GdxArray<Sector>) {
        sectorButtonArray.clear()
        sectorButtonTable.apply {
            clear()
            for (i in 0 until sectors.size) {
                val sectorButton = textButton("Sector ${i + 1}", "MenuPaneSector").apply {
                    isChecked = GAME.gameClientScreen?.playerSector == i.byte
                    addChangeListener { _, _ ->
                        if (buttonsBeingModified) return@addChangeListener
                        val rs = GAME.gameClientScreen ?: return@addChangeListener
                        buttonsBeingModified = true
                        for (j in 0 until sectorButtonArray.size) {
                            sectorButtonArray[j].isChecked = false
                            sectorButtonArray[j].style = Scene2DSkin.defaultSkin["MenuPaneSector", TextButtonStyle::class.java]
                        }
                        isChecked = true
                        selectedId = i.byte
                        val isThisSector = i.byte == rs.playerSector
                        swapButton.setText(when {
                            !isThisSector && GAME.gameClientScreen?.incomingSwapRequest == i.byte -> "Accept\nswap\nrequest"
                            !isThisSector && GAME.gameClientScreen?.swapSectorRequest == i.byte -> "Cancel\nsent\nRequest"
                            !isThisSector -> "Request\nsector\nswap"
                            else -> ""
                        })
                        swapButton.isVisible = !isThisSector
                        swapButton.style = Scene2DSkin.defaultSkin[if (isThisSector) "MenuPaneSectorChange" else "MenuPaneSectorChanged", TextButtonStyle::class.java]
                        style = Scene2DSkin.defaultSkin[if (isThisSector) "MenuPaneSector" else "MenuPaneSectorChanged", TextButtonStyle::class.java]
                        buttonsBeingModified = false
                    }
                }.cell(preferredWidth = containerWidth * 0.3f - 30, padLeft = 15f, padRight = 15f, growX = true)
                sectorButtonArray.add(sectorButton)
                if (i % 2 == 1) row()
            }
        }
        selectedId = -1
        swapButton.isVisible = false
        swapButton.style = Scene2DSkin.defaultSkin["MenuPaneSectorChange", TextButtonStyle::class.java]
    }
}