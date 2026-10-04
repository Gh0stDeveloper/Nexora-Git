package com.nexora.git.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.auth.AuthCallbackBus
import com.nexora.git.core.auth.AuthFailure
import com.nexora.git.core.auth.AuthResult
import com.nexora.git.core.auth.AuthSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authSessionRepository: AuthSessionRepository,
    callbackBus: AuthCallbackBus,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        AuthUiState(
            configured = authSessionRepository.isConfigured(),
        ),
    )

    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authSessionRepository.accounts,
                authSessionRepository.activeAccountId,
            ) { accounts, activeAccountId ->
                val active = accounts.firstOrNull {
                    it.accountId == activeAccountId
                }

                accounts to active
            }.collect { (accounts, active) ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        configured = authSessionRepository.isConfigured(),
                        accounts = accounts,
                        activeAccount = active,
                    )
                }
            }
        }

        viewModelScope.launch {
            callbackBus.callbacks.collect { uri ->
                mutableState.update {
                    it.copy(
                        operationInProgress = true,
                        errorMessage = null,
                    )
                }

                when (val result = authSessionRepository.completeCallback(uri)) {
                    is AuthResult.Success -> {
                        mutableState.update {
                            it.copy(
                                operationInProgress = false,
                                errorMessage = null,
                            )
                        }
                    }

                    is AuthResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                operationInProgress = false,
                                errorMessage = messageFor(result.reason, result.detail),
                            )
                        }
                    }
                }
            }
        }
    }

    fun startSignIn() {
        when (val result = authSessionRepository.beginAuthorization()) {
            is AuthResult.Success -> {
                mutableState.update {
                    it.copy(
                        authorizationUri = result.value,
                        errorMessage = null,
                    )
                }
            }

            is AuthResult.Failure -> {
                mutableState.update {
                    it.copy(
                        errorMessage = messageFor(result.reason, result.detail),
                    )
                }
            }
        }
    }

    fun authorizationUriConsumed() {
        mutableState.update {
            it.copy(authorizationUri = null)
        }
    }

    fun switchAccount(accountId: Long) {
        viewModelScope.launch {
            mutableState.update {
                it.copy(operationInProgress = true, errorMessage = null)
            }

            when (val result = authSessionRepository.switchAccount(accountId)) {
                is AuthResult.Success -> {
                    mutableState.update {
                        it.copy(operationInProgress = false)
                    }
                }

                is AuthResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            operationInProgress = false,
                            errorMessage = messageFor(result.reason, result.detail),
                        )
                    }
                }
            }
        }
    }

    fun signOutActiveAccount() {
        val accountId = state.value.activeAccount?.accountId ?: return

        viewModelScope.launch {
            mutableState.update {
                it.copy(operationInProgress = true, errorMessage = null)
            }

            authSessionRepository.signOut(accountId)

            mutableState.update {
                it.copy(operationInProgress = false)
            }
        }
    }

    fun clearError() {
        mutableState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun messageFor(
        failure: AuthFailure,
        detail: String?,
    ): String {
        val base = when (failure) {
            AuthFailure.NOT_CONFIGURED ->
                "GitHub authentication is not configured in this build."
            AuthFailure.INVALID_CALLBACK ->
                "The GitHub authorization response was invalid."
            AuthFailure.STATE_MISMATCH ->
                "The authorization response failed state validation."
            AuthFailure.AUTHORIZATION_EXPIRED ->
                "The authorization attempt expired. Start sign-in again."
            AuthFailure.GITHUB_DENIED ->
                "GitHub authorization was cancelled or denied."
            AuthFailure.TOKEN_EXCHANGE_FAILED ->
                "The authorization code could not be exchanged securely."
            AuthFailure.REFRESH_EXPIRED ->
                "This GitHub session expired. Sign in again."
            AuthFailure.REFRESH_FAILED ->
                "The GitHub session could not be refreshed."
            AuthFailure.IDENTITY_FAILED ->
                "The authorized GitHub account could not be loaded."
            AuthFailure.ACCOUNT_NOT_FOUND ->
                "The selected GitHub account is no longer available."
            AuthFailure.SECURE_STORAGE_FAILED ->
                "Secure authentication storage is unavailable."
            AuthFailure.UNKNOWN ->
                "Authentication failed."
        }

        return if (detail.isNullOrBlank()) {
            base
        } else {
            "$base $detail"
        }
    }
}
