package com.nexora.git.core.git

import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitJsonParser @Inject constructor() {

    fun repository(json: String): GitRepository {
        val root = JSONObject(json)
        return GitRepository(
            path = root.getString("path"),
            bare = root.getBoolean("bare"),
            head = root.optString("head"),
        )
    }

    fun status(json: String): GitStatus {
        val root = JSONObject(json)
        return GitStatus(
            branch = root.optString("branch"),
            entries = statusEntries(root.getJSONArray("entries")),
            conflicted = root.optBoolean("conflicted"),
        )
    }

    fun commit(json: String): GitCommit {
        val root = JSONObject(json)
        return GitCommit(
            oid = root.getString("oid"),
            message = root.getString("message"),
        )
    }

    fun branches(json: String): List<GitBranch> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitBranch(
                        name = item.getString("name"),
                        remote = item.getBoolean("remote"),
                        head = item.getBoolean("head"),
                        upstream = item.optString("upstream"),
                    ),
                )
            }
        }
    }

    fun diff(json: String): GitDiff {
        val root = JSONObject(json)
        return GitDiff(
            patch = root.optString("patch"),
            filesChanged = root.optLong("filesChanged"),
            insertions = root.optLong("insertions"),
            deletions = root.optLong("deletions"),
        )
    }

    fun mergeResult(json: String): GitMergeResult {
        val root = JSONObject(json)
        val state = when (root.getString("state")) {
            "up_to_date" -> GitMergeState.UP_TO_DATE
            "fast_forward" -> GitMergeState.FAST_FORWARD
            "merged" -> GitMergeState.MERGED
            "conflicts" -> GitMergeState.CONFLICTS
            else -> error("Unknown native merge state")
        }

        return GitMergeResult(
            state = state,
            commitOid = root.optString("commitOid"),
            conflicts = conflicts(root.getJSONArray("conflicts")),
        )
    }

    fun pushResult(json: String): GitPushResult {
        val root = JSONObject(json)
        return GitPushResult(
            remote = root.getString("remote"),
            refspec = root.getString("refspec"),
        )
    }

    fun conflicts(json: String): List<GitConflict> =
        conflicts(JSONArray(json))

    private fun statusEntries(
        array: JSONArray,
    ): List<GitStatusEntry> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                GitStatusEntry(
                    path = item.getString("path"),
                    flags = item.getLong("flags"),
                    staged = item.getBoolean("staged"),
                    workingTree = item.getBoolean("workingTree"),
                    untracked = item.getBoolean("untracked"),
                    conflicted = item.getBoolean("conflicted"),
                ),
            )
        }
    }

    private fun conflicts(
        array: JSONArray,
    ): List<GitConflict> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                GitConflict(
                    path = item.getString("path"),
                    ancestor = item.optString("ancestor"),
                    ours = item.optString("ours"),
                    theirs = item.optString("theirs"),
                ),
            )
        }
    }
}
