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

    fun remotes(json: String): List<GitRemote> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitRemote(
                        name = item.getString("name"),
                        url = item.optString("url"),
                    ),
                )
            }
        }
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

    fun divergence(json: String): GitDivergence {
        val root = JSONObject(json)
        return GitDivergence(
            localRef = root.getString("localRef"),
            upstreamRef = root.getString("upstreamRef"),
            localOid = root.getString("localOid"),
            upstreamOid = root.getString("upstreamOid"),
            ahead = root.optLong("ahead"),
            behind = root.optLong("behind"),
        )
    }

    fun repositoryState(value: String):
        GitRepositoryOperationState =
        when (value) {
            "none" -> GitRepositoryOperationState.NONE
            "merge" -> GitRepositoryOperationState.MERGE
            "rebase" -> GitRepositoryOperationState.REBASE
            else -> GitRepositoryOperationState.OTHER
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
            "rebased" -> GitMergeState.REBASED
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
            forceWithLease = root.optBoolean("forceWithLease"),
        )
    }

    fun conflicts(json: String): List<GitConflict> =
        conflicts(JSONArray(json))

    fun history(json: String): List<GitHistoryEntry> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitHistoryEntry(
                        oid = item.getString("oid"),
                        shortOid = item.getString("shortOid"),
                        summary = item.optString("summary"),
                        message = item.optString("message"),
                        authorName = item.optString("authorName"),
                        authorEmail = item.optString("authorEmail"),
                        timestampSeconds =
                            item.optLong("timestampSeconds"),
                        timezoneOffsetMinutes =
                            item.optInt("timezoneOffsetMinutes"),
                        parentCount = item.optInt("parentCount"),
                    ),
                )
            }
        }
    }


    fun applyResult(json: String): GitApplyResult {
        val root = JSONObject(json)
        return GitApplyResult(
            state = when (root.getString("state")) {
                "applied" -> GitApplyState.APPLIED
                "conflicts" -> GitApplyState.CONFLICTS
                else -> error("Unknown native apply state")
            },
            commitOid = root.optString("commitOid"),
            conflicts = conflicts(root.getJSONArray("conflicts")),
        )
    }

    fun stashes(json: String): List<GitStash> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitStash(
                        index = item.getInt("index"),
                        oid = item.getString("oid"),
                        message = item.optString("message"),
                    ),
                )
            }
        }
    }

    fun tags(json: String): List<GitTag> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitTag(
                        name = item.getString("name"),
                        targetOid = item.optString("targetOid"),
                        annotated = item.optBoolean("annotated"),
                        message = item.optString("message"),
                        taggerName = item.optString("taggerName"),
                        taggerEmail = item.optString("taggerEmail"),
                    ),
                )
            }
        }
    }

    fun submodules(json: String): List<GitSubmodule> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitSubmodule(
                        name = item.getString("name"),
                        path = item.optString("path"),
                        url = item.optString("url"),
                        headOid = item.optString("headOid"),
                        workdirOid = item.optString("workdirOid"),
                        status = item.optLong("status"),
                        initialized = item.optBoolean("initialized"),
                    ),
                )
            }
        }
    }

    fun blame(json: String): List<GitBlameHunk> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitBlameHunk(
                        startLine = item.getLong("startLine"),
                        lineCount = item.getLong("lineCount"),
                        finalCommitOid =
                            item.getString("finalCommitOid"),
                        originalCommitOid =
                            item.getString("originalCommitOid"),
                        originalStartLine =
                            item.getLong("originalStartLine"),
                        originalPath =
                            item.optString("originalPath"),
                        authorName =
                            item.optString("authorName"),
                        authorEmail =
                            item.optString("authorEmail"),
                        timestampSeconds =
                            item.optLong("timestampSeconds"),
                        timezoneOffsetMinutes =
                            item.optInt("timezoneOffsetMinutes"),
                        boundary = item.optBoolean("boundary"),
                    ),
                )
            }
        }
    }

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
