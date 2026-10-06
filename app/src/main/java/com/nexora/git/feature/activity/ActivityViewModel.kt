package com.nexora.git.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.social.GitHubActivityEvent
import com.nexora.git.core.social.GitHubNotificationThread
import com.nexora.git.core.social.NotificationScope
import com.nexora.git.core.social.NotificationThreadSubscription
import com.nexora.git.core.social.SocialGateway
import com.nexora.git.feature.social.toSocialMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivitySection {
    NOTIFICATIONS,
    ACTIVITY,
}

data class ActivityUiState(
    val login: String = "",
    val section: ActivitySection =
        ActivitySection.NOTIFICATIONS,
    val notificationScope: NotificationScope =
        NotificationScope.UNREAD,
    val notifications:
        List<GitHubNotificationThread> =
        emptyList(),
    val activity:
        List<GitHubActivityEvent> =
        emptyList(),
    val subscriptions:
        Map<String, NotificationThreadSubscription> =
        emptyMap(),
    val loading: Boolean = true,
    val operationInProgress: Boolean = false,
    val subscriptionLoadingThreadId: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val unreadCount: Int
        get() = notifications.count {
            it.unread
        }
}

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val social: SocialGateway,
) : ViewModel() {

    private val mutableState =
        MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> =
        mutableState.asStateFlow()

    fun start(
        login: String,
    ) {
        val normalized = login.trim()
        if (
            normalized.isBlank() ||
            normalized == state.value.login
        ) {
            return
        }

        mutableState.update {
            it.copy(login = normalized)
        }
        refresh()
    }

    fun refresh() {
        val login = state.value.login
        if (login.isBlank()) return

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            val notificationsDeferred =
                async {
                    social.listNotifications(
                        state.value.notificationScope,
                    )
                }
            val activityDeferred =
                async {
                    social.listActivity(login)
                }

            val notificationsResult =
                notificationsDeferred.await()
            val activityResult =
                activityDeferred.await()

            mutableState.update { current ->
                current.copy(
                    notifications =
                        (notificationsResult as? AppResult.Success)
                            ?.value
                            ?: current.notifications,
                    activity =
                        (activityResult as? AppResult.Success)
                            ?.value
                            ?: current.activity,
                    loading = false,
                    errorMessage =
                        (notificationsResult as? AppResult.Failure)
                            ?.error
                            ?.toSocialMessage()
                            ?: (activityResult as? AppResult.Failure)
                                ?.error
                                ?.toSocialMessage(),
                )
            }
        }
    }

    fun setSection(
        section: ActivitySection,
    ) {
        mutableState.update {
            it.copy(section = section)
        }
    }

    fun setNotificationScope(
        scope: NotificationScope,
    ) {
        mutableState.update {
            it.copy(
                notificationScope = scope,
            )
        }
        refreshNotifications()
    }

    fun markRead(
        thread: GitHubNotificationThread,
    ) {
        runOperation {
            when (
                val result =
                    social.markNotificationRead(
                        thread.id,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            notifications =
                                it.notifications.map {
                                    item ->
                                    if (
                                        item.id ==
                                            thread.id
                                    ) {
                                        item.copy(
                                            unread = false,
                                        )
                                    } else {
                                        item
                                    }
                                }.let { items ->
                                    if (
                                        it.notificationScope ==
                                            NotificationScope.UNREAD
                                    ) {
                                        items.filter {
                                            item ->
                                            item.unread
                                        }
                                    } else {
                                        items
                                    }
                                },
                            successMessage =
                                "Notification marked as read.",
                        )
                    }

                is AppResult.Failure ->
                    fail(result)
            }
        }
    }

    fun markAllRead() {
        runOperation {
            when (
                val result =
                    social.markAllNotificationsRead()
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            notifications =
                                if (
                                    it.notificationScope ==
                                        NotificationScope.UNREAD
                                ) {
                                    emptyList()
                                } else {
                                    it.notifications.map {
                                        thread ->
                                        thread.copy(
                                            unread = false,
                                        )
                                    }
                                },
                            successMessage =
                                "Notifications marked as read.",
                        )
                    }

                is AppResult.Failure ->
                    fail(result)
            }
        }
    }

    fun loadSubscription(
        threadId: String,
    ) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    subscriptionLoadingThreadId =
                        threadId,
                    errorMessage = null,
                )
            }

            when (
                val result =
                    social.getThreadSubscription(
                        threadId,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            subscriptions =
                                it.subscriptions +
                                    (
                                        threadId to
                                            result.value
                                        ),
                            subscriptionLoadingThreadId =
                                null,
                        )
                    }

                is AppResult.Failure ->
                    mutableState.update {
                        it.copy(
                            subscriptionLoadingThreadId =
                                null,
                            errorMessage =
                                result.error
                                    .toSocialMessage(),
                        )
                    }
            }
        }
    }

    fun setSubscription(
        threadId: String,
        subscribed: Boolean,
        ignored: Boolean,
    ) {
        runOperation {
            when (
                val result =
                    social.setThreadSubscription(
                        threadId = threadId,
                        subscribed = subscribed,
                        ignored = ignored,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            subscriptions =
                                it.subscriptions +
                                    (
                                        threadId to
                                            result.value
                                        ),
                            successMessage =
                                when {
                                    result.value.ignored ->
                                        "Notification thread ignored."
                                    result.value.subscribed ->
                                        "Notification thread subscribed."
                                    else ->
                                        "Notification subscription updated."
                                },
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

    private fun refreshNotifications() {
        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    errorMessage = null,
                )
            }

            when (
                val result =
                    social.listNotifications(
                        state.value.notificationScope,
                    )
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            notifications =
                                result.value,
                            loading = false,
                        )
                    }

                is AppResult.Failure ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            errorMessage =
                                result.error
                                    .toSocialMessage(),
                        )
                    }
            }
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
}
