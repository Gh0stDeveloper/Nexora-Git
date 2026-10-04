package com.nexora.git.feature.activity

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.nexora.git.ui.components.FoundationScreen

@Composable
fun ActivityScreen(contentPadding: PaddingValues) {
    FoundationScreen(
        title = "Activity",
        description = "Notifications, review requests and repository activity will be integrated through the GitHub platform layer.",
        contentPadding = contentPadding,
    )
}
