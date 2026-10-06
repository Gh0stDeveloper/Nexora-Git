package com.nexora.git.feature.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchInputParserTest {

    @Test
    fun parsesKeyValueLinesAndPreservesEqualsInValue() {
        val result = parseDispatchInputs(
            """
            environment=production
            command=echo=a=b
            dry_run=false
            """.trimIndent(),
        )

        assertTrue(
            result is DispatchInputParseResult.Success,
        )
        val values =
            (result as DispatchInputParseResult.Success).inputs
        assertEquals("production", values["environment"])
        assertEquals("echo=a=b", values["command"])
        assertEquals("false", values["dry_run"])
    }

    @Test
    fun rejectsDuplicateInputNames() {
        val result = parseDispatchInputs(
            """
            environment=staging
            environment=production
            """.trimIndent(),
        )

        assertTrue(
            result is DispatchInputParseResult.Failure,
        )
        assertEquals(
            2,
            (result as DispatchInputParseResult.Failure).line,
        )
    }

    @Test
    fun rejectsMissingSeparator() {
        val result = parseDispatchInputs("environment")

        assertTrue(
            result is DispatchInputParseResult.Failure,
        )
    }
}
