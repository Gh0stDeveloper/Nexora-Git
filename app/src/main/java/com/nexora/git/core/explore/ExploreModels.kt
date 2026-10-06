package com.nexora.git.core.explore

import com.nexora.git.core.repository.RepositorySummary

enum class ExploreSearchSection {
    REPOSITORIES,
    USERS,
    CODE,
}

data class ExploreUser(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String,
    val type: String?,
    val score: Double,
)

data class ExploreCodeResult(
    val name: String,
    val path: String,
    val sha: String,
    val htmlUrl: String,
    val repositoryFullName: String,
    val repositoryOwner: String,
    val repositoryName: String,
)

data class ExploreSearchResults(
    val repositories: List<RepositorySummary> = emptyList(),
    val users: List<ExploreUser> = emptyList(),
    val code: List<ExploreCodeResult> = emptyList(),
)
