package com.nexora.git.core.releases

data class GitTag(
    val name: String,
    val commitSha: String,
    val commitUrl: String?,
    val zipballUrl: String?,
    val tarballUrl: String?,
    val nodeId: String?,
)

data class ReleaseAsset(
    val id: Long,
    val nodeId: String,
    val name: String,
    val label: String?,
    val state: String,
    val contentType: String?,
    val sizeInBytes: Long,
    val downloadCount: Long,
    val digest: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val browserDownloadUrl: String?,
    val uploaderLogin: String?,
)

data class GitHubRelease(
    val id: Long,
    val nodeId: String,
    val tagName: String,
    val targetCommitish: String,
    val name: String?,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val immutable: Boolean,
    val createdAt: String?,
    val publishedAt: String?,
    val htmlUrl: String?,
    val uploadUrl: String?,
    val authorLogin: String?,
    val assets: List<ReleaseAsset>,
)

data class CreateReleaseRequest(
    val tagName: String,
    val targetCommitish: String,
    val name: String,
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val generateReleaseNotes: Boolean = false,
    val makeLatest: ReleaseLatestPolicy =
        ReleaseLatestPolicy.AUTO,
)

data class UpdateReleaseRequest(
    val tagName: String? = null,
    val targetCommitish: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    val makeLatest: ReleaseLatestPolicy? = null,
)

enum class ReleaseLatestPolicy(
    val wireValue: String,
) {
    AUTO("true"),
    NOT_LATEST("false"),
    LEGACY("legacy"),
}

data class ReleaseAssetDownload(
    val filePath: String,
    val fileName: String,
    val sizeInBytes: Long,
)
