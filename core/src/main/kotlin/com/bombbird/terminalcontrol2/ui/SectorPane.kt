package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.bombbird.terminalcontrol2.entities.Sector
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.SECTOR_COUNT_SIZE
import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.utilities.byte
import ktx.collections.GdxArray
import ktx.scene2d.*

class SectorPane {
    lateinit var sectorTable: KTableWidget
    private lateinit var sectorButtonTable: KTableWidget
    private lateinit var swapButton: KTextButton
    private lateinit var declineButton: KTextButton

    private val sectorButtonArray = GdxArray<KTextButton>(SECTOR_COUNT_SIZE)

    var selectedId: Byte? = null
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
                    // debugAll()
                    sectorButtonTable = table { }.cell(grow = true)
                    table {
                        swapButton = textButton("Request\nswap", "MenuPaneSectorConfirm").apply {
                            addChangeListener { _, _ ->
                                CLIENT_SCREEN?.let {
                                    if (it.swapSectorRequest == selectedId) {
                                        // If an active request with same ID is pending, cancel it
                                        it.cancelSectorSwapRequest()
                                        it.swapSectorRequest = null
                                        setText("Request\nsector\nswap")
                                        style = Scene2DSkin.defaultSkin["MenuPaneSectorConfirmChanged", TextButtonStyle::class.java]
                                        return@addChangeListener
                                    }
                                    // If no selected request, return
                                    if (selectedId == null) return@addChangeListener
                                    // If the selected ID is the same as player sector, return
                                    if (it.playerSector == selectedId) return@addChangeListener
                                    it.swapSectorRequest = selectedId
                                    it.sendSectorSwapRequest(selectedId ?: return@addChangeListener)
                                    style = Scene2DSkin.defaultSkin["MenuPaneSectorConfirm", TextButtonStyle::class.java]
                                    setText("Cancel\nswap\nrequest")
                                }
                            }
                            isVisible = false
                        }.cell(growX = true)
                        row()
                        declineButton = textButton("Decline\nswap\nrequest", "MenuPaneSectorConfirm").apply {
                            addChangeListener { _, _ ->
                                CLIENT_SCREEN?.let {
                                    // Check and remove the selected sector ID from incoming requests, send decline request to server
                                    if (it.incomingSwapRequests.contains(selectedId, false)) {
                                        CLIENT_SCREEN?.declineSectorSwapRequest(selectedId ?: return@addChangeListener)
                                        it.incomingSwapRequests.removeValue(selectedId, false)
                                    }
                                    updateSectorDisplay(CLIENT_SCREEN?.sectors ?: return@addChangeListener)
                                }
                            }
                            isVisible = false
                        }.cell(growX = true, padTop = 20f)
                    }.cell(padLeft = 30f, padRight = 10f, preferredWidth = 0.3f * paneWidth)
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
                    isChecked = CLIENT_SCREEN?.playerSector == i.byte
                    addChangeListener { _, _ ->
                        updateSelectedSectorState(this, i.byte)
                    }
                }.cell(preferredWidth = containerWidth * 0.3f - 30, padLeft = 15f, padRight = 15f, growX = true)
                sectorButtonArray.add(sectorButton)
                if (i % 2 == 1) row().padTop(20f)
            }
        }


        selectedId?.let {
            // If selected ID is larger than last index due to player number change, set the ID to this player's sector
            val correctedSelectedId = if (it >= sectors.size) {
                CLIENT_SCREEN?.playerSector ?: return
            } else it
            selectedId = correctedSelectedId
            updateSelectedSectorState(sectorButtonArray[correctedSelectedId.toInt()], correctedSelectedId)
        }
    }

    /**
     * Updates the display state of the input button given its sector, depending on the current selection state, swap
     * request state and incoming request state
     * @param button the button to modify
     * @param buttonSector the sector ID the button is representing
     * */
    private fun updateSelectedSectorState(button: KTextButton, buttonSector: Byte) {
        if (buttonsBeingModified) return
        buttonsBeingModified = true
        val rs = CLIENT_SCREEN ?: return
        for (j in 0 until sectorButtonArray.size) {
            sectorButtonArray[j].isChecked = false
            sectorButtonArray[j].style = Scene2DSkin.defaultSkin["MenuPaneSector", TextButtonStyle::class.java]
        }
        button.isChecked = true
        selectedId = buttonSector
        val isThisSector = buttonSector == rs.playerSector
        swapButton.setText(when {
            !isThisSector && CLIENT_SCREEN?.incomingSwapRequests?.contains(buttonSector, false) == true -> "Accept\nswap\nrequest"
            !isThisSector && CLIENT_SCREEN?.swapSectorRequest == buttonSector -> "Cancel\nswap\nrequest"
            !isThisSector -> "Request\nsector\nswap"
            else -> ""
        })
        swapButton.isVisible = !isThisSector
        swapButton.style = Scene2DSkin.defaultSkin[if (isThisSector) "MenuPaneSectorConfirm" else "MenuPaneSectorConfirmChanged", TextButtonStyle::class.java]
        declineButton.isVisible = rs.incomingSwapRequests.contains(buttonSector, false)
        swapButton.style = Scene2DSkin.defaultSkin[if (isThisSector) "MenuPaneSectorConfirm" else "MenuPaneSectorConfirmChanged", TextButtonStyle::class.java]
        button.style = Scene2DSkin.defaultSkin[if (isThisSector) "MenuPaneSector" else "MenuPaneSectorChanged", TextButtonStyle::class.java]
        buttonsBeingModified = false
    }
}