package com.nexora.git.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSitterGrammarSelectionTest {

    private val engine = TreeSitterSyntaxEngine(
        bridge = NativeSyntaxBridge(),
        parser = SyntaxJsonParser(),
    )

    @Test
    fun choosesGrammarFromFileExtension() {
        assertEquals("Kotlin", engine.grammarFor("Main.kt", null))
        assertEquals("Kotlin", engine.grammarFor("build.gradle.kts", null))
        assertEquals("JavaScript", engine.grammarFor("app.jsx", null))
        assertEquals("TypeScript", engine.grammarFor("app.ts", null))
        assertEquals("TSX", engine.grammarFor("App.tsx", null))
        assertEquals("Python", engine.grammarFor("main.py", null))
        assertEquals("JSON", engine.grammarFor("package.json", null))
    }

    @Test
    fun reportsOnlyBundledGrammarsAsSupported() {
        assertTrue(engine.supports("Main.kt", "Kotlin"))
        assertFalse(engine.supports("main.rs", "Rust"))
    }
}
