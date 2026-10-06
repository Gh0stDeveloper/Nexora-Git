package com.nexora.git.core.explore

import com.nexora.git.core.repository.RepositoryJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreJsonParserTest {

    private val parser = ExploreJsonParser(
        RepositoryJsonParser(),
    )

    @Test
    fun parsesRepositoryUserAndCodeSearchResults() {
        val repository = parser.repositories(
            """
            {
              "items":[
                {
                  "id":1,
                  "node_id":"R_1",
                  "name":"Nexora-Git",
                  "full_name":"Ghost/Nexora-Git",
                  "owner":{"login":"Ghost"},
                  "private":false,
                  "fork":false,
                  "archived":false,
                  "visibility":"public",
                  "default_branch":"main",
                  "clone_url":"https://github.com/Ghost/Nexora-Git.git",
                  "html_url":"https://github.com/Ghost/Nexora-Git",
                  "stargazers_count":10,
                  "forks_count":2,
                  "open_issues_count":1,
                  "size":100
                }
              ]
            }
            """.trimIndent(),
        ).single()

        assertEquals("Ghost/Nexora-Git", repository.fullName)

        val user = parser.users(
            """
            {"items":[{"id":2,"login":"Ghost","html_url":"https://github.com/Ghost","type":"User","score":1.0}]}
            """.trimIndent(),
        ).single()
        assertEquals("Ghost", user.login)

        val code = parser.code(
            """
            {
              "items":[
                {
                  "name":"Main.kt",
                  "path":"app/src/Main.kt",
                  "sha":"abcdef",
                  "html_url":"https://github.com/Ghost/Nexora-Git/blob/main/app/src/Main.kt",
                  "repository":{
                    "name":"Nexora-Git",
                    "full_name":"Ghost/Nexora-Git",
                    "owner":{"login":"Ghost"}
                  }
                }
              ]
            }
            """.trimIndent(),
        ).single()

        assertEquals("app/src/Main.kt", code.path)
        assertTrue(code.htmlUrl.contains("Main.kt"))
    }
}
