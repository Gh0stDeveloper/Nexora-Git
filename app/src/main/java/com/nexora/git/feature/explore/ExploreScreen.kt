package com.nexora.git.feature.explore

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.nexora.git.ui.components.FoundationScreen

@Composable
fun ExploreScreen(contentPadding: PaddingValues) {
    FoundationScreen(
        title = "Explore",
        description = "Repository, user and code discovery will be connected to GitHub search in the platform phase.",
        contentPadding = contentPadding,
    )
}
