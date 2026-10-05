package com.nexora.git.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitJsonParserTest {

    private val parser = GitJsonParser()

    @Test
    fun parsesStatusPayload() {
        val status = parser.status(
            """
            {
              "branch":"main",
              "entries":[
                {
                  "path":"README.md",
                  "flags":128,
                  "staged":false,
                  "workingTree":true,
                  "untracked":true,
                  "conflicted":false
                }
              ],
              "conflicted":false
            }
            """.trimIndent(),
        )

        assertEquals("main", status.branch)
        assertEquals(1, status.entries.size)
        assertTrue(status.entries.single().untracked)
    }

    @Test
    fun parsesHistoryAndBlamePayloads() {
        val history = parser.history(
            """
            [
              {
                "oid":"abcdef012345",
                "shortOid":"abcdef0",
                "summary":"Update file",
                "message":"Update file\n",
                "authorName":"Ghost",
                "authorEmail":"ghost@example.invalid",
                "timestampSeconds":1700000000,
                "timezoneOffsetMinutes":-420,
                "parentCount":1
              }
            ]
            """.trimIndent(),
        )

        assertEquals("abcdef0", history.single().shortOid)
        assertEquals("Ghost", history.single().authorName)

        val blame = parser.blame(
            """
            [
              {
                "startLine":1,
                "lineCount":2,
                "finalCommitOid":"abcdef012345",
                "originalCommitOid":"abcdef012345",
                "originalStartLine":1,
                "originalPath":"README.md",
                "authorName":"Ghost",
                "authorEmail":"ghost@example.invalid",
                "timestampSeconds":1700000000,
                "timezoneOffsetMinutes":-420,
                "boundary":false
              }
            ]
            """.trimIndent(),
        )

        assertEquals(1L, blame.single().startLine)
        assertEquals("README.md", blame.single().originalPath)
    }

    @Test
    fun parsesConflictMergeResult() {
        val result = parser.mergeResult(
            """
            {
              "state":"conflicts",
              "commitOid":"",
              "conflicts":[
                {
                  "path":"file.txt",
                  "ancestor":"file.txt",
                  "ours":"file.txt",
                  "theirs":"file.txt"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(GitMergeState.CONFLICTS, result.state)
        assertEquals("file.txt", result.conflicts.single().path)
    }
}
