package com.nexora.git.core.editor

enum class EditorSyntaxSpanKind {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    TYPE,
    FUNCTION,
    PROPERTY,
}

data class EditorSyntaxSpan(
    val start: Int,
    val endExclusive: Int,
    val kind: EditorSyntaxSpanKind,
)

data class EditorSymbol(
    val name: String,
    val kind: String,
    val start: Int,
    val endExclusive: Int,
    val line: Int,
)

data class EditorDiagnostic(
    val severity: String,
    val message: String,
    val start: Int,
    val endExclusive: Int,
    val line: Int,
    val column: Int,
)

data class EditorSyntaxSnapshot(
    val engine: String,
    val language: String,
    val rootType: String,
    val hasErrors: Boolean,
    val truncated: Boolean,
    val spans: List<EditorSyntaxSpan>,
    val symbols: List<EditorSymbol>,
    val diagnostics: List<EditorDiagnostic>,
)

interface EditorSyntaxEngine {
    fun supports(
        fileName: String,
        language: String?,
    ): Boolean

    suspend fun analyze(
        fileName: String,
        language: String?,
        source: String,
    ): EditorSyntaxSnapshot?
}
