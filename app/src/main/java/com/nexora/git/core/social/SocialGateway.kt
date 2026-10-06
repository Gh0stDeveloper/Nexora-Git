package com.nexora.git.core.social

import com.nexora.git.core.common.AppResult

interface SocialGateway {

    suspend fun getViewerProfile():
        AppResult<GitHubUserProfile>

    suspend fun updateViewerProfile(
        request: UpdateGitHubProfileRequest,
    ): AppResult<GitHubUserProfile>

    suspend fun listOrganizations():
        AppResult<List<GitHubOrganizationSummary>>

    suspend fun listStarredRepositories():
        AppResult<List<StarredRepository>>

    suspend fun setStarred(
        owner: String,
        repository: String,
        starred: Boolean,
    ): AppResult<Unit>

    suspend fun listFollowers():
        AppResult<List<GitHubSocialUser>>

    suspend fun listFollowing():
        AppResult<List<GitHubSocialUser>>

    suspend fun setFollowing(
        username: String,
        following: Boolean,
    ): AppResult<Unit>

    suspend fun listActivity(
        username: String,
    ): AppResult<List<GitHubActivityEvent>>

    suspend fun listNotifications(
        scope: NotificationScope,
    ): AppResult<List<GitHubNotificationThread>>

    suspend fun markNotificationRead(
        threadId: String,
    ): AppResult<Unit>

    suspend fun markAllNotificationsRead():
        AppResult<Unit>

    suspend fun getThreadSubscription(
        threadId: String,
    ): AppResult<NotificationThreadSubscription>

    suspend fun setThreadSubscription(
        threadId: String,
        subscribed: Boolean,
        ignored: Boolean,
    ): AppResult<NotificationThreadSubscription>
}
