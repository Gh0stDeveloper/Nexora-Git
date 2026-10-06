package com.nexora.git.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSitterGrammarSelectionTest {

    @Test
    fun choosesGrammarFromFileExtension() {
        assertEquals("Kotlin", treeSitterGrammarFor("Main.kt", null))
        assertEquals("Kotlin", treeSitterGrammarFor("build.gradle.kts", null))
        assertEquals("JavaScript", treeSitterGrammarFor("app.jsx", null))
        assertEquals("TypeScript", treeSitterGrammarFor("app.ts", null))
        assertEquals("TSX", treeSitterGrammarFor("App.tsx", null))
        assertEquals("Python", treeSitterGrammarFor("main.py", null))
        assertEquals("JSON", treeSitterGrammarFor("package.json", null))
    }

    @Test
    fun reportsOnlyBundledGrammarsAsSupported() {
        assertTrue(
            treeSitterGrammarFor("Main.kt", "Kotlin") != null,
        )
        assertFalse(
            treeSitterGrammarFor("main.rs", "Rust") != null,
        )
    }
}
