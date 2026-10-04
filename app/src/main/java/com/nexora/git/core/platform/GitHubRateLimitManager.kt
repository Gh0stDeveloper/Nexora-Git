package com.nexora.git.core.platform

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Headers

@Singleton
class GitHubRateLimitManager @Inject constructor() {

    private val mutableLimits = MutableStateFlow<Map<String, GitHubRateLimit>>(
        emptyMap(),
    )

    val limits: StateFlow<Map<String, GitHubRateLimit>> =
        mutableLimits.asStateFlow()

    fun updateFromHeaders(
        headers: Headers,
        fallbackResource: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): GitHubRateLimit? {
        val hasRateHeaders = headers["X-RateLimit-Limit"] != null ||
            headers["X-RateLimit-Remaining"] != null ||
            headers["X-RateLimit-Reset"] != null ||
            headers["Retry-After"] != null

        if (!hasRateHeaders) return null

        val resource = headers["X-RateLimit-Resource"]
            ?.takeIf { it.isNotBlank() }
            ?: fallbackResource

        val snapshot = GitHubRateLimit(
            resource = resource,
            limit = headers["X-RateLimit-Limit"]?.toLongOrNull(),
            remaining = headers["X-RateLimit-Remaining"]?.toLongOrNull(),
            used = headers["X-RateLimit-Used"]?.toLongOrNull(),
            resetAtEpochSeconds =
                headers["X-RateLimit-Reset"]?.toLongOrNull(),
            retryAfterSeconds =
                headers["Retry-After"]?.toLongOrNull(),
            observedAtEpochMillis = nowEpochMillis,
        )

        mutableLimits.value = mutableLimits.value + (resource to snapshot)
        return snapshot
    }

    fun snapshot(resource: String): GitHubRateLimit? =
        mutableLimits.value[resource]

    fun mayRequest(
        resource: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val limit = snapshot(resource) ?: return true
        if (!limit.exhausted) return true

        val retryAt = limit.retryAtEpochMillis() ?: return false
        return nowEpochMillis >= retryAt
    }
}
