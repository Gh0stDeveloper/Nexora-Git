package com.nexora.git.core.repository

import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class RepositoryJsonParser @Inject constructor() {

    fun summaries(body: String?): List<RepositorySummary> {
        val array = JSONArray(body ?: "[]")
        return buildList {
            for (index in 0 until array.length()) {
                add(summary(array.getJSONObject(index)))
            }
        }
    }

    fun details(body: String?): RepositoryDetails =
        details(JSONObject(requireNotNull(body) {
            "GitHub repository response body is empty"
        }))

    fun details(root: JSONObject): RepositoryDetails =
        RepositoryDetails(
            summary = summary(root),
            homepage = root.nullableString("homepage"),
            subscribers = root.optLong("subscribers_count"),
            hasIssues = root.optBoolean("has_issues", true),
            hasWiki = root.optBoolean("has_wiki", true),
            hasProjects = root.optBoolean("has_projects", true),
            hasPages = root.optBoolean("has_pages", false),
            deleteBranchOnMerge =
                root.optBoolean("delete_branch_on_merge", false),
            allowMergeCommit =
                root.optBoolean("allow_merge_commit", true),
            allowSquashMerge =
                root.optBoolean("allow_squash_merge", true),
            allowRebaseMerge =
                root.optBoolean("allow_rebase_merge", true),
            offlineSnapshot = false,
        )

    fun summary(root: JSONObject): RepositorySummary {
        val owner = root.optJSONObject("owner")
        val permissions = root.optJSONObject("permissions")

        return RepositorySummary(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            name = root.getString("name"),
            fullName = root.getString("full_name"),
            ownerLogin = owner?.optString("login").orEmpty(),
            ownerAvatarUrl = owner?.nullableString("avatar_url"),
            description = root.nullableString("description"),
            privateRepository = root.optBoolean("private"),
            fork = root.optBoolean("fork"),
            archived = root.optBoolean("archived"),
            visibility = root.optString("visibility", "public"),
            language = root.nullableString("language"),
            defaultBranch = root.optString("default_branch", "main"),
            cloneUrl = root.optString("clone_url"),
            htmlUrl = root.optString("html_url"),
            stars = root.optLong("stargazers_count"),
            forks = root.optLong("forks_count"),
            openIssues = root.optLong("open_issues_count"),
            sizeKb = root.optLong("size"),
            updatedAt = root.nullableString("updated_at"),
            pushedAt = root.nullableString("pushed_at"),
            permissions = RepositoryPermissions(
                admin = permissions?.optBoolean("admin") == true,
                maintain = permissions?.optBoolean("maintain") == true,
                push = permissions?.optBoolean("push") == true,
                triage = permissions?.optBoolean("triage") == true,
                pull = permissions?.optBoolean("pull", true) != false,
            ),
        )
    }

    private fun JSONObject.nullableString(
        name: String,
    ): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name)
            .takeIf { it.isNotBlank() && it != "null" }
    }
}
