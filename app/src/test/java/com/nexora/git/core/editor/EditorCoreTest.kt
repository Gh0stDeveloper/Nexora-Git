package com.nexora.git.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCoreTest {

    private val operations = EditorTextOperations()

    @Test
    fun historySupportsUndoAndRedoWithinBound() {
        val history = EditorHistory(maxEntries = 2)
        val first = EditorRevision("a", 1, 1)
        val second = EditorRevision("ab", 2, 2)
        val third = EditorRevision("abc", 3, 3)

        history.record(first)
        history.record(second)

        val undo = history.undo(third)
        assertEquals(second, undo)
        assertTrue(history.canRedo)

        val redo = history.redo(second)
        assertEquals(third, redo)
    }

    @Test
    fun searchCanBeCaseSensitiveOrInsensitive() {
        val text = "Git git GIT"

        assertEquals(
            3,
            operations.findAll(
                text = text,
                query = "git",
                matchCase = false,
            ).size,
        )

        assertEquals(
            1,
            operations.findAll(
                text = text,
                query = "git",
                matchCase = true,
            ).size,
        )
    }

    @Test
    fun replaceAllRewritesEveryMatch() {
        val result = operations.replaceAll(
            text = "foo bar foo",
            query = "foo",
            replacement = "git",
            matchCase = true,
        )

        assertEquals("git bar git", result.text)
    }

    @Test
    fun indentSelectionUsesConfiguredWidth() {
        val result = operations.insertIndent(
            revision = EditorRevision(
                text = "first\nsecond",
                selectionStart = 0,
                selectionEnd = 12,
            ),
            style = EditorIndentStyle.SPACES_2,
        )

        assertEquals("  first\n  second", result.text)
    }

    @Test
    fun localDiffReportsChangedBlock() {
        val diff = operations.localDiff(
            original = "one\ntwo\nthree",
            current = "one\nchanged\nthree",
            relativePath = "sample.txt",
        )

        assertTrue(diff.patch.contains("--- a/sample.txt"))
        assertTrue(diff.patch.contains("-two"))
        assertTrue(diff.patch.contains("+changed"))
        assertEquals(1, diff.additions)
        assertEquals(1, diff.deletions)
    }

    @Test
    fun identicalTextProducesEmptyDiff() {
        val diff = operations.localDiff(
            original = "same",
            current = "same",
            relativePath = "same.txt",
        )

        assertEquals("No changes.", diff.patch)
        assertFalse(diff.additions > 0)
        assertFalse(diff.deletions > 0)
    }
}
