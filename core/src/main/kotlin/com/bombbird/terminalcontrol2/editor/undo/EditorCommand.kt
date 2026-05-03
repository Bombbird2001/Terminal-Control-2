package com.bombbird.terminalcontrol2.editor.undo

/** Single reversible edit step for the map editor (command pattern). */
interface EditorCommand {
    /** Apply the change (forward). */
    fun execute()

    /** Revert [execute]. */
    fun undo()
}
