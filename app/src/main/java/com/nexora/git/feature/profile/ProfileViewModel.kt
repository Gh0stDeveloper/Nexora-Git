package com.nexora.git.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.social.GitHubOrganizationSummary
import com.nexora.git.core.social.GitHubSocialUser
import com.nexora.git.core.social.GitHubUserProfile
import com.nexora.git.core.social.SocialGateway
import com.nexora.git.core.social.StarredRepository
import com.nexora.git.core.social.UpdateGitHubProfileRequest
import com.nexora.git.feature.social.toSocialMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileSection {
    PROFILE,
    ORGANIZATIONS,
    STARRED,
    FOLLOWERS,
    FOLLOWING,
}

data class ProfileUiState(
    val profile: GitHubUserProfile? = null,
    val organizations: List<GitHubOrganizationSummary> =
        emptyList(),
    val starredRepositories: List<StarredRepository> =
        emptyList(),
    val followers: List<GitHubSocialUser> = emptyList(),
    val following: List<GitHubSocialUser> = emptyList(),
    val section: ProfileSection = ProfileSection.PROFILE,
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val followingLogins: Set<String>
        get() = following
            .map { it.login.lowercase() }
            .toSet()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val social: SocialGateway,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> =
        mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            val profileDeferred = async {
                social.getViewerProfile()
            }
            val organizationsDeferred = async {
                social.listOrganizations()
            }
            val starredDeferred = async {
                social.listStarredRepositories()
            }
            val followersDeferred = async {
                social.listFollowers()
            }
            val followingDeferred = async {
                social.listFollowing()
            }

            val profileResult =
                profileDeferred.await()
            val organizationsResult =
                organizationsDeferred.await()
            val starredResult =
                starredDeferred.await()
            val followersResult =
                followersDeferred.await()
            val followingResult =
                followingDeferred.await()

            mutableState.update { current ->
                current.copy(
                    profile =
                        (profileResult as? AppResult.Success)
                            ?.value
                            ?: current.profile,
                    organizations =
                        (organizationsResult as? AppResult.Success)
                            ?.value
                            ?: current.organizations,
                    starredRepositories =
                        (starredResult as? AppResult.Success)
                            ?.value
                            ?: current.starredRepositories,
                    followers =
                        (followersResult as? AppResult.Success)
                            ?.value
                            ?: current.followers,
                    following =
                        (followingResult as? AppResult.Success)
                            ?.value
                            ?: current.following,
                    loading = false,
                    errorMessage = firstFailure(
                        profileResult,
                        organizationsResult,
                        starredResult,
                        followersResult,
                        followingResult,
                    ),
                )
            }
        }
    }

    fun setSection(
        section: ProfileSection,
    ) {
        mutableState.update {
            it.copy(section = section)
        }
    }

    fun updateProfile(
        request: UpdateGitHubProfileRequest,
    ) {
        runOperation {
            when (
                val result =
                    social.updateViewerProfile(
                        request,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            profile = result.value,
                            successMessage =
                                "GitHub profile updated.",
                        )
                    }

                is AppResult.Failure ->
                    fail(result)
            }
        }
    }

    fun setFollowing(
        user: GitHubSocialUser,
        following: Boolean,
    ) {
        runOperation {
            when (
                val result =
                    social.setFollowing(
                        user.login,
                        following,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update { current ->
                        val updatedFollowing =
                            if (following) {
                                (
                                    current.following +
                                        user
                                    ).distinctBy {
                                    it.login.lowercase()
                                }
                            } else {
                                current.following
                                    .filterNot {
                                        it.login.equals(
                                            user.login,
                                            ignoreCase = true,
                                        )
                                    }
                            }

                        current.copy(
                            following =
                                updatedFollowing,
                            profile =
                                current.profile?.copy(
                                    following =
                                        updatedFollowing.size,
                                ),
                            successMessage =
                                if (following) {
                                    "Following @" +
                                        user.login + "."
                                } else {
                                    "Unfollowed @" +
                                        user.login + "."
                                },
                        )
                    }

                is AppResult.Failure ->
                    fail(result)
            }
        }
    }

    fun unstar(
        repository: StarredRepository,
    ) {
        runOperation {
            when (
                val result =
                    social.setStarred(
                        owner =
                            repository.ownerLogin,
                        repository =
                            repository.name,
                        starred = false,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            starredRepositories =
                                it.starredRepositories
                                    .filterNot { item ->
                                        item.id ==
                                            repository.id
                                    },
                            successMessage =
                                "Removed star from " +
                                    repository.fullName +
                                    ".",
                        )
                    }

                is AppResult.Failure ->
                    fail(result)
            }
        }
    }

    fun dismissError() {
        mutableState.update {
            it.copy(errorMessage = null)
        }
    }

    fun dismissSuccess() {
        mutableState.update {
            it.copy(successMessage = null)
        }
    }

    private fun runOperation(
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    operationInProgress = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            try {
                operation()
            } finally {
                mutableState.update {
                    it.copy(
                        operationInProgress = false,
                    )
                }
            }
        }
    }

    private fun fail(
        result: AppResult.Failure,
    ) {
        mutableState.update {
            it.copy(
                errorMessage =
                    result.error
                        .toSocialMessage(),
            )
        }
    }

    private fun firstFailure(
        vararg results: AppResult<*>,
    ): String? =
        results.firstNotNullOfOrNull {
            (it as? AppResult.Failure)
                ?.error
                ?.toSocialMessage()
        }
}
