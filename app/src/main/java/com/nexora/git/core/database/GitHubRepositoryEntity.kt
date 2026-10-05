package com.nexora.git.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "github_repositories",
    primaryKeys = ["accountId", "repositoryId"],
    indices = [
        Index(
            value = ["accountId", "fullName"],
            unique = true,
        ),
    ],
)
data class GitHubRepositoryEntity(
    val accountId: Long,
    val repositoryId: Long,
    val nodeId: String,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val description: String?,
    val privateRepository: Boolean,
    val fork: Boolean,
    val archived: Boolean,
    val visibility: String,
    val language: String?,
    val defaultBranch: String,
    val cloneUrl: String,
    val htmlUrl: String,
    val stars: Long,
    val forks: Long,
    val openIssues: Long,
    val sizeKb: Long,
    val updatedAt: String?,
    val pushedAt: String?,
    val permissionAdmin: Boolean,
    val permissionMaintain: Boolean,
    val permissionPush: Boolean,
    val permissionTriage: Boolean,
    val permissionPull: Boolean,
    val cachedAtEpochMillis: Long,
)
