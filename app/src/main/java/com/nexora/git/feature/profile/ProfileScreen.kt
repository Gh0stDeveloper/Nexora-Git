package com.nexora.git.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexora.git.core.auth.AuthAccountSummary

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    activeAccount: AuthAccountSummary,
    accounts: List<AuthAccountSummary>,
    operationInProgress: Boolean,
    onSwitchAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = activeAccount.name ?: activeAccount.login,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "@" + activeAccount.login,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (accounts.size > 1) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.titleMedium,
            )

            accounts.forEach { account ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (account.accountId != activeAccount.accountId) {
                            onSwitchAccount(account.accountId)
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(account.name ?: account.login)
                            Text(
                                text = "@" + account.login,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (account.accountId == activeAccount.accountId) {
                            Text(
                                text = "Active",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !operationInProgress,
            onClick = onAddAccount,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
            )
            Text(" Add GitHub account")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !operationInProgress,
            onClick = onSignOut,
        ) {
            Icon(
                imageVector = Icons.Outlined.Logout,
                contentDescription = null,
            )
            Text(" Sign out")
        }
    }
}
