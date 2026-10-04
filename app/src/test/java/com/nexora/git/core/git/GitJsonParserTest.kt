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
