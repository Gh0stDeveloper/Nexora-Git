package com.nexora.git.core.pulls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestJsonParserTest {

    private val parser = PullRequestJsonParser()

    @Test
    fun parsesPullRequestDetailsAndDraftState() {
        val pull = parser.summary(
            """
            {
              "id":1,
              "node_id":"PR_1",
              "number":12,
              "title":"Review me",
              "body":"Body",
              "state":"open",
              "draft":true,
              "locked":false,
              "merged":false,
              "mergeable":true,
              "mergeable_state":"clean",
              "comments":2,
              "review_comments":3,
              "commits":4,
              "additions":10,
              "deletions":2,
              "changed_files":3,
              "created_at":"now",
              "updated_at":"now",
              "closed_at":null,
              "merged_at":null,
              "html_url":"https://github.com/example/repo/pull/12",
              "user":{"login":"ghost","avatar_url":null},
              "head":{
                "label":"ghost:feature",
                "ref":"feature",
                "sha":"aaaaaaaa",
                "repo":{"full_name":"example/repo"}
              },
              "base":{
                "label":"example:main",
                "ref":"main",
                "sha":"bbbbbbbb",
                "repo":{"full_name":"example/repo"}
              }
            }
            """.trimIndent(),
        )

        assertEquals(12, pull.number)
        assertTrue(pull.draft)
        assertEquals("feature", pull.head.ref)
        assertEquals("main", pull.base.ref)
        assertEquals(3, pull.changedFiles)
        assertFalse(pull.merged)
    }

    @Test
    fun parsesFilesReviewsCommentsAndChecks() {
        val files = parser.files(
            """
            [
              {
                "sha":"a",
                "filename":"app.kt",
                "status":"modified",
                "additions":4,
                "deletions":1,
                "changes":5,
                "blob_url":"https://example/blob",
                "raw_url":"https://example/raw",
                "previous_filename":null,
                "patch":"@@ -1 +1 @@"
              }
            ]
            """.trimIndent(),
        )
        val reviews = parser.reviews(
            """
            [
              {
                "id":2,
                "node_id":"R_2",
                "user":{"login":"reviewer","avatar_url":null},
                "body":"Looks good",
                "state":"APPROVED",
                "html_url":"https://example/review",
                "submitted_at":"now",
                "commit_id":"abc"
              }
            ]
            """.trimIndent(),
        )
        val comments = parser.reviewComments(
            """
            [
              {
                "id":3,
                "node_id":"RC_3",
                "pull_request_review_id":2,
                "user":{"login":"reviewer","avatar_url":null},
                "body":"Change this",
                "path":"app.kt",
                "diff_hunk":"@@",
                "line":8,
                "side":"RIGHT",
                "start_line":null,
                "start_side":null,
                "commit_id":"abc",
                "in_reply_to_id":null,
                "created_at":"now",
                "updated_at":"now",
                "html_url":"https://example/comment"
              }
            ]
            """.trimIndent(),
        )
        val checks = parser.checks(
            """
            {
              "total_count":1,
              "check_runs":[
                {
                  "id":4,
                  "name":"Android CI",
                  "status":"completed",
                  "conclusion":"success",
                  "details_url":"https://example/check",
                  "started_at":"now",
                  "completed_at":"later"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("app.kt", files.single().filename)
        assertEquals("APPROVED", reviews.single().state)
        assertEquals(8, comments.single().line)
        assertEquals("success", checks.single().conclusion)
    }

    @Test
    fun parsesMergeResult() {
        val merge = parser.mergeResult(
            """
            {
              "sha":"deadbeef",
              "merged":true,
              "message":"Pull Request successfully merged"
            }
            """.trimIndent(),
        )

        assertTrue(merge.merged)
        assertEquals("deadbeef", merge.sha)
    }
}
