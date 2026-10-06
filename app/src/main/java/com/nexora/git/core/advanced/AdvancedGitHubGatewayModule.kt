package com.nexora.git.core.advanced

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdvancedGitHubGatewayModule {

    @Binds
    @Singleton
    abstract fun bindAdvancedGitHubGateway(
        implementation: GitHubAdvancedGitHubGateway,
    ): AdvancedGitHubGateway
}
