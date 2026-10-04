package com.nexora.git.feature.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.nexora.git.ui.components.FoundationScreen

@Composable
fun ProfileScreen(contentPadding: PaddingValues) {
    FoundationScreen(
        title = "Profile",
        description = "The authenticated GitHub profile and account switcher will be connected during the authentication and platform phases.",
        contentPadding = contentPadding,
    )
}
