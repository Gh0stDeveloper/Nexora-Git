package com.nexora.git.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitIgnoreMatcherTest {

    private val matcher = GitIgnoreMatcher()

    @Test
    fun ignoresCommonPatternsAndAllowsNegation() {
        val rules = matcher.parse(
            """
            build/
            *.log
            .env
            !keep.log
            """.trimIndent(),
        )

        assertTrue(
            matcher.isIgnored(
                "build/output.apk",
                isDirectory = false,
                rules = rules,
            ),
        )
        assertTrue(
            matcher.isIgnored(
                "logs/app.log",
                isDirectory = false,
                rules = rules,
            ),
        )
        assertTrue(
            matcher.isIgnored(
                ".env",
                isDirectory = false,
                rules = rules,
            ),
        )
        assertFalse(
            matcher.isIgnored(
                "keep.log",
                isDirectory = false,
                rules = rules,
            ),
        )
    }

    @Test
    fun nestedRulesOnlyApplyBelowTheirBaseDirectory() {
        val rules = matcher.parse(
            content = "*.tmp",
            basePath = "generated",
        )

        assertTrue(
            matcher.isIgnored(
                "generated/cache.tmp",
                isDirectory = false,
                rules = rules,
            ),
        )
        assertFalse(
            matcher.isIgnored(
                "src/cache.tmp",
                isDirectory = false,
                rules = rules,
            ),
        )
    }

    @Test
    fun anchoredRuleDoesNotMatchNestedSibling() {
        val rules = matcher.parse("/secrets.json")

        assertTrue(
            matcher.isIgnored(
                "secrets.json",
                isDirectory = false,
                rules = rules,
            ),
        )
        assertFalse(
            matcher.isIgnored(
                "config/secrets.json",
                isDirectory = false,
                rules = rules,
            ),
        )
    }
}
