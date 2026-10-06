package com.nexora.git.core.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGitHubJsonParserTest {

    private val parser = AdvancedGitHubJsonParser()

    @Test
    fun parsesDiscussionsAndProjects() {
        val discussions = parser.discussionHub(
            """
            {
              "repository":{
                "id":"R_1",
                "discussionCategories":{
                  "nodes":[
                    {
                      "id":"DIC_1",
                      "name":"General",
                      "description":"General discussion",
                      "isAnswerable":false
                    }
                  ]
                },
                "discussions":{
                  "nodes":[
                    {
                      "id":"D_1",
                      "number":4,
                      "title":"Roadmap",
                      "url":"https://github.com/ghost/repo/discussions/4",
                      "isAnswered":false,
                      "upvoteCount":3,
                      "updatedAt":"2026-10-05T00:00:00Z",
                      "author":{"login":"ghost"},
                      "category":{"name":"General"},
                      "comments":{"totalCount":2}
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        val projects = parser.projectHub(
            """
            {
              "repository":{
                "id":"R_1",
                "owner":{"id":"U_1"},
                "projectsV2":{
                  "nodes":[
                    {
                      "id":"PVT_1",
                      "number":7,
                      "title":"Mobile",
                      "shortDescription":"Android roadmap",
                      "url":"https://github.com/users/ghost/projects/7",
                      "closed":false,
                      "public":true,
                      "updatedAt":"2026-10-05T00:00:00Z",
                      "items":{"totalCount":12}
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("R_1", discussions.repositoryNodeId)
        assertEquals("General", discussions.categories.single().name)
        assertEquals(3, discussions.discussions.single().upvotes)
        assertEquals("U_1", projects.ownerNodeId)
        assertEquals("Mobile", projects.projects.single().title)
        assertEquals(12, projects.projects.single().itemCount)
    }

    @Test
    fun parsesPagesSecurityGistsAndCodespaces() {
        val pages = parser.pages(
            """
            {
              "html_url":"https://ghost.github.io/repo/",
              "status":"built",
              "cname":null,
              "https_enforced":true,
              "protected_domain_state":"verified",
              "build_type":"legacy",
              "source":{"branch":"main","path":"/"}
            }
            """.trimIndent(),
        )
        val dependabot = parser.dependabot(
            """
            [
              {
                "number":1,
                "state":"open",
                "html_url":"https://github.com/ghost/repo/security/dependabot/1",
                "created_at":"2026-10-01T00:00:00Z",
                "security_advisory":{"summary":"Dependency issue"},
                "security_vulnerability":{"severity":"high"}
              }
            ]
            """.trimIndent(),
        )
        val codeScanning = parser.codeScanning(
            """
            [
              {
                "number":2,
                "state":"open",
                "html_url":"https://github.com/ghost/repo/security/code-scanning/2",
                "created_at":"2026-10-01T00:00:00Z",
                "rule":{
                  "name":"unsafe-call",
                  "description":"Unsafe call",
                  "security_severity_level":"medium"
                }
              }
            ]
            """.trimIndent(),
        )
        val secrets = parser.secretScanning(
            """
            [
              {
                "number":3,
                "state":"open",
                "secret_type":"token",
                "secret_type_display_name":"Token",
                "html_url":"https://github.com/ghost/repo/security/secret-scanning/3",
                "created_at":"2026-10-01T00:00:00Z"
              }
            ]
            """.trimIndent(),
        )
        val gists = parser.gists(
            """
            [
              {
                "id":"abc123def456",
                "description":"Snippet",
                "html_url":"https://gist.github.com/ghost/abc123def456",
                "public":false,
                "files":{
                  "a.kt":{"filename":"a.kt"},
                  "b.md":{"filename":"b.md"}
                },
                "comments":1,
                "created_at":"now",
                "updated_at":"later"
              }
            ]
            """.trimIndent(),
        )
        val codespaces = parser.codespaces(
            """
            {
              "total_count":1,
              "codespaces":[
                {
                  "name":"ghost-repo-123",
                  "display_name":"Nexora workspace",
                  "state":"Available",
                  "web_url":"https://github.com/codespaces/ghost-repo-123",
                  "created_at":"now",
                  "updated_at":"later",
                  "repository":{"full_name":"ghost/repo"},
                  "machine":{"display_name":"2 cores"}
                }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(pages.enabled)
        assertEquals("main", pages.sourceBranch)
        assertEquals("high", dependabot.single().severity)
        assertEquals("Unsafe call", codeScanning.single().title)
        assertEquals("Token", secrets.single().title)
        assertFalse(gists.single().publicGist)
        assertEquals(listOf("a.kt", "b.md"), gists.single().fileNames)
        assertEquals("Available", codespaces.single().state)
        assertEquals("ghost/repo", codespaces.single().repositoryFullName)
    }
}
