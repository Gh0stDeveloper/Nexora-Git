package com.nexora.git.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.git.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.core.settings.AppThemePreference

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_appearance_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemePreference.entries.forEach { preference ->
                            FilterChip(
                                selected = state.themePreference == preference,
                                onClick = { viewModel.setTheme(preference) },
                                label = {
                                    Text(
                                        when (preference) {
                                            AppThemePreference.SYSTEM -> stringResource(R.string.settings_theme_system)
                                            AppThemePreference.LIGHT -> stringResource(R.string.settings_theme_light)
                                            AppThemePreference.DARK -> stringResource(R.string.settings_theme_dark)
                                            AppThemePreference.AMOLED -> stringResource(R.string.settings_theme_amoled)
                                        },
                                    )
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_dynamic_color))
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.dynamicColorEnabled,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_help_onboarding),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_help_onboarding_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::replayOnboarding) {
                        Text(stringResource(R.string.settings_replay_onboarding))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.app_name))
                    Text(
                        text = stringResource(R.string.settings_about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
