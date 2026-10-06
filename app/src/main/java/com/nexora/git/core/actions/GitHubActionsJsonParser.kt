package com.nexora.git.core.actions

import org.json.JSONArray
import org.json.JSONObject

class GitHubActionsJsonParser {

    fun workflows(body: String?): List<GitHubWorkflow> {
        if (body.isNullOrBlank()) return emptyList()
        val root = JSONObject(body)
        return mapArray(root.optJSONArray("workflows")) { item ->
            GitHubWorkflow(
                id = item.getLong("id"),
                nodeId = item.optString("node_id"),
                name = item.getString("name"),
                path = item.optString("path"),
                state = item.optString("state"),
                createdAt = item.optNullableString("created_at"),
                updatedAt = item.optNullableString("updated_at"),
                htmlUrl = item.optNullableString("html_url"),
                badgeUrl = item.optNullableString("badge_url"),
            )
        }
    }

    fun runs(body: String?): List<GitHubWorkflowRun> {
        if (body.isNullOrBlank()) return emptyList()
        val root = JSONObject(body)
        return mapArray(
            root.optJSONArray("workflow_runs"),
            ::run,
        )
    }

    fun run(body: String?): GitHubWorkflowRun =
        run(JSONObject(requireNotNull(body)))

    fun jobs(body: String?): List<GitHubActionsJob> {
        if (body.isNullOrBlank()) return emptyList()
        val root = JSONObject(body)
        return mapArray(root.optJSONArray("jobs")) { item ->
            GitHubActionsJob(
                id = item.getLong("id"),
                runId = item.optLong("run_id"),
                runAttempt = item.optLong("run_attempt", 1L),
                nodeId = item.optString("node_id"),
                name = item.getString("name"),
                status = item.optString("status"),
                conclusion =
                    item.optNullableString("conclusion"),
                startedAt =
                    item.optNullableString("started_at"),
                completedAt =
                    item.optNullableString("completed_at"),
                htmlUrl = item.optNullableString("html_url"),
                runnerName =
                    item.optNullableString("runner_name"),
                runnerGroupName =
                    item.optNullableString("runner_group_name"),
                labels = item.optJSONArray("labels")
                    .toStringList(),
                steps = mapArray(
                    item.optJSONArray("steps"),
                ) { step ->
                    GitHubActionsStep(
                        name = step.getString("name"),
                        status =
                            step.optString("status"),
                        conclusion =
                            step.optNullableString(
                                "conclusion",
                            ),
                        number =
                            step.optInt("number"),
                        startedAt =
                            step.optNullableString(
                                "started_at",
                            ),
                        completedAt =
                            step.optNullableString(
                                "completed_at",
                            ),
                    )
                },
            )
        }
    }

    fun artifacts(body: String?): List<GitHubActionsArtifact> {
        if (body.isNullOrBlank()) return emptyList()
        val root = JSONObject(body)
        return mapArray(
            root.optJSONArray("artifacts"),
        ) { item ->
            GitHubActionsArtifact(
                id = item.getLong("id"),
                nodeId = item.optString("node_id"),
                name = item.getString("name"),
                sizeInBytes =
                    item.optLong("size_in_bytes"),
                expired = item.optBoolean("expired"),
                createdAt =
                    item.optNullableString("created_at"),
                expiresAt =
                    item.optNullableString("expires_at"),
                updatedAt =
                    item.optNullableString("updated_at"),
                workflowRunId =
                    item.optJSONObject("workflow_run")
                        ?.optLong("id")
                        ?.takeIf { it > 0L },
            )
        }
    }

    private fun run(
        item: JSONObject,
    ): GitHubWorkflowRun =
        GitHubWorkflowRun(
            id = item.getLong("id"),
            nodeId = item.optString("node_id"),
            name = item.optNullableString("name"),
            displayTitle =
                item.optNullableString("display_title"),
            event = item.optString("event"),
            status = item.optNullableString("status"),
            conclusion =
                item.optNullableString("conclusion"),
            workflowId = item.optLong("workflow_id"),
            runNumber = item.optLong("run_number"),
            runAttempt = item.optLong("run_attempt", 1L),
            headBranch =
                item.optNullableString("head_branch"),
            headSha = item.optString("head_sha"),
            htmlUrl = item.optNullableString("html_url"),
            createdAt = item.optNullableString("created_at"),
            updatedAt = item.optNullableString("updated_at"),
            runStartedAt =
                item.optNullableString("run_started_at"),
            actorLogin = item.optJSONObject("actor")
                ?.optNullableString("login"),
        )

    private fun <T> mapArray(
        array: JSONArray?,
        mapper: (JSONObject) -> T,
    ): List<T> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(mapper(array.getJSONObject(index)))
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index)
                    .takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }

    private fun JSONObject.optNullableString(
        key: String,
    ): String? =
        if (isNull(key)) null
        else optString(key).takeIf(String::isNotBlank)
}
