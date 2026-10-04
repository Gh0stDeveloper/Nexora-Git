package com.nexora.git.core.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryCacheMapperTest {

    @Test
    fun roundTripPreservesRepositoryIdentityAndPermissions() {
        val repository = RepositorySummary(
            id = 99,
            nodeId = "R_99",
            name = "app",
            fullName = "owner/app",
            ownerLogin = "owner",
            ownerAvatarUrl = null,
            description = "sample",
            privateRepository = true,
            fork = false,
            archived = false,
            visibility = "private",
            language = "Kotlin",
            defaultBranch = "main",
            cloneUrl = "https://github.com/owner/app.git",
            htmlUrl = "https://github.com/owner/app",
            stars = 1,
            forks = 2,
            openIssues = 3,
            sizeKb = 4,
            updatedAt = "2026-10-04T10:00:00Z",
            pushedAt = "2026-10-04T09:00:00Z",
            permissions = RepositoryPermissions(
                admin = true,
                maintain = true,
                push = true,
                triage = true,
                pull = true,
            ),
        )

        val restored = repository
            .toEntity(
                accountId = 123,
                cachedAtEpochMillis = 456,
            )
            .toDomain()

        assertEquals(repository.fullName, restored.fullName)
        assertEquals(repository.cloneUrl, restored.cloneUrl)
        assertTrue(restored.permissions.admin)
        assertTrue(restored.permissions.maintain)
    }
}
