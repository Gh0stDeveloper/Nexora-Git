package com.nexora.git.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGitJsonParserTest {
    private val parser = GitJsonParser()

    @Test
    fun parsesApplyAndOperationStates() {
        val result = parser.applyResult(
            """
            {
              "state":"conflicts",
              "commitOid":"",
              "conflicts":[
                {
                  "path":"src/main.kt",
                  "ancestor":"aaa",
                  "ours":"bbb",
                  "theirs":"ccc"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(GitApplyState.CONFLICTS, result.state)
        assertEquals("src/main.kt", result.conflicts.single().path)
        assertEquals(
            GitRepositoryOperationState.CHERRY_PICK,
            parser.repositoryState("cherry_pick"),
        )
        assertEquals(
            GitRepositoryOperationState.REVERT,
            parser.repositoryState("revert"),
        )
    }

    @Test
    fun parsesStashesTagsAndSubmodules() {
        val stashes = parser.stashes(
            """[{"index":0,"oid":"abc","message":"WIP"}]""",
        )
        assertEquals(0, stashes.single().index)
        assertEquals("WIP", stashes.single().message)

        val tags = parser.tags(
            """
            [{
              "name":"v1.0.0",
              "targetOid":"def",
              "annotated":true,
              "message":"Release",
              "taggerName":"Nexora",
              "taggerEmail":"dev@example.com"
            }]
            """.trimIndent(),
        )
        assertTrue(tags.single().annotated)
        assertEquals("def", tags.single().targetOid)

        val submodules = parser.submodules(
            """
            [{
              "name":"engine",
              "path":"vendor/engine",
              "url":"https://github.com/example/engine.git",
              "headOid":"123",
              "workdirOid":"",
              "status":4,
              "initialized":false
            }]
            """.trimIndent(),
        )
        assertFalse(submodules.single().initialized)
        assertEquals("vendor/engine", submodules.single().path)
    }
}
