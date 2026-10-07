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
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexora.git.R

private data class OnboardingPage(
    val icon: ImageVector,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.Code,
        titleRes = R.string.onboarding_page_one_title,
        descriptionRes = R.string.onboarding_page_one_description,
    ),
    OnboardingPage(
        icon = Icons.Outlined.Security,
        titleRes = R.string.onboarding_page_two_title,
        descriptionRes = R.string.onboarding_page_two_description,
    ),
    OnboardingPage(
        icon = Icons.Outlined.FolderOpen,
        titleRes = R.string.onboarding_page_three_title,
        descriptionRes = R.string.onboarding_page_three_description,
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
                text = stringResource(page.titleRes),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(page.descriptionRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.onboarding_progress, pageIndex + 1, onboardingPages.size),
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
                Text(if (isLast) stringResource(R.string.onboarding_continue) else stringResource(R.string.common_next))
            }

            if (!isLast) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onComplete,
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            if (pageIndex > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    OutlinedButton(onClick = { pageIndex-- }) {
                        Text(stringResource(R.string.common_back))
                    }
                }
            }
        }
    }
}
