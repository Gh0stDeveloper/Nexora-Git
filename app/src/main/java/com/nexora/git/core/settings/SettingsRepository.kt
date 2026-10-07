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

enum class AppThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

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
        val themePreference = stringPreferencesKey("theme_preference")
        val dynamicColorEnabled = booleanPreferencesKey("dynamic_color_enabled")
    }

    val onboardingCompleted: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.onboardingCompleted] ?: false
        }

    val activeAccountId: Flow<Long?> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.activeAccountId]
        }

    val themePreference: Flow<AppThemePreference> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.themePreference]
                ?.let { stored ->
                    runCatching {
                        AppThemePreference.valueOf(stored)
                    }.getOrNull()
                }
                ?: AppThemePreference.SYSTEM
        }

    val dynamicColorEnabled: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.dynamicColorEnabled] ?: false
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

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.onboardingCompleted] = completed
        }
    }

    suspend fun setThemePreference(preference: AppThemePreference) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.themePreference] = preference.name
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.dynamicColorEnabled] = enabled
        }
    }

    suspend fun setEditorIndentStyle(style: EditorIndentStyle) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.editorIndentStyle] = style.name
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
