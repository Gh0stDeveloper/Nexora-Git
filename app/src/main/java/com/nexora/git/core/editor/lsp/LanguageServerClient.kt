package com.nexora.git.core.editor.lsp

import kotlinx.coroutines.flow.StateFlow

interface LanguageServerClient {
    val descriptor: LanguageServerDescriptor
    val state: StateFlow<LanguageServerState>

    suspend fun openDocument(
        uri: String,
        languageId: String,
        text: String,
        version: Int,
    )

    suspend fun changeDocument(
        uri: String,
        text: String,
        version: Int,
    )

    suspend fun formatting(
        uri: String,
        tabSize: Int,
        insertSpaces: Boolean,
    ): List<LspTextEdit>

    suspend fun closeDocument(uri: String)

    suspend fun stop()
}
