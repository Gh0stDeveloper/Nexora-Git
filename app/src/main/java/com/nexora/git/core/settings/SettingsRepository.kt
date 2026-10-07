package com.nexora.git.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexora.git.core.editor.EditorIndentStyle
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nexoraSettingsDataStore by preferencesDataStore(
    name = "nexora_git_settings",
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val activeAccountId = longPreferencesKey("active_account_id")
        val editorIndentStyle = stringPreferencesKey("editor_indent_style")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val appLanguage = stringPreferencesKey("app_language")
        val confirmForcePush = booleanPreferencesKey("confirm_force_push")
        val wifiOnlyLargeTransfers = booleanPreferencesKey("wifi_only_large_transfers")
    }

    val onboardingCompleted: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.onboardingCompleted] ?: false
        }

    val activeAccountId: Flow<Long?> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.activeAccountId]
        }

    val editorIndentStyle: Flow<EditorIndentStyle> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.editorIndentStyle]
                ?.let { stored ->
                    runCatching {
                        EditorIndentStyle.valueOf(stored)
                    }.getOrNull()
                }
                ?: EditorIndentStyle.SPACES_4
        }

    val themeMode: Flow<AppThemeMode> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.themeMode]
                ?.let { stored ->
                    runCatching {
                        AppThemeMode.valueOf(stored)
                    }.getOrNull()
                }
                ?: AppThemeMode.SYSTEM
        }

    val dynamicColor: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.dynamicColor] ?: false
        }

    val appLanguage: Flow<AppLanguage> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.appLanguage]
                ?.let { stored ->
                    runCatching {
                        AppLanguage.valueOf(stored)
                    }.getOrNull()
                }
                ?: AppLanguage.SYSTEM
        }

    val confirmForcePush: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.confirmForcePush] ?: true
        }

    val wifiOnlyLargeTransfers: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.wifiOnlyLargeTransfers] ?: false
        }

    val productPreferences: Flow<ProductPreferences> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            ProductPreferences(
                themeMode = preferences[Keys.themeMode]
                    ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                    ?: AppThemeMode.SYSTEM,
                dynamicColor = preferences[Keys.dynamicColor] ?: false,
                language = preferences[Keys.appLanguage]
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.SYSTEM,
                confirmForcePush = preferences[Keys.confirmForcePush] ?: true,
                wifiOnlyLargeTransfers = preferences[Keys.wifiOnlyLargeTransfers] ?: false,
            )
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.onboardingCompleted] = completed
        }
    }

    suspend fun setEditorIndentStyle(style: EditorIndentStyle) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.editorIndentStyle] = style.name
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.themeMode] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.dynamicColor] = enabled
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.appLanguage] = language.name
        }
    }

    suspend fun setConfirmForcePush(enabled: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.confirmForcePush] = enabled
        }
    }

    suspend fun setWifiOnlyLargeTransfers(enabled: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.wifiOnlyLargeTransfers] = enabled
        }
    }

    suspend fun resetProductPreferences() {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences.remove(Keys.themeMode)
            preferences.remove(Keys.dynamicColor)
            preferences.remove(Keys.appLanguage)
            preferences.remove(Keys.confirmForcePush)
            preferences.remove(Keys.wifiOnlyLargeTransfers)
            preferences.remove(Keys.editorIndentStyle)
        }
    }

    suspend fun setActiveAccountId(accountId: Long?) {
        context.nexoraSettingsDataStore.edit { preferences ->
            if (accountId == null) {
                preferences.remove(Keys.activeAccountId)
            } else {
                preferences[Keys.activeAccountId] = accountId
            }
        }
    }
}
