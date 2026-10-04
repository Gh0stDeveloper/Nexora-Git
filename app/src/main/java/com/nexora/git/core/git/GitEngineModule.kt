package com.nexora.git.core.git

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GitEngineModule {

    @Binds
    @Singleton
    abstract fun bindGitEngine(
        implementation: Libgit2GitEngine,
    ): GitEngine

    @Binds
    @Singleton
    abstract fun bindGitCredentialProvider(
        implementation: GitHubGitCredentialProvider,
    ): GitCredentialProvider
}
