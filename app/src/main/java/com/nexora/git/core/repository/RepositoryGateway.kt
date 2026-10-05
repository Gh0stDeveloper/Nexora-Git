package com.nexora.git.core.repository

import com.nexora.git.core.common.AppResult
import kotlinx.coroutines.flow.Flow

interface RepositoryGateway {
    val cachedRepositories: Flow<List<RepositorySummary>>

    suspend fun refreshRepositories(): AppResult<List<RepositorySummary>>

    suspend fun getRepository(
        owner: String,
        name: String,
    ): AppResult<RepositoryDetails>

    suspend fun createRepository(
        request: CreateRepositoryRequest,
    ): AppResult<RepositoryDetails>

    suspend fun forkRepository(
        request: ForkRepositoryRequest,
    ): AppResult<RepositoryDetails>

    suspend fun getViewerState(
        owner: String,
        name: String,
    ): AppResult<RepositoryViewerState>

    suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): AppResult<Unit>

    suspend fun setSubscription(
        repositoryNodeId: String,
        state: RepositorySubscriptionState,
    ): AppResult<RepositorySubscriptionState>

    suspend fun updateRepository(
        owner: String,
        name: String,
        request: UpdateRepositoryRequest,
    ): AppResult<RepositoryDetails>
}
