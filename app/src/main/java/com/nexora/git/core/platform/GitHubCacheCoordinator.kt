package com.nexora.git.core.platform

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubCacheCoordinator @Inject constructor(
    private val restCache: GitHubResponseCache,
    private val graphQlCache: GitHubGraphQlCache,
) {
    fun clearForAccount(accountId: Long) {
        restCache.clearForAccount(accountId)
        graphQlCache.clearForAccount(accountId)
    }

    fun clearAll() {
        restCache.clearAll()
        graphQlCache.clearAll()
    }
}
