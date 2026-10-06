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
import androidx.compose.ui.unit.dp

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
                    text = "Nexora Git",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "A complete Android workspace for local Git and GitHub development.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            HomeActionCard(
                title = "Repositories",
                description = "Create, clone or import a project, then browse code and use the full local Git workflow.",
                buttonText = "Open repositories",
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
                title = "Explore GitHub",
                description = "Search repositories, users and code using the active GitHub account.",
                buttonText = "Search GitHub",
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
                title = "Activity",
                description = "Review recent account activity and open the GitHub notification inbox when needed.",
                buttonText = "View activity",
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
                text = "GitHub authentication uses the official web flow. Nexora Git never asks for your GitHub password or passkey.",
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
