package com.nexora.git.core.platform

import javax.inject.Inject
import javax.inject.Singleton

data class GitHubCachedResponse(
    val body: String?,
    val etag: String?,
    val storedAtEpochMillis: Long,
    val ttlMillis: Long,
    val statusCode: Int,
    val requestId: String?,
    val pagination: GitHubRestPagination,
    val rateLimit: GitHubRateLimit?,
    val acceptedPermissions: GitHubPermissionRequirement,
) {
    fun isFresh(
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = nowEpochMillis - storedAtEpochMillis <= ttlMillis
}

@Singleton
class GitHubResponseCache @Inject constructor() {

    private val lock = Any()

    private val entries = object :
        LinkedHashMap<String, GitHubCachedResponse>(
            MAX_ENTRIES,
            0.75f,
            true,
        ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, GitHubCachedResponse>?,
        ): Boolean = size > MAX_ENTRIES
    }

    fun get(key: String): GitHubCachedResponse? =
        synchronized(lock) {
            entries[key]
        }

    fun put(
        key: String,
        response: GitHubCachedResponse,
    ) {
        synchronized(lock) {
            entries[key] = response
        }
    }

    fun remove(key: String) {
        synchronized(lock) {
            entries.remove(key)
        }
    }

    fun clearForAccount(accountId: Long) {
        val prefix = "$accountId|"
        synchronized(lock) {
            entries.keys.removeAll { it.startsWith(prefix) }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            entries.clear()
        }
    }

    companion object {
        private const val MAX_ENTRIES = 128
    }
}
