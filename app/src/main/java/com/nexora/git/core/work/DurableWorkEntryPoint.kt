package com.nexora.git.core.work

import com.nexora.git.core.repository.RepositoryGateway
import com.nexora.git.core.repository.RepositoryWorkspaceCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DurableWorkEntryPoint {
    fun repositoryGateway(): RepositoryGateway
    fun repositoryWorkspaceCoordinator(): RepositoryWorkspaceCoordinator
}
