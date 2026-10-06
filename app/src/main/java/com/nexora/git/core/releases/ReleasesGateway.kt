package com.nexora.git.core.releases

import com.nexora.git.core.common.AppResult

interface ReleasesGateway {

    suspend fun listTags(
        owner: String,
        repository: String,
    ): AppResult<List<GitTag>>

    suspend fun createLightweightTag(
        owner: String,
        repository: String,
        tagName: String,
        targetSha: String,
    ): AppResult<GitTag>

    suspend fun deleteTag(
        owner: String,
        repository: String,
        tagName: String,
    ): AppResult<Unit>

    suspend fun listReleases(
        owner: String,
        repository: String,
    ): AppResult<List<GitHubRelease>>

    suspend fun getRelease(
        owner: String,
        repository: String,
        releaseId: Long,
    ): AppResult<GitHubRelease>

    suspend fun createRelease(
        owner: String,
        repository: String,
        request: CreateReleaseRequest,
    ): AppResult<GitHubRelease>

    suspend fun updateRelease(
        owner: String,
        repository: String,
        releaseId: Long,
        request: UpdateReleaseRequest,
    ): AppResult<GitHubRelease>

    suspend fun deleteRelease(
        owner: String,
        repository: String,
        releaseId: Long,
    ): AppResult<Unit>

    suspend fun updateAsset(
        owner: String,
        repository: String,
        assetId: Long,
        name: String,
        label: String?,
    ): AppResult<ReleaseAsset>

    suspend fun deleteAsset(
        owner: String,
        repository: String,
        assetId: Long,
    ): AppResult<Unit>

    suspend fun uploadAsset(
        owner: String,
        repository: String,
        releaseId: Long,
        contentUri: String,
    ): AppResult<ReleaseAsset>

    suspend fun downloadAsset(
        owner: String,
        repository: String,
        asset: ReleaseAsset,
    ): AppResult<ReleaseAssetDownload>
}
