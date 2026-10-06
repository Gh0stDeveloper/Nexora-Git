package com.nexora.git.core.social

import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class SocialJsonParser @Inject constructor() {

    fun profile(
        body: String?,
    ): GitHubUserProfile {
        val root = JSONObject(requireNotNull(body))
        return GitHubUserProfile(
            id = root.getLong("id"),
            nodeId = root.optString("node_id"),
            login = root.getString("login"),
            name = root.optNullableString("name"),
            avatarUrl =
                root.optNullableString("avatar_url"),
            htmlUrl =
                root.optNullableString("html_url"),
            bio = root.optNullableString("bio"),
            company =
                root.optNullableString("company"),
            location =
                root.optNullableString("location"),
            blog = root.optNullableString("blog"),
            email = root.optNullableString("email"),
            twitterUsername =
                root.optNullableString(
                    "twitter_username",
                ),
            hireable =
                if (root.isNull("hireable")) {
                    null
                } else {
                    root.optBoolean("hireable")
                },
            publicRepos =
                root.optInt("public_repos"),
            publicGists =
                root.optInt("public_gists"),
            followers =
                root.optInt("followers"),
            following =
                root.optInt("following"),
            createdAt =
                root.optNullableString("created_at"),
            updatedAt =
                root.optNullableString("updated_at"),
        )
    }

    fun organizations(
        body: String?,
    ): List<GitHubOrganizationSummary> =
        parseArray(body) { root ->
            GitHubOrganizationSummary(
                id = root.getLong("id"),
                nodeId =
                    root.optString("node_id"),
                login = root.getString("login"),
                avatarUrl =
                    root.optNullableString(
                        "avatar_url",
                    ),
                description =
                    root.optNullableString(
                        "description",
                    ),
                htmlUrl =
                    root.optNullableString(
                        "html_url",
                    ),
            )
        }

    fun users(
        body: String?,
    ): List<GitHubSocialUser> =
        parseArray(body) { root ->
            GitHubSocialUser(
                id = root.getLong("id"),
                nodeId =
                    root.optString("node_id"),
                login = root.getString("login"),
                avatarUrl =
                    root.optNullableString(
                        "avatar_url",
                    ),
                htmlUrl =
                    root.optNullableString(
                        "html_url",
                    ),
                type =
                    root.optNullableString("type"),
            )
        }

    fun starredRepositories(
        body: String?,
    ): List<StarredRepository> =
        parseArray(body) { root ->
            val owner =
                root.getJSONObject("owner")
            StarredRepository(
                id = root.getLong("id"),
                nodeId =
                    root.optString("node_id"),
                name = root.getString("name"),
                fullName =
                    root.getString("full_name"),
                ownerLogin =
                    owner.getString("login"),
                ownerAvatarUrl =
                    owner.optNullableString(
                        "avatar_url",
                    ),
                privateRepository =
                    root.optBoolean("private"),
                description =
                    root.optNullableString(
                        "description",
                    ),
                htmlUrl =
                    root.optNullableString(
                        "html_url",
                    ),
                language =
                    root.optNullableString(
                        "language",
                    ),
                stars =
                    root.optLong(
                        "stargazers_count",
                    ),
                forks =
                    root.optLong("forks_count"),
                updatedAt =
                    root.optNullableString(
                        "updated_at",
                    ),
            )
        }

    fun activity(
        body: String?,
    ): List<GitHubActivityEvent> =
        parseArray(body) { root ->
            val payload =
                root.optJSONObject("payload")
                    ?: JSONObject()
            val actor =
                root.optJSONObject("actor")
                    ?: JSONObject()
            val repository =
                root.optJSONObject("repo")
                    ?: JSONObject()

            val issue =
                payload.optJSONObject("issue")
            val pullRequest =
                payload.optJSONObject(
                    "pull_request",
                )
            val release =
                payload.optJSONObject("release")

            GitHubActivityEvent(
                id = root.optString("id"),
                type = root.optString("type"),
                actorLogin =
                    actor.optString("login"),
                actorAvatarUrl =
                    actor.optNullableString(
                        "avatar_url",
                    ),
                repositoryName =
                    repository.optString("name"),
                publicEvent =
                    root.optBoolean("public"),
                createdAt =
                    root.optNullableString(
                        "created_at",
                    ),
                action =
                    payload.optNullableString(
                        "action",
                    ),
                ref =
                    payload.optNullableString("ref"),
                refType =
                    payload.optNullableString(
                        "ref_type",
                    ),
                number =
                    issue?.optInt("number")
                        ?.takeIf { it > 0 }
                        ?: pullRequest
                            ?.optInt("number")
                            ?.takeIf { it > 0 },
                title =
                    issue?.optNullableString(
                        "title",
                    )
                        ?: pullRequest
                            ?.optNullableString(
                                "title",
                            )
                        ?: release
                            ?.optNullableString(
                                "name",
                            ),
            )
        }

    fun notifications(
        body: String?,
    ): List<GitHubNotificationThread> =
        parseArray(body) { root ->
            val subject =
                root.getJSONObject("subject")
            val repository =
                root.getJSONObject("repository")

            GitHubNotificationThread(
                id = root.getString("id"),
                unread =
                    root.optBoolean("unread"),
                reason =
                    root.optString("reason"),
                updatedAt =
                    root.optNullableString(
                        "updated_at",
                    ),
                lastReadAt =
                    root.optNullableString(
                        "last_read_at",
                    ),
                subjectTitle =
                    subject.optString("title"),
                subjectType =
                    subject.optString("type"),
                subjectUrl =
                    subject.optNullableString("url"),
                latestCommentUrl =
                    subject.optNullableString(
                        "latest_comment_url",
                    ),
                repositoryFullName =
                    repository.optString(
                        "full_name",
                    ),
                repositoryHtmlUrl =
                    repository.optNullableString(
                        "html_url",
                    ),
            )
        }

    fun subscription(
        body: String?,
    ): NotificationThreadSubscription {
        val root =
            JSONObject(requireNotNull(body))
        return NotificationThreadSubscription(
            subscribed =
                root.optBoolean("subscribed"),
            ignored =
                root.optBoolean("ignored"),
            reason =
                root.optNullableString("reason"),
            createdAt =
                root.optNullableString(
                    "created_at",
                ),
        )
    }

    private fun <T> parseArray(
        body: String?,
        mapper: (JSONObject) -> T,
    ): List<T> {
        if (body.isNullOrBlank()) {
            return emptyList()
        }

        val array = JSONArray(body)
        return buildList {
            for (
                index in 0 until array.length()
            ) {
                add(
                    mapper(
                        array.getJSONObject(
                            index,
                        ),
                    ),
                )
            }
        }
    }

    private fun JSONObject
        .optNullableString(
            key: String,
        ): String? =
        if (isNull(key)) {
            null
        } else {
            optString(key)
                .takeIf(String::isNotBlank)
        }
}
