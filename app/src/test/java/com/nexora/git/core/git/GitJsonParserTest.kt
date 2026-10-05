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
    fun parsesRemotesDivergenceAndLeasePush() {
        val remotes = parser.remotes(
            """
            [
              {
                "name":"origin",
                "url":"https://github.com/example/repo.git"
              }
            ]
            """.trimIndent(),
        )

        assertEquals("origin", remotes.single().name)

        val divergence = parser.divergence(
            """
            {
              "localRef":"main",
              "upstreamRef":"origin/main",
              "localOid":"aaaaaaaa",
              "upstreamOid":"bbbbbbbb",
              "ahead":2,
              "behind":1
            }
            """.trimIndent(),
        )

        assertEquals(2L, divergence.ahead)
        assertEquals(1L, divergence.behind)
        assertEquals(
            GitRepositoryOperationState.REBASE,
            parser.repositoryState("rebase"),
        )

        val push = parser.pushResult(
            """
            {
              "remote":"origin",
              "refspec":"+refs/heads/main:refs/heads/main",
              "forceWithLease":true
            }
            """.trimIndent(),
        )

        assertTrue(push.forceWithLease)
    }

    @Test
    fun parsesRebasedMergeResult() {
        val result = parser.mergeResult(
            """
            {
              "state":"rebased",
              "commitOid":"abcdef012345",
              "conflicts":[]
            }
            """.trimIndent(),
        )

        assertEquals(GitMergeState.REBASED, result.state)
        assertEquals("abcdef012345", result.commitOid)
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
