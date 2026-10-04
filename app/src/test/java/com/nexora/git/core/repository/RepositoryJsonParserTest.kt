package com.nexora.git.core.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryJsonParserTest {

    private val parser = RepositoryJsonParser()

    @Test
    fun parsesRepositorySummaryAndPermissions() {
        val repositories = parser.summaries(
            """
            [
              {
                "id": 42,
                "node_id": "R_42",
                "name": "Nexora-Git",
                "full_name": "Ghost/Nexora-Git",
                "owner": {
                  "login": "Ghost",
                  "avatar_url": "https://avatars.example/1"
                },
                "private": true,
                "fork": false,
                "archived": false,
                "visibility": "private",
                "language": "Kotlin",
                "default_branch": "main",
                "clone_url": "https://github.com/Ghost/Nexora-Git.git",
                "html_url": "https://github.com/Ghost/Nexora-Git",
                "stargazers_count": 5,
                "forks_count": 2,
                "open_issues_count": 1,
                "size": 4096,
                "description": "Mobile Git",
                "permissions": {
                  "admin": true,
                  "maintain": false,
                  "push": true,
                  "triage": true,
                  "pull": true
                }
              }
            ]
            """.trimIndent(),
        )

        val repository = repositories.single()

        assertEquals(42L, repository.id)
        assertEquals("Ghost/Nexora-Git", repository.fullName)
        assertTrue(repository.privateRepository)
        assertEquals("Kotlin", repository.language)
        assertTrue(repository.permissions.admin)
        assertTrue(repository.permissions.push)
        assertTrue(repository.permissions.triage)
    }

    @Test
    fun parsesRepositoryDetails() {
        val details = parser.details(
            """
            {
              "id": 42,
              "node_id": "R_42",
              "name": "Nexora-Git",
              "full_name": "Ghost/Nexora-Git",
              "owner": {"login": "Ghost"},
              "private": false,
              "fork": false,
              "archived": false,
              "visibility": "public",
              "default_branch": "main",
              "clone_url": "https://github.com/Ghost/Nexora-Git.git",
              "html_url": "https://github.com/Ghost/Nexora-Git",
              "stargazers_count": 10,
              "forks_count": 3,
              "open_issues_count": 2,
              "size": 1024,
              "homepage": "https://nexora.example",
              "subscribers_count": 7,
              "has_issues": true,
              "has_wiki": false,
              "has_projects": true,
              "has_pages": false,
              "delete_branch_on_merge": true,
              "allow_merge_commit": true,
              "allow_squash_merge": true,
              "allow_rebase_merge": false,
              "permissions": {
                "admin": true,
                "push": true,
                "pull": true
              }
            }
            """.trimIndent(),
        )

        assertEquals("https://nexora.example", details.homepage)
        assertEquals(7L, details.subscribers)
        assertTrue(details.hasIssues)
        assertFalse(details.hasWiki)
        assertTrue(details.deleteBranchOnMerge)
        assertFalse(details.allowRebaseMerge)
        assertFalse(details.offlineSnapshot)
    }
}
