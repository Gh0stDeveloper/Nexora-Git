package com.nexora.git.core.social

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialGatewayModule {

    @Binds
    @Singleton
    abstract fun bindSocialGateway(
        implementation: GitHubSocialGateway,
    ): SocialGateway
}
