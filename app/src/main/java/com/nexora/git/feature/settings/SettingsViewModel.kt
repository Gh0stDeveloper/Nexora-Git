package com.nexora.git.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.auth.AuthConfig
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.settings.AppLanguage
import com.nexora.git.core.settings.AppThemeMode
import com.nexora.git.core.settings.ProductPreferences
import com.nexora.git.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val product: ProductPreferences = ProductPreferences(),
    val editorIndentStyle: EditorIndentStyle = EditorIndentStyle.SPACES_4,
    val privacyUrl: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val authConfig: AuthConfig,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        repository.productPreferences,
        repository.editorIndentStyle,
    ) { product, indent ->
        SettingsUiState(
            product = product,
            editorIndentStyle = indent,
            privacyUrl = authConfig.brokerBaseUrl
                .takeIf { authConfig.isConfigured }
                ?.plus("/privacy"),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setTheme(mode: AppThemeMode) = launch {
        repository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = launch {
        repository.setDynamicColor(enabled)
    }

    fun setLanguage(language: AppLanguage) = launch {
        repository.setAppLanguage(language)
    }

    fun setIndentStyle(style: EditorIndentStyle) = launch {
        repository.setEditorIndentStyle(style)
    }

    fun setConfirmForcePush(enabled: Boolean) = launch {
        repository.setConfirmForcePush(enabled)
    }

    fun setWifiOnlyLargeTransfers(enabled: Boolean) = launch {
        repository.setWifiOnlyLargeTransfers(enabled)
    }

    fun reset() = launch {
        repository.resetProductPreferences()
    }

    fun replayOnboarding() = launch {
        repository.setOnboardingCompleted(false)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }
}
