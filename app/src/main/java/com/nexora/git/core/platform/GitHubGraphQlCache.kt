package com.nexora.git.core.platform

import javax.inject.Inject
import javax.inject.Singleton

data class GitHubCachedGraphQlResponse(
    val response: GitHubGraphQlResponse,
    val storedAtEpochMillis: Long,
    val ttlMillis: Long,
) {
    fun isFresh(
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = nowEpochMillis - storedAtEpochMillis <= ttlMillis
}

@Singleton
class GitHubGraphQlCache @Inject constructor() {

    private val lock = Any()

    private val entries = object :
        LinkedHashMap<String, GitHubCachedGraphQlResponse>(
            MAX_ENTRIES,
            0.75f,
            true,
        ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, GitHubCachedGraphQlResponse>?,
        ): Boolean = size > MAX_ENTRIES
    }

    fun get(key: String): GitHubCachedGraphQlResponse? =
        synchronized(lock) {
            entries[key]
        }

    fun put(
        key: String,
        value: GitHubCachedGraphQlResponse,
    ) {
        synchronized(lock) {
            entries[key] = value
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
        private const val MAX_ENTRIES = 64
    }
}
