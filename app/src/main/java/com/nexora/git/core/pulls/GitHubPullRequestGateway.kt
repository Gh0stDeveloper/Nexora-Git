package com.nexora.git.core.pulls

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubGraphQlOperation
import com.nexora.git.core.platform.GitHubGraphQlRequest
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class GitHubPullRequestGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
) : PullRequestGateway {

    private val parser = PullRequestJsonParser()

    override suspend fun listPullRequests(
        owner: String,
        repository: String,
        state: PullRequestState,
    ): AppResult<List<PullRequestSummary>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/pulls",
                query = mapOf(
                    "state" to state.wireValue,
                    "sort" to "updated",
                    "direction" to "desc",
                    "per_page" to "100",
                ),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 20_000L,
            ),
            parse = parser::summaries,
            message = "Invalid GitHub pull request list response",
        )
    }

    override suspend fun getPullRequest(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<PullRequestSummary> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (number <= 0) return validation("Pull request number is invalid.")

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/" + number,
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 10_000L,
                ),
            ),
            parser::summary,
            "Invalid GitHub pull request response",
        )
    }

    override suspend fun createPullRequest(
        owner: String,
        repository: String,
        request: CreatePullRequestRequest,
    ): AppResult<PullRequestSummary> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        val title = request.title.trim()
        val head = request.head.trim()
        val base = request.base.trim()
        if (title.isBlank()) return validation("Pull request title is required.")
        if (!REF.matches(head) || !REF.matches(base)) {
            return validation("Pull request head/base branch is invalid.")
        }

        val payload = JSONObject()
            .put("title", title)
            .put("body", request.body)
            .put("head", head)
            .put("base", base)
            .put("draft", request.draft)
            .put("maintainer_can_modify", request.maintainerCanModify)

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::summary,
            "Invalid GitHub create pull request response",
        )
    }

    override suspend fun updatePullRequest(
        owner: String,
        repository: String,
        number: Int,
        request: UpdatePullRequestRequest,
    ): AppResult<PullRequestSummary> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (number <= 0) return validation("Pull request number is invalid.")

        val payload = JSONObject()
        request.title?.let {
            if (it.isBlank()) return validation("Pull request title cannot be empty.")
            payload.put("title", it.trim())
        }
        request.body?.let { payload.put("body", it) }
        request.state?.takeIf { it != PullRequestState.ALL }?.let {
            payload.put("state", it.wireValue)
        }
        request.base?.let {
            if (!REF.matches(it.trim())) {
                return validation("Base branch is invalid.")
            }
            payload.put("base", it.trim())
        }
        request.maintainerCanModify?.let {
            payload.put("maintainer_can_modify", it)
        }

        if (payload.length() == 0) {
            return validation("No pull request changes were provided.")
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/" + number,
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::summary,
            "Invalid GitHub update pull request response",
        )
    }

    override suspend fun listFiles(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<PullRequestFile>> =
        pullPaged(
            owner,
            repository,
            number,
            "/files",
            parser::files,
            "Invalid GitHub pull request files response",
        )

    override suspend fun listReviews(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<PullRequestReview>> =
        pullPaged(
            owner,
            repository,
            number,
            "/reviews",
            parser::reviews,
            "Invalid GitHub pull request reviews response",
        )

    override suspend fun listReviewComments(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<PullRequestReviewComment>> =
        pullPaged(
            owner,
            repository,
            number,
            "/comments",
            parser::reviewComments,
            "Invalid GitHub review comments response",
        )

    override suspend fun createReviewComment(
        owner: String,
        repository: String,
        number: Int,
        request: CreateReviewCommentRequest,
    ): AppResult<PullRequestReviewComment> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (number <= 0) return validation("Pull request number is invalid.")
        if (request.body.isBlank()) return validation("Review comment cannot be empty.")
        if (request.commitId.length < 7 || request.path.isBlank() || request.line <= 0) {
            return validation("Review comment location is invalid.")
        }

        val payload = JSONObject()
            .put("body", request.body)
            .put("commit_id", request.commitId)
            .put("path", request.path)
            .put("line", request.line)
            .put("side", request.side.wireValue)
        request.startLine?.let { payload.put("start_line", it) }
        request.startSide?.let { payload.put("start_side", it.wireValue) }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/" + number + "/comments",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::reviewComment,
            "Invalid GitHub review comment response",
        )
    }

    override suspend fun updateReviewComment(
        owner: String,
        repository: String,
        commentId: Long,
        body: String,
    ): AppResult<PullRequestReviewComment> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (commentId <= 0L || body.isBlank()) {
            return validation("Review comment update is invalid.")
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/comments/" + commentId,
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = JSONObject().put("body", body).toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::reviewComment,
            "Invalid GitHub review comment response",
        )
    }

    override suspend fun deleteReviewComment(
        owner: String,
        repository: String,
        commentId: Long,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (commentId <= 0L) return validation("Review comment id is invalid.")

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/comments/" + commentId,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun submitReview(
        owner: String,
        repository: String,
        number: Int,
        event: PullRequestReviewEvent,
        body: String,
    ): AppResult<PullRequestReview> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (number <= 0) return validation("Pull request number is invalid.")
        if (event != PullRequestReviewEvent.APPROVE && body.isBlank()) {
            return validation("A review message is required for this review.")
        }

        val payload = JSONObject()
            .put("event", event.wireValue)
            .put("body", body)

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/" + number + "/reviews",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::review,
            "Invalid GitHub review response",
        )
    }

    override suspend fun listChecks(
        owner: String,
        repository: String,
        headSha: String,
    ): AppResult<List<PullRequestCheckRun>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (!SHA.matches(headSha)) return validation("Head commit SHA is invalid.")

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/commits/" + headSha + "/check-runs",
                    query = mapOf("per_page" to "100"),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 10_000L,
                ),
            ),
            parser::checks,
            "Invalid GitHub checks response",
        )
    }

    override suspend fun merge(
        owner: String,
        repository: String,
        number: Int,
        method: PullRequestMergeMethod,
        expectedHeadSha: String,
    ): AppResult<PullRequestMergeResult> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (number <= 0 || !SHA.matches(expectedHeadSha)) {
            return validation("Pull request merge request is invalid.")
        }

        val payload = JSONObject()
            .put("sha", expectedHeadSha)
            .put("merge_method", method.wireValue)

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pulls/" + number + "/merge",
                    method = GitHubHttpMethod.PUT,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::mergeResult,
            "Invalid GitHub merge response",
        )
    }

    override suspend fun setDraft(
        nodeId: String,
        draft: Boolean,
    ): AppResult<Unit> {
        if (nodeId.isBlank()) return validation("Pull request node id is missing.")

        val mutationName = if (draft) {
            "convertPullRequestToDraft"
        } else {
            "markPullRequestReadyForReview"
        }
        val inputType = if (draft) {
            "ConvertPullRequestToDraftInput!"
        } else {
            "MarkPullRequestReadyForReviewInput!"
        }

        val variable = 36.toChar().toString() + "input"
        val query = listOf(
            "mutation NexoraDraft(" + variable + ": " + inputType + ") {",
            "  " + mutationName + "(input: " + variable + ") {",
            "    pullRequest {",
            "      id",
            "      isDraft",
            "    }",
            "  }",
            "}",
        ).joinToString(10.toChar().toString())

        return when (
            val response = platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = query,
                    variables = mapOf(
                        "input" to mapOf(
                            "pullRequestId" to nodeId,
                        ),
                    ),
                    operation = GitHubGraphQlOperation.MUTATION,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            )
        ) {
            is AppResult.Failure -> response
            is AppResult.Success -> {
                if (response.value.hasErrors) {
                    AppResult.Failure(
                        AppError.Validation(
                            response.value.errors.joinToString("; ") {
                                it.message
                            },
                        ),
                    )
                } else {
                    AppResult.Success(Unit)
                }
            }
        }
    }

    private suspend fun <T> pullPaged(
        owner: String,
        repository: String,
        number: Int,
        suffix: String,
        parser: (String?) -> List<T>,
        message: String,
    ): AppResult<List<T>> {
        val ids = validate(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        if (number <= 0) return validation("Pull request number is invalid.")

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/pulls/" + number + suffix,
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 10_000L,
            ),
            parse = parser,
            message = message,
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
            when (val response = platform.rest.execute(request)) {
                is AppResult.Failure -> return response
                is AppResult.Success -> {
                    val page = runCatching {
                        parse(response.value.body)
                    }.getOrElse {
                        return AppResult.Failure(AppError.Parsing(message, it))
                    }
                    values += page
                    val next = response.value.pagination.nextUrl
                        ?: return AppResult.Success(values)
                    if (!seen.add(next)) {
                        return validation("Repeated GitHub pagination URL.")
                    }
                    request = GitHubRestRequest(
                        pathOrUrl = next,
                        cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                        cacheTtlMillis = first.cacheTtlMillis,
                    )
                }
            }
        }

        return validation("GitHub pull request pagination exceeded the safety limit.")
    }

    private fun <T> parse(
        result: AppResult<com.nexora.git.core.platform.GitHubRestResponse>,
        parser: (String?) -> T,
        message: String,
    ): AppResult<T> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                runCatching { parser(result.value.body) }.fold(
                    onSuccess = { AppResult.Success(it) },
                    onFailure = {
                        AppResult.Failure(AppError.Parsing(message, it))
                    },
                )
        }

    private fun unit(
        result: AppResult<com.nexora.git.core.platform.GitHubRestResponse>,
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
        val safeRepo = repository.trim()
        if (!OWNER.matches(safeOwner) || !REPOSITORY.matches(safeRepo)) {
            return null
        }
        return safeOwner to safeRepo
    }

    private fun repoPath(ids: Pair<String, String>): String =
        "/repos/" + ids.first + "/" + ids.second

    private fun validation(message: String): AppResult.Failure =
        AppResult.Failure(AppError.Validation(message))

    companion object {
        private val OWNER =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
        private val REF =
            Regex("^[A-Za-z0-9._/-]{1,255}$")
        private val SHA =
            Regex("^[a-fA-F0-9]{7,64}$")
        private const val MAX_PAGES = 10
    }
}
