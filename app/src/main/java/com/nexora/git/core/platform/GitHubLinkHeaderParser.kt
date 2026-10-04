package com.nexora.git.core.platform

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubLinkHeaderParser @Inject constructor() {

    fun parse(header: String?): GitHubRestPagination {
        if (header.isNullOrBlank()) {
            return GitHubRestPagination()
        }

        val relations = buildMap {
            header.split(',').forEach { segment ->
                val parts = segment.trim().split(';')
                if (parts.size < 2) return@forEach

                val url = parts[0]
                    .trim()
                    .removePrefix("<")
                    .removeSuffix(">")
                    .takeIf { it.startsWith("https://api.github.com/") }
                    ?: return@forEach

                val relation = parts.drop(1)
                    .firstNotNullOfOrNull { parameter ->
                        val trimmed = parameter.trim()
                        if (trimmed.startsWith("rel=")) {
                            trimmed.removePrefix("rel=")
                                .trim('"')
                        } else {
                            null
                        }
                    }
                    ?: return@forEach

                put(relation, url)
            }
        }

        return GitHubRestPagination(
            nextUrl = relations["next"],
            previousUrl = relations["prev"],
            firstUrl = relations["first"],
            lastUrl = relations["last"],
        )
    }
}
