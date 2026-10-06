package com.nexora.git.core.search

data class ProjectSearchQuery(
    val text: String,
    val regex: Boolean = false,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val includeGlob: String = "",
    val excludeGlob: String = "",
)

data class ProjectSearchMatch(
    val path: String,
    val line: Int,
    val column: Int,
    val preview: String,
    val start: Int,
    val endExclusive: Int,
)

data class ProjectSearchResult(
    val matches: List<ProjectSearchMatch>,
    val filesScanned: Int,
    val filesSkipped: Int,
    val truncated: Boolean,
)
