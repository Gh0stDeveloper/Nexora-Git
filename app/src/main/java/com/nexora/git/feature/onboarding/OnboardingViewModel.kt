package com.nexora.git.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.git.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingAction {
    CLONE,
    IMPORT,
    CREATE,
    SKIP,
}

data class OnboardingUiState(
    val loaded: Boolean = false,
    val completed: Boolean = false,
    val pendingAction: OnboardingAction? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.onboardingCompleted.collect { completed ->
                mutableState.update {
                    it.copy(
                        loaded = true,
                        completed = completed,
                    )
                }
            }
        }
    }

    fun complete(action: OnboardingAction) {
        mutableState.update {
            it.copy(pendingAction = action)
        }
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    fun consumePendingAction() {
        mutableState.update {
            it.copy(pendingAction = null)
        }
    }
}
