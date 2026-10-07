package com.nexora.git.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexora.git.R

@Composable
fun OnboardingScreen(
    onAction: (OnboardingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OnboardingInfoCard(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                )
            },
            title = stringResource(R.string.onboarding_auth_title),
            body = stringResource(R.string.onboarding_auth_body),
        )
        OnboardingInfoCard(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                )
            },
            title = stringResource(R.string.onboarding_storage_title),
            body = stringResource(R.string.onboarding_storage_body),
        )

        Text(
            text = stringResource(R.string.onboarding_choose),
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(OnboardingAction.CLONE) },
        ) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
            Text(stringResource(R.string.onboarding_clone))
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(OnboardingAction.IMPORT) },
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Text(stringResource(R.string.onboarding_import))
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(OnboardingAction.CREATE) },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(stringResource(R.string.onboarding_create))
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(OnboardingAction.SKIP) },
        ) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }
}

@Composable
private fun OnboardingInfoCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
