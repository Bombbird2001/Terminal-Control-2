package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import ktx.scene2d.Scene2DSkin

/** Class for a custom dialog box; extend this class to  */
open class CustomDialog(title: String, val text: String, private val negative: String, private val positive: String,
                        val height: Int = 500, val width: Int = 1200, private val fontScale: Float = 1f):
    Dialog(title, Scene2DSkin.defaultSkin["DialogWindow", WindowStyle::class.java]) {
    companion object {
        // Dialog constants
        const val DIALOG_NEGATIVE = 0
        const val DIALOG_POSITIVE = 1
    }

    init {
        titleLabel.setAlignment(Align.top)
        titleLabel.setFontScale(fontScale)
        titleLabel.setScale(fontScale)
        buttonTable.defaults().width(5f / 12 * width).height(160f * fontScale).padLeft(0.025f * width).padRight(0.025f * width)
        isMovable = false
        initialize()
    }

    private fun initialize() {
        padTop(0.28f * height)
        padBottom(0.04f * height)

        updateText(text)
        generateButtons()

        isModal = true
    }

    private fun updateText(newText: String) {
        contentTable.clearChildren()
        val label = Label(newText, Scene2DSkin.defaultSkin, "DialogLabel")
        label.setScale(fontScale)
        label.setFontScale(fontScale)
        label.setAlignment(Align.center)
        text(label)
    }

    /*
    fun updateButtons(newNegative: String, newPositive: String) {
        negative = newNegative
        positive = newPositive
        generateButtons()
    }
    */

    private fun generateButtons() {
        buttonTable.clearChildren()

        if (negative.isNotEmpty()) {
            val negativeButton = TextButton(negative, Scene2DSkin.defaultSkin, "DialogButton")
            negativeButton.label.setScale(fontScale)
            negativeButton.label.setFontScale(fontScale)
            button(negativeButton, DIALOG_NEGATIVE)
        }
        if (positive.isNotEmpty()) {
            val positiveButton = TextButton(positive, Scene2DSkin.defaultSkin, "DialogButton")
            positiveButton.label.setScale(fontScale)
            positiveButton.label.setFontScale(fontScale)
            button(positiveButton, DIALOG_POSITIVE)
        }
    }

    override fun getPrefHeight(): Float {
        return height.toFloat()
    }

    override fun getPrefWidth(): Float {
        return width.toFloat()
    }
}