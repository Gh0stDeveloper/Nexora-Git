package com.nexora.git.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun success_preservesValue() {
        val result: AppResult<String> = AppResult.Success("nexora")

        assertTrue(result is AppResult.Success)
        assertEquals("nexora", (result as AppResult.Success).value)
    }

    @Test
    fun failure_preservesTypedError() {
        val expected = AppError.RateLimited(
            retryAfterSeconds = 60L,
            secondary = true,
        )
        val result: AppResult<Nothing> = AppResult.Failure(expected)

        assertTrue(result is AppResult.Failure)
        assertEquals(expected, (result as AppResult.Failure).error)
    }
}
