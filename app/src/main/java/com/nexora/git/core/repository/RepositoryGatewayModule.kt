package com.nexora.git.core.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryGatewayModule {

    @Binds
    @Singleton
    abstract fun bindRepositoryGateway(
        implementation: GitHubRepositoryGateway,
    ): RepositoryGateway
}
