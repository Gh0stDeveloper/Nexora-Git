package com.nexora.git.core.editor.lsp

enum class LanguageServerStatus {
    UNAVAILABLE,
    STOPPED,
    STARTING,
    READY,
    ERROR,
}

data class LspPosition(
    val line: Int,
    val character: Int,
)

data class LspRange(
    val start: LspPosition,
    val end: LspPosition,
)

data class LspTextEdit(
    val range: LspRange,
    val newText: String,
)

data class LanguageServerDescriptor(
    val id: String,
    val displayName: String,
    val languageIds: Set<String>,
    val supportsFormatting: Boolean,
    val supportsHover: Boolean,
    val supportsDefinition: Boolean,
    val supportsCompletion: Boolean,
)

data class LanguageServerState(
    val descriptor: LanguageServerDescriptor,
    val status: LanguageServerStatus,
    val message: String? = null,
)
