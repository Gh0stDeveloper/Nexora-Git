package com.nexora.git.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexora.git.R
import com.nexora.git.core.editor.EditorIndentStyle
import com.nexora.git.core.settings.AppLanguage
import com.nexora.git.core.settings.AppThemeMode

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
            top = 14.dp,
            bottom = 32.dp,
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
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_theme))
                ChoiceRow {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.product.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                        AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                        AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                        AppThemeMode.AMOLED -> stringResource(R.string.theme_amoled)
                                    },
                                )
                            },
                        )
                    }
                }
                ToggleRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = state.product.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_language)) {
                ChoiceRow {
                    AppLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = state.product.language == language,
                            onClick = { viewModel.setLanguage(language) },
                            label = {
                                Text(
                                    when (language) {
                                        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                                        AppLanguage.SPANISH -> stringResource(R.string.language_spanish)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_editor)) {
                Text(stringResource(R.string.settings_indent))
                ChoiceRow {
                    EditorIndentStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.editorIndentStyle == style,
                            onClick = { viewModel.setIndentStyle(style) },
                            label = {
                                Text(
                                    when (style) {
                                        EditorIndentStyle.TABS -> stringResource(R.string.indent_tabs)
                                        EditorIndentStyle.SPACES_2 -> stringResource(R.string.indent_spaces_2)
                                        EditorIndentStyle.SPACES_4 -> stringResource(R.string.indent_spaces_4)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_git)) {
                ToggleRow(
                    title = stringResource(R.string.settings_confirm_force_push),
                    summary = stringResource(R.string.settings_confirm_force_push_summary),
                    checked = state.product.confirmForcePush,
                    onCheckedChange = viewModel::setConfirmForcePush,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_network)) {
                ToggleRow(
                    title = stringResource(R.string.settings_wifi_only),
                    summary = stringResource(R.string.settings_wifi_only_summary),
                    checked = state.product.wifiOnlyLargeTransfers,
                    onCheckedChange = viewModel::setWifiOnlyLargeTransfers,
                )
            }
        }

        item { InfoCard(R.string.settings_accounts, R.string.settings_accounts_summary) }
        item { InfoCard(R.string.settings_security, R.string.settings_security_summary) }
        item { InfoCard(R.string.settings_updates, R.string.settings_updates_summary) }
        item { InfoCard(R.string.settings_privacy, R.string.settings_privacy_summary) }
        item { InfoCard(R.string.settings_diagnostics, R.string.settings_diagnostics_summary) }
        item { InfoCard(R.string.settings_about, R.string.settings_about_summary) }

        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::replayOnboarding,
            ) {
                Text(stringResource(R.string.onboarding_replay))
            }
        }

        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = viewModel::reset,
            ) {
                Text(stringResource(R.string.action_reset))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun InfoCard(
    titleRes: Int,
    summaryRes: Int,
) {
    SettingsCard(title = stringResource(titleRes)) {
        Text(
            text = stringResource(summaryRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
