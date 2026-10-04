package com.nexora.git.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingCompleted: Flow<Boolean> =
        context.nexoraSettingsDataStore.data.map { preferences ->
            preferences[Keys.onboardingCompleted] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.nexoraSettingsDataStore.edit { preferences ->
            preferences[Keys.onboardingCompleted] = completed
        }
    }
}
