package com.nexora.git.core.editor

class EditorHistory(
    private val maxEntries: Int = 40,
) {
    private val undo = ArrayDeque<EditorRevision>()
    private val redo = ArrayDeque<EditorRevision>()

    fun record(previous: EditorRevision) {
        if (undo.lastOrNull() == previous) return

        undo.addLast(previous)
        while (undo.size > maxEntries) {
            undo.removeFirst()
        }
        redo.clear()
    }

    fun undo(current: EditorRevision): EditorRevision? {
        val revision = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return revision
    }

    fun redo(current: EditorRevision): EditorRevision? {
        val revision = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return revision
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    val canUndo: Boolean
        get() = undo.isNotEmpty()

    val canRedo: Boolean
        get() = redo.isNotEmpty()
}
