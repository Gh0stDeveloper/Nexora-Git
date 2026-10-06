package com.nexora.git.core.advanced

import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class AdvancedGitHubJsonParser @Inject constructor() {

    fun discussionHub(dataJson: String?): AdvancedDiscussionHub {
        val repository = data(dataJson).getJSONObject("repository")
        val categories = repository
            .getJSONObject("discussionCategories")
            .optJSONArray("nodes")
            .objects()
            .map { category ->
                AdvancedDiscussionCategory(
                    id = category.getString("id"),
                    name = category.getString("name"),
                    description = category.optNullableString("description"),
                    answerable = category.optBoolean("isAnswerable"),
                )
            }

        val discussions = repository
            .getJSONObject("discussions")
            .optJSONArray("nodes")
            .objects()
            .map(::discussion)

        return AdvancedDiscussionHub(
            repositoryNodeId = repository.getString("id"),
            categories = categories,
            discussions = discussions,
        )
    }

    fun discussionMutation(dataJson: String?): AdvancedDiscussionSummary {
        val created = data(dataJson)
            .getJSONObject("createDiscussion")
            .getJSONObject("discussion")
        return discussion(created)
    }

    fun projectHub(dataJson: String?): AdvancedProjectHub {
        val repository = data(dataJson).getJSONObject("repository")
        val projects = repository
            .getJSONObject("projectsV2")
            .optJSONArray("nodes")
            .objects()
            .map(::project)

        return AdvancedProjectHub(
            ownerNodeId = repository
                .getJSONObject("owner")
                .getString("id"),
            repositoryNodeId = repository.getString("id"),
            projects = projects,
        )
    }

    fun projectMutation(dataJson: String?): AdvancedProjectSummary =
        project(
            data(dataJson)
                .getJSONObject("createProjectV2")
                .getJSONObject("projectV2"),
        )

    fun pages(body: String?): GitHubPagesSite {
        val root = JSONObject(body ?: "{}")
        val source = root.optJSONObject("source")
        return GitHubPagesSite(
            enabled = true,
            htmlUrl = root.optNullableString("html_url"),
            status = root.optNullableString("status"),
            cname = root.optNullableString("cname"),
            httpsEnforced = root.optBoolean("https_enforced"),
            protectedDomainState =
                root.optNullableString("protected_domain_state"),
            buildType = root.optNullableString("build_type"),
            sourceBranch = source?.optNullableString("branch"),
            sourcePath = source?.optNullableString("path"),
        )
    }

    fun dependabot(body: String?): List<SecurityAlertSummary> =
        JSONArray(body ?: "[]").objects().map { alert ->
            val advisory = alert.optJSONObject("security_advisory")
            val vulnerability =
                alert.optJSONObject("security_vulnerability")
            SecurityAlertSummary(
                id = alert.optLong("number").toString(),
                kind = SecurityAlertKind.DEPENDABOT,
                title = advisory
                    ?.optNullableString("summary")
                    ?: "Dependabot alert",
                severity = vulnerability
                    ?.optNullableString("severity"),
                state = alert.optString("state", "unknown"),
                htmlUrl = alert.optNullableString("html_url"),
                createdAt = alert.optNullableString("created_at"),
            )
        }

    fun codeScanning(body: String?): List<SecurityAlertSummary> =
        JSONArray(body ?: "[]").objects().map { alert ->
            val rule = alert.optJSONObject("rule")
            SecurityAlertSummary(
                id = alert.optLong("number").toString(),
                kind = SecurityAlertKind.CODE_SCANNING,
                title = rule?.optNullableString("description")
                    ?: rule?.optNullableString("name")
                    ?: "Code scanning alert",
                severity =
                    rule?.optNullableString("security_severity_level")
                        ?: rule?.optNullableString("severity"),
                state = alert.optString("state", "unknown"),
                htmlUrl = alert.optNullableString("html_url"),
                createdAt = alert.optNullableString("created_at"),
            )
        }

    fun secretScanning(body: String?): List<SecurityAlertSummary> =
        JSONArray(body ?: "[]").objects().map { alert ->
            SecurityAlertSummary(
                id = alert.optLong("number").toString(),
                kind = SecurityAlertKind.SECRET_SCANNING,
                title =
                    alert.optNullableString("secret_type_display_name")
                        ?: alert.optString(
                            "secret_type",
                            "Secret scanning alert",
                        ),
                severity = null,
                state = alert.optString("state", "unknown"),
                htmlUrl = alert.optNullableString("html_url"),
                createdAt = alert.optNullableString("created_at"),
            )
        }

    fun gists(body: String?): List<GitHubGistSummary> =
        JSONArray(body ?: "[]").objects().map(::gist)

    fun gist(body: String?): GitHubGistSummary =
        gist(JSONObject(body ?: "{}"))

    fun codespaces(body: String?): List<GitHubCodespaceSummary> {
        val root = JSONObject(body ?: "{}")
        return root.optJSONArray("codespaces")
            .objects()
            .map(::codespace)
    }

    fun codespace(body: String?): GitHubCodespaceSummary =
        codespace(JSONObject(body ?: "{}"))

    private fun discussion(
        value: JSONObject,
    ): AdvancedDiscussionSummary =
        AdvancedDiscussionSummary(
            id = value.getString("id"),
            number = value.getInt("number"),
            title = value.getString("title"),
            url = value.optNullableString("url"),
            categoryName = value
                .getJSONObject("category")
                .getString("name"),
            authorLogin = value.optJSONObject("author")
                ?.optNullableString("login"),
            comments = value
                .getJSONObject("comments")
                .optInt("totalCount"),
            upvotes = value.optInt("upvoteCount"),
            answered = value.optBoolean("isAnswered"),
            updatedAt = value.optNullableString("updatedAt"),
        )

    private fun project(
        value: JSONObject,
    ): AdvancedProjectSummary =
        AdvancedProjectSummary(
            id = value.getString("id"),
            number = value.getInt("number"),
            title = value.getString("title"),
            shortDescription =
                value.optNullableString("shortDescription"),
            url = value.optNullableString("url"),
            closed = value.optBoolean("closed"),
            publicProject = value.optBoolean("public"),
            itemCount = value
                .getJSONObject("items")
                .optInt("totalCount"),
            updatedAt = value.optNullableString("updatedAt"),
        )

    private fun gist(value: JSONObject): GitHubGistSummary {
        val files = value.optJSONObject("files")
        val names = mutableListOf<String>()
        if (files != null) {
            val iterator = files.keys()
            while (iterator.hasNext()) {
                names += iterator.next()
            }
        }
        return GitHubGistSummary(
            id = value.getString("id"),
            description = value.optNullableString("description"),
            htmlUrl = value.optNullableString("html_url"),
            publicGist = value.optBoolean("public"),
            fileNames = names.sorted(),
            comments = value.optInt("comments"),
            createdAt = value.optNullableString("created_at"),
            updatedAt = value.optNullableString("updated_at"),
        )
    }

    private fun codespace(value: JSONObject): GitHubCodespaceSummary {
        val repository = value.optJSONObject("repository")
        val machine = value.optJSONObject("machine")
        val name = value.getString("name")
        return GitHubCodespaceSummary(
            name = name,
            displayName = value.optNullableString("display_name")
                ?: name,
            state = value.optString("state", "Unknown"),
            repositoryFullName =
                repository?.optNullableString("full_name"),
            machineName =
                machine?.optNullableString("display_name"),
            webUrl = value.optNullableString("web_url"),
            createdAt = value.optNullableString("created_at"),
            updatedAt = value.optNullableString("updated_at"),
        )
    }

    private fun data(dataJson: String?): JSONObject =
        JSONObject(dataJson ?: "{}")

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun JSONObject.optNullableString(
        name: String,
    ): String? =
        opt(name)
            ?.takeUnless { it == JSONObject.NULL }
            ?.toString()
            ?.takeIf { it.isNotBlank() }
}
