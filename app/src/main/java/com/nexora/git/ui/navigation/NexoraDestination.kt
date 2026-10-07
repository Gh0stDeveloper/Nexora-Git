package com.nexora.git.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Source
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexora.git.R

enum class NexoraDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val showInBottomBar: Boolean = true,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    EXPLORE("explore", R.string.nav_explore, Icons.Outlined.Explore),
    REPOSITORIES("repositories", R.string.nav_repositories, Icons.Outlined.Source),
    ACTIVITY("activity", R.string.nav_activity, Icons.Outlined.Notifications),
    PROFILE("profile", R.string.nav_profile, Icons.Outlined.AccountCircle),
    SETTINGS(
        "settings",
        R.string.nav_settings,
        Icons.Outlined.Settings,
        showInBottomBar = false,
    ),
}
