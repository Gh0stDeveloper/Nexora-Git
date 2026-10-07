package com.nexora.git

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.auth.AuthCallbackBus
import com.nexora.git.core.settings.AppThemePreference
import com.nexora.git.ui.AppPreferencesViewModel
import com.nexora.git.ui.NexoraGitApp
import com.nexora.git.ui.theme.NexoraGitTheme
import com.nexora.git.ui.theme.NexoraThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authCallbackBus: AuthCallbackBus

    private val appPreferencesViewModel: AppPreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themePreference by
                appPreferencesViewModel.themePreference.collectAsStateWithLifecycle()
            val dynamicColor by
                appPreferencesViewModel.dynamicColorEnabled.collectAsStateWithLifecycle()
            val onboardingCompleted by
                appPreferencesViewModel.onboardingCompleted.collectAsStateWithLifecycle()

            NexoraGitTheme(
                mode = themePreference.toThemeMode(),
                dynamicColor = dynamicColor,
            ) {
                NexoraGitApp(
                    onboardingCompleted = onboardingCompleted,
                    onCompleteOnboarding =
                        appPreferencesViewModel::completeOnboarding,
                )
            }
        }

        intent?.data?.let(authCallbackBus::dispatch)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(authCallbackBus::dispatch)
    }
}

private fun AppThemePreference.toThemeMode(): NexoraThemeMode =
    when (this) {
        AppThemePreference.SYSTEM -> NexoraThemeMode.SYSTEM
        AppThemePreference.LIGHT -> NexoraThemeMode.LIGHT
        AppThemePreference.DARK -> NexoraThemeMode.DARK
        AppThemePreference.AMOLED -> NexoraThemeMode.AMOLED
    }
