package com.nexora.git.core.files

enum class BrowserFileKind {
    TEXT,
    MARKDOWN,
    IMAGE,
    BINARY,
}

data class BrowserEntry(
    val name: String,
    val relativePath: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val kind: BrowserFileKind?,
    val language: String?,
)

data class BrowserDirectory(
    val relativePath: String,
    val entries: List<BrowserEntry>,
)

data class BrowserFile(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val mimeType: String,
    val kind: BrowserFileKind,
    val language: String?,
    val text: String?,
    val truncated: Boolean,
    val lineCount: Int,
)
