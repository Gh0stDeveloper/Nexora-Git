package com.nexora.git.core.repository

import com.nexora.git.core.auth.AuthSessionRepository
import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.database.GitHubRepositoryDao
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubGraphQlOperation
import com.nexora.git.core.platform.GitHubGraphQlRequest
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

@Singleton
class GitHubRepositoryGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
    private val authSessionRepository: AuthSessionRepository,
    private val repositoryDao: GitHubRepositoryDao,
    private val parser: RepositoryJsonParser,
) : RepositoryGateway {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val cachedRepositories: Flow<List<RepositorySummary>> =
        authSessionRepository.activeAccountId.flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(emptyList())
            } else {
                repositoryDao.observeForAccount(accountId)
                    .map { entities ->
                        entities.map { it.toDomain() }
                    }
            }
        }

    override suspend fun refreshRepositories():
        AppResult<List<RepositorySummary>> {
        val accountId = activeAccountId()
            ?: return authenticationFailure()

        val collected = mutableListOf<RepositorySummary>()
        val seenPages = linkedSetOf<String>()
        var request = GitHubRestRequest(
            pathOrUrl = "/user/repos",
            query = mapOf(
                "visibility" to "all",
                "affiliation" to
                    "owner,collaborator,organization_member",
                "sort" to "updated",
                "direction" to "desc",
                "per_page" to "100",
            ),
            cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
            cacheTtlMillis = 60_000L,
        )

        repeat(MAX_REPOSITORY_PAGES) {
            when (val result = platform.rest.execute(request)) {
                is AppResult.Failure -> {
                    return cachedListOrFailure(
                        accountId = accountId,
                        failure = result,
                    )
                }

                is AppResult.Success -> {
                    val page = runCatching {
                        parser.summaries(result.value.body)
                    }.getOrElse { error ->
                        return AppResult.Failure(
                            AppError.Parsing(
                                message =
                                    "Invalid GitHub repository list",
                                cause = error,
                            ),
                        )
                    }

                    collected += page

                    val next = result.value.pagination.nextUrl
                    if (next == null) {
                        persistList(accountId, collected)
                        return AppResult.Success(collected)
                    }

                    if (!seenPages.add(next)) {
                        return AppResult.Failure(
                            AppError.Validation(
                                "Repeated GitHub pagination URL",
                            ),
                        )
                    }

                    request = GitHubRestRequest(
                        pathOrUrl = next,
                        cachePolicy =
                            GitHubCachePolicy.NETWORK_FIRST,
                        cacheTtlMillis = 60_000L,
                    )
                }
            }
        }

        return AppResult.Failure(
            AppError.Validation(
                "Repository list exceeded pagination safety limit",
            ),
        )
    }

    override suspend fun getRepository(
        owner: String,
        name: String,
    ): AppResult<RepositoryDetails> {
        val identifiers = validate(owner, name)
            ?: return invalidRepositoryIdentifier()

        val accountId = activeAccountId()
            ?: return authenticationFailure()

        val path = "/repos/" +
            identifiers.first + "/" + identifiers.second

        return when (
            val result = platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = path,
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 30_000L,
                ),
            )
        ) {
            is AppResult.Success -> {
                runCatching {
                    parser.details(result.value.body)
                }.fold(
                    onSuccess = { details ->
                        persistOne(accountId, details.summary)
                        AppResult.Success(details)
                    },
                    onFailure = { error ->
                        AppResult.Failure(
                            AppError.Parsing(
                                message =
                                    "Invalid GitHub repository response",
                                cause = error,
                            ),
                        )
                    },
                )
            }

            is AppResult.Failure -> {
                if (result.error.canUseCachedMetadata()) {
                    val cached = repositoryDao.findByFullName(
                        accountId = accountId,
                        fullName = identifiers.first +
                            "/" + identifiers.second,
                    )

                    if (cached != null) {
                        AppResult.Success(
                            cached.toDomain().asOfflineDetails(),
                        )
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
        }
    }

    override suspend fun createRepository(
        request: CreateRepositoryRequest,
    ): AppResult<RepositoryDetails> {
        val name = request.name.trim()
        if (!REPOSITORY_NAME.matches(name)) {
            return AppResult.Failure(
                AppError.Validation(
                    "Repository name contains unsupported characters",
                ),
            )
        }

        val payload = JSONObject()
            .put("name", name)
            .put("description", request.description.trim())
            .put("private", request.privateRepository)
            .put("auto_init", request.initializeWithReadme)

        return mutateRepository(
            GitHubRestRequest(
                pathOrUrl = "/user/repos",
                method = GitHubHttpMethod.POST,
                bodyJson = payload.toString(),
                cachePolicy = GitHubCachePolicy.NO_STORE,
            ),
        )
    }

    override suspend fun forkRepository(
        request: ForkRepositoryRequest,
    ): AppResult<RepositoryDetails> {
        val identifiers = validate(request.owner, request.name)
            ?: return invalidRepositoryIdentifier()

        val payload = JSONObject()
            .put("default_branch_only", request.defaultBranchOnly)

        val path = "/repos/" +
            identifiers.first + "/" +
            identifiers.second + "/forks"

        return mutateRepository(
            GitHubRestRequest(
                pathOrUrl = path,
                method = GitHubHttpMethod.POST,
                bodyJson = payload.toString(),
                cachePolicy = GitHubCachePolicy.NO_STORE,
            ),
        )
    }

    override suspend fun getViewerState(
        owner: String,
        name: String,
    ): AppResult<RepositoryViewerState> {
        val identifiers = validate(owner, name)
            ?: return invalidRepositoryIdentifier()

        val dollar = '$'
        val query =
            "query RepositoryViewerState(" +
                dollar + "owner: String!, " +
                dollar + "name: String!) {" +
                " repository(owner: " + dollar +
                "owner, name: " + dollar + "name) {" +
                " id viewerHasStarred viewerCanSubscribe" +
                " viewerSubscription } }"

        return when (
            val result = platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = query,
                    variables = mapOf(
                        "owner" to identifiers.first,
                        "name" to identifiers.second,
                    ),
                    cachePolicy = GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 15_000L,
                ),
            )
        ) {
            is AppResult.Failure -> result

            is AppResult.Success -> {
                runCatching {
                    val data = JSONObject(
                        requireNotNull(result.value.dataJson),
                    )
                    val repository = data.getJSONObject("repository")

                    RepositoryViewerState(
                        starred = repository.optBoolean(
                            "viewerHasStarred",
                        ),
                        subscription = parseSubscription(
                            repository.optString(
                                "viewerSubscription",
                                "UNKNOWN",
                            ),
                        ),
                        canSubscribe = repository.optBoolean(
                            "viewerCanSubscribe",
                        ),
                    )
                }.fold(
                    onSuccess = { AppResult.Success(it) },
                    onFailure = { error ->
                        AppResult.Failure(
                            AppError.Parsing(
                                message =
                                    "Invalid repository viewer state",
                                cause = error,
                            ),
                        )
                    },
                )
            }
        }
    }

    override suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): AppResult<Unit> {
        val identifiers = validate(owner, name)
            ?: return invalidRepositoryIdentifier()

        val method = if (starred) {
            GitHubHttpMethod.PUT
        } else {
            GitHubHttpMethod.DELETE
        }

        val path = "/user/starred/" +
            identifiers.first + "/" + identifiers.second

        return when (
            val result = platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = path,
                    method = method,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            )
        ) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(Unit)
        }
    }

    override suspend fun setSubscription(
        repositoryNodeId: String,
        state: RepositorySubscriptionState,
    ): AppResult<RepositorySubscriptionState> {
        if (repositoryNodeId.isBlank()) {
            return AppResult.Failure(
                AppError.Validation(
                    "Repository node ID is required",
                ),
            )
        }

        val targetState = when (state) {
            RepositorySubscriptionState.SUBSCRIBED ->
                "SUBSCRIBED"
            RepositorySubscriptionState.UNSUBSCRIBED ->
                "UNSUBSCRIBED"
            RepositorySubscriptionState.IGNORED ->
                "IGNORED"
            RepositorySubscriptionState.UNAVAILABLE,
            RepositorySubscriptionState.UNKNOWN ->
                return AppResult.Failure(
                    AppError.Validation(
                        "Unsupported repository subscription state",
                    ),
                )
        }

        val dollar = '$'
        val mutation =
            "mutation UpdateRepositorySubscription(" +
                dollar + "id: ID!, " +
                dollar + "state: SubscriptionState!) {" +
                " updateSubscription(input: {subscribableId: " +
                dollar + "id, state: " + dollar + "state}) {" +
                " subscribable { ... on Repository {" +
                " viewerSubscription } } } }"

        return when (
            val result = platform.graphQl.execute(
                GitHubGraphQlRequest(
                    query = mutation,
                    variables = mapOf(
                        "id" to repositoryNodeId,
                        "state" to targetState,
                    ),
                    operation = GitHubGraphQlOperation.MUTATION,
                    cachePolicy = GitHubCachePolicy.NO_STORE,
                ),
            )
        ) {
            is AppResult.Failure -> result

            is AppResult.Success -> {
                runCatching {
                    val data = JSONObject(
                        requireNotNull(result.value.dataJson),
                    )
                    val value = data
                        .getJSONObject("updateSubscription")
                        .getJSONObject("subscribable")
                        .optString(
                            "viewerSubscription",
                            targetState,
                        )

                    parseSubscription(value)
                }.fold(
                    onSuccess = { AppResult.Success(it) },
                    onFailure = { error ->
                        AppResult.Failure(
                            AppError.Parsing(
                                message =
                                    "Invalid subscription mutation response",
                                cause = error,
                            ),
                        )
                    },
                )
            }
        }
    }

    override suspend fun updateRepository(
        owner: String,
        name: String,
        request: UpdateRepositoryRequest,
    ): AppResult<RepositoryDetails> {
        val identifiers = validate(owner, name)
            ?: return invalidRepositoryIdentifier()

        val payload = JSONObject()
            .put("description", request.description.orEmpty())
            .put("homepage", request.homepage.orEmpty())
            .put("has_issues", request.hasIssues)
            .put("has_wiki", request.hasWiki)
            .put(
                "delete_branch_on_merge",
                request.deleteBranchOnMerge,
            )

        val path = "/repos/" +
            identifiers.first + "/" + identifiers.second

        return mutateRepository(
            GitHubRestRequest(
                pathOrUrl = path,
                method = GitHubHttpMethod.PATCH,
                bodyJson = payload.toString(),
                cachePolicy = GitHubCachePolicy.NO_STORE,
            ),
        )
    }

    private suspend fun mutateRepository(
        request: GitHubRestRequest,
    ): AppResult<RepositoryDetails> {
        val accountId = activeAccountId()
            ?: return authenticationFailure()

        return when (val result = platform.rest.execute(request)) {
            is AppResult.Failure -> result

            is AppResult.Success -> {
                runCatching {
                    parser.details(result.value.body)
                }.fold(
                    onSuccess = { details ->
                        persistOne(accountId, details.summary)
                        AppResult.Success(details)
                    },
                    onFailure = { error ->
                        AppResult.Failure(
                            AppError.Parsing(
                                message =
                                    "Invalid repository mutation response",
                                cause = error,
                            ),
                        )
                    },
                )
            }
        }
    }

    private suspend fun persistList(
        accountId: Long,
        repositories: List<RepositorySummary>,
    ) {
        val cachedAt = System.currentTimeMillis()
        repositoryDao.replaceForAccount(
            accountId = accountId,
            repositories = repositories.map {
                it.toEntity(accountId, cachedAt)
            },
        )
    }

    private suspend fun persistOne(
        accountId: Long,
        repository: RepositorySummary,
    ) {
        repositoryDao.upsertAll(
            listOf(
                repository.toEntity(
                    accountId = accountId,
                    cachedAtEpochMillis =
                        System.currentTimeMillis(),
                ),
            ),
        )
    }

    private suspend fun cachedListOrFailure(
        accountId: Long,
        failure: AppResult.Failure,
    ): AppResult<List<RepositorySummary>> {
        if (!failure.error.canUseCachedMetadata()) {
            return failure
        }

        val cached = repositoryDao.getForAccount(accountId)
        return if (cached.isEmpty()) {
            failure
        } else {
            AppResult.Success(cached.map { it.toDomain() })
        }
    }

    private suspend fun activeAccountId(): Long? =
        authSessionRepository.getActiveAccountId()

    private fun validate(
        owner: String,
        name: String,
    ): Pair<String, String>? {
        val normalizedOwner = owner.trim()
        val normalizedName = name.trim()

        if (!OWNER_NAME.matches(normalizedOwner) ||
            !REPOSITORY_NAME.matches(normalizedName)
        ) {
            return null
        }

        return normalizedOwner to normalizedName
    }

    private fun invalidRepositoryIdentifier():
        AppResult.Failure = AppResult.Failure(
        AppError.Validation(
            "Invalid GitHub repository identifier",
        ),
    )

    private fun authenticationFailure():
        AppResult.Failure = AppResult.Failure(
        AppError.Authentication(
            "No active GitHub account",
        ),
    )

    private fun parseSubscription(
        value: String,
    ): RepositorySubscriptionState =
        runCatching {
            RepositorySubscriptionState.valueOf(
                value.uppercase(),
            )
        }.getOrDefault(
            RepositorySubscriptionState.UNKNOWN,
        )

    private fun AppError.canUseCachedMetadata(): Boolean =
        this is AppError.Network ||
            this is AppError.Server ||
            this is AppError.RateLimited

    private fun RepositorySummary.asOfflineDetails():
        RepositoryDetails = RepositoryDetails(
        summary = this,
        homepage = null,
        subscribers = 0,
        hasIssues = true,
        hasWiki = true,
        hasProjects = true,
        hasPages = false,
        deleteBranchOnMerge = false,
        allowMergeCommit = true,
        allowSquashMerge = true,
        allowRebaseMerge = true,
        offlineSnapshot = true,
    )

    companion object {
        private const val MAX_REPOSITORY_PAGES = 100

        private val OWNER_NAME =
            Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")

        private val REPOSITORY_NAME =
            Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
