package com.nexora.git.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.git.R

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenRepositories: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenActivity: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            HomeActionCard(
                title = stringResource(R.string.home_repositories_title),
                description = stringResource(R.string.home_repositories_description),
                buttonText = stringResource(R.string.home_repositories_button),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                    )
                },
                onClick = onOpenRepositories,
            )
        }

        item {
            HomeActionCard(
                title = stringResource(R.string.home_explore_title),
                description = stringResource(R.string.home_explore_description),
                buttonText = stringResource(R.string.home_explore_button),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = null,
                    )
                },
                onClick = onOpenExplore,
            )
        }

        item {
            HomeActionCard(
                title = stringResource(R.string.home_activity_title),
                description = stringResource(R.string.home_activity_description),
                buttonText = stringResource(R.string.home_activity_button),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                    )
                },
                onClick = onOpenActivity,
            )
        }

        item {
            Text(
                text = stringResource(R.string.home_auth_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    description: String,
    buttonText: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                icon()
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = description,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
            ) {
                Text(buttonText)
            }
        }
    }
}
