package com.nexora.git.core.editor

enum class EditorIndentStyle(
    val label: String,
    val unit: String,
) {
    TABS("Tabs", "\t"),
    SPACES_2("2 spaces", "  "),
    SPACES_4("4 spaces", "    "),
}

data class EditorRevision(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

data class EditorSearchMatch(
    val start: Int,
    val endExclusive: Int,
)

data class EditorDiffPreview(
    val patch: String,
    val additions: Int,
    val deletions: Int,
)
