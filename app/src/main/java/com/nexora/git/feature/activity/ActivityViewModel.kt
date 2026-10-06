package com.nexora.git.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.common.AppResult
import com.nexora.git.core.social.GitHubActivityEvent
import com.nexora.git.core.social.SocialGateway
import com.nexora.git.feature.social.toSocialMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val activity:
        List<GitHubActivityEvent> =
        emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
) {
    val notificationsAvailable: Boolean
        get() = false

    val notificationLimitation: String
        get() =
            "GitHub's Notifications REST API does not accept GitHub App user access tokens. Nexora Git will not send requests that GitHub is documented to reject."
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
        if (normalized.isBlank()) {
            return
        }

        val changed =
            normalized != state.value.login

        mutableState.update {
            it.copy(login = normalized)
        }

        if (changed || state.value.activity.isEmpty()) {
            refresh()
        }
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

            when (
                val result =
                    social.listActivity(login)
            ) {
                is AppResult.Success ->
                    mutableState.update {
                        it.copy(
                            activity = result.value,
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

    fun setSection(
        section: ActivitySection,
    ) {
        mutableState.update {
            it.copy(section = section)
        }
    }

    fun dismissError() {
        mutableState.update {
            it.copy(errorMessage = null)
        }
    }
}
