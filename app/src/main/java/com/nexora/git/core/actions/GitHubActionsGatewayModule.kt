package com.nexora.git.core.actions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GitHubActionsGatewayModule {

    @Binds
    @Singleton
    abstract fun bindGitHubActionsGateway(
        implementation: GitHubActionsGatewayImpl,
    ): GitHubActionsGateway
}
