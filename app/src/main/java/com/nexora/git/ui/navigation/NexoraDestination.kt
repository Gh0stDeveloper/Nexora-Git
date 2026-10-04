package com.nexora.git.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Source
import androidx.compose.ui.graphics.vector.ImageVector

enum class NexoraDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    EXPLORE("explore", "Explore", Icons.Outlined.Explore),
    REPOSITORIES("repositories", "Repositories", Icons.Outlined.Source),
    ACTIVITY("activity", "Activity", Icons.Outlined.Notifications),
    PROFILE("profile", "Profile", Icons.Outlined.AccountCircle),
}
