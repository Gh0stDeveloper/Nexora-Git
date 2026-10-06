package com.nexora.git.core.social

import com.nexora.git.core.common.AppError
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.platform.GitHubCachePolicy
import com.nexora.git.core.platform.GitHubHttpMethod
import com.nexora.git.core.platform.GitHubPlatformClient
import com.nexora.git.core.platform.GitHubRestRequest
import com.nexora.git.core.platform.GitHubRestResponse
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class GitHubSocialGateway @Inject constructor(
    private val platform: GitHubPlatformClient,
    private val parser: SocialJsonParser,
) : SocialGateway {

    override suspend fun getViewerProfile():
        AppResult<GitHubUserProfile> =
        parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = "/user",
                    cachePolicy =
                        GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 30_000L,
                ),
            ),
            parser::profile,
            "Invalid GitHub profile response.",
        )

    override suspend fun updateViewerProfile(
        request: UpdateGitHubProfileRequest,
    ): AppResult<GitHubUserProfile> {
        val payload = JSONObject()

        request.name?.let {
            payload.put("name", it.trim())
        }
        request.bio?.let {
            payload.put("bio", it)
        }
        request.company?.let {
            payload.put("company", it)
        }
        request.location?.let {
            payload.put("location", it)
        }
        request.blog?.let {
            payload.put("blog", it.trim())
        }
        request.twitterUsername?.let {
            val value = it.trim().removePrefix("@")
            if (
                value.isNotBlank() &&
                !TWITTER_USERNAME.matches(value)
            ) {
                return validation(
                    "Twitter/X username is invalid.",
                )
            }
            payload.put(
                "twitter_username",
                value,
            )
        }
        request.hireable?.let {
            payload.put("hireable", it)
        }

        if (payload.length() == 0) {
            return validation(
                "No profile changes were provided.",
            )
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = "/user",
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = payload.toString(),
                    cachePolicy =
                        GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::profile,
            "Invalid GitHub profile update response.",
        )
    }

    override suspend fun listOrganizations():
        AppResult<List<GitHubOrganizationSummary>> =
        paged(
            first = GitHubRestRequest(
                pathOrUrl = "/user/orgs",
                query = mapOf(
                    "per_page" to "100",
                ),
                cachePolicy =
                    GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 60_000L,
            ),
            parse = parser::organizations,
            message =
                "Invalid GitHub organizations response.",
        )

    override suspend fun listStarredRepositories():
        AppResult<List<StarredRepository>> =
        paged(
            first = GitHubRestRequest(
                pathOrUrl = "/user/starred",
                query = mapOf(
                    "sort" to "updated",
                    "direction" to "desc",
                    "per_page" to "100",
                ),
                cachePolicy =
                    GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 30_000L,
            ),
            parse = parser::starredRepositories,
            message =
                "Invalid GitHub starred repositories response.",
        )

    override suspend fun setStarred(
        owner: String,
        repository: String,
        starred: Boolean,
    ): AppResult<Unit> {
        val ids = validateRepository(
            owner,
            repository,
        ) ?: return validation(
            "Repository identifier is invalid.",
        )

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/user/starred/" +
                            ids.first +
                            "/" +
                            ids.second,
                    method =
                        if (starred) {
                            GitHubHttpMethod.PUT
                        } else {
                            GitHubHttpMethod.DELETE
                        },
                    cachePolicy =
                        GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun listFollowers():
        AppResult<List<GitHubSocialUser>> =
        paged(
            first = GitHubRestRequest(
                pathOrUrl = "/user/followers",
                query = mapOf(
                    "per_page" to "100",
                ),
                cachePolicy =
                    GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 30_000L,
            ),
            parse = parser::users,
            message =
                "Invalid GitHub followers response.",
        )

    override suspend fun listFollowing():
        AppResult<List<GitHubSocialUser>> =
        paged(
            first = GitHubRestRequest(
                pathOrUrl = "/user/following",
                query = mapOf(
                    "per_page" to "100",
                ),
                cachePolicy =
                    GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 30_000L,
            ),
            parse = parser::users,
            message =
                "Invalid GitHub following response.",
        )

    override suspend fun setFollowing(
        username: String,
        following: Boolean,
    ): AppResult<Unit> {
        val user = username.trim()
        if (!USERNAME.matches(user)) {
            return validation(
                "GitHub username is invalid.",
            )
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/user/following/" + user,
                    method =
                        if (following) {
                            GitHubHttpMethod.PUT
                        } else {
                            GitHubHttpMethod.DELETE
                        },
                    cachePolicy =
                        GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun listActivity(
        username: String,
    ): AppResult<List<GitHubActivityEvent>> {
        val user = username.trim()
        if (!USERNAME.matches(user)) {
            return validation(
                "GitHub username is invalid.",
            )
        }

        return paged(
            first = GitHubRestRequest(
                pathOrUrl =
                    "/users/" + user + "/events",
                query = mapOf(
                    "per_page" to "100",
                ),
                cachePolicy =
                    GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 20_000L,
            ),
            parse = parser::activity,
            message =
                "Invalid GitHub activity response.",
        )
    }

    override suspend fun listNotifications(
        scope: NotificationScope,
    ): AppResult<List<GitHubNotificationThread>> {
        val query = buildMap<String, String?> {
            put("per_page", "100")
            when (scope) {
                NotificationScope.UNREAD -> {
                    put("all", "false")
                    put("participating", "false")
                }
                NotificationScope.ALL -> {
                    put("all", "true")
                    put("participating", "false")
                }
                NotificationScope.PARTICIPATING -> {
                    put("all", "true")
                    put("participating", "true")
                }
            }
        }

        return paged(
            first = GitHubRestRequest(
                pathOrUrl = "/notifications",
                query = query,
                cachePolicy =
                    GitHubCachePolicy.NETWORK_FIRST,
                cacheTtlMillis = 10_000L,
            ),
            parse = parser::notifications,
            message =
                "Invalid GitHub notifications response.",
        )
    }

    override suspend fun markNotificationRead(
        threadId: String,
    ): AppResult<Unit> {
        val thread = threadId.trim()
        if (!THREAD_ID.matches(thread)) {
            return validation(
                "Notification thread id is invalid.",
            )
        }

        return unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/notifications/threads/" +
                            thread,
                    method = GitHubHttpMethod.PATCH,
                    bodyJson = "{}",
                    cachePolicy =
                        GitHubCachePolicy.NO_STORE,
                ),
            ),
        )
    }

    override suspend fun markAllNotificationsRead():
        AppResult<Unit> =
        unit(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl = "/notifications",
                    method = GitHubHttpMethod.PUT,
                    bodyJson = "{}",
                    cachePolicy =
                        GitHubCachePolicy.NO_STORE,
                ),
            ),
        )

    override suspend fun getThreadSubscription(
        threadId: String,
    ): AppResult<NotificationThreadSubscription> {
        val thread = threadId.trim()
        if (!THREAD_ID.matches(thread)) {
            return validation(
                "Notification thread id is invalid.",
            )
        }

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/notifications/threads/" +
                            thread +
                            "/subscription",
                    cachePolicy =
                        GitHubCachePolicy.NETWORK_FIRST,
                    cacheTtlMillis = 5_000L,
                ),
            ),
            parser::subscription,
            "Invalid GitHub notification subscription response.",
        )
    }

    override suspend fun setThreadSubscription(
        threadId: String,
        subscribed: Boolean,
        ignored: Boolean,
    ): AppResult<NotificationThreadSubscription> {
        val thread = threadId.trim()
        if (!THREAD_ID.matches(thread)) {
            return validation(
                "Notification thread id is invalid.",
            )
        }

        val payload = JSONObject()
            .put("subscribed", subscribed)
            .put("ignored", ignored)

        return parse(
            platform.rest.execute(
                GitHubRestRequest(
                    pathOrUrl =
                        "/notifications/threads/" +
                            thread +
                            "/subscription",
                    method = GitHubHttpMethod.PUT,
                    bodyJson = payload.toString(),
                    cachePolicy =
                        GitHubCachePolicy.NO_STORE,
                ),
            ),
            parser::subscription,
            "Invalid GitHub notification subscription response.",
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
                        response.value
                            .pagination
                            .nextUrl
                            ?: return AppResult.Success(
                                values,
                            )

                    if (!seen.add(next)) {
                        return validation(
                            "Repeated GitHub pagination URL.",
                        )
                    }

                    request =
                        GitHubRestRequest(
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
            "GitHub social pagination exceeded the safety limit.",
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
            is AppResult.Success ->
                AppResult.Success(Unit)
        }

    private fun validateRepository(
        owner: String,
        repository: String,
    ): Pair<String, String>? {
        val safeOwner = owner.trim()
        val safeRepository =
            repository.trim()

        if (
            !USERNAME.matches(safeOwner) ||
            !REPOSITORY.matches(
                safeRepository,
            )
        ) {
            return null
        }

        return safeOwner to safeRepository
    }

    private fun validation(
        message: String,
    ): AppResult.Failure =
        AppResult.Failure(
            AppError.Validation(message),
        )

    companion object {
        private val USERNAME =
            Regex(
                "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$",
            )
        private val REPOSITORY =
            Regex("^[A-Za-z0-9._-]{1,100}$")
        private val THREAD_ID =
            Regex("^[A-Za-z0-9_-]{1,128}$")
        private val TWITTER_USERNAME =
            Regex("^[A-Za-z0-9_]{1,15}$")
        private const val MAX_PAGES = 10
    }
}
