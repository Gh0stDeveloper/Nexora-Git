package com.nexora.git.core.platform

import com.nexora.git.core.common.AppError
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubGraphQlErrorClassifierTest {

    private val classifier = GitHubGraphQlErrorClassifier()

    @Test
    fun forbiddenBecomesPermissionError() {
        val result = classifier.classify(
            listOf(
                GitHubGraphQlError(
                    message = "Resource not accessible",
                    type = "FORBIDDEN",
                    path = listOf("repository"),
                ),
            ),
        )

        assertTrue(result is AppError.PermissionDenied)
    }

    @Test
    fun notFoundBecomesNotFoundError() {
        val result = classifier.classify(
            listOf(
                GitHubGraphQlError(
                    message = "Could not resolve",
                    type = "NOT_FOUND",
                    path = emptyList(),
                ),
            ),
        )

        assertTrue(result is AppError.NotFound)
    }
}
