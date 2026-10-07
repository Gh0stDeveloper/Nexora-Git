package com.nexora.git.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.Code,
        title = "A real Git workspace on Android",
        description =
            "Nexora Git works with real local repositories, branches, commits, diffs and GitHub workflows without requiring Termux.",
    ),
    OnboardingPage(
        icon = Icons.Outlined.Security,
        title = "GitHub authentication stays on GitHub",
        description =
            "Sign-in uses GitHub's official authorization page, PKCE and a verified HTTPS callback. Nexora Git never asks for your GitHub password, passkey or 2FA secret.",
    ),
    OnboardingPage(
        icon = Icons.Outlined.FolderOpen,
        title = "Start with the workflow you already use",
        description =
            "After sign-in you can clone a repository, import an existing local project or create a new repository and continue from your phone.",
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    val isLast = pageIndex == onboardingPages.lastIndex

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(22.dp))

            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = (pageIndex + 1).toString() + " / " + onboardingPages.size,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (isLast) onComplete() else pageIndex++
                },
            ) {
                Text(if (isLast) "Continue to sign in" else "Next")
            }

            if (!isLast) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onComplete,
                ) {
                    Text("Skip introduction")
                }
            }

            if (pageIndex > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    OutlinedButton(onClick = { pageIndex-- }) {
                        Text("Back")
                    }
                }
            }
        }
    }
}
