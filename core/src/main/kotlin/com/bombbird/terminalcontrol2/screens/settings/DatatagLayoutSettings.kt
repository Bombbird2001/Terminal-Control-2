package com.bombbird.terminalcontrol2.screens.settings

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.bombbird.terminalcontrol2.files.deleteDatatagLayout
import com.bombbird.terminalcontrol2.files.saveDatatagLayout
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.screens.BasicUIScreen
import com.bombbird.terminalcontrol2.ui.addChangeListener
import com.bombbird.terminalcontrol2.ui.datatag.DatatagConfig
import com.bombbird.terminalcontrol2.ui.defaultSettingsSelectBox
import com.bombbird.terminalcontrol2.ui.newSettingsRow
import ktx.collections.GdxArray
import ktx.collections.GdxArrayMap
import ktx.scene2d.*

class DatatagLayoutSettings: BasicUIScreen() {
    private val arrangementSelectBoxes = GdxArray<SelectBox<String>>(DatatagConfig.ARRANGEMENT_COLS * DatatagConfig.ARRANGEMENT_ROWS)
    private val miniArrangementFirstSelectBoxes = GdxArray<SelectBox<String>>(DatatagConfig.MINI_ARRANGEMENT_ROWS * DatatagConfig.MINI_ARRANGEMENT_COLS)
    private val miniArrangementSecondSelectBoxes = GdxArray<SelectBox<String>>(DatatagConfig.MINI_ARRANGEMENT_ROWS * DatatagConfig.MINI_ARRANGEMENT_COLS)
    private val showWhenChangedOptions = GdxArrayMap<String, Boolean>(DatatagConfig.CAN_BE_HIDDEN.size)
    private var currName: String? = null
    private val layoutNameField: TextField
    private var currSelectedCheckboxOption: String? = null
    private val showWhenChangedCheckbox: CheckBox
    private val previewLabel: Label
    private val pageButton: KTextButton
    private var showingMainPage = true

