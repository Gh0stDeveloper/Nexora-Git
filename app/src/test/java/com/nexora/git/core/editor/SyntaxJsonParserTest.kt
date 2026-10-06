package com.nexora.git.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxJsonParserTest {

    private val parser = SyntaxJsonParser()

    @Test
    fun parsesTreeSitterSnapshot() {
        val snapshot = parser.parse(
            """
            {
              "engine":"tree-sitter",
              "language":"Kotlin",
              "rootType":"source_file",
              "hasErrors":false,
              "truncated":false,
              "spans":[
                {"start":0,"end":3,"kind":"keyword"},
                {"start":4,"end":8,"kind":"function"}
              ],
              "symbols":[
                {"name":"main","kind":"function","start":4,"end":8,"line":1}
              ],
              "diagnostics":[]
            }
            """.trimIndent(),
        )

        assertEquals("tree-sitter", snapshot.engine)
        assertEquals("Kotlin", snapshot.language)
        assertEquals(2, snapshot.spans.size)
        assertEquals(
            EditorSyntaxSpanKind.FUNCTION,
            snapshot.spans[1].kind,
        )
        assertEquals("main", snapshot.symbols.single().name)
        assertFalse(snapshot.hasErrors)
    }

    @Test
    fun parsesDiagnosticsAndIgnoresUnknownSpanKinds() {
        val snapshot = parser.parse(
            """
            {
              "engine":"tree-sitter",
              "language":"JSON",
              "rootType":"document",
              "hasErrors":true,
              "truncated":true,
              "spans":[
                {"start":0,"end":1,"kind":"future_kind"}
              ],
              "symbols":[],
              "diagnostics":[
                {
                  "severity":"error",
                  "message":"Syntax error",
                  "start":7,
                  "end":8,
                  "line":2,
                  "column":3
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(snapshot.hasErrors)
        assertTrue(snapshot.truncated)
        assertTrue(snapshot.spans.isEmpty())
        assertEquals(2, snapshot.diagnostics.single().line)
    }
}
