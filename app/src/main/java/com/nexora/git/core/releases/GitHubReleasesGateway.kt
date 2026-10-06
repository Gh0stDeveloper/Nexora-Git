package com.nexora.git.core.releases

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import com.nexora.git.core.platform.GitHubRestResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class GitHubReleasesGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
    private val binary: ReleaseAssetBinaryClient,
    private val parser: ReleasesJsonParser,
) : ReleasesGateway {

    override suspend fun listTags(
        owner: String,
        repository: String,
    ): AppResult<List<GitTag>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/tags",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 30_000L,
            ),
            parse = parser::tags,
            message = "Invalid GitHub tags response.",
        )
    }

    override suspend fun createLightweightTag(
        owner: String,
        repository: String,
        tagName: String,
        targetSha: String,
    ): AppResult<GitTag> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        val tag = tagName.trim()
        val sha = targetSha.trim()

        if (!isSafeTag(tag)) {
            return validation("Tag name is invalid.")
        }
        if (!SHA.matches(sha)) {
            return validation("Target commit SHA is invalid.")
        }

        val payload = JSONObject()
            .put("ref", "refs/tags/" + tag)
            .put("sha", sha)

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/git/refs",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            { body ->
                parser.tagFromRef(body, tag)
            },
            "Invalid GitHub tag creation response.",
        )
    }

    override suspend fun deleteTag(
        owner: String,
        repository: String,
        tagName: String,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        val tag = tagName.trim()

        if (!isSafeTag(tag)) {
            return validation("Tag name is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/git/refs/tags/" +
                            encodePathPart(tag),
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun listReleases(
        owner: String,
        repository: String,
    ): AppResult<List<GitHubRelease>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/releases",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 20_000L,
            ),
            parse = parser::releases,
            message = "Invalid GitHub releases response.",
        )
    }

    override suspend fun getRelease(
        owner: String,
        repository: String,
        releaseId: Long,
    ): AppResult<GitHubRelease> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (releaseId <= 0L) {
            return validation("Release id is invalid.")
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/releases/" +
                            releaseId,
                    cachePolicy =
                        GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 10_000L,
                ),
            ),
            parser::release,
            "Invalid GitHub release response.",
        )
    }

    override suspend fun createRelease(
        owner: String,
        repository: String,
        request: CreateReleaseRequest,
    ): AppResult<GitHubRelease> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        val tag = request.tagName.trim()
        val target = request.targetCommitish.trim()
        val name = request.name.trim()

        if (!isSafeTag(tag)) {
            return validation("Release tag is invalid.")
        }
        if (target.isBlank() || target.length > 255) {
            return validation("Target commitish is invalid.")
        }
        if (name.isBlank()) {
            return validation("Release name is required.")
        }

        val payload = JSONObject()
            .put("tag_name", tag)
            .put("target_commitish", target)
            .put("name", name)
            .put("body", request.body)
            .put("draft", request.draft)
            .put("prerelease", request.prerelease)
            .put(
                "generate_release_notes",
                request.generateReleaseNotes,
            )
            .put(
                "make_latest",
                request.makeLatest.wireValue,
            )

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) + "/releases",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::release,
            "Invalid GitHub create release response.",
        )
    }

    override suspend fun updateRelease(
        owner: String,
        repository: String,
        releaseId: Long,
        request: UpdateReleaseRequest,
    ): AppResult<GitHubRelease> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (releaseId <= 0L) {
            return validation("Release id is invalid.")
        }

        val payload = JSONObject()

        request.tagName?.let {
            val value = it.trim()
            if (!isSafeTag(value)) {
                return validation("Release tag is invalid.")
            }
            payload.put("tag_name", value)
        }
        request.targetCommitish?.let {
            val value = it.trim()
            if (value.isBlank() || value.length > 255) {
                return validation("Target commitish is invalid.")
            }
            payload.put("target_commitish", value)
        }
        request.name?.let {
            val value = it.trim()
            if (value.isBlank()) {
                return validation("Release name cannot be empty.")
            }
            payload.put("name", value)
        }
        request.body?.let {
            payload.put("body", it)
        }
        request.draft?.let {
            payload.put("draft", it)
        }
        request.prerelease?.let {
            payload.put("prerelease", it)
        }
        request.makeLatest?.let {
            payload.put("make_latest", it.wireValue)
        }

        if (payload.length() == 0) {
            return validation("No release changes were provided.")
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/releases/" +
                            releaseId,
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::release,
            "Invalid GitHub update release response.",
        )
    }

    override suspend fun deleteRelease(
        owner: String,
        repository: String,
        releaseId: Long,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (releaseId <= 0L) {
            return validation("Release id is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/releases/" +
                            releaseId,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun updateAsset(
        owner: String,
        repository: String,
        assetId: Long,
        name: String,
        label: String?,
    ): AppResult<ReleaseAsset> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (assetId <= 0L) {
            return validation("Release asset id is invalid.")
        }
        val safeName = name.trim()
        if (safeName.isBlank() || safeName.length > 255) {
            return validation("Release asset name is invalid.")
        }

        val payload = JSONObject()
            .put("name", safeName)
        label?.let {
            payload.put("label", it)
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/releases/assets/" +
                            assetId,
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::asset,
            "Invalid GitHub release asset response.",
        )
    }

    override suspend fun deleteAsset(
        owner: String,
        repository: String,
        assetId: Long,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (assetId <= 0L) {
            return validation("Release asset id is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/releases/assets/" +
                            assetId,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun uploadAsset(
        owner: String,
        repository: String,
        releaseId: Long,
        contentUri: String,
    ): AppResult<ReleaseAsset> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (releaseId <= 0L || contentUri.isBlank()) {
            return validation("Release asset upload is invalid.")
        }

        return binary.uploadAsset(
            owner = ids.first,
            repository = ids.second,
            releaseId = releaseId,
            contentUri = contentUri,
        )
    }

    override suspend fun downloadAsset(
        owner: String,
        repository: String,
        asset: ReleaseAsset,
    ): AppResult<ReleaseAssetDownload> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (asset.id <= 0L) {
            return validation("Release asset id is invalid.")
        }

        return binary.downloadAsset(
            owner = ids.first,
            repository = ids.second,
            asset = asset,
        )
    }

    private suspend fun <T> paged(
        first: GitHubRestRequest,
        parse: (String?) -> List<T>,
        message: String,
    ): AppResult<List<T>> {
        val values = mutableListOf<T>()
        val seen = linkedSetOf<String>()
        var request = first

        repeat(MAX_PAGES) {
            when (
                val response =
                    platform.rest.execute(request)
            ) {
                is AppResult.Failure ->
                    return response

                is AppResult.Success -> {
                    val page = runCatching {
                        parse(response.value.body)
                    }.getOrElse {
                        return AppResult.Failure(
                            AppError.Parsing(
                                message,
                                it,
                            ),
                        )
                    }
                    values += page

                    val next =
                        response.value.pagination.nextUrl
                            ?: return AppResult.Success(values)
                    if (!seen.add(next)) {
                        return validation(
                            "Repeated GitHub pagination URL.",
                        )
                    }

                    request = GitHubRestRequest(
                        pathOrUrl = next,
                        cachePolicy =
                            GitHubCachePolicy.NETWORK_FIRST,
                        cacheTtlMillis =
                            first.cacheTtlMillis,
                    )
                }
            }
        }

        return validation(
            "GitHub release pagination exceeded the safety limit.",
        )
    }

    private fun <T> parse(
        result: AppResult<GitHubRestResponse>,
        parser: (String?) -> T,
        message: String,
    ): AppResult<T> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                runCatching {
                    parser(result.value.body)
                }.fold(
                    onSuccess = {
                        AppResult.Success(it)
                    },
                    onFailure = {
                        AppResult.Failure(
                            AppError.Parsing(
                                message,
                                it,
                            ),
                        )
                    },
                )
        }

    private fun unit(
        result: AppResult<GitHubRestResponse>,
    ): AppResult<Unit> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(Unit)
        }

    private fun validate(
        owner: String,
        repository: String,
    ): Pair<String, String>? {
        val safeOwner = owner.trim()
        val safeRepository = repository.trim()

        if (
            !OWNER.matches(safeOwner) ||
            !REPOSITORY.matches(safeRepository)
        ) {
            return null
        }

        return safeOwner to safeRepository
    }

    private fun repoPath(
        ids: Pair<String, String>,
    ): String =
        "/repos/" + ids.first + "/" + ids.second

    private fun isSafeTag(
        tag: String,
    ): Boolean =
        tag.isNotBlank() &&
            tag.length <= 255 &&
            !tag.startsWith("-") &&
            !tag.endsWith("/") &&
            !tag.endsWith(".") &&
            !tag.contains("..") &&
            !tag.contains("@{") &&
            !tag.contains("//") &&
            tag.none {
                it.isWhitespace() ||
                    it.code < 32 ||
                    it in setOf(
                        '~',
                        '^',
                        ':',
                        '?',
                        '*',
                        '[',
                        '\\',
                    )
            }

    private fun encodePathPart(
        value: String,
    ): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name(),
        ).replace("+", "%20")

    private fun validation(
        message: String,
    ): AppResult.Failure =
        AppResult.Failure(
            AppError.Validation(message),
        )

    companion object {
        private val OWNER =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
        private val SHA =
            Regex("^[a-fA-F0-9]{7,64}$")
        private const val MAX_PAGES = 20
    }
}