    companion object {
        private const val NONE = "(None)"
        private const val NEW_LAYOUT = "+ New layout"

        private val FIELD_POSITIONS = GdxArrayMap<String, Int>(17).apply {
            put(DatatagConfig.CALLSIGN, 0)
            put(DatatagConfig.CALLSIGN_RECAT, 1)
            put(DatatagConfig.CALLSIGN_WAKE, 2)
            put(DatatagConfig.ICAO_TYPE, 3)
            put(DatatagConfig.ICAO_TYPE_RECAT, 4)
            put(DatatagConfig.ICAO_TYPE_WAKE, 5)
            put(DatatagConfig.ALTITUDE, 6)
            put(DatatagConfig.ALTITUDE_TREND, 7)
            put(DatatagConfig.CMD_ALTITUDE, 8)
            put(DatatagConfig.EXPEDITE, 9)
            put(DatatagConfig.CLEARED_ALT, 10)
            put(DatatagConfig.HEADING, 11)
            put(DatatagConfig.LAT_CLEARED, 12)
            put(DatatagConfig.SIDSTARAPP_CLEARED, 13)
            put(DatatagConfig.GROUND_SPEED, 14)
            put(DatatagConfig.CLEARED_IAS, 15)
            put(DatatagConfig.AIRPORT, 16)
        }
    }

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    table {
                        defaultSettingsSelectBox<String>().apply {
                            val availableLayouts = GdxArray<String>()
                            availableLayouts.add(NONE)
                            DATATAG_LAYOUTS.keys.filter { it != DatatagConfig.DEFAULT && it != DatatagConfig.COMPACT }.forEach { availableLayouts.add(it) }
                            availableLayouts.add(NEW_LAYOUT)
                            items = availableLayouts
                            addChangeListener { _, _ ->
                                setSelectBoxesToLayout(selected)
                            }
                        }
                        layoutNameField = textField("", "DatatagLayoutName").cell(width = BUTTON_WIDTH_MEDIUM, padLeft = 50f).apply {
                            isVisible = false
                        }
                    }.padTop(50f)
                    row().padTop(50f)
                    table {
                        table {
                            table {
                                for (i in 0 until DatatagConfig.ARRANGEMENT_ROWS) {
                                    for (j in 0 until DatatagConfig.ARRANGEMENT_COLS) {
                                        defaultSettingsSelectBox<String>().cell(padRight = 20f, width = BUTTON_WIDTH_BIG / 3).apply {
                                            isVisible = false
                                            items = GdxArray<String>()
                                            arrangementSelectBoxes.add(this)
                                            addChangeListener { _, _ ->
                                                updateSelectBoxOptionsForArrangement(arrangementSelectBoxes)
                                                currSelectedCheckboxOption = selected
                                                updateShowWhenChangedCheckbox()
                                            }
                                            addListener(object: ClickListener() {
                                                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                                                    super.clicked(event, x, y)
                                                    currSelectedCheckboxOption = selected
                                                    updateShowWhenChangedCheckbox()
                                                }
                                            })
                                        }
                                    }
                                    newSettingsRow()
                                }
                            }
                            row()
                            table {
                                table {
                                    for (i in 0 until DatatagConfig.MINI_ARRANGEMENT_ROWS) {
                                        for (j in 0 until DatatagConfig.MINI_ARRANGEMENT_COLS) {
                                            defaultSettingsSelectBox<String>().cell(padRight = 20f, width = BUTTON_WIDTH_BIG / 3).apply {
                                                isVisible = false
                                                items = GdxArray<String>()
                                                miniArrangementFirstSelectBoxes.add(this)
                                                addChangeListener { _, _ ->
                                                    updateSelectBoxOptionsForArrangement(miniArrangementFirstSelectBoxes)
                                                    currSelectedCheckboxOption = selected
                                                    updateShowWhenChangedCheckbox()
                                                }
                                                addListener(object: ClickListener() {
                                                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                                                        super.clicked(event, x, y)
                                                        currSelectedCheckboxOption = selected
                                                        updateShowWhenChangedCheckbox()
                                                    }
                                                })
                                            }
                                        }
                                        newSettingsRow()
                                    }
                                }.padRight(100f)
                                table {
                                    for (i in 0 until DatatagConfig.MINI_ARRANGEMENT_ROWS) {
                                        for (j in 0 until DatatagConfig.MINI_ARRANGEMENT_COLS) {
                                            defaultSettingsSelectBox<String>().cell(padRight = 20f, width = BUTTON_WIDTH_BIG / 3).apply {
                                                isVisible = false
                                                items = GdxArray<String>()
                                                miniArrangementSecondSelectBoxes.add(this)
                                                addChangeListener { _, _ ->
                                                    updateSelectBoxOptionsForArrangement(miniArrangementSecondSelectBoxes)
                                                    currSelectedCheckboxOption = selected
                                                    updateShowWhenChangedCheckbox()
                                                }
                                                addListener(object: ClickListener() {
                                                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                                                        super.clicked(event, x, y)
                                                        currSelectedCheckboxOption = selected
                                                        updateShowWhenChangedCheckbox()
                                                    }
                                                })
                                            }
                                        }
                                        newSettingsRow()
                                    }
                                }
                            }.padTop(-BUTTON_HEIGHT_BIG / 1.5f * 4 - 3 * 30)
                        }.cell(padRight = 30f)
                        table {
                            showWhenChangedCheckbox = checkBox(" Show only when changed", "DatatagShowWhenChanged").apply {
                                isVisible = false
                                addChangeListener { _, _ ->
                                    if (showWhenChangedOptions.containsKey(currSelectedCheckboxOption)) {
                                        showWhenChangedOptions.put(currSelectedCheckboxOption, isChecked)
                                    }
                                }
                            }.cell(padBottom = 50f)
                            row()
                            previewLabel = label("Set fields and see\nthe preview here", "DatatagLayoutPreview").apply {
                                setAlignment(Align.center)
                                isVisible = false
                            }.cell(align = Align.center, padBottom = 50f)
                            row()
                            pageButton = textButton("Next =>", "DatatagLayoutNextPage").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG).apply {
                                isVisible = false
                                addChangeListener { _, _ ->
                                    showingMainPage = !showingMainPage
                                    setText(if (showingMainPage) "Next =>" else "<= Back")
                                    updateSelectBoxesForPage()
                                }
                            }
                        }
                    }.cell(expandY = true)
                    row().padTop(50f)
                    table {
                        textButton("Cancel", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, padRight = 100f, align = Align.bottom).addChangeListener { _, _ ->
                            GAME.setScreen<DatatagSettings>()
                        }
                        textButton("Save", "Menu").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG, padBottom = BOTTOM_BUTTON_MARGIN, align = Align.bottom).addChangeListener { _, _ ->
                            val newName = layoutNameField.text
                            val newLayout = getLayoutObjectFromSelectBoxes(newName)
                            if (saveDatatagLayout(newLayout)) {
                                if (currName != newName) {
                                    currName?.let {
                                        deleteDatatagLayout(it)
                                        DATATAG_LAYOUTS.remove(it)
                                    }
                                } else {
                                    DATATAG_LAYOUTS[newName] = newLayout
                                }
                            }
                            GAME.setScreen<DatatagSettings>()
                        }
                    }
                }
            }
        }
    }

    /** Updates visibility of the select boxes depending on whether the main page is selected */
    private fun updateSelectBoxesForPage() {
        for (i in 0 until arrangementSelectBoxes.size) arrangementSelectBoxes[i].isVisible = showingMainPage
        for (i in 0 until miniArrangementFirstSelectBoxes.size) miniArrangementFirstSelectBoxes[i].isVisible = !showingMainPage
        for (i in 0 until miniArrangementSecondSelectBoxes.size) miniArrangementSecondSelectBoxes[i].isVisible = !showingMainPage
    }

    /**
     * Sets the select box choices to that of the layout with the provided name
     * @param layoutName name of the layout to set the select boxes to
     */
    private fun setSelectBoxesToLayout(layoutName: String) {
        currName = layoutName
        if (layoutName == NONE) {
            for (i in 0 until arrangementSelectBoxes.size) arrangementSelectBoxes[i].isVisible = false
            for (i in 0 until miniArrangementFirstSelectBoxes.size) miniArrangementFirstSelectBoxes[i].isVisible = false
            for (i in 0 until miniArrangementSecondSelectBoxes.size) miniArrangementSecondSelectBoxes[i].isVisible = false
            layoutNameField.isVisible = false
            previewLabel.isVisible = false
            pageButton.isVisible = false
        } else {
            updateSelectBoxesForPage()
            layoutNameField.isVisible = true
            previewLabel.isVisible = true
            pageButton.isVisible = true
        }

        val layout = DATATAG_LAYOUTS[layoutName]
        layoutNameField.text = if (layoutName == NEW_LAYOUT) "New layout" else layoutName
        updateSelectBoxOptionsForArrangement(arrangementSelectBoxes)
        updateSelectBoxOptionsForArrangement(miniArrangementFirstSelectBoxes)
        updateSelectBoxOptionsForArrangement(miniArrangementSecondSelectBoxes)
        if (layout == null) {
            // Set all to none
            for (i in 0 until arrangementSelectBoxes.size) arrangementSelectBoxes[i].selected = NONE
            for (i in 0 until miniArrangementFirstSelectBoxes.size) arrangementSelectBoxes[i].selected = NONE
            for (i in 0 until miniArrangementSecondSelectBoxes.size) arrangementSelectBoxes[i].selected = NONE
        } else {
            // Set to selected values
            for ((rowIndex, row) in layout.arrangement.withIndex()) {
                for ((colIndex, col) in row.withIndex()) {
                    arrangementSelectBoxes[rowIndex * DatatagConfig.ARRANGEMENT_COLS + colIndex].selected = col
                }
            }
            for ((rowIndex, row) in layout.miniArrangementFirst.withIndex()) {
                for ((colIndex, col) in row.withIndex()) {
                    miniArrangementFirstSelectBoxes[rowIndex * DatatagConfig.MINI_ARRANGEMENT_COLS + colIndex].selected = col
                }
            }
            for ((rowIndex, row) in layout.miniArrangementSecond.withIndex()) {
                for ((colIndex, col) in row.withIndex()) {
                    miniArrangementSecondSelectBoxes[rowIndex * DatatagConfig.MINI_ARRANGEMENT_COLS + colIndex].selected = col
                }
            }
        }
    }

    /**
     * Updates the options available to the various select boxes depending on the currently selected options
     * @param selectBoxes array of select boxes to update
     */
    private fun updateSelectBoxOptionsForArrangement(selectBoxes: GdxArray<SelectBox<String>>) {
        val selectedOptions = GdxArray<String>()

        for (i in 0 until selectBoxes.size) {
            selectedOptions.add(selectBoxes[i].selected)
        }

        val availableOptions = GdxArray<String>().apply { add(NONE) }
        for (i in 0 until FIELD_POSITIONS.size) {
            val option = FIELD_POSITIONS.getKeyAt(i)
            if (!selectedOptions.contains(option, false)) availableOptions.add(option)
        }

        for (i in 0 until selectBoxes.size) {
            val itemsCopy = GdxArray<String>(availableOptions)
            val selectBox = selectBoxes[i]
            val selected = selectBox.selected
            var added = false
            for (j in 0 until itemsCopy.size) {
                val itemIndex = FIELD_POSITIONS[itemsCopy[j]] ?: continue
                val selectedIndex = FIELD_POSITIONS[selected] ?: continue
                if (selectedIndex < itemIndex) {
                    itemsCopy.insert(j, selected)
                    added = true
                    break
                }
            }
            if (!added) itemsCopy.add(selected)
            selectBox.items = itemsCopy
        }
    }

    /** Updates the checkbox status depending on currently selected option */
    private fun updateShowWhenChangedCheckbox() {
        if (showWhenChangedOptions.containsKey(currSelectedCheckboxOption)) {
            showWhenChangedCheckbox.isVisible = true
            showWhenChangedCheckbox.isChecked = showWhenChangedOptions[currSelectedCheckboxOption]
        } else showWhenChangedCheckbox.isVisible = false
    }

    /**
     * Constructs a new datatag config object from the select box settings with the input name
     * @param name the name of the config
     */
    private fun getLayoutObjectFromSelectBoxes(name: String): DatatagConfig {
        val arrangement = Array(DatatagConfig.ARRANGEMENT_ROWS) { Array(DatatagConfig.ARRANGEMENT_COLS) { "" } }
        for (i in 0 until arrangementSelectBoxes.size) {
            val selectBox = arrangementSelectBoxes[i]
            val text = selectBox.selected
            val row = i / DatatagConfig.ARRANGEMENT_COLS
            val col = i % DatatagConfig.ARRANGEMENT_COLS
            arrangement[row][col] = if (text == NONE) "" else text
        }

        val miniArrangementFirst = Array(DatatagConfig.MINI_ARRANGEMENT_ROWS) { Array(DatatagConfig.MINI_ARRANGEMENT_COLS) { "" } }
        for (i in 0 until miniArrangementFirstSelectBoxes.size) {
            val selectBox = miniArrangementFirstSelectBoxes[i]
            val text = selectBox.selected
            val row = i / DatatagConfig.MINI_ARRANGEMENT_COLS
            val col = i % DatatagConfig.MINI_ARRANGEMENT_COLS
            miniArrangementFirst[row][col] = if (text == NONE) "" else text
        }

        val miniArrangementSecond = Array(DatatagConfig.MINI_ARRANGEMENT_ROWS) { Array(DatatagConfig.MINI_ARRANGEMENT_COLS) { "" } }
        for (i in 0 until miniArrangementSecondSelectBoxes.size) {
            val selectBox = miniArrangementSecondSelectBoxes[i]
            val text = selectBox.selected
            val row = i / DatatagConfig.MINI_ARRANGEMENT_COLS
            val col = i % DatatagConfig.MINI_ARRANGEMENT_COLS
            miniArrangementSecond[row][col] = if (text == NONE) "" else text
        }

        val showWhenChanged = HashSet<String>()
        for (i in 0 until showWhenChangedOptions.size) {
            val option = showWhenChangedOptions.getKeyAt(i)
            val checked = showWhenChangedOptions.getValueAt(i)
            if (checked) showWhenChanged.add(option)
        }

        return DatatagConfig(name, showWhenChanged, arrangement, miniArrangementFirst, miniArrangementSecond)
    }
}