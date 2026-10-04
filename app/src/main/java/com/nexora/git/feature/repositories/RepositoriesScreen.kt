package com.nexora.git.feature.repositories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.nexora.git.ui.components.FoundationScreen

@Composable
fun RepositoriesScreen(contentPadding: PaddingValues) {
    FoundationScreen(
        title = "Repositories",
        description = "Local workspaces and authorized GitHub repositories will appear here as repository features are implemented.",
        contentPadding = contentPadding,
    )
}
