package com.bombbird.terminalcontrol2.screens

import com.badlogic.gdx.scenes.scene2d.ui.Dialog

interface ShowsDialog {
    /**
     * Shows the input dialog in the UI stage
     * @param dialog the dialog to show
     */
    fun showDialog(dialog: Dialog)
}