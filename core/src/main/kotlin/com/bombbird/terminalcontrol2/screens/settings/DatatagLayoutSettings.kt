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
import com.bombbird.terminalcontrol2.ui.CustomDialog
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
    private val layoutNameLabel: Label
    private val layoutNameField: TextField
    private var currSelectedCheckboxOption: String? = null
    private val showWhenChangedCheckbox: CheckBox
    private val previewLabel: Label
    private val pageButton: KTextButton
    private val configSelectBox: KSelectBox<String>
    private var showingMainPage = true
    private var currPreviewLayout: DatatagConfig = DatatagConfig("Empty")
    private var miniArrangementFirstEmpty = false
    private var miniArrangementSecondEmpty = false
    private var modificationInProgress = false

    companion object {
        private const val NONE = "(None)"
        private const val NEW_LAYOUT = "+ New layout"
        private const val NEW_LAYOUT_PLACEHOLDER = "New layout"

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

        private val PREVIEW_DATA = HashMap<String, String>().apply {
            put(DatatagConfig.CALLSIGN, "SHB123")
            put(DatatagConfig.CALLSIGN_RECAT, "SHB123/B")
            put(DatatagConfig.CALLSIGN_WAKE, "SHB123/H")
            put(DatatagConfig.ICAO_TYPE, "B77W")
            put(DatatagConfig.ICAO_TYPE_RECAT, "B77W/B")
            put(DatatagConfig.ICAO_TYPE_WAKE, "B77W/H")
            put(DatatagConfig.ALTITUDE, "24")
            put(DatatagConfig.ALTITUDE_TREND, "^")
            put(DatatagConfig.CMD_ALTITUDE, "90")
            put(DatatagConfig.EXPEDITE, "=>>")
            put(DatatagConfig.CLEARED_ALT, "150")
            put(DatatagConfig.HEADING, "54")
            put(DatatagConfig.LAT_CLEARED, "SHIBA")
            put(DatatagConfig.SIDSTARAPP_CLEARED, "HICAL1C")
            put(DatatagConfig.GROUND_SPEED, "214")
            put(DatatagConfig.CLEARED_IAS, "250")
            put(DatatagConfig.AIRPORT, "TCTP")
        }
    }

    init {
        stage.actors {
            container = container {
                fill()
                setSize(UI_WIDTH, UI_HEIGHT)
                table {
                    table {
                        configSelectBox = defaultSettingsSelectBox<String>().apply {
                            val availableLayouts = GdxArray<String>()
                            availableLayouts.add(NONE)
                            DATATAG_LAYOUTS.keys.filter { it != DatatagConfig.DEFAULT && it != DatatagConfig.COMPACT }.forEach { availableLayouts.add(it) }
                            availableLayouts.add(NEW_LAYOUT)
                            items = availableLayouts
                            addChangeListener { _, _ ->
                                setSelectBoxesToLayout(selected)
                            }
                        }
                        layoutNameLabel = label("Layout name:", "DatatagLayoutName").cell(padLeft = 70f).apply {
                            isVisible = false
                        }
                        layoutNameField = textField("", "DatatagLayoutName").cell(width = BUTTON_WIDTH_MEDIUM, padLeft = 20f).apply {
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
                                            items = GdxArray<String>().apply { add(NONE) }
                                            selected = NONE
                                            arrangementSelectBoxes.add(this)
                                            addChangeListener { _, _ ->
                                                if (modificationInProgress) return@addChangeListener
                                                updateSelectBoxOptionsForArrangement(arrangementSelectBoxes)
                                                currSelectedCheckboxOption = selected
                                                updateShowWhenChangedCheckbox()
                                                currPreviewLayout = getLayoutObjectFromSelectBoxes("Preview")
                                                updatePreview()
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
                                                items = GdxArray<String>().apply { add(NONE) }
                                                selected = NONE
                                                miniArrangementFirstSelectBoxes.add(this)
                                                addChangeListener { _, _ ->
                                                    if (modificationInProgress) return@addChangeListener
                                                    updateSelectBoxOptionsForArrangement(miniArrangementFirstSelectBoxes)
                                                    currSelectedCheckboxOption = selected
                                                    updateShowWhenChangedCheckbox()
                                                    miniArrangementFirstEmpty = checkIfAllSelectBoxesEmpty(miniArrangementFirstSelectBoxes)
                                                    currPreviewLayout = getLayoutObjectFromSelectBoxes("Preview")
                                                    updatePreview()
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
                                                items = GdxArray<String>().apply { add(NONE) }
                                                selected = NONE
                                                miniArrangementSecondSelectBoxes.add(this)
                                                addChangeListener { _, _ ->
                                                    if (modificationInProgress) return@addChangeListener
                                                    updateSelectBoxOptionsForArrangement(miniArrangementSecondSelectBoxes)
                                                    currSelectedCheckboxOption = selected
                                                    updateShowWhenChangedCheckbox()
                                                    miniArrangementSecondEmpty = checkIfAllSelectBoxesEmpty(miniArrangementSecondSelectBoxes)
                                                    currPreviewLayout = getLayoutObjectFromSelectBoxes("Preview")
                                                    updatePreview()
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
                                setAlignment(Align.left)
                                isVisible = false
                            }.cell(align = Align.center, padBottom = 50f)
                            row()
                            pageButton = textButton("Next =>", "DatatagLayoutNextPage").cell(width = BUTTON_WIDTH_BIG / 1.5f, height = BUTTON_HEIGHT_BIG).apply {
                                isVisible = false
                                addChangeListener { _, _ ->
                                    showingMainPage = !showingMainPage
                                    setText(if (showingMainPage) "Next =>" else "<= Back")
                                    updateSelectBoxesForPage()
                                    updatePreview()
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
                            if (!validateLayoutName(newName)) {
                                CustomDialog("Invalid name", "Names can only contain letters, digits, spaces," +
                                        " underscores and hyphens with maximum length of 20 characters", "", "Ok").show(stage)
                                return@addChangeListener
                            }
                            val newLayout = getLayoutObjectFromSelectBoxes(newName)
                            if (saveDatatagLayout(newLayout)) {
                                if (currName != newName) {
                                    currName?.let {
                                        deleteDatatagLayout(it)
                                        DATATAG_LAYOUTS.remove(it)
                                        if (DATATAG_STYLE_NAME == it) DATATAG_STYLE_NAME = DatatagConfig.DEFAULT
                                    }
                                }
                                DATATAG_LAYOUTS[newName] = newLayout
                                updateDatatagConfigChoices()
                                GAME.getScreen<DatatagSettings>().updateDatatagConfigChoices()
                                configSelectBox.selected = newName
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
        if (currName == NEW_LAYOUT) currName = NEW_LAYOUT_PLACEHOLDER
        if (layoutName == NONE) {
            for (i in 0 until arrangementSelectBoxes.size) arrangementSelectBoxes[i].isVisible = false
            for (i in 0 until miniArrangementFirstSelectBoxes.size) miniArrangementFirstSelectBoxes[i].isVisible = false
            for (i in 0 until miniArrangementSecondSelectBoxes.size) miniArrangementSecondSelectBoxes[i].isVisible = false
            layoutNameField.isVisible = false
            layoutNameLabel.isVisible = false
            previewLabel.isVisible = false
            pageButton.isVisible = false
        } else {
            updateSelectBoxesForPage()
            layoutNameField.isVisible = true
            layoutNameLabel.isVisible = true
            previewLabel.isVisible = true
            pageButton.isVisible = true
        }

        modificationInProgress = true
        val layout = DATATAG_LAYOUTS[layoutName]
        layoutNameField.text = if (layoutName == NEW_LAYOUT) NEW_LAYOUT_PLACEHOLDER else layoutName
        updateSelectBoxOptionsForArrangement(arrangementSelectBoxes)
        updateSelectBoxOptionsForArrangement(miniArrangementFirstSelectBoxes)
        updateSelectBoxOptionsForArrangement(miniArrangementSecondSelectBoxes)
        if (layout == null) {
            // Set all to none
            for (i in 0 until arrangementSelectBoxes.size) arrangementSelectBoxes[i].selected = NONE
            for (i in 0 until miniArrangementFirstSelectBoxes.size) arrangementSelectBoxes[i].selected = NONE
            for (i in 0 until miniArrangementSecondSelectBoxes.size) arrangementSelectBoxes[i].selected = NONE
            currPreviewLayout = DatatagConfig("Empty")
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
            currPreviewLayout = layout
        }

        updateSelectBoxOptionsForArrangement(arrangementSelectBoxes)
        updateSelectBoxOptionsForArrangement(miniArrangementFirstSelectBoxes)
        updateSelectBoxOptionsForArrangement(miniArrangementSecondSelectBoxes)

        miniArrangementFirstEmpty = checkIfAllSelectBoxesEmpty(miniArrangementFirstSelectBoxes)
        miniArrangementSecondEmpty = checkIfAllSelectBoxesEmpty(miniArrangementSecondSelectBoxes)
        updatePreview()
        modificationInProgress = false
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
            if (!added && selected != null && selected != NONE) itemsCopy.add(selected)
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

    /**
     * Checks whether all select boxes for the given arrangement are set to NONE
     * @param arrangementBoxes GdxArray of select boxes of the arrangement
     */
    private fun checkIfAllSelectBoxesEmpty(arrangementBoxes: GdxArray<SelectBox<String>>): Boolean {
        for (i in 0 until arrangementBoxes.size) {
            if (arrangementBoxes[i].selected != NONE) return false
        }
        return true
    }

    /** Updates the preview label depending on the selected options, page and timing */
    private fun updatePreview() {
        previewLabel.setText(currPreviewLayout.generateTagText(PREVIEW_DATA, !showingMainPage, false))
    }

    /** Update the choices available in the datatag config select box */
    private fun updateDatatagConfigChoices() {
        val availableLayouts = GdxArray<String>()
        availableLayouts.add(NONE)
        DATATAG_LAYOUTS.keys.filter { it != DatatagConfig.DEFAULT && it != DatatagConfig.COMPACT }.forEach { availableLayouts.add(it) }
        availableLayouts.add(NEW_LAYOUT)
        configSelectBox.items = availableLayouts
    }

    /**
     * Validates the layout name to contain only letters, digits, spaces, underscores and hyphens, max length 20
     * characters
     * @param name the name to validate
     */
    private fun validateLayoutName(name: String): Boolean {
        if (name == NEW_LAYOUT) return false
        if (name.length > 20) return false
        for (c in name) {
            if (!c.isLetterOrDigit() && c != ' ' && c != '_' && c != '-') return false
        }
        return true
    }

    override fun render(delta: Float) {
        super.render(delta)
        // We'll call update preview here if minimised page is active, and both first and second mini arrangement are
        // not empty
        if (currName != null && currName != NONE && !showingMainPage && !miniArrangementFirstEmpty && !miniArrangementSecondEmpty)
            updatePreview()
    }
}