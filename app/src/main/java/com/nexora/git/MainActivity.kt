package com.nexora.git

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexora.git.core.auth.AuthCallbackBus
import com.nexora.git.core.settings.AppLanguage
import com.nexora.git.core.settings.AppThemeMode
import com.nexora.git.core.settings.SettingsRepository
import com.nexora.git.ui.NexoraGitApp
import com.nexora.git.ui.theme.NexoraGitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authCallbackBus: AuthCallbackBus

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        observeApplicationLanguage()

        setContent {
            val themeMode = settingsRepository.themeMode
                .collectAsStateWithLifecycle(
                    initialValue = AppThemeMode.SYSTEM,
                )
                .value
            val dynamicColor = settingsRepository.dynamicColor
                .collectAsStateWithLifecycle(
                    initialValue = false,
                )
                .value

            NexoraGitTheme(
                mode = themeMode,
                dynamicColor = dynamicColor,
            ) {
                NexoraGitApp()
            }
        }

        intent?.data?.let(authCallbackBus::dispatch)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(authCallbackBus::dispatch)
    }

    private fun observeApplicationLanguage() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.appLanguage
                    .distinctUntilChanged()
                    .collect(::applyLanguage)
            }
        }
    }

    private fun applyLanguage(language: AppLanguage) {
        val locales = language.languageTag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()

        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
