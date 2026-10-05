package com.nexora.git.core.issues

import org.json.JSONArray
import org.json.JSONObject

class IssueJsonParser {

    fun summaries(body: String?): List<IssueSummary> {
        if (body.isNullOrBlank()) return emptyList()
        val root = body.trim()
        val array = if (root.startsWith("{")) {
            JSONObject(root).optJSONArray("items") ?: JSONArray()
        } else {
            JSONArray(root)
        }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                if (item.has("pull_request")) continue
                add(summary(item))
            }
        }
    }

    fun details(body: String?): IssueDetails {
        val root = JSONObject(requireNotNull(body))
        return IssueDetails(
            summary = summary(root),
            body = root.optNullableString("body"),
            reactions = reactions(root.optJSONObject("reactions")),
        )
    }

    fun labels(body: String?): List<IssueLabel> =
        parseArray(body) { label(it) }

    fun users(body: String?): List<IssueUser> =
        parseArray(body) { user(it) }

    fun milestones(body: String?): List<IssueMilestone> =
        parseArray(body) { milestone(it) }

    fun comments(body: String?): List<IssueComment> =
        parseArray(body) { comment(it) }

    fun comment(body: String?): IssueComment =
        comment(JSONObject(requireNotNull(body)))

    fun reaction(body: String?): IssueReaction {
        val root = JSONObject(requireNotNull(body))
        return IssueReaction(
            id = root.getLong("id"),
            content = root.getString("content"),
            user = user(root.getJSONObject("user")),
        )
    }

    private fun summary(root: JSONObject): IssueSummary =
        IssueSummary(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            number = root.getInt("number"),
            title = root.getString("title"),
            state = root.getString("state"),
            locked = root.optBoolean("locked"),
            author = user(root.getJSONObject("user")),
            labels = jsonArray(root, "labels") { label(it) },
            assignees = jsonArray(root, "assignees") { user(it) },
            milestone = root.optJSONObject("milestone")?.let {
                milestone(it)
            },
            comments = root.optInt("comments"),
            createdAt = root.optString("created_at"),
            updatedAt = root.optString("updated_at"),
            closedAt = root.optNullableString("closed_at"),
            htmlUrl = root.optString("html_url"),
        )

    private fun label(root: JSONObject): IssueLabel =
        IssueLabel(
            id = root.getLong("id"),
            name = root.getString("name"),
            color = root.optString("color"),
            description = root.optNullableString("description"),
        )

    private fun milestone(root: JSONObject): IssueMilestone =
        IssueMilestone(
            number = root.getInt("number"),
            title = root.getString("title"),
            state = root.optString("state"),
            openIssues = root.optInt("open_issues"),
            closedIssues = root.optInt("closed_issues"),
            dueAt = root.optNullableString("due_on"),
        )

    private fun user(root: JSONObject): IssueUser =
        IssueUser(
            login = root.getString("login"),
            avatarUrl = root.optNullableString("avatar_url"),
        )

    private fun comment(root: JSONObject): IssueComment =
        IssueComment(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            body = root.optString("body"),
            author = user(root.getJSONObject("user")),
            createdAt = root.optString("created_at"),
            updatedAt = root.optString("updated_at"),
            htmlUrl = root.optString("html_url"),
            reactions = reactions(root.optJSONObject("reactions")),
        )

    private fun reactions(root: JSONObject?): IssueReactionSummary =
        IssueReactionSummary(
            totalCount = root?.optInt("total_count") ?: 0,
            plusOne = root?.optInt("+1") ?: 0,
            minusOne = root?.optInt("-1") ?: 0,
            laugh = root?.optInt("laugh") ?: 0,
            hooray = root?.optInt("hooray") ?: 0,
            confused = root?.optInt("confused") ?: 0,
            heart = root?.optInt("heart") ?: 0,
            rocket = root?.optInt("rocket") ?: 0,
            eyes = root?.optInt("eyes") ?: 0,
        )

    private fun <T> parseArray(
        body: String?,
        mapper: (JSONObject) -> T,
    ): List<T> {
        if (body.isNullOrBlank()) return emptyList()
        val array = JSONArray(body)
        return buildList {
            for (index in 0 until array.length()) {
                add(mapper(array.getJSONObject(index)))
            }
        }
    }

    private fun <T> jsonArray(
        root: JSONObject,
        key: String,
        mapper: (JSONObject) -> T,
    ): List<T> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(mapper(array.getJSONObject(index)))
            }
        }
    }

    private fun JSONObject.optNullableString(
        key: String,
    ): String? =
        if (isNull(key)) null
        else optString(key).takeIf(String::isNotBlank)
}
