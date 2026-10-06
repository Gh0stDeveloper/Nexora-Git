package com.nexora.git.core.social

data class GitHubUserProfile(
    val id: Long,
    val nodeId: String,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val htmlUrl: String?,
    val bio: String?,
    val company: String?,
    val location: String?,
    val blog: String?,
    val email: String?,
    val twitterUsername: String?,
    val hireable: Boolean?,
    val publicRepos: Int,
    val publicGists: Int,
    val followers: Int,
    val following: Int,
    val createdAt: String?,
    val updatedAt: String?,
)

data class UpdateGitHubProfileRequest(
    val name: String?,
    val bio: String?,
    val company: String?,
    val location: String?,
    val blog: String?,
    val twitterUsername: String?,
    val hireable: Boolean?,
)

data class GitHubOrganizationSummary(
    val id: Long,
    val nodeId: String,
    val login: String,
    val avatarUrl: String?,
    val description: String?,
    val htmlUrl: String?,
)

data class GitHubSocialUser(
    val id: Long,
    val nodeId: String,
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String?,
    val type: String?,
)

data class StarredRepository(
    val id: Long,
    val nodeId: String,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val privateRepository: Boolean,
    val description: String?,
    val htmlUrl: String?,
    val language: String?,
    val stars: Long,
    val forks: Long,
    val updatedAt: String?,
)

data class GitHubActivityEvent(
    val id: String,
    val type: String,
    val actorLogin: String,
    val actorAvatarUrl: String?,
    val repositoryName: String,
    val publicEvent: Boolean,
    val createdAt: String?,
    val action: String?,
    val ref: String?,
    val refType: String?,
    val number: Int?,
    val title: String?,
)

data class GitHubNotificationThread(
    val id: String,
    val unread: Boolean,
    val reason: String,
    val updatedAt: String?,
    val lastReadAt: String?,
    val subjectTitle: String,
    val subjectType: String,
    val subjectUrl: String?,
    val latestCommentUrl: String?,
    val repositoryFullName: String,
    val repositoryHtmlUrl: String?,
)

data class NotificationThreadSubscription(
    val subscribed: Boolean,
    val ignored: Boolean,
    val reason: String?,
    val createdAt: String?,
)

enum class NotificationScope {
    UNREAD,
    ALL,
    PARTICIPATING,
}
