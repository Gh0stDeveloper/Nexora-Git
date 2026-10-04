package com.nexora.git.core.platform

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubGraphQlPageInfoParser @Inject constructor() {

    fun parse(json: JSONObject?): GitHubGraphQlPageInfo? {
        if (json == null) return null

        return GitHubGraphQlPageInfo(
            hasNextPage = json.optBoolean("hasNextPage", false),
            hasPreviousPage = json.optBoolean("hasPreviousPage", false),
            startCursor = json.optString("startCursor")
                .takeIf { it.isNotBlank() && it != "null" },
            endCursor = json.optString("endCursor")
                .takeIf { it.isNotBlank() && it != "null" },
        )
    }
}
