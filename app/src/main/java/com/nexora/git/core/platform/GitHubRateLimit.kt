package com.nexora.git.core.platform

data class GitHubRateLimit(
    val resource: String,
    val limit: Long?,
    val remaining: Long?,
    val used: Long?,
    val resetAtEpochSeconds: Long?,
    val retryAfterSeconds: Long?,
    val observedAtEpochMillis: Long,
) {
    val exhausted: Boolean
        get() = remaining != null && remaining <= 0L

    fun retryAtEpochMillis(): Long? =
        retryAfterSeconds
            ?.let { observedAtEpochMillis + (it * 1_000L) }
            ?: resetAtEpochSeconds?.times(1_000L)
}
