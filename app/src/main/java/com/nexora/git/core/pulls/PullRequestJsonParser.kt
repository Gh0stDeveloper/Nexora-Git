package com.nexora.git.core.pulls

import org.json.JSONArray
import org.json.JSONObject

class PullRequestJsonParser {

    fun summaries(body: String?): List<PullRequestSummary> =
        parseArray(body) { summary(it) }

    fun summary(body: String?): PullRequestSummary =
        summary(JSONObject(requireNotNull(body)))

    fun files(body: String?): List<PullRequestFile> =
        parseArray(body) { root ->
            PullRequestFile(
                sha = root.optString("sha"),
                filename = root.getString("filename"),
                status = root.optString("status"),
                additions = root.optInt("additions"),
                deletions = root.optInt("deletions"),
                changes = root.optInt("changes"),
                blobUrl = root.optNullableString("blob_url"),
                rawUrl = root.optNullableString("raw_url"),
                previousFilename =
                    root.optNullableString("previous_filename"),
                patch = root.optNullableString("patch"),
            )
        }

    fun reviews(body: String?): List<PullRequestReview> =
        parseArray(body) { review(it) }

    fun review(body: String?): PullRequestReview =
        review(JSONObject(requireNotNull(body)))

    fun reviewComments(
        body: String?,
    ): List<PullRequestReviewComment> =
        parseArray(body) { reviewComment(it) }

    fun reviewComment(
        body: String?,
    ): PullRequestReviewComment =
        reviewComment(JSONObject(requireNotNull(body)))

    fun checks(body: String?): List<PullRequestCheckRun> {
        if (body.isNullOrBlank()) return emptyList()
        val root = JSONObject(body)
        val array = root.optJSONArray("check_runs") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PullRequestCheckRun(
                        id = item.getLong("id"),
                        name = item.getString("name"),
                        status = item.optString("status"),
                        conclusion =
                            item.optNullableString("conclusion"),
                        detailsUrl =
                            item.optNullableString("details_url"),
                        startedAt =
                            item.optNullableString("started_at"),
                        completedAt =
                            item.optNullableString("completed_at"),
                    ),
                )
            }
        }
    }

    fun mergeResult(body: String?): PullRequestMergeResult {
        val root = JSONObject(body ?: "{}")
        return PullRequestMergeResult(
            sha = root.optNullableString("sha"),
            merged = root.optBoolean("merged"),
            message = root.optNullableString("message"),
        )
    }

    private fun summary(root: JSONObject): PullRequestSummary =
        PullRequestSummary(
            id = root.getLong("id"),
            nodeId = root.getString("node_id"),
            number = root.getInt("number"),
            title = root.getString("title"),
            body = root.optNullableString("body"),
            state = root.getString("state"),
            draft = root.optBoolean("draft"),
            locked = root.optBoolean("locked"),
            merged = root.optBoolean("merged"),
            mergeable =
                if (root.isNull("mergeable")) null
                else root.optBoolean("mergeable"),
            mergeableState =
                root.optNullableString("mergeable_state"),
            author = user(root.getJSONObject("user")),
            head = ref(root.getJSONObject("head")),
            base = ref(root.getJSONObject("base")),
            comments = root.optInt("comments"),
            reviewComments = root.optInt("review_comments"),
            commits = root.optInt("commits"),
            additions = root.optInt("additions"),
            deletions = root.optInt("deletions"),
            changedFiles = root.optInt("changed_files"),
            createdAt = root.optString("created_at"),
            updatedAt = root.optString("updated_at"),
            closedAt = root.optNullableString("closed_at"),
            mergedAt = root.optNullableString("merged_at"),
            htmlUrl = root.optString("html_url"),
        )

    private fun ref(root: JSONObject): PullRequestRef =
        PullRequestRef(
            label = root.optString("label"),
            ref = root.optString("ref"),
            sha = root.optString("sha"),
            repositoryFullName = root.optJSONObject("repo")
                ?.optNullableString("full_name"),
        )

    private fun user(root: JSONObject): PullRequestUser =
        PullRequestUser(
            login = root.getString("login"),
            avatarUrl = root.optNullableString("avatar_url"),
        )

    private fun review(root: JSONObject): PullRequestReview =
        PullRequestReview(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            author = user(root.getJSONObject("user")),
            body = root.optNullableString("body"),
            state = root.optString("state"),
            htmlUrl = root.optNullableString("html_url"),
            submittedAt =
                root.optNullableString("submitted_at"),
            commitId = root.optNullableString("commit_id"),
        )

    private fun reviewComment(
        root: JSONObject,
    ): PullRequestReviewComment =
        PullRequestReviewComment(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            reviewId = root.optLong("pull_request_review_id")
                .takeIf { it > 0L },
            author = user(root.getJSONObject("user")),
            body = root.optString("body"),
            path = root.optString("path"),
            diffHunk = root.optNullableString("diff_hunk"),
            line = root.optInt("line")
                .takeIf { !root.isNull("line") && it > 0 },
            side = root.optNullableString("side"),
            startLine = root.optInt("start_line")
                .takeIf {
                    !root.isNull("start_line") && it > 0
                },
            startSide =
                root.optNullableString("start_side"),
            commitId = root.optNullableString("commit_id"),
            inReplyToId = root.optLong("in_reply_to_id")
                .takeIf {
                    !root.isNull("in_reply_to_id") && it > 0L
                },
            createdAt = root.optString("created_at"),
            updatedAt = root.optString("updated_at"),
            htmlUrl = root.optNullableString("html_url"),
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

    private fun JSONObject.optNullableString(
        key: String,
    ): String? =
        if (isNull(key)) null
        else optString(key).takeIf(String::isNotBlank)
}
