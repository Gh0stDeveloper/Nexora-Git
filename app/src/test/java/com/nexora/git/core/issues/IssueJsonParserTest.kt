package com.nexora.git.core.issues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueJsonParserTest {

    private val parser = IssueJsonParser()

    @Test
    fun parsesIssueDetailsMetadataAndReactions() {
        val issue = parser.details(
            """
            {
              "id":101,
              "node_id":"I_101",
              "number":42,
              "title":"Mobile issue",
              "state":"open",
              "locked":false,
              "body":"Description",
              "comments":3,
              "created_at":"2026-10-05T00:00:00Z",
              "updated_at":"2026-10-05T01:00:00Z",
              "closed_at":null,
              "html_url":"https://github.com/example/repo/issues/42",
              "user":{"login":"ghost","avatar_url":"https://example/avatar.png"},
              "labels":[
                {"id":1,"name":"bug","color":"ff0000","description":"Bug"}
              ],
              "assignees":[
                {"login":"dev","avatar_url":null}
              ],
              "milestone":{
                "number":2,
                "title":"1.0",
                "state":"open",
                "open_issues":4,
                "closed_issues":1,
                "due_on":null
              },
              "reactions":{
                "total_count":4,
                "+1":1,
                "-1":0,
                "laugh":0,
                "hooray":0,
                "confused":0,
                "heart":2,
                "rocket":1,
                "eyes":0
              }
            }
            """.trimIndent(),
        )

        assertEquals(42, issue.summary.number)
        assertEquals("Mobile issue", issue.summary.title)
        assertEquals("bug", issue.summary.labels.single().name)
        assertEquals("dev", issue.summary.assignees.single().login)
        assertEquals("1.0", issue.summary.milestone?.title)
        assertEquals(4, issue.reactions.totalCount)
        assertEquals(2, issue.reactions.heart)
        assertNull(issue.summary.closedAt)
    }

    @Test
    fun searchResultsExcludePullRequests() {
        val issues = parser.summaries(
            """
            {
              "total_count":2,
              "items":[
                {
                  "id":1,
                  "node_id":"I_1",
                  "number":1,
                  "title":"Issue",
                  "state":"open",
                  "locked":false,
                  "comments":0,
                  "created_at":"",
                  "updated_at":"",
                  "closed_at":null,
                  "html_url":"",
                  "user":{"login":"ghost","avatar_url":null},
                  "labels":[],
                  "assignees":[],
                  "milestone":null
                },
                {
                  "id":2,
                  "node_id":"PR_2",
                  "number":2,
                  "title":"Pull request",
                  "state":"open",
                  "locked":false,
                  "comments":0,
                  "created_at":"",
                  "updated_at":"",
                  "closed_at":null,
                  "html_url":"",
                  "user":{"login":"ghost","avatar_url":null},
                  "labels":[],
                  "assignees":[],
                  "milestone":null,
                  "pull_request":{"url":"https://api.github.com/pulls/2"}
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, issues.size)
        assertEquals("Issue", issues.single().title)
    }

    @Test
    fun parsesCommentsAndReactionPayload() {
        val comments = parser.comments(
            """
            [
              {
                "id":10,
                "node_id":"IC_10",
                "body":"Looks good",
                "created_at":"now",
                "updated_at":"now",
                "html_url":"",
                "user":{"login":"ghost","avatar_url":null},
                "reactions":{
                  "total_count":1,
                  "+1":1,
                  "-1":0,
                  "laugh":0,
                  "hooray":0,
                  "confused":0,
                  "heart":0,
                  "rocket":0,
                  "eyes":0
                }
              }
            ]
            """.trimIndent(),
        )

        assertEquals("Looks good", comments.single().body)
        assertEquals(1, comments.single().reactions.plusOne)

        val reaction = parser.reaction(
            """
            {
              "id":99,
              "content":"rocket",
              "user":{"login":"ghost","avatar_url":null}
            }
            """.trimIndent(),
        )

        assertEquals(99L, reaction.id)
        assertEquals("rocket", reaction.content)
        assertTrue(reaction.user.login.isNotBlank())
    }
}
