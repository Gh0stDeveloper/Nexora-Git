package com.nexora.git.feature.auth

import android.net.Uri
import com.nexora.git.core.auth.AuthAccountSummary

data class AuthUiState(
    val loading: Boolean = true,
    val configured: Boolean = true,
    val activeAccount: AuthAccountSummary? = null,
    val accounts: List<AuthAccountSummary> = emptyList(),
    val authorizationUri: Uri? = null,
    val operationInProgress: Boolean = false,
    val errorMessage: String? = null,
) {
    val signedIn: Boolean
        get() = activeAccount != null
}
