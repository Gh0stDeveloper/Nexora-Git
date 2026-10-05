package com.nexora.git.core.issues

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class GitHubIssueGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
) : IssueGateway {

    override suspend fun listIssues(
        owner: String,
        repository: String,
        filters: IssueFilters,
    ): AppResult<List<IssueSummary>> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()

        val request = if (filters.query.isBlank()) {
            GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/issues",
                query = buildMap {
                    put("state", filters.state.wireValue)
                    put("sort", filters.sort.wireValue)
                    put("direction", filters.direction.wireValue)
                    put("per_page", "100")
                    if (filters.labels.isNotEmpty()) {
                        put(
                            "labels",
                            filters.labels.joinToString(","),
                        )
                    }
                    filters.assignee
                        ?.takeIf(String::isNotBlank)
                        ?.let { put("assignee", it) }
                    filters.milestone
                        ?.takeIf(String::isNotBlank)
                        ?.let { put("milestone", it) }
                },
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 30_000L,
            )
        } else {
            val qualifiers = buildList {
                add("repo:" + ids.first + "/" + ids.second)
                add("is:issue")
                when (filters.state) {
                    IssueState.OPEN -> add("is:open")
                    IssueState.CLOSED -> add("is:closed")
                    IssueState.ALL -> Unit
                }
                filters.labels.forEach {
                    add("label:\"" + it.replace("\"", "") + "\"")
                }
                filters.assignee
                    ?.takeIf(String::isNotBlank)
                    ?.let { add("assignee:" + it) }
            }

            GitHubRestRequest(
                pathOrUrl = "/search/issues",
                query = mapOf(
                    "q" to (
                        filters.query.trim() + " " +
                            qualifiers.joinToString(" ")
                        ).trim(),
                    "sort" to filters.sort.wireValue,
                    "order" to filters.direction.wireValue,
                    "per_page" to "100",
                ),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 20_000L,
            )
        }

        return pagedIssues(request, filters)
    }

    override suspend fun getIssue(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<IssueDetails> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (number <= 0) return invalidIssueNumber()

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) + "/issues/" + number,
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 15_000L,
                ),
            ),
            parser = issueParser::details,
            message = "Invalid GitHub issue response",
        )
    }

    override suspend fun createIssue(
        owner: String,
        repository: String,
        request: CreateIssueRequest,
    ): AppResult<IssueDetails> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        val title = request.title.trim()
        if (title.isBlank()) {
            return validation("Issue title is required.")
        }

        val payload = JSONObject()
            .put("title", title)
            .put("body", request.body)
            .put("labels", JSONArray(request.labels))
            .put("assignees", JSONArray(request.assignees))

        request.milestone?.let {
            payload.put("milestone", it)
        }

        return mutateIssue(
            GitHubRestRequest(
                pathOrUrl = repoPath(ids) + "/issues",
                method = GitHubHttpMethod.POST,
                bodyJson = payload.toString(),
                cachePolicy = GitHubCachePolicy.NO_STORE,
            ),
        )
    }

    override suspend fun updateIssue(
        owner: String,
        repository: String,
        number: Int,
        request: UpdateIssueRequest,
    ): AppResult<IssueDetails> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (number <= 0) return invalidIssueNumber()

        val payload = JSONObject()
        request.title?.let {
            val title = it.trim()
            if (title.isBlank()) {
                return validation("Issue title cannot be empty.")
            }
            payload.put("title", title)
        }
        request.body?.let { payload.put("body", it) }
        request.state?.takeIf { it != IssueState.ALL }?.let {
            payload.put("state", it.wireValue)
        }
        request.labels?.let {
            payload.put("labels", JSONArray(it))
        }
        request.assignees?.let {
            payload.put("assignees", JSONArray(it))
        }
        when {
            request.clearMilestone ->
                payload.put("milestone", JSONObject.NULL)
            request.milestone != null ->
                payload.put("milestone", request.milestone)
        }

        if (payload.length() == 0) {
            return validation("No issue changes were provided.")
        }

        return mutateIssue(
            GitHubRestRequest(
                pathOrUrl =
                    repoPath(ids) + "/issues/" + number,
                method = GitHubHttpMethod.PATCH,
                bodyJson = payload.toString(),
                cachePolicy = GitHubCachePolicy.NO_STORE,
            ),
        )
    }

    override suspend fun listLabels(
        owner: String,
        repository: String,
    ): AppResult<List<IssueLabel>> =
        pagedList(
            owner = owner,
            repository = repository,
            suffix = "/labels",
            parse = issueParser::labels,
            errorMessage = "Invalid GitHub labels response",
        )

    override suspend fun listAssignees(
        owner: String,
        repository: String,
    ): AppResult<List<IssueUser>> =
        pagedList(
            owner = owner,
            repository = repository,
            suffix = "/assignees",
            parse = issueParser::users,
            errorMessage = "Invalid GitHub assignees response",
        )

    override suspend fun listMilestones(
        owner: String,
        repository: String,
    ): AppResult<List<IssueMilestone>> =
        pagedList(
            owner = owner,
            repository = repository,
            suffix = "/milestones",
            extraQuery = mapOf("state" to "all"),
            parse = issueParser::milestones,
            errorMessage = "Invalid GitHub milestones response",
        )

    override suspend fun listComments(
        owner: String,
        repository: String,
        number: Int,
    ): AppResult<List<IssueComment>> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (number <= 0) return invalidIssueNumber()

        return paged(
            first = GitHubRestRequest(
                pathOrUrl =
                    repoPath(ids) +
                        "/issues/" + number + "/comments",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 10_000L,
            ),
            parse = issueParser::comments,
            errorMessage = "Invalid GitHub issue comments response",
        )
    }

    override suspend fun createComment(
        owner: String,
        repository: String,
        number: Int,
        body: String,
    ): AppResult<IssueComment> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (number <= 0) return invalidIssueNumber()
        if (body.isBlank()) {
            return validation("Comment cannot be empty.")
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/issues/" + number + "/comments",
                    method = GitHubHttpMethod.POST,
                    bodyJson = JSONObject()
                        .put("body", body)
                        .toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser = issueParser::comment,
            message = "Invalid GitHub issue comment response",
        )
    }

    override suspend fun updateComment(
        owner: String,
        repository: String,
        commentId: Long,
        body: String,
    ): AppResult<IssueComment> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (commentId <= 0L) {
            return validation("Comment id is invalid.")
        }
        if (body.isBlank()) {
            return validation("Comment cannot be empty.")
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/issues/comments/" + commentId,
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = JSONObject()
                        .put("body", body)
                        .toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser = issueParser::comment,
            message = "Invalid GitHub issue comment response",
        )
    }

    override suspend fun deleteComment(
        owner: String,
        repository: String,
        commentId: Long,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (commentId <= 0L) {
            return validation("Comment id is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/issues/comments/" + commentId,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun addIssueReaction(
        owner: String,
        repository: String,
        number: Int,
        content: IssueReactionContent,
    ): AppResult<IssueReaction> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (number <= 0) return invalidIssueNumber()

        return addReaction(
            path = repoPath(ids) +
                "/issues/" + number + "/reactions",
            content = content,
        )
    }

    override suspend fun addCommentReaction(
        owner: String,
        repository: String,
        commentId: Long,
        content: IssueReactionContent,
    ): AppResult<IssueReaction> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (commentId <= 0L) {
            return validation("Comment id is invalid.")
        }

        return addReaction(
            path = repoPath(ids) +
                "/issues/comments/" + commentId + "/reactions",
            content = content,
        )
    }

    override suspend fun deleteReaction(
        owner: String,
        repository: String,
        reactionId: Long,
    ): AppResult<Unit> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()
        if (reactionId <= 0L) {
            return validation("Reaction id is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        repoPath(ids) +
                            "/reactions/" + reactionId,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    private suspend fun mutateIssue(
        request: GitHubRestRequest,
    ): AppResult<IssueDetails> =
        parse(
            platform.rest.execute(request),
            parser = issueParser::details,
            message = "Invalid GitHub issue response",
        )

    private suspend fun addReaction(
        path: String,
        content: IssueReactionContent,
    ): AppResult<IssueReaction> =
        parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = path,
                    method = GitHubHttpMethod.POST,
                    bodyJson = JSONObject()
                        .put("content", content.wireValue)
                        .toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser = issueParser::reaction,
            message = "Invalid GitHub reaction response",
        )

    private suspend fun pagedIssues(
        first: GitHubRestRequest,
        filters: IssueFilters,
    ): AppResult<List<IssueSummary>> {
        val result = paged(
            first = first,
            parse = issueParser::summaries,
            errorMessage = "Invalid GitHub issues response",
        )

        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val milestone = filters.milestone
                    ?.takeIf(String::isNotBlank)
                if (milestone == null || filters.query.isBlank()) {
                    result
                } else {
                    AppResult.Success(
                        result.value.filter {
                            it.milestone?.number?.toString() == milestone ||
                                it.milestone?.title == milestone
                        },
                    )
                }
            }
        }
    }

    private suspend fun <T> pagedList(
        owner: String,
        repository: String,
        suffix: String,
        extraQuery: Map<String, String> = emptyMap(),
        parse: (String?) -> List<T>,
        errorMessage: String,
    ): AppResult<List<T>> {
        val ids = validate(owner, repository)
            ?: return invalidRepository()

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = repoPath(ids) + suffix,
                query = extraQuery + ("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 60_000L,
            ),
            parse = parse,
            errorMessage = errorMessage,
        )
    }

    private suspend fun <T> paged(
        first: GitHubRestRequest,
        parse: (String?) -> List<T>,
        errorMessage: String,
    ): AppResult<List<T>> {
        val collected = mutableListOf<T>()
        val seen = linkedSetOf<String>()
        var request = first

        repeat(MAX_PAGES) {
            when (val response = platform.rest.execute(request)) {
                is AppResult.Failure -> return response
                is AppResult.Success -> {
                    val page = runCatching {
                        parse(response.value.body)
                    }.getOrElse { error ->
                        return AppResult.Failure(
                            AppError.Parsing(
                                message = errorMessage,
                                cause = error,
                            ),
                        )
                    }
                    collected += page

                    val next = response.value.pagination.nextUrl
                        ?: return AppResult.Success(collected)
                    if (!seen.add(next)) {
                        return validation(
                            "Repeated GitHub pagination URL.",
                        )
                    }
                    request = GitHubRestRequest(
                        pathOrUrl = next,
                        cachePolicy =
                            GitHubCachePolicy.NETWORK_FIRST,
                        cacheTtlMillis = first.cacheTtlMillis,
                    )
                }
            }
        }

        return validation(
            "GitHub issue pagination exceeded the safety limit.",
        )
    }

    private fun <T> parse(
        result: AppResult<com.nexora.git.core.platform.GitHubRestResponse>,
        parser: (String?) -> T,
        message: String,
    ): AppResult<T> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                runCatching {
                    parser(result.value.body)
                }.fold(
                    onSuccess = { AppResult.Success(it) },
                    onFailure = {
                        AppResult.Failure(
                            AppError.Parsing(
                                message = message,
                                cause = it,
                            ),
                        )
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

        if (!OWNER.matches(safeOwner) ||
            !REPOSITORY.matches(safeRepo)
        ) {
            return null
        }
        return safeOwner to safeRepo
    }

    private fun repoPath(ids: Pair<String, String>): String =
        "/repos/" + ids.first + "/" + ids.second

    private fun invalidRepository():
        AppResult.Failure =
        validation("Repository identifier is invalid.")

    private fun invalidIssueNumber():
        AppResult.Failure =
        validation("Issue number is invalid.")

    private fun validation(message: String):
        AppResult.Failure =
        AppResult.Failure(AppError.Validation(message))

    companion object {
        private val issueParser = IssueJsonParser()
        private val OWNER =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
        private const val MAX_PAGES = 10
    }
}
