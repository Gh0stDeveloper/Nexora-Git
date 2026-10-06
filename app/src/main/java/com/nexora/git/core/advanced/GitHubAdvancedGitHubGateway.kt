package com.nexora.git.core.advanced

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubGraphQlOperation
import com.nexora.git.core.platform.GitHubGraphQlRequest
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import com.nexora.git.core.platform.GitHubRestResponse
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class GitHubAdvancedGitHubGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
    private val parser: AdvancedGitHubJsonParser,
) : AdvancedGitHubGateway {

    override suspend fun listDiscussions(
        owner: String,
        repository: String,
    ): AppResult<AdvancedDiscussionHub> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return graph(
            platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = discussionsQuery(),
                    variables = mapOf(
                        "owner" to ids.first,
                        "name" to ids.second,
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 20_000L,
                ),
            ),
            parser::discussionHub,
            "Invalid GitHub Discussions response.",
        )
    }

    override suspend fun createDiscussion(
        owner: String,
        repository: String,
        categoryId: String,
        title: String,
        body: String,
    ): AppResult<AdvancedDiscussionSummary> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        val safeCategoryId = categoryId.trim()
        val safeTitle = title.trim()
        val safeBody = body.trim()

        if (safeCategoryId.isBlank()) {
            return validation("A discussion category is required.")
        }
        if (safeTitle.isBlank()) {
            return validation("Discussion title is required.")
        }
        if (safeBody.isBlank()) {
            return validation("Discussion body is required.")
        }

        val identity = when (val result = repositoryIdentity(ids)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        val dollar = '$'
        val mutation = listOf(
            "mutation NexoraCreateDiscussion(",
            "  " + dollar + "repositoryId: ID!,",
            "  " + dollar + "categoryId: ID!,",
            "  " + dollar + "title: String!,",
            "  " + dollar + "body: String!",
            ") {",
            "  createDiscussion(input: {",
            "    repositoryId: " + dollar + "repositoryId,",
            "    categoryId: " + dollar + "categoryId,",
            "    title: " + dollar + "title,",
            "    body: " + dollar + "body",
            "  }) {",
            "    discussion {",
            DISCUSSION_FIELDS,
            "    }",
            "  }",
            "}",
        ).joinToString("\n")

        return graph(
            platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = mutation,
                    variables = mapOf(
                        "repositoryId" to identity.repositoryNodeId,
                        "categoryId" to safeCategoryId,
                        "title" to safeTitle,
                        "body" to safeBody,
                    ),
                    operation = GitHubGraphQlOperation.MUTATION,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::discussionMutation,
            "Invalid GitHub create discussion response.",
        )
    }

    override suspend fun listProjects(
        owner: String,
        repository: String,
    ): AppResult<AdvancedProjectHub> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return graph(
            platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = projectsQuery(),
                    variables = mapOf(
                        "owner" to ids.first,
                        "name" to ids.second,
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 20_000L,
                ),
            ),
            parser::projectHub,
            "Invalid GitHub Projects response.",
        )
    }

    override suspend fun createProject(
        owner: String,
        repository: String,
        title: String,
    ): AppResult<AdvancedProjectSummary> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        val safeTitle = title.trim()
        if (safeTitle.isBlank()) {
            return validation("Project title is required.")
        }

        val identity = when (val result = repositoryIdentity(ids)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        val dollar = '$'
        val mutation = listOf(
            "mutation NexoraCreateProject(",
            "  " + dollar + "ownerId: ID!,",
            "  " + dollar + "repositoryId: ID!,",
            "  " + dollar + "title: String!",
            ") {",
            "  createProjectV2(input: {",
            "    ownerId: " + dollar + "ownerId,",
            "    repositoryId: " + dollar + "repositoryId,",
            "    title: " + dollar + "title",
            "  }) {",
            "    projectV2 {",
            PROJECT_FIELDS,
            "    }",
            "  }",
            "}",
        ).joinToString("\n")

        return graph(
            platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = mutation,
                    variables = mapOf(
                        "ownerId" to identity.ownerNodeId,
                        "repositoryId" to identity.repositoryNodeId,
                        "title" to safeTitle,
                    ),
                    operation = GitHubGraphQlOperation.MUTATION,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::projectMutation,
            "Invalid GitHub create project response.",
        )
    }

    override suspend fun getPages(
        owner: String,
        repository: String,
    ): AppResult<GitHubPagesSite> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return when (
            val result = platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pages",
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 20_000L,
                ),
            )
        ) {
            is AppResult.Failure -> {
                if (result.error is AppError.NotFound) {
                    AppResult.Success(
                        GitHubPagesSite(
                            enabled = false,
                            htmlUrl = null,
                            status = null,
                            cname = null,
                            httpsEnforced = false,
                            protectedDomainState = null,
                            buildType = null,
                            sourceBranch = null,
                            sourcePath = null,
                        ),
                    )
                } else {
                    result
                }
            }

            is AppResult.Success ->
                parseRest(
                    result,
                    parser::pages,
                    "Invalid GitHub Pages response.",
                )
        }
    }

    override suspend fun enablePages(
        owner: String,
        repository: String,
    ): AppResult<GitHubPagesSite> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        val branch = when (val result = defaultBranch(ids)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        val payload = JSONObject()
            .put(
                "source",
                JSONObject()
                    .put("branch", branch)
                    .put("path", "/"),
            )

        return parseRest(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pages",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::pages,
            "Invalid GitHub Pages enable response.",
        )
    }

    override suspend fun requestPagesBuild(
        owner: String,
        repository: String,
    ): AppResult<Unit> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/pages/builds",
                    method = GitHubHttpMethod.POST,
                    bodyJson = "{}",
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun getSecurityOverview(
        owner: String,
        repository: String,
    ): AppResult<RepositorySecurityOverview> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        val base = repoPath(ids)

        val dependabot = securityFeed(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = base + "/dependabot/alerts",
                    query = mapOf(
                        "state" to "open",
                        "per_page" to "100",
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 20_000L,
                ),
            ),
            parser::dependabot,
        )

        val codeScanning = securityFeed(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = base + "/code-scanning/alerts",
                    query = mapOf(
                        "state" to "open",
                        "per_page" to "100",
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 20_000L,
                ),
            ),
            parser::codeScanning,
        )

        val secretScanning = securityFeed(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = base + "/secret-scanning/alerts",
                    query = mapOf(
                        "state" to "open",
                        "per_page" to "100",
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 20_000L,
                ),
            ),
            parser::secretScanning,
        )

        return AppResult.Success(
            RepositorySecurityOverview(
                dependabot = dependabot,
                codeScanning = codeScanning,
                secretScanning = secretScanning,
            ),
        )
    }

    override suspend fun listGists():
        AppResult<List<GitHubGistSummary>> =
        paged(
            first = GitHubRestRequest(
                pathOrUrl = "/gists",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 30_000L,
            ),
            parse = parser::gists,
            message = "Invalid GitHub Gists response.",
        )

    override suspend fun createGist(
        fileName: String,
        content: String,
        description: String,
        publicGist: Boolean,
    ): AppResult<GitHubGistSummary> {
        val safeName = fileName.trim()
        if (!GIST_FILE.matches(safeName)) {
            return validation("Gist file name is invalid.")
        }

        val payload = JSONObject()
            .put("description", description.trim())
            .put("public", publicGist)
            .put(
                "files",
                JSONObject().put(
                    safeName,
                    JSONObject().put("content", content),
                ),
            )

        return parseRest(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = "/gists",
                    method = GitHubHttpMethod.POST,
                    bodyJson = payload.toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::gist,
            "Invalid GitHub create Gist response.",
        )
    }

    override suspend fun deleteGist(
        gistId: String,
    ): AppResult<Unit> {
        val id = gistId.trim()
        if (!GIST_ID.matches(id)) {
            return validation("Gist identifier is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = "/gists/" + id,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun listCodespaces():
        AppResult<List<GitHubCodespaceSummary>> =
        paged(
            first = GitHubRestRequest(
                pathOrUrl = "/user/codespaces",
                query = mapOf("per_page" to "100"),
                cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 15_000L,
            ),
            parse = parser::codespaces,
            message = "Invalid GitHub Codespaces response.",
        )

    override suspend fun createCodespace(
        owner: String,
        repository: String,
    ): AppResult<GitHubCodespaceSummary> {
        val ids = validateRepository(owner, repository)
            ?: return validation("Repository identifier is invalid.")
        val branch = when (val result = defaultBranch(ids)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }

        return parseRest(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids) + "/codespaces",
                    method = GitHubHttpMethod.POST,
                    bodyJson = JSONObject()
                        .put("ref", branch)
                        .toString(),
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::codespace,
            "Invalid GitHub create Codespace response.",
        )
    }

    override suspend fun setCodespaceRunning(
        name: String,
        running: Boolean,
    ): AppResult<GitHubCodespaceSummary> {
        val safeName = name.trim()
        if (!CODESPACE.matches(safeName)) {
            return validation("Codespace name is invalid.")
        }

        val action = if (running) "start" else "stop"
        return parseRest(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/user/codespaces/" + safeName + "/" + action,
                    method = GitHubHttpMethod.POST,
                    bodyJson = "{}",
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::codespace,
            "Invalid GitHub Codespace " + action + " response.",
        )
    }

    override suspend fun deleteCodespace(
        name: String,
    ): AppResult<Unit> {
        val safeName = name.trim()
        if (!CODESPACE.matches(safeName)) {
            return validation("Codespace name is invalid.")
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/user/codespaces/" + safeName,
                    method = GitHubHttpMethod.DELETE,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    private suspend fun repositoryIdentity(
        ids: Pair<String, String>,
    ): AppResult<RepositoryIdentity> {
        val dollar = '$'
        val query = listOf(
            "query NexoraRepositoryIdentity(",
            "  " + dollar + "owner: String!,",
            "  " + dollar + "name: String!",
            ") {",
            "  repository(owner: " + dollar +
                "owner, name: " + dollar + "name) {",
            "    id",
            "    owner { id }",
            "  }",
            "}",
        ).joinToString("\n")

        return graph(
            platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = query,
                    variables = mapOf(
                        "owner" to ids.first,
                        "name" to ids.second,
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 30_000L,
                ),
            ),
            { body ->
                val repository = JSONObject(body ?: "{}")
                    .getJSONObject("repository")
                RepositoryIdentity(
                    ownerNodeId = repository
                        .getJSONObject("owner")
                        .getString("id"),
                    repositoryNodeId =
                        repository.getString("id"),
                )
            },
            "Invalid GitHub repository identity response.",
        )
    }

    private suspend fun defaultBranch(
        ids: Pair<String, String>,
    ): AppResult<String> =
        parseRest(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = repoPath(ids),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 30_000L,
                ),
            ),
            { body ->
                JSONObject(body ?: "{}")
                    .getString("default_branch")
            },
            "Invalid repository metadata response.",
        )

    private suspend fun <T> paged(
        first: GitHubRestRequest,
        parse: (String?) -> List<T>,
        message: String,
    ): AppResult<List<T>> {
        val values = mutableListOf<T>()
        val seen = linkedSetOf<String>()
        var request = first

        repeat(MAX_PAGES) {
            when (val result = platform.rest.execute(request)) {
                is AppResult.Failure -> return result
                is AppResult.Success -> {
                    val page = runCatching {
                        parse(result.value.body)
                    }.getOrElse { error ->
                        return AppResult.Failure(
                            AppError.Parsing(message, error),
                        )
                    }
                    values += page

                    val next = result.value.pagination.nextUrl
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
            "GitHub pagination exceeded the Phase O safety limit.",
        )
    }

    private fun securityFeed(
        result: AppResult<GitHubRestResponse>,
        parse: (String?) -> List<SecurityAlertSummary>,
    ): SecurityAlertFeed =
        when (result) {
            is AppResult.Failure ->
                SecurityAlertFeed(
                    available = false,
                    message = result.error.conciseMessage(),
                )

            is AppResult.Success ->
                runCatching {
                    parse(result.value.body)
                }.fold(
                    onSuccess = { alerts ->
                        SecurityAlertFeed(
                            available = true,
                            alerts = alerts,
                        )
                    },
                    onFailure = {
                        SecurityAlertFeed(
                            available = false,
                            message =
                                "GitHub returned an unreadable security response.",
                        )
                    },
                )
        }

    private fun <T> parseRest(
        result: AppResult<GitHubRestResponse>,
        parse: (String?) -> T,
        message: String,
    ): AppResult<T> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                runCatching {
                    parse(result.value.body)
                }.fold(
                    onSuccess = {
                        AppResult.Success(it)
                    },
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

    private fun <T> graph(
        result: AppResult<com.nexora.git.core.platform.GitHubGraphQlResponse>,
        parse: (String?) -> T,
        message: String,
    ): AppResult<T> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                if (result.value.hasErrors) {
                    AppResult.Failure(
                        AppError.Validation(
                            result.value.errors.joinToString("; ") {
                                it.message
                            },
                        ),
                    )
                } else {
                    runCatching {
                        parse(result.value.dataJson)
                    }.fold(
                        onSuccess = {
                            AppResult.Success(it)
                        },
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
            }
        }

    private fun unit(
        result: AppResult<GitHubRestResponse>,
    ): AppResult<Unit> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(Unit)
        }

    private fun validateRepository(
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

    private fun validation(
        message: String,
    ): AppResult.Failure =
        AppResult.Failure(
            AppError.Validation(message),
        )

    private fun AppError.conciseMessage(): String =
        when (this) {
            is AppError.Authentication ->
                message ?: "Authentication is required."
            is AppError.PermissionDenied ->
                message ?: "The GitHub App lacks this permission."
            is AppError.RateLimited ->
                message ?: "GitHub rate limit reached."
            is AppError.NotFound ->
                message ?: "This security feature is unavailable."
            is AppError.Network ->
                message ?: "Network request failed."
            is AppError.Server ->
                message ?: "GitHub returned a server error."
            is AppError.Conflict ->
                message ?: "GitHub rejected the request because of a conflict."
            is AppError.Validation ->
                message ?: "GitHub rejected the request."
            is AppError.Parsing ->
                message ?: "GitHub returned an unreadable response."
            is AppError.Unknown ->
                "Unexpected GitHub error."
            AppError.StoragePermission,
            AppError.DiskFull,
            AppError.GitConflict ->
                "Local application error."
        }

    private fun discussionsQuery(): String {
        val dollar = '$'
        return listOf(
            "query NexoraDiscussions(",
            "  " + dollar + "owner: String!,",
            "  " + dollar + "name: String!",
            ") {",
            "  repository(owner: " + dollar +
                "owner, name: " + dollar + "name) {",
            "    id",
            "    discussionCategories(first: 25) {",
            "      nodes {",
            "        id",
            "        name",
            "        description",
            "        isAnswerable",
            "      }",
            "    }",
            "    discussions(",
            "      first: 50,",
            "      orderBy: {field: UPDATED_AT, direction: DESC}",
            "    ) {",
            "      nodes {",
            DISCUSSION_FIELDS,
            "      }",
            "    }",
            "  }",
            "}",
        ).joinToString("\n")
    }

    private fun projectsQuery(): String {
        val dollar = '$'
        return listOf(
            "query NexoraProjects(",
            "  " + dollar + "owner: String!,",
            "  " + dollar + "name: String!",
            ") {",
            "  repository(owner: " + dollar +
                "owner, name: " + dollar + "name) {",
            "    id",
            "    owner { id }",
            "    projectsV2(",
            "      first: 50,",
            "      orderBy: {field: UPDATED_AT, direction: DESC}",
            "    ) {",
            "      nodes {",
            PROJECT_FIELDS,
            "      }",
            "    }",
            "  }",
            "}",
        ).joinToString("\n")
    }

    private data class RepositoryIdentity(
        val ownerNodeId: String,
        val repositoryNodeId: String,
    )

    companion object {
        private const val DISCUSSION_FIELDS =
            "id number title url isAnswered upvoteCount updatedAt " +
                "author { login } category { name } " +
                "comments { totalCount }"

        private const val PROJECT_FIELDS =
            "id number title shortDescription url closed public " +
                "updatedAt items(first: 1) { totalCount }"

        private val OWNER =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
        private val GIST_ID =
            Regex("^[A-Za-z0-9]{8,128}$")
        private val GIST_FILE =
            Regex("^[^/\\\\]{1,255}$")
        private val CODESPACE =
            Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,127}$")
        private const val MAX_PAGES = 10
    }
}
