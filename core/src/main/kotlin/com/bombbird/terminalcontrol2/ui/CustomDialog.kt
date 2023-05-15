package com.bombbird.terminalcontrol2.ui

import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import ktx.scene2d.Scene2DSkin

/** Class for a custom dialog box; extend this class to  */
open class CustomDialog(title: String, val text: String, private val negative: String, private val positive: String,
                        val height: Int = 500, val width: Int = 1200, private val fontScale: Float = 1f,
                        private val onNegative: (() -> Unit)? = null, private val onPositive: (() -> Unit)? = null):
    Dialog(title, Scene2DSkin.defaultSkin["DialogWindow", WindowStyle::class.java]) {
    companion object {
        // Dialog constants
        const val DIALOG_NEGATIVE = 0
        const val DIALOG_POSITIVE = 1
    }

    init {
        titleLabel.setAlignment(Align.top)
        titleLabel.setFontScale(fontScale)
        buttonTable.defaults().width(5f / 12 * width).height(140f * fontScale).padLeft(0.025f * width).padRight(0.025f * width)
        isMovable = false
        initialize()
        // debugAll()
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
        val label = Label("$newText\n", Scene2DSkin.defaultSkin, "DialogLabel")
        label.setFontScale(fontScale * 1.25f)
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
            negativeButton.label.setFontScale(fontScale * 1.25f)
            button(negativeButton, DIALOG_NEGATIVE)
        }
        if (positive.isNotEmpty()) {
            val positiveButton = TextButton(positive, Scene2DSkin.defaultSkin, "DialogButton")
            positiveButton.label.setFontScale(fontScale * 1.25f)
            button(positiveButton, DIALOG_POSITIVE)
        }
    }

    override fun getPrefHeight(): Float {
        return height.toFloat()
    }

    override fun getPrefWidth(): Float {
        return width.toFloat()
    }

    override fun result(res: Any?) {
        if (res == DIALOG_NEGATIVE && onNegative != null) return onNegative.invoke()
        if (res == DIALOG_POSITIVE && onPositive != null) return onPositive.invoke()
    }
}