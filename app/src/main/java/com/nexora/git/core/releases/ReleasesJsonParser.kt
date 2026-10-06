package com.nexora.git.core.releases

import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class ReleasesJsonParser @Inject constructor() {

    fun tags(body: String?): List<GitTag> =
        parseArray(body) { item ->
            val commit = item.optJSONObject("commit")
            GitTag(
                name = item.getString("name"),
                commitSha = commit?.optString("sha").orEmpty(),
                commitUrl =
                    commit?.optNullableString("url"),
                zipballUrl =
                    item.optNullableString("zipball_url"),
                tarballUrl =
                    item.optNullableString("tarball_url"),
                nodeId = item.optNullableString("node_id"),
            )
        }

    fun tagFromRef(
        body: String?,
        tagName: String,
    ): GitTag {
        val root = JSONObject(requireNotNull(body))
        val objectValue =
            root.optJSONObject("object")
        return GitTag(
            name = tagName,
            commitSha =
                objectValue?.optString("sha").orEmpty(),
            commitUrl =
                objectValue?.optNullableString("url"),
            zipballUrl = null,
            tarballUrl = null,
            nodeId =
                root.optNullableString("node_id"),
        )
    }

    fun releases(body: String?): List<GitHubRelease> =
        parseArray(body, ::release)

    fun release(body: String?): GitHubRelease =
        release(JSONObject(requireNotNull(body)))

    fun asset(body: String?): ReleaseAsset =
        asset(JSONObject(requireNotNull(body)))

    private fun release(
        root: JSONObject,
    ): GitHubRelease =
        GitHubRelease(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            tagName = root.getString("tag_name"),
            targetCommitish =
                root.optString("target_commitish"),
            name = root.optNullableString("name"),
            body = root.optNullableString("body"),
            draft = root.optBoolean("draft"),
            prerelease =
                root.optBoolean("prerelease"),
            immutable =
                root.optBoolean("immutable"),
            createdAt =
                root.optNullableString("created_at"),
            publishedAt =
                root.optNullableString("published_at"),
            htmlUrl =
                root.optNullableString("html_url"),
            uploadUrl =
                root.optNullableString("upload_url"),
            authorLogin =
                root.optJSONObject("author")
                    ?.optNullableString("login"),
            assets = buildList {
                val array =
                    root.optJSONArray("assets")
                        ?: JSONArray()
                for (index in 0 until array.length()) {
                    add(asset(array.getJSONObject(index)))
                }
            },
        )

    private fun asset(
        root: JSONObject,
    ): ReleaseAsset =
        ReleaseAsset(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            name = root.getString("name"),
            label = root.optNullableString("label"),
            state = root.optString("state"),
            contentType =
                root.optNullableString("content_type"),
            sizeInBytes = root.optLong("size"),
            downloadCount =
                root.optLong("download_count"),
            digest = root.optNullableString("digest"),
            createdAt =
                root.optNullableString("created_at"),
            updatedAt =
                root.optNullableString("updated_at"),
            browserDownloadUrl =
                root.optNullableString(
                    "browser_download_url",
                ),
            uploaderLogin =
                root.optJSONObject("uploader")
                    ?.optNullableString("login"),
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
