package com.bombbird.terminalcontrol2.editor.undo

import kotlin.collections.ArrayDeque

/** Stack-based undo/redo with optional depth cap. */
class UndoRedoHistory(private val maxDepth: Int = 200) {
    private val undoStack = ArrayDeque<EditorCommand>()
    private val redoStack = ArrayDeque<EditorCommand>()

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** Applies [command] forward and clears the redo stack. */
    fun execute(command: EditorCommand) {
        command.execute()
        undoStack.addLast(command)
        while (undoStack.size > maxDepth) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        val cmd = undoStack.removeLastOrNull() ?: return
        cmd.undo()
        redoStack.addLast(cmd)
    }

    fun redo() {
        val cmd = redoStack.removeLastOrNull() ?: return
        cmd.execute()
        undoStack.addLast(cmd)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
